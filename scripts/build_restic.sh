#!/usr/bin/env bash
set -euo pipefail

# Builds restic for all required Android ABIs
# Usage: ./build_restic.sh [path_to_go_binary]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RESTIC_DIR="$REPO_ROOT/restic"
OUTPUT_BASE="$REPO_ROOT/app/src/main/jniLibs"
GO_BIN="${1:-go}"

declare -A ABI_TO_GOARCH=(
    ["arm64-v8a"]="arm64"
    ["x86_64"]="amd64"
)

for abi in "${!ABI_TO_GOARCH[@]}"; do
    goarch="${ABI_TO_GOARCH[$abi]}"
    out_dir="$OUTPUT_BASE/$abi"
    out_file="$out_dir/librestic.so"

    mkdir -p "$out_dir"
    echo "==> Building restic: abi=$abi GOARCH=$goarch"

    GOOS=linux \
    GOARCH="$goarch" \
    CGO_ENABLED=0 \
        "$GO_BIN" build \
            -C "$RESTIC_DIR" \
            -buildvcs=false \
            -ldflags="-s -w" \
            -trimpath \
            -o "$out_file" \
            ./cmd/restic

    echo "    OK: $out_file"
done

echo "==> All restic binaries built."
