package com.eddyizm.tempus.ui.queue

import android.os.Build
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.util.Preferences
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun rememberAppColorScheme(): ColorScheme {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (Preferences.getTheme()) {
        "light" -> false
        "dark" -> true
        else -> isSystemDark
    }

    val defaultScheme = when {
        Preferences.isDynamicColorAccent() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> darkColorScheme()
        else -> lightColorScheme()
    }

    val isAmoled = isDark && Preferences.isDarkThemeBlack()

    return remember(context, isDark, isAmoled) {
        val theme = context.theme
        val typedValue = TypedValue()

        fun resolveColor(@AttrRes attr: Int, fallback: Color): Color {
            return if (theme.resolveAttribute(attr, typedValue, true)) {
                Color(typedValue.data)
            } else {
                fallback
            }
        }

        val primary = resolveColor(com.google.android.material.R.attr.colorPrimary, defaultScheme.primary)
        val onPrimary = resolveColor(com.google.android.material.R.attr.colorOnPrimary, defaultScheme.onPrimary)
        val surface = resolveColor(com.google.android.material.R.attr.colorSurface, defaultScheme.surface)
        val onSurface = resolveColor(com.google.android.material.R.attr.colorOnSurface, defaultScheme.onSurface)
        val onSurfaceVariant = resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant, defaultScheme.onSurfaceVariant)
        val surfaceContainer = resolveColor(com.google.android.material.R.attr.colorSurfaceContainer, defaultScheme.surfaceContainer)
        val surfaceContainerLow = resolveColor(com.google.android.material.R.attr.colorSurfaceContainerLow, defaultScheme.surfaceContainerLow)
        val surfaceContainerHighest = resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHighest, defaultScheme.surfaceContainerHighest)
        val secondaryContainer = resolveColor(com.google.android.material.R.attr.colorSecondaryContainer, defaultScheme.secondaryContainer)
        val onSecondaryContainer = resolveColor(com.google.android.material.R.attr.colorOnSecondaryContainer, defaultScheme.onSecondaryContainer)
        val error = resolveColor(com.google.android.material.R.attr.colorError, defaultScheme.error)
        val onError = resolveColor(com.google.android.material.R.attr.colorOnError, defaultScheme.onError)
        val errorContainer = resolveColor(com.google.android.material.R.attr.colorErrorContainer, defaultScheme.errorContainer)
        val onErrorContainer = resolveColor(com.google.android.material.R.attr.colorOnErrorContainer, defaultScheme.onErrorContainer)

        defaultScheme.copy(
            primary = primary,
            onPrimary = onPrimary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            surface = if (isAmoled) Color.Black else surface,
            surfaceContainerLow = if (isAmoled) Color.Black else surfaceContainerLow,
            surfaceContainer = if (isAmoled) Color(0xFF121212) else surfaceContainer,
            surfaceContainerHighest = if (isAmoled) Color(0xFF1E1E1E) else surfaceContainerHighest,
            onSurface = onSurface,
            onSurfaceVariant = onSurfaceVariant,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerQueueSheet(
    isOpen: Boolean,
    onDismissRequest: () -> Unit,
    queueSongs: List<Child>,
    currentIndex: Int,
    isPlaying: Boolean,
    downloadedIds: Set<String>,
    onTrackClick: (Int, Child, List<Child>) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onSwapTracks: (from: Int, to: Int) -> Unit,
    onClearUpcoming: (List<Child>, Int) -> Unit,
    onShuffleUpcoming: (List<Child>, Int) -> Unit,
    onSaveToPlaylist: (List<Child>) -> Unit,
    onDownloadAll: (List<Child>) -> Unit,
    onLoadSavedQueue: () -> Unit,
    modifier: Modifier = Modifier,
    currentSongId: String? = null,
    externalRefreshTrigger: Any? = null,
    onSheetReady: ((suspend () -> Unit)) -> Unit = {},
) {
    if (!isOpen) return

    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (Preferences.getTheme()) {
        "light" -> false
        "dark" -> true
        else -> isSystemDark
    }
    val isAmoled = isDark && Preferences.isDarkThemeBlack()

    val colorScheme = rememberAppColorScheme()
    val containerColor = if (isAmoled) Color.Black else colorScheme.surfaceContainerLow

    var reorderedChildList by remember { mutableStateOf<List<Child>?>(null) }
    val currentRawSongs = reorderedChildList ?: queueSongs

    val currentSongs = remember(currentRawSongs) {
        // Duplicate tracks need distinct keys, hence "id#n" past the first occurrence.
        val counts = HashMap<String, Int>()
        currentRawSongs.map { child ->
            val occ = counts.getOrDefault(child.id, 0)
            counts[child.id] = occ + 1
            val stableId = if (occ == 0) child.id else "${child.id}#$occ"
            QueueSongItem(
                stableId = stableId,
                song = child
            )
        }
    }

    val currentPlayingStableId = remember(queueSongs, currentIndex, currentSongId) {
        if (currentIndex in queueSongs.indices) {
            val counts = HashMap<String, Int>()
            var targetStableId: String? = null
            for ((i, song) in queueSongs.withIndex()) {
                val occ = counts.getOrDefault(song.id, 0)
                counts[song.id] = occ + 1
                if (i == currentIndex) {
                    targetStableId = if (occ == 0) song.id else "${song.id}#$occ"
                    break
                }
            }
            targetStableId
        } else {
            null
        }
    }

    val effectiveCurrentIndex = remember(currentSongs, currentIndex, currentPlayingStableId, currentSongId) {
        if (currentPlayingStableId != null) {
            val idx = currentSongs.indexOfFirst { it.stableId == currentPlayingStableId }
            if (idx >= 0) idx else currentIndex
        } else if (currentSongId != null) {
            val idx = currentSongs.indexOfFirst { it.song.id == currentSongId }
            if (idx >= 0) idx else currentIndex
        } else {
            currentIndex
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    LaunchedEffect(sheetState) {
        onSheetReady { sheetState.hide() }
    }
    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = effectiveCurrentIndex.coerceAtLeast(0)
    )

    val hapticFeedback = LocalHapticFeedback.current
    var dragStartIndex by remember { mutableStateOf<Int?>(null) }
    var dragEndIndex by remember { mutableStateOf<Int?>(null) }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
    val reorderScrollPadding = PaddingValues(
        bottom = 72.dp + navBarPadding.calculateBottomPadding()
    )

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        scrollThresholdPadding = reorderScrollPadding
    ) { from, to ->
        if (dragStartIndex == null) {
            dragStartIndex = from.index
        }
        dragEndIndex = to.index
        val current = currentRawSongs.toMutableList()
        if (from.index in current.indices && to.index in current.indices) {
            val item = current.removeAt(from.index)
            current.add(to.index, item)
            reorderedChildList = current
        }
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    val totalDurationSeconds = remember(currentSongs) {
        currentSongs.sumOf { (it.song.duration ?: 0).toLong() }
    }

    val upcomingCount = remember(currentSongs.size, effectiveCurrentIndex) {
        if (effectiveCurrentIndex >= 0) {
            (currentSongs.size - (effectiveCurrentIndex + 1)).coerceAtLeast(0)
        } else {
            currentSongs.size
        }
    }

    MaterialTheme(colorScheme = colorScheme) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = containerColor,
            contentColor = colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            dragHandle = null,
            modifier = modifier
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PlayerQueueHeader(
                    songCount = currentSongs.size,
                    totalDurationSeconds = totalDurationSeconds,
                    upcomingSongsCount = upcomingCount,
                    isSyncEnabled = Preferences.isSyncronizationEnabled(),
                    onClearUpcoming = { onClearUpcoming(currentRawSongs, effectiveCurrentIndex) },
                    onShuffleUpcoming = { onShuffleUpcoming(currentRawSongs, effectiveCurrentIndex) },
                    onSaveToPlaylist = { onSaveToPlaylist(currentSongs.map { it.song }) },
                    onDownloadAll = { onDownloadAll(currentSongs.map { it.song }) },
                    onLoadSavedQueue = onLoadSavedQueue
                )

                PlayerQueueList(
                    items = currentSongs,
                    currentIndex = effectiveCurrentIndex,
                    isPlaying = isPlaying,
                    lazyListState = lazyListState,
                    reorderableState = reorderableState,
                    itemContainerColor = containerColor,
                    downloadedIds = downloadedIds,
                    externalRefreshTrigger = externalRefreshTrigger,
                    onDragStarted = {
                        dragStartIndex = null
                        dragEndIndex = null
                    },
                    onDragStopped = {
                        val initialIndex = dragStartIndex
                        val finalIndex = dragEndIndex
                        dragStartIndex = null
                        dragEndIndex = null
                        reorderedChildList = null
                        if (initialIndex != null && finalIndex != null && initialIndex != finalIndex) {
                            onSwapTracks(initialIndex, finalIndex)
                        }
                    },
                    onTrackClick = { clickedIdx, clickedChild ->
                        onTrackClick(clickedIdx, clickedChild, currentRawSongs)
                    },
                    onRemoveTrack = { removeIndex, _ ->
                        reorderedChildList = null
                        onRemoveTrack(removeIndex)
                    }
                )
            }
        }
    }
}
