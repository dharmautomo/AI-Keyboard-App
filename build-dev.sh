#!/bin/bash

# Fast development build script
echo "🚀 Starting fast development build..."

# Clean only if needed
if [ "$1" = "--clean" ]; then
    echo "🧹 Cleaning project..."
    ./gradlew clean
fi

# Build only debug variant with optimizations
echo "🔨 Building debug variant..."
./gradlew assembleDebug \
    --parallel \
    --build-cache \
    --configure-on-demand \
    --no-daemon \
    --max-workers=4

echo "✅ Build completed!"
echo "📱 APK location: app/build/outputs/apk/debug/app-debug.apk" 