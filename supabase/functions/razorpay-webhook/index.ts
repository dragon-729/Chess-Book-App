// Razorpay webhook. This is the safety net: if the phone loses its connection after paying,
// Razorpay still tells us, and Premium is activated anyway.
//
// IMPORTANT: when deploying, turn OFF "Verify JWT" for this function.
// Razorpay cannot send a Supabase login token. Security comes from the signature check below.
//
// Secret it needs: RAZORPAY_WEBHOOK_SECRET (the secret you type in the Razorpay dashboard
// when you create the webhook). In Razorpay, tick the events: payment.captured, order.paid
// Webhook URL: https://<your-project>.supabase.co/functions/v1/razorpay-webhook

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

Deno.serve(async (req: Request): Promise<Response> => {
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });

  const webhookSecret = Deno.env.get("RAZORPAY_WEBHOOK_SECRET");
  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!webhookSecret || !supabaseUrl || !serviceKey) {
    console.error("Webhook is not configured (missing secrets)");
    return new Response("Not configured", { status: 500 });
  }

  try {
    // 1. Signature over the RAW body. Anything unsigned or wrongly signed is rejected.
    const rawBody = await req.text();
    const received = (req.headers.get("x-razorpay-signature") ?? "").toLowerCase();
    const expected = await hmacSha256Hex(webhookSecret, rawBody);
    if (!received || !safeEqual(received, expected)) {
      return new Response("Invalid signature", { status: 400 });
    }

    // 2. Only successful payments matter.
    const event = JSON.parse(rawBody);
    if (event.event !== "payment.captured" && event.event !== "order.paid") {
      return new Response("Ignored", { status: 200 });
    }

    const payment = event.payload?.payment?.entity;
    const orderId: string | undefined = payment?.order_id;
    const paymentId: string | undefined = payment?.id;
    const amount: number | undefined = payment?.amount;
    if (!orderId || !paymentId || typeof amount !== "number" || payment?.status !== "captured") {
      return new Response("Ignored", { status: 200 });
    }

    // 3. One atomic database step: checks the amount, marks paid, grants Premium.
    //    Repeating it (Razorpay retries, or the app already verified) changes nothing.
    const admin = createClient(supabaseUrl, serviceKey, { auth: { persistSession: false } });
    const { data: result, error } = await admin.rpc("fulfil_payment", {
      p_razorpay_order_id: orderId,
      p_razorpay_payment_id: paymentId,
      p_amount_paise: amount,
    });
    if (error) {
      console.error("fulfil_payment failed", error.message);
      return new Response("Try again", { status: 500 }); // Razorpay will retry
    }
    if (result === "amount_mismatch" || result === "plan_not_found") {
      console.error("Payment needs manual review:", orderId, paymentId, result);
    }
    // order_not_found: not one of ours. Answer 200 so Razorpay stops retrying.
    return new Response(String(result), { status: 200 });
  } catch (err) {
    console.error("webhook error", err instanceof Error ? err.message : err);
    return new Response("Try again", { status: 500 });
  }
});
