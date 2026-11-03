package com.eddyizm.tempus.ui.components

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.bumptech.glide.signature.ObjectKey
import com.eddyizm.tempus.R
import com.eddyizm.tempus.glide.CustomGlideRequest
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.util.Preferences
import com.eddyizm.tempus.util.RadioCoverArtDownloader
import kotlin.math.absoluteValue

@Composable
fun NowPlayingArtworkPager(
    queue: List<Child>,
    currentIndex: Int,
    isRadio: Boolean = false,
    radioArtworkUri: Uri? = null,
    radioCoverArtId: String? = null,
    onPageSelected: (Int) -> Unit = {},
    onCoverClick: () -> Unit = {}
) {
    val currentQueue by rememberUpdatedState(queue)
    val currentTrackIndex by rememberUpdatedState(currentIndex)
    val currentOnPageSelected by rememberUpdatedState(onPageSelected)
    val currentOnCoverClick by rememberUpdatedState(onCoverClick)

    val count = if (isRadio || queue.isEmpty()) 1 else queue.size
    val safeIndex = if (isRadio) 0 else currentIndex.coerceIn(0, (count - 1).coerceAtLeast(0))

    val pagerState = rememberPagerState(
        initialPage = safeIndex,
        pageCount = { count }
    )
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    var userSwiped by remember { mutableStateOf(false) }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            userSwiped = true
        }
    }

    var firstSelectionDone by remember { mutableStateOf(false) }
    LaunchedEffect(safeIndex, count) {
        if (!isDragged && safeIndex in 0 until count && safeIndex != pagerState.targetPage) {
            if (!firstSelectionDone || (safeIndex - pagerState.currentPage).absoluteValue > 1) {
                pagerState.scrollToPage(safeIndex)
                firstSelectionDone = true
            } else {
                pagerState.animateScrollToPage(
                    page = safeIndex,
                    animationSpec = tween(durationMillis = 300)
                )
            }
        }
    }

    // Capture user swipe settlement and trigger track change only when user physically swiped
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settledPage ->
            if (userSwiped && !isRadio && settledPage in currentQueue.indices && settledPage != currentTrackIndex) {
                userSwiped = false
                currentOnPageSelected(settledPage)
            }
        }
    }

    val cornerRadius = if (Preferences.isCornerRoundingEnabled()) {
        Preferences.getRoundedCornerSize().dp
    } else {
        0.dp
    }

    if (!isRadio && queue.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadius)),
            contentAlignment = Alignment.Center
        ) {
            SongArtwork(coverArtId = null, title = null)
        }
        return
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 0.dp),
        pageSpacing = 16.dp,
        userScrollEnabled = !isRadio && count > 1,
        beyondViewportPageCount = 1,
        key = { page ->
            if (isRadio) "radio" else "${queue.getOrNull(page)?.id ?: "track"}_$page"
        }
    ) { page ->
        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
        val scale = lerp(0.92f, 1.0f, 1f - pageOffset.coerceIn(0f, 1f))
        val alpha = lerp(0.7f, 1.0f, 1f - pageOffset.coerceIn(0f, 1f))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .clip(RoundedCornerShape(cornerRadius))
                .clickable {
                    if (page == pagerState.currentPage) {
                        onCoverClick()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isRadio) {
                RadioArtwork(artworkUri = radioArtworkUri, coverArtId = radioCoverArtId)
            } else {
                val song = queue.getOrNull(page)
                SongArtwork(coverArtId = song?.coverArtId, title = song?.title)
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun SongArtwork(
    coverArtId: String?,
    title: String?
) {
    if (coverArtId.isNullOrEmpty()) {
        Image(
            painter = painterResource(id = R.drawable.ic_placeholder_song),
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        return
    }

    val dataSaving = rememberDataSavingMode()

    GlideImage(
        model = CustomGlideRequest.createUrl(coverArtId, Preferences.getImageSize()),
        contentDescription = title,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        requestBuilderTransform = { request ->
            var transformed = request
                .signature(ObjectKey(coverArtId))
                .diskCacheStrategy(CustomGlideRequest.DEFAULT_DISK_CACHE_STRATEGY)
            if (dataSaving) {
                transformed = transformed.onlyRetrieveFromCache(true)
            }
            transformed
        },
        loading = placeholder {
            Image(
                painter = painterResource(id = R.drawable.ic_placeholder_song),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        },
        failure = placeholder {
            Image(
                painter = painterResource(id = R.drawable.ic_placeholder_song),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun RadioArtwork(
    artworkUri: Uri?,
    coverArtId: String?
) {
    if (artworkUri == null) {
        Image(
            painter = painterResource(id = R.drawable.ic_placeholder_radio),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        return
    }

    GlideImage(
        model = artworkUri,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        requestBuilderTransform = { request ->
            var transformed = request
                .signature(ObjectKey(coverArtId ?: artworkUri.toString()))
                .diskCacheStrategy(CustomGlideRequest.DEFAULT_DISK_CACHE_STRATEGY)
            transformed = RadioCoverArtDownloader.applyLocalFileSignature(transformed, artworkUri)
            transformed
        },
        loading = placeholder {
            Image(
                painter = painterResource(id = R.drawable.ic_placeholder_radio),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        },
        failure = placeholder {
            Image(
                painter = painterResource(id = R.drawable.ic_placeholder_radio),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    )
}
