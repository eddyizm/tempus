package com.eddyizm.tempus.util

import android.content.Context
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.scheduler.Requirements
import com.eddyizm.tempus.R
import com.eddyizm.tempus.repository.ArtistRepository
import com.eddyizm.tempus.subsonic.utils.CacheUtil
import com.eddyizm.tempus.subsonic.models.Child

object ArtistDownload {

    /**
     * Downloads the artist's tracks that are not on the device yet: the same songs as play next and artist sync,
     * the artist's own tracks across its albums. Every outcome ends in a toast, including why nothing was queued.
     * Holds only the application context, so it finishes after the menu has closed. Call on the main thread.
     */
    @JvmStatic
    @OptIn(UnstableApi::class)
    fun start(context: Context, artistId: String) {
        val appContext = context.applicationContext
        toast(appContext, appContext.getString(R.string.artist_bottom_sheet_loading_tracks))

        ArtistRepository().getArtistAllSongs(artistId, object : ArtistRepository.ArtistSongsCallback {
            override fun onSongsCollected(songs: List<Child>) {
                if (songs.isEmpty()) {
                    toast(appContext, appContext.getString(R.string.artist_download_no_tracks))
                    return
                }
                // Skips what is downloaded or already requested and honours a custom download folder.
                DownloadUtil.downloadMissing(appContext, songs, null, null) { queued ->
                    toast(appContext, when {
                        queued > 0 && waitsForNetwork(appContext) ->
                            appContext.resources.getQuantityString(R.plurals.artist_download_waiting_for_network, queued, queued)
                        queued > 0 -> appContext.resources.getQuantityString(R.plurals.songs_download_started, queued, queued)
                        queued == 0 -> appContext.getString(R.string.artist_download_already_downloaded)
                        else -> appContext.getString(R.string.artist_download_not_started)
                    })
                }
            }

            // Offline, timed out, or an album failed to load: queue nothing rather than part of the artist.
            override fun onFailed(songs: List<Child>) {
                toast(appContext, appContext.getString(R.string.artist_download_load_failed))
            }
        })
    }

    // Offline, the track list can come from the HTTP cache. Media3 then holds the downloads until the network is back,
    // which a custom download folder does not do.
    @OptIn(UnstableApi::class)
    private fun waitsForNetwork(context: Context): Boolean =
        !CacheUtil.isConnected() && Preferences.getDownloadDirectoryUri() == null &&
            (DownloadUtil.getDownloadManager(context).notMetRequirements and
                (Requirements.NETWORK or Requirements.NETWORK_UNMETERED)) != 0

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
