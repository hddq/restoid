#!/usr/bin/env bash
set -euo pipefail

# Builds rclone for all required Android ABIs
# Usage: ./build_rclone.sh [path_to_go_binary]

ulimit -n 65535 2>/dev/null || true

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RCLONE_DIR="$REPO_ROOT/rclone"
OUTPUT_BASE="$REPO_ROOT/app/src/main/jniLibs"
GO_BIN="${1:-go}"

NDK_VER=$(sed -n -E 's/.*ndkVersion = "(.*)".*/\1/p' "$REPO_ROOT/app/build.gradle.kts" | tr -d ' ')
MIN_SDK=$(sed -n -E 's/.*minSdk = ([0-9]+).*/\1/p' "$REPO_ROOT/app/build.gradle.kts" | tr -d ' ')

if [ -z "$MIN_SDK" ]; then
    echo "Error: minSdk not found in app/build.gradle.kts"
    exit 1
fi

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
API="$MIN_SDK"

declare -A ABI_TO_GOARCH=(
    ["arm64-v8a"]="arm64"
    ["x86_64"]="amd64"
)

declare -A ABI_TO_CC=(
    ["arm64-v8a"]="$TOOLCHAIN/bin/aarch64-linux-android${API}-clang"
    ["x86_64"]="$TOOLCHAIN/bin/x86_64-linux-android${API}-clang"
)

# Exclude rclone's web GUI (cmd/gui embeds a prebuilt dist.zip, which is deleted for F-Droid).
# A Go build overlay drops the blank import from cmd/all/all.go without touching the submodule.
RCLONE_ALL_GO="$RCLONE_DIR/cmd/all/all.go"
RCLONE_GUI_IMPORT_RE='^[[:space:]]*_[[:space:]]*"github\.com/rclone/rclone/cmd/gui"[[:space:]]*$'

if ! grep -q "$RCLONE_GUI_IMPORT_RE" "$RCLONE_ALL_GO"; then
    echo "Error: cmd/gui import not found in $RCLONE_ALL_GO; update build_rclone.sh"
    exit 1
fi

if grep -rl --include='*.go' --exclude='*_test.go' '"github\.com/rclone/rclone/cmd/gui"' "$RCLONE_DIR" | grep -vxF "$RCLONE_ALL_GO" | grep -q .; then
    echo "Error: unexpected cmd/gui import found outside $RCLONE_ALL_GO; update build_rclone.sh"
    exit 1
fi

OVERLAY_DIR="$(mktemp -d)"
trap 'rm -rf "$OVERLAY_DIR"' EXIT
grep -v "$RCLONE_GUI_IMPORT_RE" "$RCLONE_ALL_GO" > "$OVERLAY_DIR/all.go"
json_escape() {
    local s="${1//\\/\\\\}"
    s="${s//\"/\\\"}"
    if [[ "$s" == *[[:cntrl:]]* ]]; then
        echo "Error: path contains control characters: $1" >&2
        exit 1
    fi
    printf '%s' "$s"
}
OVERLAY_SRC_JSON="$(json_escape "$RCLONE_ALL_GO")"
OVERLAY_DST_JSON="$(json_escape "$OVERLAY_DIR/all.go")"
printf '{"Replace":{"%s":"%s"}}\n' "$OVERLAY_SRC_JSON" "$OVERLAY_DST_JSON" > "$OVERLAY_DIR/overlay.json"

for abi in "${!ABI_TO_GOARCH[@]}"; do
    goarch="${ABI_TO_GOARCH[$abi]}"
    cc="${ABI_TO_CC[$abi]}"
    out_dir="$OUTPUT_BASE/$abi"
    out_file="$out_dir/librclone.so"

    mkdir -p "$out_dir"
    echo "==> Building rclone: abi=$abi GOARCH=$goarch CC=$cc"

    export CGO_CFLAGS="-O2 -ffile-prefix-map=$ANDROID_NDK_HOME=/ndk"
    export CGO_LDFLAGS="-Wl,--build-id=none"

    GOOS=android \
    GOARCH="$goarch" \
    CGO_ENABLED=1 \
    CC="$cc" \
        "$GO_BIN" build \
            -C "$RCLONE_DIR" \
            -buildvcs=false \
            -tags "noselfupdate" \
            -ldflags="-s -w -buildid=" \
            -trimpath \
            -overlay "$OVERLAY_DIR/overlay.json" \
            -o "$out_file" \
            .

    echo "    OK: $out_file"
done

echo "==> All rclone binaries built."
