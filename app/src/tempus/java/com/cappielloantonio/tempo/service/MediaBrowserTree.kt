package com.cappielloantonio.tempo.service

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import com.cappielloantonio.tempo.BuildConfig
import com.cappielloantonio.tempo.repository.AutomotiveRepository
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.util.ConstantsAA
import com.cappielloantonio.tempo.util.Preferences
import androidx.core.net.toUri
import androidx.media3.session.SessionError
import com.cappielloantonio.tempo.util.Preferences.getServerId

@UnstableApi
object MediaBrowserTree {
    private lateinit var appContext: Context
    private lateinit var automotiveRepository: AutomotiveRepository

    private var treeNodes: MutableMap<String, MediaItemNode> = mutableMapOf()

    private var isInitialized = false

    private fun iconUri(resId: Int): Uri =
        "android.resource://${BuildConfig.APPLICATION_ID}/$resId".toUri()

    private class MediaItemNode(val item: MediaItem) {
        private val children: MutableList<MediaItem> = ArrayList()

        fun addChild(childID: String) {
            this.children.add(treeNodes[childID]!!.item)
        }

        fun getChildren(): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val listenableFuture = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            val libraryResult = LibraryResult.ofItemList(children, null)

            listenableFuture.set(libraryResult)

            return listenableFuture
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildMediaItem(
        gridView: Boolean,
        title: String,
        mediaId: String,
        isPlayable: Boolean,
        isBrowsable: Boolean,
        mediaType: @MediaMetadata.MediaType Int,
        subtitleConfigurations: List<SubtitleConfiguration> = mutableListOf(),
        album: String? = null,
        artist: String? = null,
        genre: String? = null,
        sourceUri: Uri? = null,
        imageUri: Uri? = null
    ): MediaItem {
        val extras = Bundle()
        if( gridView ) {
                extras.apply {
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                )
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                )
            }
        }
        else{
            extras.apply {
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                )
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                )
            }
        }

        val metadata = MediaMetadata.Builder()
            .setAlbumTitle(album)
            .setTitle(title)
            .setArtist(artist)
            .setGenre(genre)
            .setIsBrowsable(isBrowsable)
            .setIsPlayable(isPlayable)
            .setArtworkUri(imageUri)
            .setMediaType(mediaType)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setSubtitleConfigurations(subtitleConfigurations)
            .setMediaMetadata(metadata)
            .setUri(sourceUri)
            .build()
    }
    fun initialize(
        context: Context,
        automotiveRepository: AutomotiveRepository) {
        this.automotiveRepository = automotiveRepository
        appContext = context.applicationContext
        if (isInitialized) return

        isInitialized = true
    }

    fun buildTree() {
        val albumView: Boolean = Preferences.isAndroidAutoAlbumViewEnabled()
        val homeView: Boolean = Preferences.isAndroidAutoHomeViewEnabled()
        val playlistView: Boolean = Preferences.isAndroidAutoPlaylistViewEnabled()
        val podcastView: Boolean = Preferences.isAndroidAutoPodcastViewEnabled()
        val radioView: Boolean = Preferences.isAndroidAutoRadioViewEnabled()

        // clear before rebuild
        treeNodes.clear()

        // This list must be exactly the same as the one in aa_tab_titles
        val allFunctions = listOf(
            ConstantsAA.HOME_ID,
            ConstantsAA.LAST_PLAYED_ID,
            ConstantsAA.ALBUMS_ID,
            ConstantsAA.ARTISTS_ID,
            ConstantsAA.PLAYLIST_ID,
            ConstantsAA.PODCAST_ID,
            ConstantsAA.RADIO_ID,
            ConstantsAA.FOLDER_ID,
            ConstantsAA.MOST_PLAYED_ID,
            ConstantsAA.RECENTLY_ADDED_ID,
            ConstantsAA.MADE_FOR_YOU_ID,
            ConstantsAA.STARRED_BUNDLE_ID,
            ConstantsAA.TRACKS_ID,
            ConstantsAA.GENRES_ID
        )

        // Prevents index error
        val indexMax = allFunctions.lastIndex

        fun indexGuard(index: Int, reset: () -> Unit): Int {
            return if (index in -1..indexMax) index else {
                reset()
                -1
            }
        }

        val tabIndex = listOf(
            indexGuard(Preferences.getAndroidAutoFirstTab(), Preferences::resetAndroidAutoFirstTab),
            indexGuard(Preferences.getAndroidAutoSecondTab(), Preferences::resetAndroidAutoSecondTab),
            indexGuard(Preferences.getAndroidAutoThirdTab(), Preferences::resetAndroidAutoThirdTab),
            indexGuard(Preferences.getAndroidAutoFourthTab(), Preferences::resetAndroidAutoFourthTab)
        )

        // Root level
        treeNodes[ConstantsAA.ROOT_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = "Root Folder",
                    mediaId = ConstantsAA.ROOT_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

		// All available functions
		// if HOME is in first place or no item is selected
        if (tabIndex.firstOrNull() == 0 || tabIndex.all { it == -1 }){
			treeNodes[ConstantsAA.HOME_ID] =
				MediaItemNode(
					buildMediaItem(
						gridView = homeView,
						title = appContext.getString(R.string.aa_home),
						mediaId = ConstantsAA.HOME_ID,
						isPlayable = false,
						isBrowsable = true,
						imageUri = iconUri(R.drawable.ic_aa_home),
						mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
					)
				)
		}
		else { // More instead of Home
			treeNodes[ConstantsAA.HOME_ID] =
				MediaItemNode(
					buildMediaItem(
						gridView = homeView,
						title = appContext.getString(R.string.aa_more),
						mediaId = ConstantsAA.HOME_ID,
						isPlayable = false,
						isBrowsable = true,
						imageUri = iconUri(R.drawable.ic_aa_other),
						mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
					)
				)
		}
		
        treeNodes[ConstantsAA.LAST_PLAYED_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_recent_albums),
                    mediaId = ConstantsAA.LAST_PLAYED_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_recent),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )
			
        treeNodes[ConstantsAA.ALBUMS_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_albums),
                    mediaId = ConstantsAA.ALBUMS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_albums),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )
			
        treeNodes[ConstantsAA.ARTISTS_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_artists),
                    mediaId = ConstantsAA.ARTISTS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_artists),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )
			
        treeNodes[ConstantsAA.PLAYLIST_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = playlistView,
                    title = appContext.getString(R.string.aa_playlists),
                    mediaId = ConstantsAA.PLAYLIST_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_playlist),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                )
            )

        treeNodes[ConstantsAA.PODCAST_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = podcastView,
                    title = appContext.getString(R.string.aa_podcast),
                    mediaId = ConstantsAA.PODCAST_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_podcasts),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS
                )
            )

        treeNodes[ConstantsAA.RADIO_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = radioView,
                    title = appContext.getString(R.string.aa_radio),
                    mediaId = ConstantsAA.RADIO_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_radio),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS
                )
            )

        treeNodes[ConstantsAA.MOST_PLAYED_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_album_most_played),
                    mediaId = ConstantsAA.MOST_PLAYED_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_mostplayed),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )
        treeNodes[ConstantsAA.RECENTLY_ADDED_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_album_recently_added),
                    mediaId = ConstantsAA.RECENTLY_ADDED_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_added_album),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )
		
        treeNodes[ConstantsAA.RECENT_TRACKS_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = false,
                    title = appContext.getString(R.string.aa_song_recently_played),
                    mediaId = ConstantsAA.RECENT_TRACKS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_recent_title),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[ConstantsAA.TRACKS_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = homeView,
                    title = appContext.getString(R.string.aa_tracks),
                    mediaId = ConstantsAA.TRACKS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_title),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[ConstantsAA.MADE_FOR_YOU_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = homeView,
                    title = appContext.getString(R.string.aa_made_for_you),
                    mediaId = ConstantsAA.MADE_FOR_YOU_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_for_you),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                )
            )

        treeNodes[ConstantsAA.STARRED_TRACKS_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = false,
                    title = appContext.getString(R.string.aa_starred_tracks),
                    mediaId = ConstantsAA.STARRED_TRACKS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_star_title),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[ConstantsAA.STARRED_BUNDLE_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_starred),
                    mediaId = ConstantsAA.STARRED_BUNDLE_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_bundle_star),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )

        treeNodes[ConstantsAA.STARRED_ALBUMS_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_starred_albums),
                    mediaId = ConstantsAA.STARRED_ALBUMS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_star_album),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )

        treeNodes[ConstantsAA.STARRED_ARTISTS_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_starred_artists),
                    mediaId = ConstantsAA.STARRED_ARTISTS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_artists),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
                )
            )

        treeNodes[ConstantsAA.FOLDER_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = false,
                    title = appContext.getString(R.string.aa_music_folder),
                    mediaId = ConstantsAA.FOLDER_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_folders),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[ConstantsAA.RANDOM_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = false,
                    title = appContext.getString(R.string.aa_random),
                    mediaId = ConstantsAA.RANDOM_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_random),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[ConstantsAA.GENRES_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = false,
                    title = appContext.getString(R.string.aa_genres),
                    mediaId = ConstantsAA.GENRES_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_genres),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[ConstantsAA.DOWNLOADED_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = false,
                    title = appContext.getString(R.string.download_title_section),
                    mediaId = ConstantsAA.DOWNLOADED_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_downloaded),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[ConstantsAA.QUICKMIX_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = false,
                    title = appContext.getString(R.string.aa_quick_mix),
                    artist = "By Tempus",
                    mediaId = ConstantsAA.MADE_FOR_YOU_SOURCE
                            + "[" +ConstantsAA.NUMBER_OF_TRACKS_IN_SMALL_MIX + "]"
                            + ConstantsAA.QUICKMIX_ID,
                    isPlayable = true,
                    isBrowsable = false,
                    imageUri = iconUri(R.drawable.ic_aa_quickmix),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[ConstantsAA.MYMIX_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = false,
                    title = appContext.getString(R.string.aa_my_mix),
                    artist = "By Tempus",
                    mediaId = ConstantsAA.MADE_FOR_YOU_SOURCE
                            + "[" + ConstantsAA.NUMBER_OF_TRACKS_IN_MEDIUM_MIX + "]"
                            + ConstantsAA.MYMIX_ID,
                    isPlayable = true,
                    isBrowsable = false,
                    imageUri = iconUri(R.drawable.ic_aa_mymix),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[ConstantsAA.DISCOVERYMIX_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = false,
                    title = appContext.getString(R.string.aa_discovery_mix),
                    artist = "By Tempus",
                    mediaId = ConstantsAA.MADE_FOR_YOU_SOURCE
                            + "[" + ConstantsAA.NUMBER_OF_TRACKS_IN_LARGE_MIX + "]"
                            + ConstantsAA.DISCOVERYMIX_ID,
                    isPlayable = true,
                    isBrowsable = false,
                    imageUri = iconUri(R.drawable.ic_aa_discoverymix),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        val root = treeNodes[ConstantsAA.ROOT_ID]!!
        val selectedIds = mutableSetOf<String>()

        // First level
		// add functions selected by user for the 4 tabs
        tabIndex
            .filter { it != -1 }
            .forEach { index ->
                allFunctions.getOrNull(index)?.let { function ->
                    if (selectedIds.add(function)) {
                        root.addChild(function)
                    }
                }
            }
		// if no function is selected, add at least HOME_ID
        if (selectedIds.isEmpty()) {
            root.addChild(ConstantsAA.HOME_ID)
            selectedIds.add(ConstantsAA.HOME_ID)
        }

        // Second level for HOME_ID even there is no HOME_ID displayed
        // Downloads first: it is the only section that works with no connectivity
        treeNodes[ConstantsAA.HOME_ID]?.addChild(ConstantsAA.DOWNLOADED_ID)

		// add all functions not previously added
        allFunctions
            .filter { it !in selectedIds }
            .forEach { function ->
                when (function) {
                    ConstantsAA.MADE_FOR_YOU_ID -> {
                        // add Quick Mix, My Mix and Discovery Mix instead of Made For You to Home
                        treeNodes[ConstantsAA.HOME_ID]!!.addChild(ConstantsAA.QUICKMIX_ID)
                        treeNodes[ConstantsAA.HOME_ID]!!.addChild(ConstantsAA.MYMIX_ID)
                        treeNodes[ConstantsAA.HOME_ID]!!.addChild(ConstantsAA.DISCOVERYMIX_ID)
                    }
                    ConstantsAA.STARRED_BUNDLE_ID -> {
                        // add starred function instead of Starred bundle to Home
                        treeNodes[ConstantsAA.HOME_ID]?.addChild(ConstantsAA.STARRED_ARTISTS_ID)
                        treeNodes[ConstantsAA.HOME_ID]?.addChild(ConstantsAA.STARRED_ALBUMS_ID)
                        treeNodes[ConstantsAA.HOME_ID]?.addChild(ConstantsAA.STARRED_TRACKS_ID)
                    }
                    ConstantsAA.TRACKS_ID -> {
                        // add Random and Recent instead of Tracks to Home
                        treeNodes[ConstantsAA.HOME_ID]?.addChild(ConstantsAA.RANDOM_ID)
                        treeNodes[ConstantsAA.HOME_ID]?.addChild(ConstantsAA.RECENT_TRACKS_ID)
                    }
                    else -> treeNodes[ConstantsAA.HOME_ID]?.addChild(function)
                }
            }

        // build Made For You bundle
        treeNodes[ConstantsAA.MADE_FOR_YOU_ID]!!.addChild(ConstantsAA.QUICKMIX_ID)
        treeNodes[ConstantsAA.MADE_FOR_YOU_ID]!!.addChild(ConstantsAA.MYMIX_ID)
        treeNodes[ConstantsAA.MADE_FOR_YOU_ID]!!.addChild(ConstantsAA.DISCOVERYMIX_ID)
        treeNodes[ConstantsAA.MADE_FOR_YOU_ID]!!.addChild(ConstantsAA.STARRED_ARTISTS_ID)
        treeNodes[ConstantsAA.MADE_FOR_YOU_ID]!!.addChild(ConstantsAA.STARRED_ALBUMS_ID)
        treeNodes[ConstantsAA.MADE_FOR_YOU_ID]!!.addChild(ConstantsAA.STARRED_TRACKS_ID)

        // create starred bundle
        treeNodes[ConstantsAA.STARRED_BUNDLE_ID]?.addChild(ConstantsAA.STARRED_ARTISTS_ID)
        treeNodes[ConstantsAA.STARRED_BUNDLE_ID]?.addChild(ConstantsAA.STARRED_ALBUMS_ID)
        treeNodes[ConstantsAA.STARRED_BUNDLE_ID]?.addChild(ConstantsAA.STARRED_TRACKS_ID)

        // create tracks bundle
        treeNodes[ConstantsAA.TRACKS_ID]?.addChild(ConstantsAA.RANDOM_ID)
        treeNodes[ConstantsAA.TRACKS_ID]?.addChild(ConstantsAA.GENRES_ID)
        treeNodes[ConstantsAA.TRACKS_ID]?.addChild(ConstantsAA.RECENT_TRACKS_ID)
        treeNodes[ConstantsAA.TRACKS_ID]?.addChild(ConstantsAA.STARRED_TRACKS_ID)
	}
	
    fun getRootItem(): MediaItem {
        return treeNodes[ConstantsAA.ROOT_ID]!!.item
    }

    fun getChildren(
        id: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return when (id) {
            ConstantsAA.ROOT_ID -> treeNodes[ConstantsAA.ROOT_ID]?.getChildren()!!

            ConstantsAA.HOME_ID -> treeNodes[ConstantsAA.HOME_ID]?.getChildren()!!
            ConstantsAA.MADE_FOR_YOU_ID -> treeNodes[ConstantsAA.MADE_FOR_YOU_ID]?.getChildren()!!
            ConstantsAA.STARRED_BUNDLE_ID -> treeNodes[ConstantsAA.STARRED_BUNDLE_ID]?.getChildren()!!
            ConstantsAA.TRACKS_ID -> treeNodes[ConstantsAA.TRACKS_ID]?.getChildren()!!

            ConstantsAA.LAST_PLAYED_ID -> automotiveRepository.getAlbums(
                ConstantsAA.ALBUM_ID,
                "recent",
                ConstantsAA.NUMBER_OF_DISPLAYED_ALBUMS,
                false)
            ConstantsAA.ALBUMS_ID -> automotiveRepository.getAlbums(
                ConstantsAA.ALBUM_ID,
                "alphabeticalByName",
                ConstantsAA.MAX_ITEMS,
                true)
            ConstantsAA.ARTISTS_ID -> automotiveRepository.getArtists(
                ConstantsAA.ARTIST_ID,
                true)
            ConstantsAA.PLAYLIST_ID -> automotiveRepository.getPlaylists(ConstantsAA.PLAYLIST_ID)
            ConstantsAA.PODCAST_ID -> automotiveRepository.getNewestPodcastEpisodes(ConstantsAA.NUMBER_OF_DISPLAYED_PODCASTS)
            ConstantsAA.RADIO_ID -> automotiveRepository.getInternetRadioStations()
            ConstantsAA.FOLDER_ID -> automotiveRepository.getMusicFolders(ConstantsAA.FOLDER_ID)
            ConstantsAA.MOST_PLAYED_ID -> automotiveRepository.getAlbums(
                ConstantsAA.ALBUM_ID,
                "frequent",
                ConstantsAA.NUMBER_OF_DISPLAYED_ALBUMS,
                false)
            ConstantsAA.RECENT_TRACKS_ID -> automotiveRepository.getRecentlyPlayedSongs(
                getServerId(),
                ConstantsAA.NUMBER_OF_DISPLAYED_RECENT_TRACKS)
            ConstantsAA.RECENTLY_ADDED_ID -> automotiveRepository.getAlbums(
                ConstantsAA.ALBUM_ID,
                "newest",
                ConstantsAA.NUMBER_OF_DISPLAYED_ALBUMS,
                false)
            ConstantsAA.DOWNLOADED_ID -> automotiveRepository.getDownloadedSongs()
            ConstantsAA.STARRED_TRACKS_ID -> automotiveRepository.getStarredSongs()
            ConstantsAA.STARRED_ALBUMS_ID -> automotiveRepository.getStarredAlbums(
                ConstantsAA.ALBUM_ID,
                true)
            ConstantsAA.STARRED_ARTISTS_ID -> automotiveRepository.getStarredArtists(
                ConstantsAA.ARTIST_ID,
                true)
            ConstantsAA.RANDOM_ID -> automotiveRepository.getRandomSongs(ConstantsAA.MAX_SHUFFLE_ITEMS)
            ConstantsAA.GENRES_ID -> automotiveRepository.getGenres(ConstantsAA.GENRES_ID)

            ConstantsAA.JUMP_TO_ALBUMS_ID -> automotiveRepository.getAlbums(
                ConstantsAA.ALBUM_ID,
                "alphabeticalByName",
                ConstantsAA.MAX_ITEMS,
                false)
            ConstantsAA.JUMP_TO_STARRED_ALBUMS_ID -> automotiveRepository.getStarredAlbums(
                ConstantsAA.ALBUM_ID,
                false)
            ConstantsAA.JUMP_TO_ARTISTS_ID -> automotiveRepository.getArtists(
                ConstantsAA.ARTIST_ID,
                false)
            ConstantsAA.JUMP_TO_STARRED_ARTISTS_ID -> automotiveRepository.getStarredArtists(
                ConstantsAA.ARTIST_ID,
                false)
            ConstantsAA.ARTISTS_BY_ALBUMS_ID -> automotiveRepository.getAlbums(
                ConstantsAA.ALBUM_ID,
                "alphabeticalByArtist",
                ConstantsAA.MAX_ITEMS,
                false)

            else -> {
                if (id.startsWith(ConstantsAA.GENRES_ID)) {
                    val shuffle = Preferences.isAndroidAutoShuffleGenreSongsEnabled()
                    // If the user doesn't want random songs, it's likely it's for perusing them, so provide as many as possible
                    val count = if (shuffle) ConstantsAA.MAX_SHUFFLE_ITEMS else ConstantsAA.MAX_ITEMS
                    return automotiveRepository.getSongsByGenre(id.removePrefix(ConstantsAA.GENRES_ID), count, shuffle)
                }

                if (id.startsWith(ConstantsAA.PLAYLIST_ID)) {
                    return automotiveRepository.getPlaylistSongs(id.removePrefix(ConstantsAA.PLAYLIST_ID))
                }

                if (id.startsWith(ConstantsAA.ALBUM_ID)) {
                    return automotiveRepository.getAlbumTracks(id.removePrefix(ConstantsAA.ALBUM_ID))
                }

                if (id.startsWith(ConstantsAA.ARTIST_ID)) {
                    return automotiveRepository.getArtistAlbum(ConstantsAA.ALBUM_ID,id.removePrefix(ConstantsAA.ARTIST_ID))
                }

                if (id.startsWith(ConstantsAA.FOLDER_ID)) {
                    return automotiveRepository.getIndexes(ConstantsAA.INDEX_ID,id.removePrefix(ConstantsAA.FOLDER_ID))
                }

                if (id.startsWith(ConstantsAA.INDEX_ID)) {
                    return automotiveRepository.getDirectories(ConstantsAA.DIRECTORY_ID,id.removePrefix(ConstantsAA.INDEX_ID))
                }

                if (id.startsWith(ConstantsAA.DIRECTORY_ID)) {
                    return automotiveRepository.getDirectories(ConstantsAA.DIRECTORY_ID,id.removePrefix(ConstantsAA.DIRECTORY_ID))
                }

                return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            }
        }
    }

    fun search(query: String): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return automotiveRepository.search(
            query,
            ConstantsAA.ALBUM_ID,
            ConstantsAA.ARTIST_ID
        )
    }
}
