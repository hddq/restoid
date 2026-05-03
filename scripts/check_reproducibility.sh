#!/usr/bin/env bash
set -euo pipefail

# Ensure we're in the project root
cd "$(dirname "$0")/.."

echo "==> Extracting dependencies versions (Single source of truth)..."
GO_VER=$(cat .go-version | tr -d '\r\n')
COMPILE_SDK=$(grep "compileSdk =" app/build.gradle.kts | sed -E 's/.*= ([0-9]+).*/\1/' | tr -d ' ')
BUILD_TOOLS=$(grep "buildToolsVersion =" app/build.gradle.kts | sed -E 's/.*"(.*)".*/\1/' | tr -d ' ')
NDK_VER=$(grep "ndkVersion =" app/build.gradle.kts | sed -E 's/.*"(.*)".*/\1/' | tr -d ' ')

echo "Detected Versions:"
echo "  Go:          $GO_VER"
echo "  Compile SDK: $COMPILE_SDK"
echo "  Build Tools: $BUILD_TOOLS"
echo "  NDK:         $NDK_VER"

build_in_docker() {
    local base_image=$1
    local tag_name=$2

    echo ""
    echo "=========================================================="
    echo "==> Building Docker image ($base_image) as $tag_name..."
    echo "=========================================================="
    docker build -t "$tag_name" \
        --build-arg BASE_IMAGE="$base_image" \
        --build-arg GO_VERSION="$GO_VER" \
        --build-arg COMPILE_SDK="$COMPILE_SDK" \
        --build-arg BUILD_TOOLS="$BUILD_TOOLS" \
        --build-arg NDK_VER="$NDK_VER" \
        -f scripts/Dockerfile.repro .

    mkdir -p "out/$tag_name"
    echo "==> Running build in $tag_name container..."
    docker run --rm -v "$(pwd):/src:ro" -v "$(pwd)/out/$tag_name:/out" "$tag_name" bash /src/scripts/repro-build.sh
}

# Step 1: Ubuntu (GitHub Actions simulation)
build_in_docker "ubuntu:26.04" "restoid-repro-ubuntu"

# Step 2: Debian Trixie (F-Droid simulation)
build_in_docker "debian:trixie" "restoid-repro-debian"

echo ""
echo "=========================================================="
echo "==> Verification"
echo "=========================================================="

check_apk() {
    local apk_name=$1
    local ubuntu_apk="out/restoid-repro-ubuntu/$apk_name"
    local debian_apk="out/restoid-repro-debian/$apk_name"

    if [ ! -f "$ubuntu_apk" ] || [ ! -f "$debian_apk" ]; then
        echo "❌ Error: Missing $apk_name in one or both outputs!"
        exit 1
    fi

    local ubuntu_sha=$(sha256sum "$ubuntu_apk" | awk '{print $1}')
    local debian_sha=$(sha256sum "$debian_apk" | awk '{print $1}')

    echo "Verifying $apk_name:"
    echo "  Ubuntu: $ubuntu_sha"
    echo "  Debian: $debian_sha"

    if [ "$ubuntu_sha" = "$debian_sha" ]; then
        echo "  ✅ Matches!"
    else
        echo "  ❌ Differs!"
        exit 1
    fi
}

check_apk "app-arm64-v8a-release-unsigned.apk"
check_apk "app-x86_64-release-unsigned.apk"
check_apk "app-universal-release-unsigned.apk"

echo ""
echo "🎉 All builds are 100% reproducible across different distributions!"
