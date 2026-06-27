#!/bin/bash
set -euo pipefail

# ------------------------------------------------------------------
# Configuration
FFMPEG_MODULE_PATH="/build/media/libraries/decoder_ffmpeg/src/main"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
NDK_PATH="${NDK_PATH:-$ANDROID_SDK_ROOT/ndk/27.0.12077973}"
HOST_PLATFORM="linux-x86_64"
ANDROID_API=24
ENABLED_DECODERS=(alac)

# Helper
step() {
  echo "============================================================"
  echo "  $1"
  echo "  Started at: $(date)"
  echo "============================================================"
}

# Start
step "🚀 Starting FFmpeg + AAR build"
rm -rf ffmpeg media

# ---------- 1. Clone ffmpeg ----------
step "📦 Cloning ffmpeg (release/6.0) via HTTPS"
git clone --depth 1 --branch release/6.0 https://github.com/FFmpeg/FFmpeg.git ffmpeg
cd ffmpeg
FFMPEG_PATH="$(pwd)"
cd ..
echo "✅ ffmpeg cloned to $FFMPEG_PATH"

# ---------- 2. Clone androidx/media ----------
step "📦 Cloning androidx/media (shallow) via HTTPS"
git clone --depth 1 https://github.com/androidx/media.git
echo "✅ media repo cloned"

# ---------- 3. Symlink ----------
step "🔗 Creating symlink to ffmpeg inside jni/"
cd "${FFMPEG_MODULE_PATH}/jni"
ln -sf "$FFMPEG_PATH" ffmpeg
cd /build
echo "✅ Symlink created"

# ---------- 4. Build FFmpeg ----------
step "🔧 Building FFmpeg with decoders: ${ENABLED_DECODERS[*]}"
cd "${FFMPEG_MODULE_PATH}/jni"
./build_ffmpeg.sh \
  "${FFMPEG_MODULE_PATH}" "${NDK_PATH}" "${HOST_PLATFORM}" "${ANDROID_API}" "${ENABLED_DECODERS[@]}"
cd /build
echo "✅ FFmpeg build completed"

# ---------- 5. Assemble AAR ----------
step "📦 Assembling .aar with Gradle (bundleReleaseAar)"
cd media
# Use the correct module name as discovered:
./gradlew :lib-decoder-ffmpeg:bundleReleaseAar --no-daemon
cd /build
echo "✅ Gradle build finished"

# ---------- 6. Copy result ----------
step "📁 Copying .aar to /build/output"
mkdir -p /build/output
# Use the actual output path (verified)
cp media/libraries/decoder_ffmpeg/buildout/outputs/aar/lib-decoder-ffmpeg-release.aar /build/output/
echo "✅ AAR copied successfully"

ls -lh /build/output/
step "🎉 ALL DONE!"
