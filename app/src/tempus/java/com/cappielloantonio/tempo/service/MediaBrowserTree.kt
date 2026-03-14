package com.cappielloantonio.tempo.service

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import com.cappielloantonio.tempo.BuildConfig
import com.cappielloantonio.tempo.repository.AutomotiveRepository
import com.cappielloantonio.tempo.util.Preferences.getServerId
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.util.Preferences

object MediaBrowserTree {
    private lateinit var appContext: Context
    private lateinit var automotiveRepository: AutomotiveRepository

    private var treeNodes: MutableMap<String, MediaItemNode> = mutableMapOf()

    private var isInitialized = false

/*	data class FunctionItem(
		val id: String,
		var isDisplayed: Boolean
	)
*/
    // Root
    private const val ROOT_ID = "[rootID]"
	// Available functions
    private const val HOME_ID = "[homeID]"
    private const val LAST_PLAYED_ID = "[lastPlayedID]"
    private const val ALBUMS_ID = "[albumsID]"
    private const val ARTISTS_ID = "[artistsID]"
    private const val MOST_PLAYED_ID = "[mostPlayedID]"
    private const val PLAYLIST_ID = "[playlistID]"
    private const val PODCAST_ID = "[podcastID]"
    private const val RADIO_ID = "[radioID]"
    private const val RECENTLY_ADDED_ID = "[recentlyAddedID]"
    private const val RECENT_SONGS_ID = "[recentSongsID]"
    private const val MADE_FOR_YOU_ID = "[madeForYouID]"
    private const val STARRED_TRACKS_ID = "[starredTracksID]"
    private const val STARRED_ALBUMS_ID = "[starredAlbumsID]"
    private const val STARRED_ARTISTS_ID = "[starredArtistsID]"
    private const val RANDOM_ID = "[randomID]"
    private const val FOLDER_ID = "[folderID]"

	// System functions
    private const val INDEX_ID = "[indexID]"
    private const val DIRECTORY_ID = "[directoryID]"
    private const val ALBUM_ID = "[albumID]"
    private const val ARTIST_ID = "[artistID]"

    private fun iconUri(resId: Int): Uri =
        Uri.parse("android.resource://${BuildConfig.APPLICATION_ID}/$resId")

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
        var extras = Bundle()
        if( gridView ) {
                extras = Bundle().apply {
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
            extras = Bundle().apply {
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

		val tabIndex = listOf(
			Preferences.getAndroidAutoFirstTab(),
			Preferences.getAndroidAutoSecondTab(),
			Preferences.getAndroidAutoThirdTab(),
			Preferences.getAndroidAutoFourthTab()
		)
        // clear before rebuild
        treeNodes.clear()

        // This list must be exactly the same as the one in aa_tab_titles
        val allFunctions = listOf(
            HOME_ID,
            LAST_PLAYED_ID,
            ALBUMS_ID,
            ARTISTS_ID,
            PLAYLIST_ID,
            PODCAST_ID,
            RADIO_ID,
            FOLDER_ID,
            MOST_PLAYED_ID,
            // RECENT_SONGS_ID,            // => doesn't work !
            RECENTLY_ADDED_ID,
            // MADE_FOR_YOU_ID,            // => doesn't work !
            STARRED_TRACKS_ID,
            STARRED_ALBUMS_ID,
            STARRED_ARTISTS_ID,
            RANDOM_ID
        )

        // Root level
        treeNodes[ROOT_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = "Root Folder",
                    mediaId = ROOT_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

		// All available functions
		// if HOME is in first place or no item is selected
		if (tabIndex.firstOrNull() == 0 || tabIndex.all { it == -1 }){
			treeNodes[HOME_ID] =
				MediaItemNode(
					buildMediaItem(
						gridView = homeView,
						title = appContext.getString(R.string.aa_home),
						mediaId = HOME_ID,
						isPlayable = false,
						isBrowsable = true,
						imageUri = iconUri(R.drawable.ic_aa_home),
						mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
					)
				)
		}
		else { // More instead of Home
			treeNodes[HOME_ID] =
				MediaItemNode(
					buildMediaItem(
						gridView = homeView,
						title = appContext.getString(R.string.aa_more),
						mediaId = HOME_ID,
						isPlayable = false,
						isBrowsable = true,
						imageUri = iconUri(R.drawable.ic_aa_other),
						mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
					)
				)
		}
		
        treeNodes[LAST_PLAYED_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_recent_albums),
                    mediaId = LAST_PLAYED_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_recent),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )
			
        treeNodes[ALBUMS_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_albums),
                    mediaId = ALBUMS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_albums),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )
			
        treeNodes[ARTISTS_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_artists),
                    mediaId = ARTISTS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_artists),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )
			
        treeNodes[PLAYLIST_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = playlistView,
                    title = appContext.getString(R.string.aa_playlists),
                    mediaId = PLAYLIST_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_playlist),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                )
            )

        treeNodes[PODCAST_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = podcastView,
                    title = appContext.getString(R.string.aa_podcast),
                    mediaId = PODCAST_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_podcasts),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS
                )
            )

        treeNodes[RADIO_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = radioView,
                    title = appContext.getString(R.string.aa_radio),
                    mediaId = RADIO_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_radio),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS
                )
            )

        treeNodes[MOST_PLAYED_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_album_most_played),
                    mediaId = MOST_PLAYED_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_mostplayed),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )
        treeNodes[RECENTLY_ADDED_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_album_recently_added),
                    mediaId = RECENTLY_ADDED_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_added_album),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )
		
        treeNodes[RECENT_SONGS_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_song_recently_played),
                    mediaId = RECENT_SONGS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_recent_title),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )
		
        treeNodes[MADE_FOR_YOU_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_made_for_you),
                    mediaId = MADE_FOR_YOU_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_for_you),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                )
            )

        treeNodes[STARRED_TRACKS_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_starred_tracks),
                    mediaId = STARRED_TRACKS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_star_title),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[STARRED_ALBUMS_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_starred_albums),
                    mediaId = STARRED_ALBUMS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_star_album),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                )
            )

        treeNodes[STARRED_ARTISTS_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_starred_artists),
                    mediaId = STARRED_ARTISTS_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_artists),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
                )
            )

        treeNodes[FOLDER_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = false,
                    title = appContext.getString(R.string.aa_music_folder),
                    mediaId = FOLDER_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_folders),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        treeNodes[RANDOM_ID] =
            MediaItemNode(
                buildMediaItem(
                    gridView = albumView,
                    title = appContext.getString(R.string.aa_random),
                    mediaId = RANDOM_ID,
                    isPlayable = false,
                    isBrowsable = true,
                    imageUri = iconUri(R.drawable.ic_aa_random),
                    mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                )
            )

        val root = treeNodes[ROOT_ID]!!
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
            root.addChild(HOME_ID)
            selectedIds.add(HOME_ID)
        }

        // Second level for HOME_ID even there is no HOME_ID displayed
		// add all functions not previously added
        allFunctions
            .filter { it !in selectedIds }
            .forEach { function ->
                treeNodes[HOME_ID]?.addChild(function)
            }
	}
	
    fun getRootItem(): MediaItem {
        return treeNodes[ROOT_ID]!!.item
    }

    fun getChildren(
        id: String
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return when (id) {
            ROOT_ID -> treeNodes[ROOT_ID]?.getChildren()!!

            HOME_ID -> treeNodes[HOME_ID]?.getChildren()!!
            LAST_PLAYED_ID -> automotiveRepository.getAlbums(id, "recent", 15)
            ALBUMS_ID -> automotiveRepository.getAlbums(id, "alphabeticalByName", 500)
            ARTISTS_ID -> automotiveRepository.getAlbums(id, "alphabeticalByArtist", 500)
            PLAYLIST_ID -> automotiveRepository.getPlaylists(id)
            PODCAST_ID -> automotiveRepository.getNewestPodcastEpisodes(100)
            RADIO_ID -> automotiveRepository.internetRadioStations
            FOLDER_ID -> automotiveRepository.getMusicFolders(id)
            MOST_PLAYED_ID -> automotiveRepository.getAlbums(id, "frequent", 15)
            RECENT_SONGS_ID -> automotiveRepository.getRecentlyPlayedSongs(getServerId(),30)
            RECENTLY_ADDED_ID -> automotiveRepository.getAlbums(id, "newest", 15)
            MADE_FOR_YOU_ID -> automotiveRepository.getStarredArtists(id)
            STARRED_TRACKS_ID -> automotiveRepository.starredSongs
            STARRED_ALBUMS_ID -> automotiveRepository.getStarredAlbums(id)
            STARRED_ARTISTS_ID -> automotiveRepository.getStarredArtists(id)
            RANDOM_ID -> automotiveRepository.getRandomSongs(100)

            else -> {
                if (id.startsWith(LAST_PLAYED_ID)) {
                    return automotiveRepository.getAlbumTracks(id.removePrefix(LAST_PLAYED_ID))
                }

                if (id.startsWith(ALBUMS_ID)) {
                    return automotiveRepository.getAlbumTracks(id.removePrefix(ALBUMS_ID))
                }

                if (id.startsWith(ARTISTS_ID)) {
                    return automotiveRepository.getAlbumTracks(id.removePrefix(ARTISTS_ID))
                }

                if (id.startsWith(HOME_ID)) {
                    return automotiveRepository.getAlbumTracks(id.removePrefix(HOME_ID))
                }

                if (id.startsWith(MOST_PLAYED_ID)) {
                    return automotiveRepository.getAlbumTracks(id.removePrefix(MOST_PLAYED_ID))
                }

                if (id.startsWith(RECENTLY_ADDED_ID)) {
                    return automotiveRepository.getAlbumTracks(id.removePrefix(RECENTLY_ADDED_ID))
                }

                if (id.startsWith(MADE_FOR_YOU_ID)) {
                    return automotiveRepository.getMadeForYou(id.removePrefix(MADE_FOR_YOU_ID),20)
                }

                if (id.startsWith(STARRED_ALBUMS_ID)) {
                    return automotiveRepository.getAlbumTracks(id.removePrefix(STARRED_ALBUMS_ID))
                }

                if (id.startsWith(STARRED_ARTISTS_ID)) {
                    return automotiveRepository.getArtistAlbum(STARRED_ALBUMS_ID,id.removePrefix(STARRED_ARTISTS_ID))
                }

                if (id.startsWith(PLAYLIST_ID)) {
                    return automotiveRepository.getPlaylistSongs(id.removePrefix(PLAYLIST_ID))
                }

                if (id.startsWith(ALBUM_ID)) {
                    return automotiveRepository.getAlbumTracks(id.removePrefix(ALBUM_ID))
                }

                if (id.startsWith(ARTIST_ID)) {
                    return automotiveRepository.getArtistAlbum(ALBUM_ID,id.removePrefix(ARTIST_ID))
                }

                if (id.startsWith(FOLDER_ID)) {
                    return automotiveRepository.getIndexes(INDEX_ID,id.removePrefix(FOLDER_ID))
                }

                if (id.startsWith(INDEX_ID)) {
                    return automotiveRepository.getDirectories(DIRECTORY_ID,id.removePrefix(INDEX_ID))
                }

                if (id.startsWith(DIRECTORY_ID)) {
                    return automotiveRepository.getDirectories(DIRECTORY_ID,id.removePrefix(DIRECTORY_ID))
                }

                return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
        }
    }

    // https://github.com/androidx/media/issues/156
    fun getItems(mediaItems: List<MediaItem>): List<MediaItem> {
        val updatedMediaItems = ArrayList<MediaItem>()

        mediaItems.forEach {
            if (it.localConfiguration?.uri != null) {
                updatedMediaItems.add(it)
            } else {
                val sessionMediaItem = automotiveRepository.getSessionMediaItem(it.mediaId)

                if (sessionMediaItem != null) {
                    var toAdd = automotiveRepository.getMetadatas(sessionMediaItem.timestamp!!)
                    val index = toAdd.indexOfFirst { mediaItem -> mediaItem.mediaId == it.mediaId }

                    toAdd = toAdd.subList(index, toAdd.size)

                    updatedMediaItems.addAll(toAdd)
                }
            }
        }

        return updatedMediaItems
    }

    fun search(query: String): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return automotiveRepository.search(
            query,
       //     ALBUM_ID,
            ALBUM_ID,
            ARTIST_ID
        )
    }
}
