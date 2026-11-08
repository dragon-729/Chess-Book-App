// Runs the REAL function files under Node with a fake database and fake Razorpay.
// Usage:  node --experimental-strip-types --test backend/tests/functions.test.mjs
import test from "node:test";
import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const FN = join(here, "..", "supabase", "functions");
const OUT = join(process.env.TEST_TMP || tmpdir(), "chessbook-fn-test");
mkdirSync(OUT, { recursive: true });

const KEY_SECRET = "test_key_secret";
const WEBHOOK_SECRET = "test_webhook_secret";
const U1 = { id: "user-1" };
const U2 = { id: "user-2" };

// ---------- fakes ----------
function freshDb() {
  return {
    env: {
      SUPABASE_URL: "https://x.supabase.co", SUPABASE_SERVICE_ROLE_KEY: "svc",
      RAZORPAY_KEY_ID: "rzp_test_abc", RAZORPAY_KEY_SECRET: KEY_SECRET, RAZORPAY_WEBHOOK_SECRET: WEBHOOK_SECRET,
    },
    users: { "tok-1": U1, "tok-2": U2 },
    tables: {
      plans: [
        { id: "1h", name: "1 Hour Premium", price: 2, currency: "INR", duration_hours: 1, is_lifetime: false, is_active: true },
        { id: "lifetime", name: "Lifetime", price: 50, currency: "INR", duration_hours: null, is_lifetime: true, is_active: true },
        { id: "old", name: "Old", price: 9, currency: "INR", duration_hours: 1, is_lifetime: false, is_active: false },
      ],
      payment_orders: [], entitlements: [],
    },
    razorpayPayments: {}, fetchCalls: [], rpcCalls: [],
  };
}

class Q {
  constructor(db, t) { this.db = db; this.t = t; this.f = []; this.mode = "select"; this.opts = null; }
  select(_c, opts) { this.opts = opts || null; return this; }
  insert(row) { this.mode = "insert"; this.row = row; return this; }
  eq(c, v) { this.f.push((r) => r[c] === v); return this; }
  gte(c, v) { this.f.push((r) => r[c] >= v); return this; }
  rows() { return this.db.tables[this.t].filter((r) => this.f.every((g) => g(r))); }
  maybeSingle() { return Promise.resolve({ data: this.rows()[0] ?? null, error: null }); }
  then(res, rej) {
    let out;
    if (this.mode === "insert") {
      this.db.tables[this.t].push({ created_at: new Date().toISOString(), ...this.row });
      out = { data: null, error: null };
    } else if (this.opts?.head) out = { count: this.rows().length, data: null, error: null };
    else out = { data: this.rows(), error: null };
    return Promise.resolve(out).then(res, rej);
  }
}

function fakeClient(db) {
  const status = (uid) => {
    const e = db.tables.entitlements.find((x) => x.user_id === uid);
    const premium = !!e && (e.is_lifetime || (e.expires_at && new Date(e.expires_at) > new Date()));
    return { premium, lifetime: !!e?.is_lifetime, expires_at: e?.is_lifetime ? null : (e?.expires_at ?? null), server_time: new Date().toISOString() };
  };
  return {
    auth: { getUser: async (tok) => db.users[tok] ? { data: { user: db.users[tok] }, error: null } : { data: { user: null }, error: { message: "bad token" } } },
    from: (t) => new Q(db, t),
    rpc: async (name, args) => {
      db.rpcCalls.push({ name, args });
      if (name === "entitlement_status") return { data: status(args.p_user_id), error: null };
      if (name === "fulfil_payment") {
        const o = db.tables.payment_orders.find((x) => x.razorpay_order_id === args.p_razorpay_order_id);
        if (!o) return { data: "order_not_found", error: null };
        if (o.status === "paid") return { data: "already_paid", error: null };
        if (o.amount !== args.p_amount_paise) return { data: "amount_mismatch", error: null };
        o.status = "paid";
        const pl = db.tables.plans.find((p) => p.id === o.plan_id);
        db.tables.entitlements.push({ user_id: o.user_id, is_lifetime: pl.is_lifetime, expires_at: pl.is_lifetime ? null : new Date(Date.now() + pl.duration_hours * 3600e3).toISOString() });
        return { data: "fulfilled", error: null };
      }
      return { data: null, error: { message: "unknown rpc" } };
    },
  };
}

let seq = 0;
async function load(name, db) {
  globalThis.__db = db;
  let handler;
  globalThis.Deno = { env: { get: (k) => db.env[k] }, serve: (h) => { handler = h; } };
  globalThis.fetch = async (url, init) => {
    db.fetchCalls.push({ url: String(url), init });
    if (String(url).endsWith("/v1/orders")) {
      const b = JSON.parse(init.body);
      return Response.json({ id: "order_TEST" + (++seq) + "abcdef", amount: b.amount, currency: b.currency });
    }
    const m = String(url).match(/\/v1\/payments\/(pay_\w+)$/);
    if (m && db.razorpayPayments[m[1]]) return Response.json(db.razorpayPayments[m[1]]);
    return new Response("{}", { status: 404 });
  };
  const src = readFileSync(join(FN, name, "index.ts"), "utf8")
    .replace(/import \{ createClient \} from "https:\/\/esm\.sh\/[^"]+";/, 'import { createClient } from "./stub.mjs";');
  assert.ok(src.includes('./stub.mjs'), "import line was replaced");
  writeFileSync(join(OUT, "stub.mjs"), "export const createClient = () => globalThis.__fakeClient();\n");
  globalThis.__fakeClient = () => fakeClient(db);
  const file = join(OUT, `${name}-${++seq}.ts`);
  writeFileSync(file, src);
  await import(file);
  assert.ok(handler, "function registered a handler");
  return handler;
}

const call = (h, body, token = "tok-1", extra = {}) =>
  h(new Request("http://x/", { method: "POST", headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}), ...extra }, body: typeof body === "string" ? body : JSON.stringify(body) }));
const sign = (o, p) => createHmac("sha256", KEY_SECRET).update(`${o}|${p}`).digest("hex");

async function createOrder(h, plan = "1h", token = "tok-1") {
  const r = await call(h, { action: "create_order", plan }, token);
  return { r, j: await r.json() };
}

// ---------- helper functions ----------
test("HMAC helper matches Node's crypto, safeEqual is strict", async () => {
  const src = readFileSync(join(FN, "bright-api", "index.ts"), "utf8");
  const block = src.split("// ---- helpers (unit tested) ----")[1].split("// ---- end helpers ----")[0];
  const f = join(OUT, "helpers.ts");
  writeFileSync(f, block + "\nexport { hmacSha256Hex, safeEqual };\n");
  const { hmacSha256Hex, safeEqual } = await import(f);
  const expected = createHmac("sha256", "secret").update("order_1|pay_1").digest("hex");
  assert.equal(await hmacSha256Hex("secret", "order_1|pay_1"), expected);
  assert.equal(safeEqual("abc", "abc"), true);
  assert.equal(safeEqual("abc", "abd"), false);
  assert.equal(safeEqual("abc", "abcd"), false);
  // both functions must carry identical helper code
  const w = readFileSync(join(FN, "razorpay-webhook", "index.ts"), "utf8").split("// ---- helpers (unit tested) ----")[1].split("// ---- end helpers ----")[0];
  assert.equal(w, block);
});

// ---------- bright-api ----------
test("rejects calls without a valid login", async () => {
  const h = await load("bright-api", freshDb());
  assert.equal((await call(h, { action: "status" }, null)).status, 401);
  assert.equal((await call(h, { action: "status" }, "nope")).status, 401);
});

test("create_order: price comes from the server (Rs 2 -> 200 paise), app cannot change it", async () => {
  const db = freshDb(); const h = await load("bright-api", db);
  const r = await call(h, { action: "create_order", plan: "1h", amount: 1, price: 1 });
  assert.equal(r.status, 200);
  const j = await r.json();
  assert.equal(j.amount, 200);
  assert.match(j.order_id, /^order_/);
  const sent = JSON.parse(db.fetchCalls[0].init.body);
  assert.equal(sent.amount, 200);
  assert.equal(db.tables.payment_orders[0].user_id, "user-1");
  assert.equal(db.tables.payment_orders[0].amount, 200);
  assert.ok(!JSON.stringify(j).includes(KEY_SECRET));
  assert.ok(db.fetchCalls[0].init.headers.Authorization.startsWith("Basic "));
});

test("create_order: lifetime Rs 50 -> 5000 paise; unknown, inactive and malformed plans refused", async () => {
  const db = freshDb(); const h = await load("bright-api", db);
  assert.equal((await createOrder(h, "lifetime")).j.amount, 5000);
  assert.equal((await createOrder(h, "nope")).r.status, 404);
  assert.equal((await createOrder(h, "old")).r.status, 404);
  assert.equal((await createOrder(h, "1h; drop table")).r.status, 400);
});

test("create_order: stops order flooding", async () => {
  const db = freshDb(); const h = await load("bright-api", db);
  for (let i = 0; i < 10; i++) assert.equal((await createOrder(h)).r.status, 200);
  assert.equal((await createOrder(h)).r.status, 429);
});

test("verify_payment: forged signature is refused and nothing is granted", async () => {
  const db = freshDb(); const h = await load("bright-api", db);
  const { j } = await createOrder(h);
  db.razorpayPayments["pay_FAKE12345"] = { order_id: j.order_id, amount: 200, currency: "INR", status: "captured" };
  const r = await call(h, { action: "verify_payment", order_id: j.order_id, payment_id: "pay_FAKE12345", signature: "a".repeat(64) });
  assert.equal(r.status, 400);
  assert.equal(db.rpcCalls.filter((c) => c.name === "fulfil_payment").length, 0);
  assert.equal(db.tables.entitlements.length, 0);
});

test("verify_payment: genuine captured payment activates Premium; repeating is harmless", async () => {
  const db = freshDb(); const h = await load("bright-api", db);
  const { j } = await createOrder(h);
  db.razorpayPayments["pay_REAL12345"] = { order_id: j.order_id, amount: 200, currency: "INR", status: "captured" };
  const body = { action: "verify_payment", order_id: j.order_id, payment_id: "pay_REAL12345", signature: sign(j.order_id, "pay_REAL12345") };
  const r1 = await call(h, body);
  assert.equal(r1.status, 200);
  const s1 = await r1.json();
  assert.equal(s1.premium, true); assert.equal(s1.pending, false);
  const r2 = await call(h, body);
  assert.equal(r2.status, 200);
  assert.equal(db.tables.entitlements.length, 1);
});

test("verify_payment: someone else's order is invisible", async () => {
  const db = freshDb(); const h = await load("bright-api", db);
  const { j } = await createOrder(h, "1h", "tok-1");
  db.razorpayPayments["pay_REAL12345"] = { order_id: j.order_id, amount: 200, currency: "INR", status: "captured" };
  const r = await call(h, { action: "verify_payment", order_id: j.order_id, payment_id: "pay_REAL12345", signature: sign(j.order_id, "pay_REAL12345") }, "tok-2");
  assert.equal(r.status, 404);
  assert.equal(db.tables.entitlements.length, 0);
});

test("verify_payment: Razorpay says different amount, failed, or unknown payment -> refused", async () => {
  const db = freshDb(); const h = await load("bright-api", db);
  const { j } = await createOrder(h);
  const b = (p) => ({ action: "verify_payment", order_id: j.order_id, payment_id: p, signature: sign(j.order_id, p) });
  db.razorpayPayments["pay_LOWAMT123"] = { order_id: j.order_id, amount: 100, currency: "INR", status: "captured" };
  assert.equal((await call(h, b("pay_LOWAMT123"))).status, 400);
  db.razorpayPayments["pay_FAILED123"] = { order_id: j.order_id, amount: 200, currency: "INR", status: "failed" };
  assert.equal((await call(h, b("pay_FAILED123"))).status, 400);
  db.razorpayPayments["pay_OTHERORD1"] = { order_id: "order_someoneelse1", amount: 200, currency: "INR", status: "captured" };
  assert.equal((await call(h, b("pay_OTHERORD1"))).status, 400);
  assert.equal((await call(h, b("pay_UNKNOWN123"))).status, 502);
  assert.equal(db.tables.entitlements.length, 0);
});

test("verify_payment: authorized-but-not-captured is reported as pending, not granted", async () => {
  const db = freshDb(); const h = await load("bright-api", db);
  const { j } = await createOrder(h);
  db.razorpayPayments["pay_AUTH12345"] = { order_id: j.order_id, amount: 200, currency: "INR", status: "authorized" };
  const r = await call(h, { action: "verify_payment", order_id: j.order_id, payment_id: "pay_AUTH12345", signature: sign(j.order_id, "pay_AUTH12345") });
  const s = await r.json();
  assert.equal(r.status, 200); assert.equal(s.pending, true); assert.equal(s.premium, false);
});

test("status and bad input", async () => {
  const h = await load("bright-api", freshDb());
  const s = await (await call(h, { action: "status" })).json();
  assert.equal(s.premium, false);
  assert.equal((await call(h, { action: "hack" })).status, 400);
  assert.equal((await call(h, "not json")).status, 400);
  assert.equal((await call(h, { action: "verify_payment", order_id: "x", payment_id: "y", signature: "z" })).status, 400);
});

// ---------- razorpay-webhook ----------
const wsign = (raw, secret = WEBHOOK_SECRET) => createHmac("sha256", secret).update(raw).digest("hex");
const hook = (h, event, { sig, secret } = {}) => {
  const raw = JSON.stringify(event);
  return h(new Request("http://x/", { method: "POST", headers: { "x-razorpay-signature": sig ?? wsign(raw, secret) }, body: raw }));
};
const captured = (order, id = "pay_WH1234567", amount = 200, status = "captured") =>
  ({ event: "payment.captured", payload: { payment: { entity: { id, order_id: order, amount, status } } } });

test("webhook: wrong, missing or other-secret signature is rejected", async () => {
  const db = freshDb(); const h = await load("razorpay-webhook", db);
  assert.equal((await hook(h, captured("order_x"), { sig: "00" })).status, 400);
  assert.equal((await hook(h, captured("order_x"), { secret: "other" })).status, 400);
  const raw = JSON.stringify(captured("order_x"));
  assert.equal((await h(new Request("http://x/", { method: "POST", body: raw }))).status, 400);
  assert.equal(db.rpcCalls.length, 0);
});

test("webhook: captured payment activates Premium once, duplicates are harmless", async () => {
  const db = freshDb();
  const api = await load("bright-api", db);
  const { j } = await createOrder(api);
  const h = await load("razorpay-webhook", db);
  const r1 = await hook(h, captured(j.order_id));
  assert.equal(r1.status, 200); assert.equal(await r1.text(), "fulfilled");
  const r2 = await hook(h, captured(j.order_id));
  assert.equal(await r2.text(), "already_paid");
  assert.equal(db.tables.entitlements.length, 1);
});

test("webhook: wrong amount is not granted; other events and unknown orders get 200", async () => {
  const db = freshDb();
  const api = await load("bright-api", db);
  const { j } = await createOrder(api);
  const h = await load("razorpay-webhook", db);
  assert.equal(await (await hook(h, captured(j.order_id, "pay_WH1234567", 1))).text(), "amount_mismatch");
  assert.equal(db.tables.entitlements.length, 0);
  assert.equal((await hook(h, { event: "payment.failed", payload: {} })).status, 200);
  assert.equal(await (await hook(h, captured("order_notours1"))).text(), "order_not_found");
  assert.equal(await (await hook(h, captured(j.order_id, "pay_WH7654321", 200, "authorized"))).text(), "Ignored");
});
