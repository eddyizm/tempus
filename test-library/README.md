# Test music library

This folder is mounted into the Navidrome container in CI as its music folder
(`ND_MUSICFOLDER`). Drop short audio files here to give the app a library to
browse and play during emulator tests.

For the resume/continue-listening tests, tracks must be > 10 minutes (the
app's resumability threshold) — generate them in CI with ffmpeg, or seed
bookmarks directly via the Subsonic API instead of relying on real playback.
