# Razorpay payments (design, not yet implemented)

No payment code is in the app yet. This is the plan the code must follow.

## Rules

- Razorpay **key secret**, webhook secret and database service key live only in the backend environment.
  Never in the APK or the repository.
- The app never decides that a payment succeeded. Only the backend does.

## Flow

1. App asks the backend to create an order for a plan id (`1h`, `6h`, `12h`, `24h`, `lifetime`).
2. Backend reads the price from its own plans table (prices are configurable there; the app's
   displayed price is only a label), calls the Razorpay Orders API, and stores the order with the user id.
3. App opens Razorpay Checkout with the order id and the public key id.
4. App sends `razorpay_order_id`, `razorpay_payment_id`, `razorpay_signature` to the backend.
5. Backend verifies `HMAC_SHA256(order_id + "|" + payment_id, key_secret)` equals the signature.
6. Backend marks the order paid **once** (unique constraint on payment id), then grants the entitlement.
7. The Razorpay webhook (`payment.captured`) does the same fulfilment, so delayed or lost client
   responses are still handled. Duplicate events are ignored because of the unique payment id.

## Entitlements

- Timed plans store `expires_at` computed from **server time** at verified payment.
  Buying while active extends from the current expiry instead of overwriting it.
- Lifetime is a permanent row tied to the account, restored on login.
- The app shows remaining time from the server value and re-checks on start, so changing the phone
  clock does nothing.
- When an entitlement lapses the app returns to Lite. Saved games are never deleted.

## Suggested tables

`plans`, `payment_orders`, `payment_transactions`, `entitlements`, with row-level security so users
can read only their own rows and **cannot write** entitlement or payment rows.

## Backend options

Supabase Edge Functions or any small server (for example FastAPI) work. Start with `create-order`,
`verify-payment` and `razorpay-webhook` endpoints.
