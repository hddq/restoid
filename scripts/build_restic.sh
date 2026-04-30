#!/usr/bin/env bash
set -euo pipefail

# Builds restic for all required Android ABIs
# Usage: ./build_restic.sh [path_to_go_binary]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RESTIC_DIR="$REPO_ROOT/restic"
OUTPUT_BASE="$REPO_ROOT/app/src/main/jniLibs"
GO_BIN="${1:-go}"

NDK_VER=$(sed -n -E 's/.*ndkVersion = "(.*)".*/\1/p' "$REPO_ROOT/app/build.gradle.kts" | tr -d ' ')

if [ -n "${ANDROID_HOME:-}" ]; then
    EXPECTED_NDK_HOME="$ANDROID_HOME/ndk/$NDK_VER"
else
    EXPECTED_NDK_HOME=""
fi

if [ -n "${ANDROID_NDK_HOME:-}" ] && [ -n "$EXPECTED_NDK_HOME" ] && [ "$ANDROID_NDK_HOME" != "$EXPECTED_NDK_HOME" ]; then
    echo "Warning: ANDROID_NDK_HOME points to $ANDROID_NDK_HOME, overriding to $EXPECTED_NDK_HOME"
    ANDROID_NDK_HOME="$EXPECTED_NDK_HOME"
fi

if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    if [ -n "$EXPECTED_NDK_HOME" ]; then
        ANDROID_NDK_HOME="$EXPECTED_NDK_HOME"
    else
        echo "Error: ANDROID_NDK_HOME or ANDROID_HOME must be set"
        exit 1
    fi
fi

if [ ! -d "$ANDROID_NDK_HOME" ]; then
    echo "Error: NDK not found at $ANDROID_NDK_HOME"
    exit 1
fi

NDK_REV=""
if [ -f "$ANDROID_NDK_HOME/source.properties" ]; then
    NDK_REV=$(sed -n -E 's/^Pkg.Revision\s*=\s*(.*)$/\1/p' "$ANDROID_NDK_HOME/source.properties" | tr -d ' ')
fi

if [ -n "$NDK_REV" ] && [ "$NDK_REV" != "$NDK_VER" ]; then
    echo "Error: NDK revision $NDK_REV does not match expected $NDK_VER"
    exit 1
fi

if [ "$(uname -s)" = "Darwin" ]; then
    HOST_TAG="darwin-x86_64"
else
    HOST_TAG="linux-x86_64"
fi

TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG"
API=33

declare -A ABI_TO_GOARCH=(
    ["arm64-v8a"]="arm64"
    ["x86_64"]="amd64"
)

declare -A ABI_TO_CC=(
    ["arm64-v8a"]="$TOOLCHAIN/bin/aarch64-linux-android${API}-clang"
    ["x86_64"]="$TOOLCHAIN/bin/x86_64-linux-android${API}-clang"
)

for abi in "${!ABI_TO_GOARCH[@]}"; do
    goarch="${ABI_TO_GOARCH[$abi]}"
    cc="${ABI_TO_CC[$abi]}"
    out_dir="$OUTPUT_BASE/$abi"
    out_file="$out_dir/librestic.so"

    mkdir -p "$out_dir"
    echo "==> Building restic: abi=$abi GOARCH=$goarch CC=$cc"

    export CGO_CFLAGS="-O2 -ffile-prefix-map=$ANDROID_NDK_HOME=/ndk"
    export CGO_LDFLAGS="-Wl,--build-id=none"

    GOOS=android \
    GOARCH="$goarch" \
    CGO_ENABLED=1 \
    CC="$cc" \
        "$GO_BIN" build \
            -C "$RESTIC_DIR" \
            -buildvcs=false \
            -ldflags="-s -w -buildid=" \
            -trimpath \
            -o "$out_file" \
            ./cmd/restic

    echo "    OK: $out_file"
done

echo "==> All restic binaries built."
