# Premium backend (Supabase + Razorpay)

Files:

| File | What it is | Where it goes |
|---|---|---|
| `supabase/schema.sql` | Tables, security rules, payment fulfilment | Supabase > SQL Editor |
| `supabase/functions/bright-api/index.ts` | Create order, verify payment, status (what the app calls) | Edge function named `bright-api` |
| `supabase/functions/razorpay-webhook/index.ts` | Razorpay safety net | Edge function named `razorpay-webhook`, **Verify JWT off** |

## Setup, in order

1. **SQL.** Supabase > SQL Editor > New query > paste all of `schema.sql` > Run. You should see your 5 plans listed.
2. **Anonymous sign-in.** Supabase > Authentication > Sign In / Providers > turn on **Allow anonymous sign-ins**.
3. **Secrets.** Supabase > Edge Functions > Secrets. You need:
   `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`.
   (`RAZORPAY_WEBHOOK_SECRET` is any long random text you invent in step 5.)
4. **Functions.** Edge Functions > open `bright-api` > replace ALL the code with the file above > Deploy.
   Then create a new function named `razorpay-webhook`, paste its file, and in its settings turn **Verify JWT off**.
   For `bright-api` also turn **Verify JWT off**: the code checks the user itself and this avoids login-token format problems.
5. **Razorpay webhook.** Razorpay Dashboard > Account & Settings > Webhooks > Add. URL:
   `https://<your-project>.supabase.co/functions/v1/razorpay-webhook`. Secret: the same text as `RAZORPAY_WEBHOOK_SECRET`.
   Tick **payment.captured** and **order.paid**.
6. **GitHub secrets** (repo > Settings > Secrets and variables > Actions): `SUPABASE_URL`,
   `SUPABASE_PUBLISHABLE_KEY`, `RAZORPAY_KEY_ID`. All three are public values. Never add the Key Secret to GitHub.
7. Run **Android Debug Build** again. The Premium button now says "Buy ...".

## Tests

```
node --experimental-strip-types --no-warnings --test backend/tests/functions.test.mjs
```

Runs the real function code against a fake database and fake Razorpay (forged signatures, other users' orders,
wrong amounts, duplicate webhooks). `schema.sql` was also run twice against a real PostgreSQL 16.
Nothing here has been run against your live Supabase or the real Razorpay, so do a test-mode payment before going live.

## Test payment

In Razorpay **Test mode** use the test card details from Razorpay's docs (any future expiry, any CVV). No real money moves.
After paying, the Premium screen should say "Premium active until ...".
