#!/bin/bash
set -euo pipefail

# ------------------------------------------------------------------
# Configuration
FFMPEG_MODULE_PATH="/build/media/libraries/decoder_ffmpeg/src/main"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-/opt/android-sdk}"
NDK_PATH="${NDK_PATH:-$ANDROID_SDK_ROOT/ndk/29.0.14206865}"
HOST_PLATFORM="linux-x86_64"
ANDROID_API=24
ENABLED_DECODERS=(alac)
MEDIA3_VERSION="1.9.2"

# ------------------------------------------------------------------
# Deterministic build flags – must match F-Droid
export CFLAGS="-fno-ident -ffile-prefix-map=${PWD}=. -fdebug-prefix-map=${PWD}=."
export LDFLAGS="-Wl,--build-id=none"
export SOURCE_DATE_EPOCH=0
export ARFLAGS="rcsD"
# ------------------------------------------------------------------

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
step "📦 Cloning androidx/media (shallow, version: $MEDIA3_VERSION) via HTTPS"
git clone --depth 1 --branch "$MEDIA3_VERSION" https://github.com/androidx/media.git media
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
./gradlew :lib-decoder-ffmpeg:bundleReleaseAar --no-daemon
cd /build
echo "✅ Gradle build finished"

# ---------- 6. Strip build ID from .so files inside the AAR ----------
step "🗑️ Stripping build ID from libraries inside AAR"

AAR_PATH="media/libraries/decoder_ffmpeg/buildout/outputs/aar/lib-decoder-ffmpeg-release.aar"
TEMP_DIR="/tmp/aar_strip"
mkdir -p /build/output

# Unpack the AAR
mkdir -p "$TEMP_DIR"
cp "$AAR_PATH" "$TEMP_DIR/"
cd "$TEMP_DIR"
unzip -q lib-decoder-ffmpeg-release.aar
rm lib-decoder-ffmpeg-release.aar

# Explicitly use the NDK's llvm-strip to guarantee clean removal of the .note.gnu.build-id section
find . -name "*.so" -exec "${NDK_PATH}/toolchains/llvm/prebuilt/${HOST_PLATFORM}/bin/llvm-strip" --remove-section=.note.gnu.build-id {} \;

# Repack the stripped assets directly into your target output location
zip -q -X -r /build/output/lib-decoder-ffmpeg-release.aar *
cd /build
rm -rf "$TEMP_DIR"
echo "✅ AAR safely repacked with stripped native libraries"

# ---------- 7. Verify 16KB alignment ----------
step "🔒 Verifying 16KB page alignment of 64-bit ELF libraries"
python3 - << 'EOF' /build/output/lib-decoder-ffmpeg-release.aar
import zipfile
import struct
import sys

def verify_elf_alignment(elf_data, filename):
    if len(elf_data) < 64:
        print(f"Error: {filename} is too small to be a valid ELF")
        return False
    magic = elf_data[:4]
    if magic != b'\x7fELF':
        print(f"Error: {filename} has invalid magic: {magic}")
        return False
    elf_class = elf_data[4]
    if elf_class != 2:  # Only enforce 16KB alignment for 64-bit native libraries
        return True
    e_phoff = struct.unpack_from("<Q", elf_data, 32)[0]
    e_phentsize = struct.unpack_from("<H", elf_data, 54)[0]
    e_phnum = struct.unpack_from("<H", elf_data, 56)[0]
    
    success = True
    for i in range(e_phnum):
        ph_offset = e_phoff + i * e_phentsize
        if ph_offset + e_phentsize > len(elf_data):
            print(f"Error: Program header {i} out of bounds in {filename}")
            return False
        p_type = struct.unpack_from("<I", elf_data, ph_offset)[0]
        if p_type == 1:  # PT_LOAD
            p_align = struct.unpack_from("<Q", elf_data, ph_offset + 48)[0]
            print(f"  {filename} - PT_LOAD segment alignment: {p_align} (hex: {hex(p_align)})")
            if p_align < 0x4000:
                print(f"  FAIL: {filename} has PT_LOAD segment with alignment {p_align} (less than 16KB/0x4000)")
                success = False
    return success

def main():
    if len(sys.argv) < 2:
        print("Usage: verify_alignment.py <path-to-aar>")
        sys.exit(1)
    aar_path = sys.argv[1]
    success = True
    with zipfile.ZipFile(aar_path, 'r') as z:
        for name in z.namelist():
            if name.endswith(".so") and ("arm64-v8a" in name or "x86_64" in name):
                print(f"Checking {name}...")
                elf_data = z.read(name)
                if not verify_elf_alignment(elf_data, name):
                    success = False
    if not success:
        print("Verification FAILED: One or more 64-bit ELF libraries are misaligned!")
        sys.exit(1)
    print("Verification SUCCESS: All 64-bit ELF libraries are 16KB aligned!")
    sys.exit(0)

if __name__ == "__main__":
    main()
EOF

ls -lh /build/output/
step "🎉 ALL DONE!"
