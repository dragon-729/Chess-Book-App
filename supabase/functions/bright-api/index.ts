// Chess-Book payments function. Deploy it with the name: bright-api
// The app calls it with { action: "create_order" | "verify_payment" | "status" }.
//
// Secrets it needs (Supabase > Edge Functions > Secrets):
//   RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are provided by Supabase automatically.
//
// Rules this function follows:
//  * The price comes from the plans table, never from the app.
//  * Premium is granted only after the Razorpay signature is checked AND Razorpay itself
//    confirms the payment is captured for the right order and amount.
//  * Granting is done by the database function fulfil_payment, which is safe to repeat.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// ---- helpers (unit tested) ----
function toHex(buf: ArrayBuffer): string {
  return Array.from(new Uint8Array(buf))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

async function hmacSha256Hex(secret: string, message: string): Promise<string> {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  return toHex(await crypto.subtle.sign("HMAC", key, enc.encode(message)));
}

function safeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}
// ---- end helpers ----

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "Content-Type": "application/json" },
  });
}

const ORDER_ID = /^order_[A-Za-z0-9]{6,40}$/;
const PAYMENT_ID = /^pay_[A-Za-z0-9]{6,40}$/;
const SIGNATURE = /^[a-fA-F0-9]{64}$/;
const PLAN_ID = /^[A-Za-z0-9_-]{1,32}$/;

Deno.serve(async (req: Request): Promise<Response> => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const keyId = Deno.env.get("RAZORPAY_KEY_ID");
  const keySecret = Deno.env.get("RAZORPAY_KEY_SECRET");
  if (!supabaseUrl || !serviceKey || !keyId || !keySecret) {
    return json({ error: "Server is not configured yet" }, 500);
  }

  try {
    const admin = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });

    // 1. Who is calling? Checked here, so this works even with "Verify JWT" turned off.
    const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
    if (!token) return json({ error: "Please sign in again" }, 401);
    const { data: auth, error: authError } = await admin.auth.getUser(token);
    if (authError || !auth?.user) return json({ error: "Please sign in again" }, 401);
    const user = auth.user;

    const body = await req.json().catch(() => null);
    if (!body || typeof body !== "object") return json({ error: "Bad request" }, 400);
    const basic = "Basic " + btoa(`${keyId}:${keySecret}`);

    // ------------------------------------------------------------------ status
    if (body.action === "status") {
      const { data, error } = await admin.rpc("entitlement_status", { p_user_id: user.id });
      if (error) return json({ error: "Could not read your Premium status" }, 500);
      return json(data);
    }

    // ------------------------------------------------------------ create_order
    if (body.action === "create_order") {
      const planId = body.plan ?? body.plan_id;
      if (typeof planId !== "string" || !PLAN_ID.test(planId)) {
        return json({ error: "Invalid plan" }, 400);
      }

      const { data: plan } = await admin
        .from("plans").select("*").eq("id", planId).eq("is_active", true).maybeSingle();
      if (!plan) return json({ error: "Plan not found" }, 404);

      // Basic abuse guard: no more than 10 unpaid orders in 10 minutes per user.
      const since = new Date(Date.now() - 10 * 60 * 1000).toISOString();
      const { count } = await admin
        .from("payment_orders")
        .select("id", { count: "exact", head: true })
        .eq("user_id", user.id).eq("status", "created").gte("created_at", since);
      if ((count ?? 0) >= 10) return json({ error: "Too many attempts. Please wait a few minutes." }, 429);

      const amount = Math.round(plan.price * 100); // plans.price is rupees; Razorpay wants paise
      const currency = plan.currency || "INR";

      const rz = await fetch("https://api.razorpay.com/v1/orders", {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: basic },
        body: JSON.stringify({
          amount,
          currency,
          receipt: "cb_" + crypto.randomUUID().replaceAll("-", ""),
          notes: { user_id: user.id, plan_id: plan.id },
        }),
      });
      const order = await rz.json().catch(() => ({}));
      if (!rz.ok || !order?.id) {
        console.error("Razorpay order failed", rz.status, order?.error?.description);
        return json({ error: "Could not create the payment order" }, 502);
      }

      const { error: insertError } = await admin.from("payment_orders").insert({
        user_id: user.id,
        plan_id: plan.id,
        amount,
        currency,
        razorpay_order_id: order.id,
        status: "created",
      });
      if (insertError) {
        console.error("Order insert failed", insertError.message);
        return json({ error: "Could not save the payment order" }, 500);
      }
      return json({ order_id: order.id, amount, currency, plan: plan.id });
    }

    // ---------------------------------------------------------- verify_payment
    if (body.action === "verify_payment") {
      const { order_id, payment_id, signature } = body;
      if (
        typeof order_id !== "string" || !ORDER_ID.test(order_id) ||
        typeof payment_id !== "string" || !PAYMENT_ID.test(payment_id) ||
        typeof signature !== "string" || !SIGNATURE.test(signature)
      ) {
        return json({ error: "Bad payment details" }, 400);
      }

      // The order must exist and belong to THIS user.
      const { data: order } = await admin
        .from("payment_orders").select("*").eq("razorpay_order_id", order_id).maybeSingle();
      if (!order || order.user_id !== user.id) return json({ error: "Order not found" }, 404);

      let pending = false;
      if (order.status !== "paid") {
        // (a) The signature proves Razorpay produced this order_id + payment_id pair.
        const expected = await hmacSha256Hex(keySecret, `${order_id}|${payment_id}`);
        if (!safeEqual(signature.toLowerCase(), expected)) {
          return json({ error: "Payment signature is not valid" }, 400);
        }

        // (b) Ask Razorpay directly, so we trust Razorpay, not the phone.
        const pr = await fetch(`https://api.razorpay.com/v1/payments/${payment_id}`, {
          headers: { Authorization: basic },
        });
        if (!pr.ok) return json({ error: "Could not confirm the payment with Razorpay yet" }, 502);
        const p = await pr.json();
        if (p.order_id !== order_id || p.amount !== order.amount || p.currency !== order.currency) {
          return json({ error: "Payment does not match the order" }, 400);
        }

        if (p.status === "captured") {
          const { data: result, error } = await admin.rpc("fulfil_payment", {
            p_razorpay_order_id: order_id,
            p_razorpay_payment_id: payment_id,
            p_amount_paise: order.amount,
          });
          if (error) {
            console.error("fulfil_payment failed", error.message);
            return json({ error: "Could not activate Premium yet. It will retry automatically." }, 500);
          }
          if (result !== "fulfilled" && result !== "already_paid") {
            console.error("fulfil_payment returned", result);
            return json({ error: "Payment could not be matched to a plan" }, 409);
          }
        } else if (p.status === "authorized") {
          pending = true; // Razorpay will capture shortly; the webhook then activates Premium.
        } else {
          return json({ error: "The payment was not successful" }, 400);
        }
      }

      const { data: status, error: statusError } = await admin.rpc("entitlement_status", { p_user_id: user.id });
      if (statusError) return json({ error: "Could not read your Premium status" }, 500);
      return json({ ...status, pending });
    }

    return json({ error: "Unknown action" }, 400);
  } catch (err) {
    console.error("bright-api error", err instanceof Error ? err.message : err);
    return json({ error: "Something went wrong. Please try again." }, 500);
  }
});
