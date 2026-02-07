The binary ffmpeg extension was built with following decoders:

```
ENABLED_DECODERS=(alac)
```

Complete [build instructions](https://github.com/androidx/media/blob/release/libraries/decoder_ffmpeg/README.md).

To assemble ``.aar``:

```
./gradlew :lib-decoder-ffmpeg:bundleReleaseAar
```
