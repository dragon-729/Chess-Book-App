# Engine setup

## How the engine gets into the app

`scripts/build-stockfish.sh` runs inside GitHub Actions. It:

1. Clones the official Stockfish repository at the pinned tag (`SF_TAG`, currently `sf_17.1`)
2. Builds it with the Android NDK for arm64 (`ARCH=armv8 COMP=ndk`). The Stockfish makefile downloads
   the NNUE networks and embeds them in the binary
3. Strips it and saves it as `app/src/main/jniLibs/arm64-v8a/libstockfish.so`

At run time `UciEngine` starts that file as a process and speaks UCI to it. The Android manifest
setting `useLegacyPackaging` makes Android extract the file so it can be executed.

If the build fails, the app still works with a small built-in Kotlin engine. The Analysis and Home
screens always show which engine is running.

## Upgrading Stockfish

Change `SF_TAG` in `scripts/build-stockfish.sh` (or set it as a workflow environment variable) to a
newer official tag, then re-run the workflow.

## Lite vs Full

Both tiers use the same binary. The difference is real resource limits (`TierLimits` in `Engine.kt`):

| | Lite (free) | Full (premium) |
|---|---|---|
| Max depth | 14 | 40 |
| Time per position | 1.5 s | 8 s |
| Threads | 1 | up to 4, never more than cores - 1 |
| Hash | 16 MB | up to 256 MB, capped by app memory |
| Lines (MultiPV) | 1 | 3 |

Game review uses shorter limits so a whole game finishes in reasonable time.

Full is capped by the phone's cores and memory. It cannot go beyond what the hardware and battery allow.

## Known limits

- Only arm64 phones get Stockfish (nearly all modern phones). Other CPUs use the fallback engine.
- The binary targets plain `armv8` for compatibility. A `armv8-dotprod` build is faster on newer
  CPUs but crashes on older ones, so it needs runtime detection before it can be shipped.
- The workflow cannot run the arm64 binary to test it. Check the first install: the Analysis screen
  should say "Running: Stockfish".
- Multi-line display (MultiPV 3) is configured for Full but only the best line is shown for now.
