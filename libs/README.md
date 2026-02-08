The binary ffmpeg extension was built with following decoders:

```
ENABLED_DECODERS=(alac)
```

Complete [build instructions](https://github.com/androidx/media/blob/release/libraries/decoder_ffmpeg/README.md).

To assemble ``.aar``:

Full instructions if you want to update current lib-decoder-ffmpeg-release.aar
These are for linux, if you use another platform see complete build instructions above.

```bash
FFMPEG_MODULE_PATH="$(pwd)/media/libraries/decoder_ffmpeg/src/main"
ANDROID_SDK_ROOT=$HOME/Android/Sdk
NDK_PATH="$ANDROID_SDK_ROOT/ndk/27.0.12077973"
HOST_PLATFORM="linux-x86_64"
ANDROID_ABI=24
ENABLED_DECODERS=(alac)

git clone git://source.ffmpeg.org/ffmpeg && \
cd ffmpeg && \
git checkout release/6.0 && \
FFMPEG_PATH="$(pwd)"
cd ..

git clone --depth 1 git@github.com:androidx/media.git

cd "${FFMPEG_MODULE_PATH}/jni" && \
ln -s "$FFMPEG_PATH" ffmpeg

cd ../../../../../../ 

cd "${FFMPEG_MODULE_PATH}/jni" && \
./build_ffmpeg.sh \
  "${FFMPEG_MODULE_PATH}" "${NDK_PATH}" "${HOST_PLATFORM}" "${ANDROID_ABI}" "${ENABLED_DECODERS[@]}"

cd ../../../../../../ 

cd media
./gradlew :media-lib-decoder-ffmpeg:bundleReleaseAar
cd ..
cp media/libraries/decoder_ffmpeg/buildout/outputs/aar/lib-decoder-ffmpeg-release.aar libs/
```
