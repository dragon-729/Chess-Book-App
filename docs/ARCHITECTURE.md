# Architecture

```
app/src/main/java/com/chessbook/app/
  chess/    Position (rules, FEN, SAN), Pgn, ToolMath (Elo, round robin)   - pure Kotlin, unit tested
  engine/   ChessEngine interface, UciEngine (Stockfish), BuiltinEngine,
            ResilientEngine (fallback), TierLimits, GameReview
  data/     AppSettings, Importers (Lichess, Chess.com, files)
  ui/       Compose screens, ChessBoardView, ViewModels (AnalysisVM, PlayVM)
```

MVVM with Jetpack Compose and Material 3. The chess and engine layers have no Android dependency
apart from `UciEngine` process launch and `EngineHolder`, which is why they run in plain JVM unit tests.

## Testing

`./gradlew testDebugUnitTest` runs 24 tests, including perft counts against published values for four
standard positions (these exercise castling, en passant, promotion and pins), PGN parse/export round trips,
engine tactics, review classification and the Elo and round-robin maths.

## Smoothness (target: 120 fps)

What the app does:

- Requests the highest refresh rate the screen supports at the current resolution
  (`MainActivity.requestHighestRefreshRate`). Android treats this as a request.
- Draws the whole board in one `Canvas` with reused `Paint` objects: no per-square composables, so very little work per frame.
- Runs engine and review work on background dispatchers, so the main thread is never blocked. Every
  analysis has a Stop button and is cancelled when it is no longer needed.
- Ships a baseline profile (`baseline-prof.txt`) with `profileinstaller`, so hot code is pre-compiled for smoother first use.
- Release builds use R8 shrinking.
- Lite engine is single-threaded with a small hash, which keeps heat and battery in check.

What no app can promise: 120 fps needs a 120 Hz screen, and Android lowers the rate in battery saver
or when the phone is hot. This applies equally to free and premium users. The About screen shows
the current and highest supported refresh rate on the device.

## Adding features later

- New engine: implement `ChessEngine`.
- New screen: add a route in `Common.kt` and a destination in `AppRoot.kt`.
- Backend: see `PAYMENT_SETUP.md`.
