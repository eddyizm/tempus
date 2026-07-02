Build ffmpeg .aar lib in container

```
mkdir ./output
podman build -t ffmpeg-aar-builder .
podman run --rm -v ./output:/build/output ffmpeg-aar-builder /build/build.sh
```
resulting file can be copied to the libs dir. 
This file should be a match to what is generated in the fdroid build. (need to test and validate)
