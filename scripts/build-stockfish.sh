#!/usr/bin/env bash
# Builds the official Stockfish engine for Android arm64 and places it in the app as
# libstockfish.so, where UciEngine looks for it. Runs on a GitHub Actions Ubuntu runner,
# which already has the Android NDK. No Android Studio is needed.
#
# If this script fails, the workflows carry on and the app falls back to its built-in engine.
set -euo pipefail

SF_TAG="${SF_TAG:-sf_17.1}"   # pinned official release tag; change it to upgrade
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT/app/src/main/jniLibs/arm64-v8a"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_ROOT:-}}}"
if [ -z "$NDK" ] || [ ! -d "$NDK" ]; then
  echo "Android NDK not found (looked at ANDROID_NDK_HOME / ANDROID_NDK_LATEST_HOME)." >&2
  exit 1
fi
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
export PATH="$TOOLCHAIN:$PATH"

echo "Cloning Stockfish $SF_TAG ..."
git clone --depth 1 --branch "$SF_TAG" https://github.com/official-stockfish/Stockfish.git "$WORK/Stockfish"

cd "$WORK/Stockfish/src"
echo "Building (ARCH=armv8, COMP=ndk) ..."
# The Stockfish makefile downloads the NNUE networks and embeds them in the binary.
make -j"$(nproc)" build ARCH=armv8 COMP=ndk

"$TOOLCHAIN/llvm-strip" stockfish
mkdir -p "$OUT_DIR"
cp stockfish "$OUT_DIR/libstockfish.so"
file "$OUT_DIR/libstockfish.so" || true
ls -l "$OUT_DIR/libstockfish.so"
echo "Stockfish $SF_TAG bundled as libstockfish.so"
