# Changelog

***This log is for this fork to detail updates since 3.9.0 from the main repo.***

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
