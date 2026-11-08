# Chess-Book

An Android chess app: play bots, import and review games with Stockfish, and use chess tools.
Everything builds on **GitHub Actions**, so no Android Studio is needed.

- GitHub: https://github.com/dragon-729
- Telegram: [@YogeshBhai729](https://t.me/YogeshBhai729)

Both links are also inside the app (Home and About screens).

## Get an APK without Android Studio

1. Create a GitHub repository and upload this project (see `docs/BUILD.md`).
2. Open the **Actions** tab, choose **Android Debug Build**, press **Run workflow**.
3. When it finishes (about 10 minutes), open the run and download **Chess-Book-debug-apk** from *Artifacts*.
4. Unzip it and install the APK on your phone.

## What works in this version (phase 1)

| Area | Status |
|---|---|
| Legal chess rules (castling, en passant, promotion, check, mate, stalemate, draws) | Done, tested with perft |
| Interactive board (tap to move, flip, last move, check highlight, 3 board themes) | Done |
| Analysis: PGN text, PGN file, clipboard, FEN, Lichess and Chess.com import | Done |
| Full-game review: per-move classification, accuracy, eval graph, worst move | Done (see notes) |
| Variations without destroying the main line | Done |
| Play vs bot: 5 levels, takeback, hint, resign, copy PGN, send to review | Done |
| Stockfish Lite / Full tiers with different real resource limits | Done |
| Stockfish binary (built in CI from official source) | Built by workflow; falls back to built-in engine if the build fails |
| Tools: chess clock, Elo calculator, round-robin generator, FEN viewer | Done |
| GitHub and Telegram links in the app | Done |
| 120 Hz request, baseline profile, R8, single-canvas board | Done (see `docs/ARCHITECTURE.md`) |

## Not built yet

These are in the original brief but are **not in this version**. The Premium screen says so
instead of pretending.

- Razorpay payments and server-side entitlements (design in `docs/PAYMENT_SETUP.md`)
- Accounts and sync, leaderboards, community courses
- Opening trainer, puzzles, brain games, AI coach, Chess DNA report
- Saved games database, daily streaks
- USCF and FIDE-specific calculators (the Elo tool uses the standard formula with FIDE K presets)
- "Brilliant" and "Great" move labels (need sacrifice and uniqueness detection)

## Notes on honesty

- Premium cannot be unlocked by the app on its own. Until the backend exists, the only way to try
  the Full tier is the debug-only simulation switch, which does not exist in release builds.
- Evaluations come from the engine named under each result ("Stockfish" or "Built-in engine").
- Accuracy is a simplified estimate based on win-probability loss.

## License

GPL-3.0 (required because the app bundles Stockfish). See `docs/LICENSES.md`.
