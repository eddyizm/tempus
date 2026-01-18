# Tempus Usage Guide 
[<- back home](README.md)

## Table of Contents
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Server Configuration](#server-configuration)
- [Main Features](#main-features)
- [Navigation](#navigation)
- [Playback Controls](#playback-controls)
- [Favorites](#favorites)
- [Playlist Management](#playlist-management)
- [Android Auto](#android-auto)
- [Settings](#settings)
- [Known Issues](#known-issues)

## Prerequisites

**Important Notice**: This app is a Subsonic-compatible client and does not provide any music content itself. To use this application, you must have:

- An active Subsonic API server (or compatible service) already set up
- Valid login credentials for your Subsonic server
- Music content uploaded and organized on your server

### Verified backends
This app works with any service that implements the Subsonic API, including:
- [LMS - Lightweight Music Server](https://github.com/epoupon/lms) -  *personal fave and my backend*
- [Navidrome](https://www.navidrome.org/)
- [Gonic](https://github.com/sentriz/gonic)
- [Ampache](https://github.com/ampache/ampache)
- [NextCloud Music](https://apps.nextcloud.com/apps/music)
- [Airsonic Advanced](https://github.com/kagemomiji/airsonic-advanced)



## Getting Started

### Installation
1. Download the APK from the [Releases](https://github.com/eddyizm/tempus/releases) section
2. Enable "Install from unknown sources" in your Android settings
3. Install the application

### First Launch
1. Open the application
2. You will be prompted to configure your server connection
3. Grant necessary permissions for media playback and background operation

## Server Configuration

### Initial Setup
**IN PROGRESS**
1. Enter your server URL (e.g., `https://your-subsonic-server.com`)
2. Provide your username and password
3. Test the connection to ensure proper configuration

### Advanced Settings
**TODO**

## Main Features

### Library View

**Multi-library**

Tempus handles multi-library setups gracefully. They are displayed as Library folders. 

However, if you want to limit or change libraries you could use a workaround, if your server supports it.

You can create multiple users , one for each library, and save each of them in Tempus app.

### Folder or index playback

If your Subsonic-compatible server exposes the folder tree **or** provides an artist index (for example Gonic, Navidrome, or any backend with folder browsing enabled), Tempus lets you play an entire folder from anywhere in the library hierarchy:

<p align="left">
    <img src="mockup/usage/music_folders_root.png" width=317 style="margin-right:16px;">
    <img src="mockup/usage/music_folders_playback.png" width=317>
</p>

- The **Library ▸ Music folders** screen shows each top-level folder with a play icon only after you drill into it. The root entry remains a simple navigator.
- When viewing **inner folders** **or artist index entries**, tap the new play button to immediately enqueue every audio track inside that folder/index and all nested subfolders.
- Video files are excluded automatically, so only playable audio ends up in the queue.

No extra config is needed—Tempus adjusts based on the connected backend.

### Now Playing Screen

On the main player control screen, tapping on the artwork will reveal a small collection of 4 buttons/icons. 
<p align="left">
    <img src="mockup/usage/player_icons.png" width=159>
</p>

*marked the icons with numbers for clarity* 

1. Downloads the track (there is a notification if the android screen but not a pop toast currently )
2. Adds track to playlist - pops up playlist dialog.
3. Adds tracks to the queue via instant mix function
    * TBD: what is the _instant mix function_?
    * Uses [getSimilarSongs](https://opensubsonic.netlify.app/docs/endpoints/getsimilarsongs/) of OpenSubsonic API.
      Which tracks to be mixed depends on the server implementation. For example, Navidrome gets 15 similar artists from LastFM, then 20 top songs from each.
4. Saves play queue (if the feature is enabled in the settings) 
    * if the setting is not enabled, it toggles a view of the lyrics if available (slides to the right) 

### Podcasts  
If your server supports it - add a podcast rss feed
<p align="left">
    <img src="mockup/usage/add_podcast_feed.png" width=317>
</p>

### Radio Stations
If your server supports it - add a internet radio station feed
<p align="left">
    <img src="mockup/usage/add_radio_station.png" width=326>
</p>

## Navigation

### Bottom Navigation Bar
**IN PROGRESS**
- **Home**: Recently played and server recommendations
- **Library**: Your server's complete music collection
- **Download**: Locally downloaded files from server 

## Playback Controls

### Streaming Controls
**TODO**

### Advanced Controls
**TODO**

## Favorites

### Favorites (aka heart aka star) to albums and artists
- Long pressing on an album gives you access to heart/unheart an album   

<p align="center">
    <img src="mockup/usage/fave_album.png" width=376>
</p>

- Long pressing on an artist cover gets you the same access to to heart/unheart an album   

<p align="center">
    <img src="mockup/usage/fave_artist.png" width=376>
</p>


## Playlist Management

### Server Playlists
**TODO**

### Creating Playlists
**TODO**

## Settings


## Android Auto

### Enabling on your head unit
To allow the Tempus app on your car's head unit, "Unknown sources" needs to be enabled in the Android Auto "Developer settings". This is because Tempus isn't installed through Play Store. Note that the Android Auto developer settings are different from the global Android "Developer options".
1. Switch to developer mode in the Android Auto settings by tapping ten times on the "Version" item at the bottom, followed by giving your permission.
<p align="left">
   <img width="270" height="600" alt="1a" src="https://github.com/user-attachments/assets/f09f6999-9761-4b05-8ec7-bf221a15dda3" />
   <img width="270" height="600" alt="1b" src="https://github.com/user-attachments/assets/0795e508-ba01-41c5-96a7-7c03b0156591" />
   <img width="270" height="600" alt="1c" src="https://github.com/user-attachments/assets/51c15f67-fddb-452e-b5d3-5092edeab390" />
</p>
   
2. Go to the "Developer settings" by the menu at the top right.
<p align="left">
   <img width="270" height="600" alt="2" src="https://github.com/user-attachments/assets/1ecd1f3e-026d-4d25-87f2-be7f12efbac6" />
</p>

3. Scroll down to the bottom and check "Unknown sources".
<p align="left">
   <img width="270" height="600" alt="3" src="https://github.com/user-attachments/assets/37db88e9-1b76-417f-9c47-da9f3a750fff" />
</p>


### Server Settings
**IN PROGRESS**
- Manage multiple server connections
- Configure sync intervals
- Set data usage limits for streaming

### Audio Settings
**IN PROGRESS**
- Streaming quality settings
- Offline caching preferences

### Appearance
**TODO**

## Known Issues

### Airsonic Distorted Playback

First reported in issue [#226](https://github.com/eddyizm/tempus/issues/226)  
The work around is to disable the cache in the settings, (set to 0), and if needed, cleaning the (Android) cache fixes the problem.

### Support
For additional help:
- Question? Start a [Discussion](https://github.com/eddyizm/tempus/discussions)
- Open an [issue](https://github.com/eddyizm/tempus/issues) if you don't find a discussion solving your issue. 
- Consult your Subsonic server's documentation

---

*Note: This app requires a pre-existing Subsonic-compatible server with music content.*
