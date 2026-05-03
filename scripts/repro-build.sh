#!/usr/bin/env bash
set -euo pipefail

echo "==> Cloning source to working directory (simulating CI clone)..."
git clone -q --depth 1 "file:///src" /app
cd /app
git submodule update --init --depth 1

echo "==> Cleaning up environment..."
rm -f local.properties

echo "==> Building release APK..."
./gradlew clean assembleRelease

echo "==> Copying APKs to output directory..."
cp app/build/outputs/apk/release/*-unsigned.apk /out/

# Fix permissions so the host can access it if podman maps it to root
chmod 666 /out/*
