# Changelog

## Pending release..
* fix: Equalizer fix in main build variant by @jaime-grj in https://github.com/eddyizm/tempus/pull/239
* fix: Images not filling holder by @eddyizm in https://github.com/eddyizm/tempus/pull/244
* feat: Make artist and album clickable by @eddyizm in https://github.com/eddyizm/tempus/pull/243
* feat: implement scroll to currently playing feature by @shrapnelnet in https://github.com/eddyizm/tempus/pull/247
* fix: shuffling genres only queuing 25 songs by @shrapnelnet in https://github.com/eddyizm/tempus/pull/246

## [4.1.3](https://github.com/eddyizm/tempo/releases/tag/v4.1.3) (2025-11-06)
## What's Changed
* [fix: equalizer missing referenced value](https://github.com/eddyizm/tempus/commit/923cfd5bc97ed7db28c90348e3619d0a784fc434)
* Fix: Album track list bug by @eddyizm in https://github.com/eddyizm/tempus/pull/237
* fix: Add listener to enable equalizer when audioSessionId changes by @jaime-grj in https://github.com/eddyizm/tempus/pull/235

**Full Changelog**: https://github.com/eddyizm/tempus/compare/v4.1.0...v4.1.3

## [4.1.0](https://github.com/eddyizm/tempo/releases/tag/v4.1.0) (2025-11-05)
## What's Changed
* chore(i18n): Update Spanish (es-ES) translation by @jaime-grj in https://github.com/eddyizm/tempus/pull/205
* shuffle for artists without using `getTopSongs` by @pca006132 in https://github.com/eddyizm/tempus/pull/207
* Update USAGE.md with instant mix details by @zc-devs in https://github.com/eddyizm/tempus/pull/220
* feat: sort artists by album count by @pca006132 in https://github.com/eddyizm/tempus/pull/206
* Fix downloaded tab performance by @pca006132 in https://github.com/eddyizm/tempus/pull/210
* fix: remove NestedScrollViews for fragment_album_page by @pca006132 in https://github.com/eddyizm/tempus/pull/216
* fix: playlist page should not snap by @pca006132 in https://github.com/eddyizm/tempus/pull/218
* fix: do not override getItemViewType and getItemId by @pca006132 in https://github.com/eddyizm/tempus/pull/221
* chore: update media3 dependencies by @pca006132 in https://github.com/eddyizm/tempus/pull/217
* fix: update MediaItems after network change by @pca006132 in https://github.com/eddyizm/tempus/pull/222
* fix: skip mapping downloaded item by @pca006132 in https://github.com/eddyizm/tempus/pull/228

## New Contributors
* @pca006132 made their first contribution in https://github.com/eddyizm/tempus/pull/207

**Full Changelog**: https://github.com/eddyizm/tempus/compare/v4.0.7...v4.1.0

## [4.0.7](https://github.com/eddyizm/tempo/releases/tag/v4.0.7) (2025-10-28)
## What's Changed
* chore: updated tempo references to tempus including github check by @eddyizm in https://github.com/eddyizm/tempus/pull/197
* fix: Crash on share no expiration date or field returned from api by @eddyizm in https://github.com/eddyizm/tempus/pull/199

**Full Changelog**: https://github.com/eddyizm/tempus/compare/v4.0.6...v4.0.7

## [4.0.6](https://github.com/eddyizm/tempo/releases/tag/v4.0.6) (2025-10-26)
## Attention
This release will not update previous installs as it is considered a new app, no longer `Tempo`, new icon, new app id, and new app name. Hoping it will not be a huge inconvenience but was necessary in order to publish to app stores like IzzyDroid and FDroid.  

**Android Auto** 
Support should be the same as before, however, I was not able to test any of the icons/visuals, so please let me know if there are any remnants of the tempo logo/icon as I believe I removed them all and replaced them successfully.  

## What's Changed
* Check also underlying transport by @zc-devs in https://github.com/eddyizm/tempus/pull/90
* fix: updated workflow for 32/64 bit apks by @eddyizm in https://github.com/eddyizm/tempus/pull/176
* Unhide genre from album details view by @sebaFlame in https://github.com/eddyizm/tempus/pull/161
* fix: persist album sorting on resume by @eddyizm in https://github.com/eddyizm/tempus/pull/181
* chore: update readme and usage references to tempus. added new banner… by @eddyizm in https://github.com/eddyizm/tempus/pull/182
* Tempus rebrand by @eddyizm in https://github.com/eddyizm/tempus/pull/183
* Update Polish translation by @skajmer in https://github.com/eddyizm/tempus/pull/188

## New Contributors
* @zc-devs made their first contribution in https://github.com/eddyizm/tempus/pull/90
* @sebaFlame made their first contribution in https://github.com/eddyizm/tempus/pull/161

**Full Changelog**: https://github.com/eddyizm/tempus/compare/v3.17.14...v4.0.1

## [3.17.14](https://github.com/eddyizm/tempo/releases/tag/v3.17.14) (2025-10-16)
## What's Changed
* fix: General build warning and playback issues by @le-firehawk in https://github.com/eddyizm/tempo/pull/167
* fix: persist album sort preference by @eddyizm in https://github.com/eddyizm/tempo/pull/168
* Fix album parse empty date field by @eddyizm in https://github.com/eddyizm/tempo/pull/171
* fix: Include shuffle/repeat controls in f-droid build's media notific… by @le-firehawk in https://github.com/eddyizm/tempo/pull/174
* fix: limits image size to prevent widget crash #172 by @eddyizm in https://github.com/eddyizm/tempo/pull/175

**Full Changelog**: https://github.com/eddyizm/tempo/compare/v3.17.0...v3.17.14

## [3.17.0](https://github.com/eddyizm/tempo/releases/tag/v3.17.0) (2025-10-10)
## What's Changed
* chore: adding screenshot and docs for 4 icons/buttons in player control by @eddyizm in https://github.com/eddyizm/tempo/pull/162
* Update Polish translation by @skajmer in https://github.com/eddyizm/tempo/pull/160
* feat: Make all objects in Tempo references for quick access by @le-firehawk in https://github.com/eddyizm/tempo/pull/158
* fix: Glide module incorrectly encoding IPv6 addresses by @le-firehawk in https://github.com/eddyizm/tempo/pull/159

**Full Changelog**: https://github.com/eddyizm/tempo/compare/v3.16.6...v3.17.0

## [3.16.6](https://github.com/eddyizm/tempo/releases/tag/v3.16.6) (2025-10-08)
## What's Changed
* chore(i18n): Update Spanish translation by @jaime-grj in https://github.com/eddyizm/tempo/pull/151
* fix: Re-add new equalizer settings that got lost by @jaime-grj in https://github.com/eddyizm/tempo/pull/153
* chore: removed play variant by @eddyizm in https://github.com/eddyizm/tempo/pull/155
* fix: updating release workflow to account for the 32/64 bit builds an… by @eddyizm in https://github.com/eddyizm/tempo/pull/156
* feat: Show sampling rate and bit depth in downloads by @jaime-grj in https://github.com/eddyizm/tempo/pull/154
* fix: Replace hardcoded strings in SettingsFragment by @jaime-grj in https://github.com/eddyizm/tempo/pull/152


**Full Changelog**: https://github.com/eddyizm/tempo/compare/v3.16.0...v3.16.6

## [3.16.0](https://github.com/eddyizm/tempo/releases/tag/v3.16.0) (2025-10-07)
## What's Changed
* chore: add sha256 fingerprint for validation by @eddyizm in https://github.com/eddyizm/tempo/commit/3c58e6fbb2157a804853259dfadbbffe3b6793b5
* fix: Prevent crash when getting artist radio and song list is null by @jaime-grj in https://github.com/eddyizm/tempo/pull/117
* chore: Update French localization by @benoit-smith in https://github.com/eddyizm/tempo/pull/125
* fix: Update search query validation to require at least 2 characters instead of 3 by @jaime-grj in https://github.com/eddyizm/tempo/pull/124
* feat: download starred artists. by @eddyizm in https://github.com/eddyizm/tempo/pull/137
* feat: Enable downloading of song lyrics for offline viewing by @le-firehawk in https://github.com/eddyizm/tempo/pull/99
* fix: Lag during startup when local url is not available by @SinTan1729 in https://github.com/eddyizm/tempo/pull/110
* chore: add link to discussion page in settings by @eddyizm in https://github.com/eddyizm/tempo/pull/143
* feat: Notification heart rating by @eddyizm in https://github.com/eddyizm/tempo/pull/140
* chore: Unify and update polish translation by @skajmer in https://github.com/eddyizm/tempo/pull/146
* chore: added sha256 signing key for verification by @eddyizm in https://github.com/eddyizm/tempo/pull/147
* feat: Support user-defined download directory for media by @le-firehawk in https://github.com/eddyizm/tempo/pull/21
* feat: Added support for skipping duplicates by @SinTan1729 in https://github.com/eddyizm/tempo/pull/135
* feat: Add home screen music playback widget and some updates in Turkish localization by @mucahit-kaya in https://github.com/eddyizm/tempo/pull/98

## New Contributors
* @SinTan1729 made their first contribution in https://github.com/eddyizm/tempo/pull/110

**Full Changelog**: https://github.com/eddyizm/tempo/compare/v3.15.0...v3.16.0

## [3.15.0](https://github.com/eddyizm/tempo/releases/tag/v3.15.0) (2025-09-23)
## What's Changed
* chore: Update French localization by @benoit-smith in https://github.com/eddyizm/tempo/pull/84
* chore: Update RU locale by @ArchiDevil in https://github.com/eddyizm/tempo/pull/87
* chore: Update Korean translations by @kongwoojin in https://github.com/eddyizm/tempo/pull/97
* fix: only plays the first song on an album by @eddyizm in https://github.com/eddyizm/tempo/pull/81
* fix: handle null and not crash when disconnecting chromecast by @eddyizm in https://github.com/eddyizm/tempo/pull/81
* feat: Built-in audio equalizer by @jaime-grj in https://github.com/eddyizm/tempo/pull/94
* fix: Resolve playback issues with live radio MPEG & HLS streams by @jaime-grj in https://github.com/eddyizm/tempo/pull/89
* chore: Updates to polish translation by @skajmer in https://github.com/eddyizm/tempo/pull/105
* feat: added 32bit build and debug build for testing. Removed unused f… by @eddyizm in https://github.com/eddyizm/tempo/pull/108
* feat: Mark currently playing song with play/pause button by @jaime-grj in https://github.com/eddyizm/tempo/pull/107
* fix: add listener to track playlist click/change by @eddyizm in https://github.com/eddyizm/tempo/pull/113
* feat: Tap anywhere on the song item to toggle playback by @jaime-grj in https://github.com/eddyizm/tempo/pull/112

## New Contributors
* @ArchiDevil made their first contribution in https://github.com/eddyizm/tempo/pull/87
* @kongwoojin made their first contribution in https://github.com/eddyizm/tempo/pull/97

**Full Changelog**: https://github.com/eddyizm/tempo/compare/v3.14.8...v3.15.0


## [3.14.8](https://github.com/eddyizm/tempo/releases/tag/v3.14.8) (2025-08-30)
## What's Changed
* fix: Use correct SearchView widget to avoid crash in AlbumListPageFragment by @jaime-grj in https://github.com/eddyizm/tempo/pull/76
* chore(i18n): Update Spanish (es-ES) and English translations by @jaime-grj in https://github.com/eddyizm/tempo/pull/77
* style: Center subtitle text in empty_download_layout in fragment_download.xml when there is more than one line by @jaime-grj in https://github.com/eddyizm/tempo/pull/78
* fix: Disable "sync starred tracks/albums" switches when Cancel is clicked in warning dialog, use proper view for "Sync starred albums" dialog by @jaime-grj in https://github.com/eddyizm/tempo/pull/79
* bug fixes, chores, docs v3.14.8 by @eddyizm in https://github.com/eddyizm/tempo/pull/80


**Full Changelog**: https://github.com/eddyizm/tempo/compare/v3.14.1...v3.14.8

## [3.14.1](https://github.com/eddyizm/tempo/releases/tag/v3.14.1) (2025-08-30)
## What's Changed
* feat: rating dialog added to album page by @eddyizm in https://github.com/eddyizm/tempo/pull/52
* style: Add song rating bar in landscape player controller layout by @jaime-grj in https://github.com/eddyizm/tempo/pull/57
* feat: setting to show/hide 5 star rating on playerview by @eddyizm in https://github.com/eddyizm/tempo/pull/59
* chore: setting-to-hide-song-rating by @eddyizm in https://github.com/eddyizm/tempo/pull/60
* fix: catches null value and prepares bundle appropriately adding sing… by @eddyizm in https://github.com/eddyizm/tempo/pull/64
* fix: artist filtering in library view browse artist resolves #45 by @eddyizm in https://github.com/eddyizm/tempo/pull/69
* chore: Update French localization by @benoit-smith in https://github.com/eddyizm/tempo/pull/70
* feat: adds sync starred albums functionality #66 by @eddyizm in https://github.com/eddyizm/tempo/pull/73


**Full Changelog**: https://github.com/eddyizm/tempo/compare/v3.13.0...v3.14.1

## [3.13.0](https://github.com/eddyizm/tempo/releases/tag/v3.13.0) (2025-08-23)
## What's Changed
* style: Change position and size of rating container by @jaime-grj in https://github.com/eddyizm/tempo/pull/44
* feat: Add Turkish localization (values-tr) by @mucahit-kaya in https://github.com/eddyizm/tempo/pull/50
* chore: adding a note/not fully baked label to the sync user play queue setting by @eddyizm in https://github.com/eddyizm/tempo/commit/8ed0a4642bd0cd637c65e3115142596331fa7ef7
* fix: moved hardcoded italian save text to string template, updated with english and italian language xmls by @eddyizm in https://github.com/eddyizm/tempo/commit/26a5fb029a07752c9c0db0d08a89afd638772579


## New Contributors
* @mucahit-kaya made their first contribution in https://github.com/eddyizm/tempo/pull/50

**Full Changelog**: https://github.com/eddyizm/tempo/compare/v3.12.0...v3.13.0

## [3.12.0](https://github.com/eddyizm/tempo/releases/tag/v3.12.0) (2025-08-15)
### What's Changed
* [chore]: add German translations for track info and home section strings (#29) by @BreadWare92 in https://github.com/eddyizm/tempo/pull/31
* [chore]: increased "Offline mode" text size, changed its color in dark theme by @jaime-grj in https://github.com/eddyizm/tempo/pull/33
* [chore]: Translations for sections by @skajmer in https://github.com/eddyizm/tempo/pull/30
* [chore]: Update French localization by @benoit-smith in https://github.com/eddyizm/tempo/pull/36
* [fix]: Show placeholder string in TrackInfoDialog fields when there is no data by @jaime-grj in https://github.com/eddyizm/tempo/pull/37
* [feat]: added transcoding codec and bitrate info to PlayerControllerFragment, replace hardcoded strings by @jaime-grj in https://github.com/eddyizm/tempo/pull/38
* [chore]: Update French localization by @benoit-smith in https://github.com/eddyizm/tempo/pull/39
* [feat]: show rating on song view by @eddyizm in https://github.com/eddyizm/tempo/pull/40

### New Contributors
* @BreadWare92 made their first contribution in https://github.com/eddyizm/tempo/pull/31
* @skajmer made their first contribution in https://github.com/eddyizm/tempo/pull/30
* @benoit-smith made their first contribution in https://github.com/eddyizm/tempo/pull/36

**Full Changelog**: https://github.com/eddyizm/tempo/compare/v3.11.2...v3.12.0

## [3.11.2](https://github.com/eddyizm/tempo/releases/tag/v3.11.2) (2025-08-09)


([Full Changelog](https://github.com/eddyizm/tempo/compare/v3.10.0...eddyizm:tempo:v3.11.2?expand=1))

**Housekeeping:**

- [Chore] Added change log.

**Merged pull requests:**

- [Fix] make hardcoded strings in home fragment dynamic [\#27](https://github.com/eddyizm/tempo/pull/22) ([jaime-grj](https://github.com/jaime-grj))

- [Fix] show "System default" language option, sort languages alphabetically, include country when showing language in settings [\#26](https://github.com/eddyizm/tempo/pull/26) ([jaime-grj ](https://github.com/jaime-grj))

- [Fix] check for IP connectivity instead of Internet access [\#25](https://github.com/eddyizm/tempo/pull/25) ([jaime-grj](https://github.com/jaime-grj))

- [Fix] hide unnecessary TextViews in AlbumPageFragment when there is no data, fixed incorrect album release date [\#24](https://github.com/eddyizm/tempo/pull/24) ([jaime-grj](https://github.com/jaime-grj))

- [Feat] show sampling rate and bit depth if available [\#22](https://github.com/eddyizm/tempo/pull/22) ([jaime-grj](https://github.com/jaime-grj))

- [Feat] Fix lyric scrolling during playback, keep screen on while viewing [\#20](https://github.com/eddyizm/tempo/pull/20) ([le-firehawk](https://github.com/le-firehawk))

## [3.10.0](https://github.com/eddyizm/tempo/releases/tag/v3.10.0) (2025-08-04)

**Merged pull requests:**

- [Fix] redirection to artist fragment on artist label click [\#379](https://github.com/CappielloAntonio/tempo/pull/379)
- [Fix] Player queue lag, limits [\#385](https://github.com/CappielloAntonio/tempo/pull/385)
- [Fix] crash when sorting albums with a null artist  [\#389](https://github.com/CappielloAntonio/tempo/pull/389)
- [Feat] Display toast message after adding a song to a playlist [\#371](https://github.com/CappielloAntonio/tempo/pull/371)
- [Feat] Album add to playlist context menu item [\#367](https://github.com/CappielloAntonio/tempo/pull/367)
- [Feat] Store and retrieve replay and shuffle states in preferences [\#397](https://github.com/CappielloAntonio/tempo/pull/397)
- [Feat] Enhance Android media player notification window #400
 [\#400](https://github.com/CappielloAntonio/tempo/pull/400)
- [Chore] Spanish translation [\#374](https://github.com/CappielloAntonio/tempo/pull/374)
- [Chore] Polish translation [\#378](https://github.com/CappielloAntonio/tempo/pull/378)

***This log is for this fork to detail updates since 3.9.0 from the main repo.***