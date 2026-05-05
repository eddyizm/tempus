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
import com.cappielloantonio.tempo.util.Constants
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
            Constants.AA_HOME_ID,
            Constants.AA_LAST_PLAYED_ID,
            Constants.AA_ALBUMS_ID,
            Constants.AA_ARTISTS_ID,
            Constants.AA_PLAYLIST_ID,
            Constants.AA_PODCAST_ID,
            Constants.AA_RADIO_ID,
            Constants.AA_FOLDER_ID,
            Constants.AA_MOST_PLAYED_ID,
            Constants.AA_RECENTLY_ADDED_ID,
            Constants.AA_MADE_FOR_YOU_ID,
            Constants.AA_STARRED_BUNDLE_ID,
            Constants.AA_TRACKS_ID,
            Constants.AA_GENRES_ID
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
        treeNodes[Constants.AA_ROOT_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = "Root Folder",
                    mediaId = Constants.AA_ROOT_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

		// All available functions
		// if HOME is in first place or no item is selected
        if (tabIndex.firstOrNull() == 0 || tabIndex.all { it == -1 }){
			treeNodes[Constants.AA_HOME_ID] =
				MediaItemNode(
					buildMediaItem(
						gridView = homeView,
						title = appContext.getString(R.string.aa_home),
						mediaId = Constants.AA_HOME_ID,
						isPlayable = false,
						isBrowsable = true,
						imageUri = iconUri(R.drawable.ic_aa_home),
						mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
					)
				)
		}
		else { // More instead of Home
			treeNodes[Constants.AA_HOME_ID] =
				MediaItemNode(
					buildMediaItem(
						gridView = homeView,
						title = appContext.getString(R.string.aa_more),
						mediaId = Constants.AA_HOME_ID,
						isPlayable = false,
						isBrowsable = true,
						imageUri = iconUri(R.drawable.ic_aa_other),
						mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
					)
				)
		}
		
        treeNodes[Constants.AA_LAST_PLAYED_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_recent_albums),
                    mediaId = Constants.AA_LAST_PLAYED_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_recent),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )
			
        treeNodes[Constants.AA_ALBUMS_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_albums),
                    mediaId = Constants.AA_ALBUMS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_albums),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )
			
        treeNodes[Constants.AA_ARTISTS_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_artists),
                    mediaId = Constants.AA_ARTISTS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_artists),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )
			
        treeNodes[Constants.AA_PLAYLIST_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = playlistView,
                    title = appContext.getString(R.string.aa_playlists),
                    mediaId = Constants.AA_PLAYLIST_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_playlist),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                )
            )

        treeNodes[Constants.AA_PODCAST_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = podcastView,
                    title = appContext.getString(R.string.aa_podcast),
                    mediaId = Constants.AA_PODCAST_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_podcasts),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS
                )
            )

        treeNodes[Constants.AA_RADIO_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = radioView,
                    title = appContext.getString(R.string.aa_radio),
                    mediaId = Constants.AA_RADIO_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_radio),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS
                )
            )

        treeNodes[Constants.AA_MOST_PLAYED_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_album_most_played),
                    mediaId = Constants.AA_MOST_PLAYED_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_mostplayed),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )
        treeNodes[Constants.AA_RECENTLY_ADDED_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_album_recently_added),
                    mediaId = Constants.AA_RECENTLY_ADDED_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_added_album),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )
		
        treeNodes[Constants.AA_RECENT_TRACKS_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = false,
                    title = appContext.getString(R.string.aa_song_recently_played),
                    mediaId = Constants.AA_RECENT_TRACKS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_recent_title),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[Constants.AA_TRACKS_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = homeView,
                    title = appContext.getString(R.string.aa_tracks),
                    mediaId = Constants.AA_TRACKS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_title),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[Constants.AA_MADE_FOR_YOU_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = homeView,
                    title = appContext.getString(R.string.aa_made_for_you),
                    mediaId = Constants.AA_MADE_FOR_YOU_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_for_you),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                )
            )

        treeNodes[Constants.AA_STARRED_TRACKS_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = false,
                    title = appContext.getString(R.string.aa_starred_tracks),
                    mediaId = Constants.AA_STARRED_TRACKS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_star_title),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[Constants.AA_STARRED_BUNDLE_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_starred),
                    mediaId = Constants.AA_STARRED_BUNDLE_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_bundle_star),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )

        treeNodes[Constants.AA_STARRED_ALBUMS_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_starred_albums),
                    mediaId = Constants.AA_STARRED_ALBUMS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_star_album),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )

        treeNodes[Constants.AA_STARRED_ARTISTS_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_starred_artists),
                    mediaId = Constants.AA_STARRED_ARTISTS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_artists),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
                )
            )

        treeNodes[Constants.AA_FOLDER_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = false,
                    title = appContext.getString(R.string.aa_music_folder),
                    mediaId = Constants.AA_FOLDER_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_folders),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[Constants.AA_RANDOM_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = false,
                    title = appContext.getString(R.string.aa_random),
                    mediaId = Constants.AA_RANDOM_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_random),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[Constants.AA_GENRES_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = false,
                    title = appContext.getString(R.string.aa_genres),
                    mediaId = Constants.AA_GENRES_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_genres),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[Constants.AA_QUICKMIX_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = false,
                    title = appContext.getString(R.string.aa_quick_mix),
                    artist = "By Tempus",
                    mediaId = Constants.AA_MADE_FOR_YOU_SOURCE + "[" + 12 + "]" + Constants.AA_QUICKMIX_ID,
                    isPlayable = true,
                    isBrowsable = false,
                    imageUri = iconUri(R.drawable.ic_aa_quickmix),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[Constants.AA_MYMIX_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = false,
                    title = appContext.getString(R.string.aa_my_mix),
                    artist = "By Tempus",
                    mediaId = Constants.AA_MADE_FOR_YOU_SOURCE + "[" + 15 + "]" + Constants.AA_MYMIX_ID,
                    isPlayable = true,
                    isBrowsable = false,
                    imageUri = iconUri(R.drawable.ic_aa_mymix),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[Constants.AA_DISCOVERYMIX_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = false,
                    title = appContext.getString(R.string.aa_discovery_mix),
                    artist = "By Tempus",
                    mediaId = Constants.AA_MADE_FOR_YOU_SOURCE + "[" + 18 + "]" + Constants.AA_DISCOVERYMIX_ID,
                    isPlayable = true,
                    isBrowsable = false,
                    imageUri = iconUri(R.drawable.ic_aa_discoverymix),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        val root = treeNodes[Constants.AA_ROOT_ID]!!
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
            root.addChild(Constants.AA_HOME_ID)
            selectedIds.add(Constants.AA_HOME_ID)
        }

        // Second level for HOME_ID even there is no HOME_ID displayed
		// add all functions not previously added
        allFunctions
            .filter { it !in selectedIds }
            .forEach { function ->
                when (function) {
                    Constants.AA_MADE_FOR_YOU_ID -> {
                        // add Quick Mix, My Mix and Discovery Mix instead of Made For You to Home
                        treeNodes[Constants.AA_HOME_ID]!!.addChild(Constants.AA_QUICKMIX_ID)
                        treeNodes[Constants.AA_HOME_ID]!!.addChild(Constants.AA_MYMIX_ID)
                        treeNodes[Constants.AA_HOME_ID]!!.addChild(Constants.AA_DISCOVERYMIX_ID)
                    }
                    Constants.AA_STARRED_BUNDLE_ID -> {
                        // add starred function instead of Starred bundle to Home
                        treeNodes[Constants.AA_HOME_ID]?.addChild(Constants.AA_STARRED_ARTISTS_ID)
                        treeNodes[Constants.AA_HOME_ID]?.addChild(Constants.AA_STARRED_ALBUMS_ID)
                        treeNodes[Constants.AA_HOME_ID]?.addChild(Constants.AA_STARRED_TRACKS_ID)
                    }
                    Constants.AA_TRACKS_ID -> {
                        // add Random and Recent instead of Tracks to Home
                        treeNodes[Constants.AA_HOME_ID]?.addChild(Constants.AA_RANDOM_ID)
                        treeNodes[Constants.AA_HOME_ID]?.addChild(Constants.AA_RECENT_TRACKS_ID)
                    }
                    else -> treeNodes[Constants.AA_HOME_ID]?.addChild(function)
                }
            }

        // build Made For You bundle
        treeNodes[Constants.AA_MADE_FOR_YOU_ID]!!.addChild(Constants.AA_QUICKMIX_ID)
        treeNodes[Constants.AA_MADE_FOR_YOU_ID]!!.addChild(Constants.AA_MYMIX_ID)
        treeNodes[Constants.AA_MADE_FOR_YOU_ID]!!.addChild(Constants.AA_DISCOVERYMIX_ID)
        treeNodes[Constants.AA_MADE_FOR_YOU_ID]!!.addChild(Constants.AA_STARRED_ARTISTS_ID)
        treeNodes[Constants.AA_MADE_FOR_YOU_ID]!!.addChild(Constants.AA_STARRED_ALBUMS_ID)
        treeNodes[Constants.AA_MADE_FOR_YOU_ID]!!.addChild(Constants.AA_STARRED_TRACKS_ID)

        // create starred bundle
        treeNodes[Constants.AA_STARRED_BUNDLE_ID]?.addChild(Constants.AA_STARRED_ARTISTS_ID)
        treeNodes[Constants.AA_STARRED_BUNDLE_ID]?.addChild(Constants.AA_STARRED_ALBUMS_ID)
        treeNodes[Constants.AA_STARRED_BUNDLE_ID]?.addChild(Constants.AA_STARRED_TRACKS_ID)

        // create tracks bundle
        treeNodes[Constants.AA_TRACKS_ID]?.addChild(Constants.AA_RANDOM_ID)
        treeNodes[Constants.AA_TRACKS_ID]?.addChild(Constants.AA_GENRES_ID)
        treeNodes[Constants.AA_TRACKS_ID]?.addChild(Constants.AA_RECENT_TRACKS_ID)
        treeNodes[Constants.AA_TRACKS_ID]?.addChild(Constants.AA_STARRED_TRACKS_ID)
	}
	
    fun getRootItem(): MediaItem {
        return treeNodes[Constants.AA_ROOT_ID]!!.item
    }

    fun getChildren(
        id: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return when (id) {
            Constants.AA_ROOT_ID -> treeNodes[Constants.AA_ROOT_ID]?.getChildren()!!

            Constants.AA_HOME_ID -> treeNodes[Constants.AA_HOME_ID]?.getChildren()!!
            Constants.AA_MADE_FOR_YOU_ID -> treeNodes[Constants.AA_MADE_FOR_YOU_ID]?.getChildren()!!
            Constants.AA_STARRED_BUNDLE_ID -> treeNodes[Constants.AA_STARRED_BUNDLE_ID]?.getChildren()!!
            Constants.AA_TRACKS_ID -> treeNodes[Constants.AA_TRACKS_ID]?.getChildren()!!

            Constants.AA_LAST_PLAYED_ID -> automotiveRepository.getAlbums(Constants.AA_ALBUM_ID, "recent", 15, false)
            Constants.AA_ALBUMS_ID -> automotiveRepository.getAlbums(Constants.AA_ALBUM_ID, "alphabeticalByName", 500, true)
            Constants.AA_ARTISTS_ID -> automotiveRepository.getArtists(Constants.AA_ARTIST_ID, 500, true)
            Constants.AA_PLAYLIST_ID -> automotiveRepository.getPlaylists(Constants.AA_PLAYLIST_ID)
            Constants.AA_PODCAST_ID -> automotiveRepository.getNewestPodcastEpisodes(100)
            Constants.AA_RADIO_ID -> automotiveRepository.getInternetRadioStations()
            Constants.AA_FOLDER_ID -> automotiveRepository.getMusicFolders(Constants.AA_FOLDER_ID)
            Constants.AA_MOST_PLAYED_ID -> automotiveRepository.getAlbums(Constants.AA_ALBUM_ID, "frequent", 15, false)
            Constants.AA_RECENT_TRACKS_ID -> automotiveRepository.getRecentlyPlayedSongs(getServerId(),100)
            Constants.AA_RECENTLY_ADDED_ID -> automotiveRepository.getAlbums(Constants.AA_ALBUM_ID, "newest", 15, false)
            Constants.AA_STARRED_TRACKS_ID -> automotiveRepository.getStarredSongs(500)
            Constants.AA_STARRED_ALBUMS_ID -> automotiveRepository.getStarredAlbums(Constants.AA_ALBUM_ID, true)
            Constants.AA_STARRED_ARTISTS_ID -> automotiveRepository.getStarredArtists(Constants.AA_ARTIST_ID, true)
            Constants.AA_RANDOM_ID -> automotiveRepository.getRandomSongs(100)
            Constants.AA_GENRES_ID -> automotiveRepository.getGenres(Constants.AA_GENRES_ID)

            Constants.AA_JUMP_TO_ALBUMS_ID -> automotiveRepository.getAlbums(Constants.AA_ALBUM_ID, "alphabeticalByName", 500, false)
            Constants.AA_JUMP_TO_STARRED_ALBUMS_ID -> automotiveRepository.getStarredAlbums(Constants.AA_ALBUM_ID, false)
            Constants.AA_JUMP_TO_ARTISTS_ID -> automotiveRepository.getArtists(Constants.AA_ARTIST_ID, 500, false)
            Constants.AA_JUMP_TO_STARRED_ARTISTS_ID -> automotiveRepository.getStarredArtists(Constants.AA_ARTIST_ID, false)
            Constants.AA_ARTISTS_BY_ALBUMS_ID -> automotiveRepository.getAlbums(Constants.AA_ALBUM_ID, "alphabeticalByArtist", 500, false)

            else -> {
                if (id.startsWith(Constants.AA_GENRES_ID)) {
                    val shuffle = Preferences.isAndroidAutoShuffleGenreSongsEnabled()
                    // If the user doesn't want random songs, it's likely it's for perusing them, so provide as many as possible
                    val count = if (shuffle) 100 else 500
                    return automotiveRepository.getSongsByGenre(id.removePrefix(Constants.AA_GENRES_ID), count, shuffle)
                }

                if (id.startsWith(Constants.AA_PLAYLIST_ID)) {
                    return automotiveRepository.getPlaylistSongs(id.removePrefix(Constants.AA_PLAYLIST_ID))
                }

                if (id.startsWith(Constants.AA_ALBUM_ID)) {
                    return automotiveRepository.getAlbumTracks(id.removePrefix(Constants.AA_ALBUM_ID))
                }

                if (id.startsWith(Constants.AA_ARTIST_ID)) {
                    return automotiveRepository.getArtistAlbum(Constants.AA_ALBUM_ID,id.removePrefix(Constants.AA_ARTIST_ID))
                }

                if (id.startsWith(Constants.AA_FOLDER_ID)) {
                    return automotiveRepository.getIndexes(Constants.AA_INDEX_ID,id.removePrefix(Constants.AA_FOLDER_ID))
                }

                if (id.startsWith(Constants.AA_INDEX_ID)) {
                    return automotiveRepository.getDirectories(Constants.AA_DIRECTORY_ID,id.removePrefix(Constants.AA_INDEX_ID))
                }

                if (id.startsWith(Constants.AA_DIRECTORY_ID)) {
                    return automotiveRepository.getDirectories(Constants.AA_DIRECTORY_ID,id.removePrefix(Constants.AA_DIRECTORY_ID))
                }

                return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
            }
        }
    }

    fun search(query: String): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return automotiveRepository.search(
            query,
            Constants.AA_ALBUM_ID,
            Constants.AA_ARTIST_ID
        )
    }
}
