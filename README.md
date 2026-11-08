# Chess-Book

Chess-Book is a free Android app for playing chess against the computer, analysing your games and importing PGN files. It runs a Stockfish chess engine directly on your phone, so analysis works offline.

## What the app does

- Play chess against the built-in engine
- Analyse positions and review full games with move-by-move evaluation
- Import games from PGN
- Chess tools and six board themes
- Classic navy-and-gold look with colourful cards, in light and dark mode
- Works on Android 8.0 and above

## Free and Premium

The app is free to use with **Stockfish Lite**, which never expires. **Premium** unlocks **Stockfish Full**, a stronger and deeper analysis mode. Premium is a digital feature unlocked inside the app after a successful payment.

### Premium features

- **Stockfish Full**: deeper, longer analysis with 3 lines at once.
- **Custom Elo range**: play against a bot of any strength from 1320 to 3190 Elo (Play tab).
- **Human touch**: the bot varies between near-equal moves, never throws away a forced mate, and thinks for a natural, changing time (Play tab).
- **Move review with suggestion arrows**: after "Review this game" every move is rated on the board with a badge (Best, Mistake, Blunder ...). A green arrow shows the better move and a blue arrow shows another good option. "Prev / Next mistake" buttons jump between key moments (Analysis tab). Free users still get the game report, accuracy and eval graph.

## Pricing (in Indian Rupees)

| Plan | Price | Access |
| --- | --- | --- |
| 1 Hour | ₹2 | 60 minutes of Premium |
| 6 Hours | ₹5 | 6 hours of Premium |
| 12 Hours | ₹8 | 12 hours of Premium |
| 24 Hours | ₹10 | 24 hours of Premium |
| Lifetime | ₹50 | Permanent Premium on your account |

Payments are handled securely by **Razorpay**. We never see or store your card, UPI or bank details. Premium turns on only after our server verifies the payment with Razorpay.

## How to buy Premium

1. Open the app and go to the Premium screen.
2. Choose a plan and tap Buy.
3. Complete the payment on the Razorpay screen.
4. Premium activates automatically once the payment is verified.

If money was deducted but Premium did not turn on, close and reopen the app. It re-checks your payment with the server and activates Premium. If it still does not work within a few minutes, contact us using the details below.

## Cancellation and Refund Policy

- Premium plans are one-time purchases and do not auto-renew. There is nothing to cancel.
- Because Premium is a digital service that is delivered immediately after payment, completed purchases are generally non-refundable.
- If you were charged but Premium was not activated, or you were charged twice for the same plan, contact us with your payment ID and we will fix the activation or refund the extra amount.
- Approved refunds are returned to the original payment method, usually within 5 to 7 working days.

## Delivery Policy

This is a digital service with no physical shipping. Premium is activated in the app within seconds of a verified payment.

## Privacy

The app signs you in anonymously. We store only your anonymous account ID, your plan and its expiry time. We do not collect your name, phone number or payment details. Your saved games stay on your device.

## Contact and Support

- Telegram: [@YogeshBhai729](https://t.me/YogeshBhai729)
- Email: yogeshbangari123@gmail.com
- GitHub: [dragon-729](https://github.com/dragon-729)

We usually reply within 24 to 48 hours.

## Download

Get the latest signed APK from the **Releases** section of this repository.

## For developers: backend setup

The Premium backend uses Supabase and Razorpay.

1. Run `supabase/schema.sql` in the Supabase SQL Editor.
2. Turn on Allow anonymous sign-ins in Supabase Authentication.
3. Add the secrets `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` and `RAZORPAY_WEBHOOK_SECRET` in Supabase Edge Functions.
4. Deploy the functions `bright-api` and `razorpay-webhook`, both with Verify JWT turned off.
5. In Razorpay, add a webhook to `https://<your-project>.supabase.co/functions/v1/razorpay-webhook` with the events `payment.captured` and `order.paid`.
6. Add the GitHub secrets `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` and `RAZORPAY_KEY_ID`, then run the build workflow.

Never put the Razorpay Key Secret in GitHub or in the app.

## License

See the LICENSE file.
