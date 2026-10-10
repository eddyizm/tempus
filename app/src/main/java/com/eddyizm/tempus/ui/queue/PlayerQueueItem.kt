package com.eddyizm.tempus.ui.queue

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.bumptech.glide.signature.ObjectKey
import com.eddyizm.tempus.R
import com.eddyizm.tempus.glide.CustomGlideRequest
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.util.DownloadUtil
import com.eddyizm.tempus.util.ExternalAudioReader
import com.eddyizm.tempus.util.FavoriteRegistry
import com.eddyizm.tempus.util.MusicUtil
import com.eddyizm.tempus.util.Preferences
import com.eddyizm.tempus.util.RadioCoverArtDownloader

private class StateHolder<T>(var value: T? = null)

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerQueueItem(
    index: Int,
    item: QueueSongItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isPrior: Boolean,
    isDragging: Boolean,
    itemContainerColor: Color,
    cornerRadius: Dp,
    showRating: Boolean,
    onClick: (Int, Child) -> Unit,
    onRemove: (Int, QueueSongItem) -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    downloadedIds: Set<String> = emptySet(),
    canDismiss: Boolean = true,
    externalRefreshTrigger: Any? = null
) {
    val context = LocalContext.current
    val textAlpha = if (isPrior) 0.45f else 1.0f

    val currentIndex by rememberUpdatedState(index)
    val currentOnRemove by rememberUpdatedState(onRemove)
    val currentItem by rememberUpdatedState(item)

    val downloadDirUri = remember { Preferences.getDownloadDirectoryUri() }
    val downloaderManager = remember(context) { DownloadUtil.getDownloadTracker(context) }

    val isDownloaded = remember(item.song.id, downloadedIds, externalRefreshTrigger) {
        if (downloadDirUri == null) {
            downloadedIds.contains(item.song.id) || downloaderManager?.isDownloaded(item.song.id) == true
        } else {
            ExternalAudioReader.getUri(item.song) != null
        }
    }
    val isStarred = remember(item.song.id, item.song.starred) {
        FavoriteRegistry.resolve(FavoriteRegistry.Kind.SONG, item.song.id, item.song.starred != null)
    }
    val rating = item.song.userRating ?: 0

    val durationStr = remember(item.song.duration) {
        MusicUtil.getReadableDurationString(item.song.duration, false)
    }
    val qualityStr = remember(item.song) {
        MusicUtil.getReadableAudioQualityString(item.song)
    }
    val subtitle = stringResource(
        R.string.song_subtitle_formatter,
        item.song.artist ?: "",
        durationStr,
        qualityStr
    ).trim()

    var hasTriggeredDismiss by remember(item.song.id) { mutableStateOf(false) }
    var itemWidthPx by remember { mutableFloatStateOf(0f) }
    val dismissStateRef = remember { StateHolder<SwipeToDismissBoxState>() }

    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.4f },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart && !isCurrent && canDismiss) {
                val offset = runCatching { dismissStateRef.value?.requireOffset() }.getOrNull() ?: 0f
                val fraction = if (itemWidthPx > 0f) kotlin.math.abs(offset) / itemWidthPx else 1f
                if (fraction < 0.4f) {
                    return@rememberSwipeToDismissBoxState false
                }
                if (!hasTriggeredDismiss) {
                    hasTriggeredDismiss = true
                    currentOnRemove(currentIndex, currentItem)
                }
                true
            } else {
                false
            }
        }
    )
    dismissStateRef.value = dismissState

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !isCurrent && canDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { itemWidthPx = it.width.toFloat() },
        backgroundContent = {
            if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart ||
                dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.player_queue_remove),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        },
        content = {
            val itemBackground = if (isCurrent) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                itemContainerColor
            }

            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onClick(index, item.song) },
                color = itemBackground,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = if (isDragging) 8.dp else 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Artwork with play/pause overlay
                    QueueArtwork(
                        coverArtId = item.song.coverArtId,
                        size = 160,
                        cornerRadius = cornerRadius,
                        modifier = Modifier.size(52.dp),
                        overlayContent = if (isCurrent) {
                            {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0x80000000)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                                        contentDescription = stringResource(if (isPlaying) R.string.player_queue_pause else R.string.player_queue_play),
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        } else null
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title and Subtitle
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = item.song.title ?: "",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = (if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                .copy(alpha = textAlpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (isCurrent) Modifier.basicMarquee() else Modifier
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Metadata badges (Favorite, Rating, Download)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isStarred) {
                            Icon(
                                painter = painterResource(R.drawable.ic_favorite),
                                contentDescription = stringResource(R.string.player_queue_starred),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = textAlpha),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (showRating && rating > 0) {
                            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                repeat(5) { starIndex ->
                                    val isFilled = rating > starIndex
                                    Icon(
                                        painter = painterResource(if (isFilled) R.drawable.ic_star else R.drawable.ic_star_outlined),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = textAlpha),
                                        modifier = Modifier.size(8.dp)
                                    )
                                }
                            }
                        }

                        if (isDownloaded) {
                            Icon(
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = stringResource(R.string.player_queue_downloaded),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Trailing Reorder Handle (48dp minimum touch target)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .then(dragHandleModifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_drag_handle),
                                contentDescription = stringResource(R.string.player_queue_reorder),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun QueueArtwork(
    coverArtId: String?,
    modifier: Modifier = Modifier,
    artworkUri: Uri? = null,
    size: Int = 160,
    cornerRadius: Dp = 8.dp,
    contentDescription: String? = null,
    @DrawableRes placeholderRes: Int = R.drawable.ic_placeholder_song,
    overlayContent: (@Composable () -> Unit)? = null
) {
    val placeholderContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(placeholderRes),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
    val placeholder = placeholder(placeholderContent)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (coverArtId.isNullOrEmpty() && artworkUri == null) {
            placeholderContent()
        } else {
            GlideImage(
                model = artworkUri ?: CustomGlideRequest.createUrl(coverArtId, size),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                requestBuilderTransform = { request ->
                    var transformed = request
                        .signature(ObjectKey(coverArtId ?: artworkUri.toString()))
                        .diskCacheStrategy(CustomGlideRequest.DEFAULT_DISK_CACHE_STRATEGY)
                    if (Preferences.isDataSavingMode()) {
                        transformed = transformed.onlyRetrieveFromCache(true)
                    }
                    if (artworkUri != null) {
                        transformed = RadioCoverArtDownloader.applyLocalFileSignature(transformed, artworkUri)
                    }
                    transformed
                },
                loading = placeholder,
                failure = placeholder,
            )
        }

        overlayContent?.invoke()
    }
}
