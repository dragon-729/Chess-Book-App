# Licenses

## Chess-Book: GPL-3.0

The app can bundle Stockfish, which is GPL-3.0. Distributing it together with the app means the app's
source must also be offered under GPL-3.0. That is why this repository uses GPL-3.0 (`LICENSE`).
Keep the source public (or provide it on request) for every APK you distribute.

## Stockfish: GPL-3.0

Built from the official source at https://github.com/official-stockfish/Stockfish (tag set in
`scripts/build-stockfish.sh`). Stockfish and its NNUE networks are by the Stockfish developers.
Keep a link to the source in any release notes.

## Chessigma

Chess-Book is inspired by the feature set of chessigma.com but contains none of its code, design,
branding or assets. Piece graphics come from the Unicode chess glyphs of the phone's font.

## Game data

Lichess and Chess.com games are fetched on demand from their public APIs. The accuracy curve follows
the shape of the publicly documented Lichess formula and was implemented independently.
Respect each site's terms and rate limits.

## Libraries

Jetpack Compose, AndroidX, Kotlin and kotlinx.coroutines are Apache-2.0.
