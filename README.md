<p align="center">
  <img alt="Tempus" title="Tempus" src="mockup/svg/tempus_horizontal_logo.png" width="250">
</p>

---

<p align="center">
  <b>Access your music library on all your android devices</b>
</p>

<div align="center">

<a href="https://github.com/eddyizm/tempus/releases/">
    <img alt="Releases" src="https://img.shields.io/github/downloads/eddyizm/tempus/total.svg?color=4B95DE&style=flat">
</a>
  <!-- Reproducible build  -->
<a href="https://shields.rbtlog.dev/com.eddyizm.degoogled.tempus"><img src="https://shields.rbtlog.dev/simple/com.eddyizm.degoogled.tempus" alt="RB Status"></a>
<a href="https://www.gnu.org/licenses/gpl-3.0">
    <img src="https://img.shields.io/badge/license-GPL%20v3-2B6DBE.svg?style=flat">
</a>
</div>

<div style="text-align: center;" markdown="1">

[Changelog](CHANGELOG.md) | [Wiki](USAGE.md) | [Support](https://github.com/eddyizm/tempus#Support)

</div>

<p align="center">
    <a href="https://github.com/eddyizm/tempus/releases"><img src="https://i.ibb.co/q0mdc4Z/get-it-on-github.png" width="200"></a>
    <a href="https://apt.izzysoft.de/fdroid/index/apk/com.eddyizm.degoogled.tempus"><img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" width="200"></a>
    <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.eddyizm.tempus%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Feddyizm%2Ftempus%22%2C%22author%22%3A%22eddyizm%22%2C%22name%22%3A%22Tempus%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22filterReleaseNotesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22%3Afalse%2C%5C%22releaseTitleAsVersion%5C%22%3Afalse%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22releaseDateAsVersion%5C%22%3Afalse%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22tempus%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22appName%5C%22%3A%5C%22%5C%22%2C%5C%22appAuthor%5C%22%3A%5C%22%5C%22%2C%5C%22shizukuPretendToBeGooglePlay%5C%22%3Afalse%2C%5C%22allowInsecure%5C%22%3Afalse%2C%5C%22exemptFromBackgroundUpdates%5C%22%3Afalse%2C%5C%22skipUpdateNotifications%5C%22%3Afalse%2C%5C%22about%5C%22%3A%5C%22%5C%22%2C%5C%22refreshBeforeDownload%5C%22%3Afalse%2C%5C%22includeZips%5C%22%3Afalse%2C%5C%22zippedApkFilterRegEx%5C%22%3A%5C%22%5C%22%7D%22%2C%22overrideSource%22%3A%22GitHub%22%7D"><img width="200" src="https://github.com/user-attachments/assets/119e7ff4-2636-43cb-ab7f-1b6a58ac3570" /></a>
</p>
<!-- 
    <a href="https://f-droid.org/packages/com.cappielloantonio.notquitemy.tempo"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" width="200"></a>
-->
  

**Tempus** is an open-source and lightweight music client for Subsonic, designed and built natively for Android. It provides a seamless and intuitive music streaming experience, allowing you to access and play your Subsonic music library directly from your Android device. 

Tempus does not rely on magic algorithms to decide what you should listen to. Instead, the interface is built around your listening history, randomness, and optionally integrates with services like Listenbrainz.org and Last.fm to personalize your music experience (These must be supported by your backend). 

The project is a fork of [Tempo](#credits).

**If you find Tempus useful, please consider starring the project on GitHub. It would mean a lot to me and help promote the app to a wider audience.**

**Use the Github version of the app for full Android Auto and Chromecast support.**

sha256 signing key fingerprint   
`B7:85:01:B9:34:D0:4E:0A:CA:8D:94:AF:D6:72:6A:4D:1D:CE:65:79:7F:1D:41:71:0F:64:3C:29:00:EB:1D:1D`  

### Releases  

Please note the two variants in the release assets include release/debug and 32/64 bit flavors.

`app-tempus` <- The github release with all the android auto/chromecast features 

`app-degoogled*` <- The izzyOnDroid release that goes without any of the google stuff. It is now available on izzyOnDroid (64bit) I am releasing the both 32/64bit apk's here on github for those who need a 32bit version.


## Features
- **Subsonic Integration**: Tempus seamlessly integrates with your Subsonic server, providing you with easy access to your entire music collection on the go.
- **Sleek and Intuitive UI**: Enjoy a clean and user-friendly interface designed to enhance your music listening experience, tailored to your preferences and listening history.
- **Browse and Search**: Easily navigate through your music library using various browsing and searching options, including artists, albums, genres, playlists, decades and more.
- **Streaming and Offline Mode**: Stream music directly from your Subsonic server. Offline mode is currently under active development and may have limitations when using multiple servers.
- **Playlist Management**: Create, edit, and manage playlists to curate your perfect music collection.
- **Gapless Playback**: Experience uninterrupted playback with gapless listening mode.
- **Chromecast Support**: Stream your music to Chromecast devices. The support is currently in a rudimentary state.*
- **Scrobbling Integration**: Optionally integrate Tempus with Last.fm or Listenbrainz.org to scrobble your played tracks, gather music insights, and further personalize your music recommendations, if supported by your Subsonic server.
- **Podcasts and Radio**: If your Subsonic server supports it, listen to podcasts and radio shows directly within Tempus, expanding your audio entertainment options.
- **Instant Mix**: Full refactor of instant mix function which leverages subsonics similarSongs2 by artist/album and similarSongs endpoints to server a larger play queue more reliably.
- **Transcoding Support**: Activate transcoding of tracks on your Subsonic server, allowing you to set a transcoding profile for optimized streaming directly from the app. This feature requires support from your Subsonic server.
- **Android Auto Support**: Enjoy your favorite music on the go with full Android Auto integration, allowing you to seamlessly control and listen to your tracks directly from your mobile device while driving.* 
- **Multiple Libraries**: Tempus handles multi-library setups gracefully. They are displayed as Library folders.
- **Equalizer**: Option to use in app equalizer.
- **Widget**: New widget to keeping the basic controls on your screen at all times.
- **Available in 11 languages**: Currently in Chinese, French, German, Italian, Korean, Polish, Portuguese, Russion, Spanish and Turkish

 **Github version only*
 
## Screenshot

<p align="center">
  <b>Light theme</b>
</p>

<p align="center">
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_light.png" width=200>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_light.png" width=200>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_light.png" width=200>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4_light.png" width=200>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5_light.png" width=200>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6_light.png" width=200>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/8_light.png" width=200>
</p>

<br>

<p align="center">
  <b>Dark theme</b>
</p>

<p align="center">
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_dark.png" width=200>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_dark.png" width=200>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_dark.png" width=200>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4_dark.png" width=200>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5_dark.png" width=200>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6_dark.png" width=200>
    <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/8_dark.png" width=200>
    
</p>

## Contributing  

Please fork and open PR's against the development branch. Make sure your PR builds successfully. 

If there is an UI change, please include a before/after screenshot and a short video/gif if that helps elaborating the fix/feature in the PR. 

Currently there are no tests but I would love to start on some unit tests. 

Not a hard requirement but any new feature/change should ideally include an update to the nacent documention. 

## Support

[**Buy me a coffee**](https://ko-fi.com/eddyizm)  
bitcoin: `3QVHSSCJvn6yXEcJ3A3cxYLMmbvFsrnUs5`  

## License

Tempus is released under the [GNU General Public License v3.0](LICENSE). Feel free to modify, distribute, and use the app in accordance with the terms of the license. Contributions to the project are also welcome. 

## Credits
Thanks to the original repo/creator [CappielloAntonio](https://github.com/CappielloAntonio) (forked from v3.9.0)

[Opensvg.org](https://opensvg.org) for the new turntable logo. 
