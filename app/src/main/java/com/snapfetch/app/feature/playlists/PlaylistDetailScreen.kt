package com.snapfetch.app.feature.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfetch.app.AppContainer
import com.snapfetch.app.core.perf.SnapThumbnail
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapTypography
import com.snapfetch.app.data.local.entities.PlaylistItemEntity
import com.snapfetch.app.domain.model.FormatSizeCalculator
import com.snapfetch.app.domain.model.LibraryMediaItem
import com.snapfetch.app.domain.model.MediaType
import com.snapfetch.app.domain.model.PlaylistSortMode
import com.snapfetch.app.domain.model.formatDurationSeconds
import com.snapfetch.app.feature.player.QueueTrack
import com.snapfetch.app.feature.player.RepeatMode
import kotlinx.coroutines.launch

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    container: AppContainer,
    onBack: () -> Unit,
    onPlayTrack: (String, Boolean) -> Unit
) {
    val colors = LocalSnapFetchColors.current
    val scope = rememberCoroutineScope()
    val dao = container.database.playlistDao()
    val playlist by dao.observePlaylist(playlistId).collectAsStateWithLifecycle(initialValue = null)
    val dbItems by dao.observePlaylistItems(playlistId).collectAsStateWithLifecycle(initialValue = emptyList())
    val queueState by container.playbackQueueController.state.collectAsStateWithLifecycle()

    val localItems = remember { mutableStateListOf<PlaylistItemEntity>() }
    val sortMode = playlist?.sortMode ?: PlaylistSortMode.MANUAL

    LaunchedEffect(dbItems, sortMode) {
        val sorted = when (sortMode) {
            PlaylistSortMode.MANUAL -> dbItems
            PlaylistSortMode.DATE_ADDED -> dbItems.sortedByDescending { it.addedAtEpochMs }
            PlaylistSortMode.TITLE -> dbItems.sortedBy { it.title.lowercase() }
            PlaylistSortMode.DURATION -> dbItems.sortedByDescending { it.durationSec }
            PlaylistSortMode.FILE_SIZE -> dbItems.sortedByDescending { it.sizeBytes }
        }
        localItems.clear()
        localItems.addAll(sorted)
    }

    var showSortMenu by remember { mutableStateOf(false) }
    var showAddVideosDialog by remember { mutableStateOf(false) }
    var availableToAdd by remember { mutableStateOf<List<LibraryMediaItem>>(emptyList()) }

    val totalDurationSec = localItems.sumOf { it.durationSec }
    val totalSizeBytes = localItems.sumOf { it.sizeBytes }

    val rowHeightPx = with(LocalDensity.current) { 70.dp.toPx() }
    var draggingIdx by remember { mutableIntStateOf(-1) }
    var dragDeltaY by remember { mutableFloatStateOf(0f) }

    fun launchPlayback(startIndex: Int, shuffle: Boolean) {
        if (localItems.isEmpty()) return
        val tracks = localItems.map {
            QueueTrack(
                mediaKey = it.mediaKey,
                title = it.title,
                subtitle = it.subtitle,
                uri = it.mediaUri.ifBlank { it.mediaKey },
                thumbnailUrl = it.thumbnailUri.orEmpty(),
                durationSec = it.durationSec,
                isLocal = true
            )
        }
        container.playbackQueueController.setQueue(tracks, startIndex = startIndex, shuffle = shuffle)
        onPlayTrack(tracks[startIndex].mediaKey, true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist?.name ?: "Playlist",
                    style = SnapTypography.ScreenTitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${localItems.size} items • ${formatDurationSeconds(totalDurationSec)} • ${FormatSizeCalculator.formatBytes(totalSizeBytes)}",
                    style = SnapTypography.Metadata,
                    color = colors.textSecondary
                )
            }
            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = colors.textPrimary)
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    PlaylistSortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = {
                                showSortMenu = false
                                scope.launch { dao.updateSortMode(playlistId, mode, System.currentTimeMillis()) }
                            }
                        )
                    }
                }
            }
            IconButton(onClick = { container.playbackQueueController.cycleRepeat() }) {
                Icon(
                    imageVector = if (queueState.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat",
                    tint = if (queueState.repeatMode != RepeatMode.OFF) colors.accent else colors.textSecondary
                )
            }
        }

        // Play All / Shuffle / + Add Videos bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { launchPlayback(0, shuffle = false) },
                enabled = localItems.isNotEmpty(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.buttonTextOnAccent
                )
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Play All", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = {
                    val idx = if (localItems.isNotEmpty()) localItems.indices.random() else 0
                    launchPlayback(idx, shuffle = true)
                },
                enabled = localItems.isNotEmpty(),
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textPrimary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Shuffle", color = colors.textPrimary)
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val completedDownloads = container.database.downloadDao().getPendingOrPaused()
                        val media = container.storageScanner.scanDeviceMedia()
                        val completedFromDb = mutableListOf<LibraryMediaItem>()
                        // Combine scanned media
                        availableToAdd = media
                        showAddVideosDialog = true
                    }
                },
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = colors.textPrimary)
                Text("Add", color = colors.textPrimary)
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = colors.dividerSubtle)

        if (localItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("Tap + Add to populate this playlist", style = SnapTypography.ListRowTitle, color = colors.textSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(localItems, key = { _, item -> item.id }) { index, item ->
                    val isDragged = index == draggingIdx
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(70.dp)
                            .background(if (isDragged) colors.cardSurface else Color.Transparent)
                            .clickable { launchPlayback(index, shuffle = false) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = colors.textMuted,
                            modifier = Modifier
                                .size(26.dp)
                                .pointerInput(item.id, localItems.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingIdx = index
                                            dragDeltaY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragDeltaY += dragAmount.y
                                            val steps = (dragDeltaY / rowHeightPx).toInt()
                                            if (steps != 0) {
                                                val target = (draggingIdx + steps).coerceIn(0, localItems.lastIndex)
                                                if (target != draggingIdx) {
                                                    val moved = localItems.removeAt(draggingIdx)
                                                    localItems.add(target, moved)
                                                    draggingIdx = target
                                                    dragDeltaY = 0f
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            draggingIdx = -1
                                            dragDeltaY = 0f
                                            val orderedIds = localItems.map { it.id }
                                            scope.launch { dao.reorderPlaylistItems(playlistId, orderedIds) }
                                        },
                                        onDragCancel = {
                                            draggingIdx = -1
                                            dragDeltaY = 0f
                                        }
                                    )
                                }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SnapThumbnail(
                            model = item.thumbnailUri,
                            contentDescription = item.title,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.cardSurface),
                            targetWidthPx = 150
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = SnapTypography.ListRowTitle,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatDurationSeconds(item.durationSec)} • ${FormatSizeCalculator.formatBytes(item.sizeBytes)}",
                                style = SnapTypography.Metadata,
                                color = colors.textSecondary
                            )
                        }
                        IconButton(onClick = {
                            scope.launch { dao.removeItem(playlistId, item.mediaKey) }
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove", tint = colors.textMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAddVideosDialog) {
        val selectedKeys = remember { mutableStateListOf<String>() }
        AlertDialog(
            onDismissRequest = { showAddVideosDialog = false },
            containerColor = colors.elevatedSurface,
            title = { Text("Add media to playlist", color = colors.textPrimary) },
            text = {
                if (availableToAdd.isEmpty()) {
                    Text("No local media files found yet.", color = colors.textSecondary)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(availableToAdd.size) { idx ->
                            val media = availableToAdd[idx]
                            val checked = media.mediaKey in selectedKeys
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (checked) selectedKeys.remove(media.mediaKey) else selectedKeys.add(media.mediaKey)
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(media.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.textPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val picked = availableToAdd.filter { it.mediaKey in selectedKeys }
                        scope.launch {
                            var pos = dao.getMaxPosition(playlistId) + 1
                            val now = System.currentTimeMillis()
                            dao.insertItems(
                                picked.map {
                                    PlaylistItemEntity(
                                        playlistId = playlistId,
                                        mediaKey = it.mediaKey,
                                        title = it.title,
                                        subtitle = it.subtitle,
                                        thumbnailUri = it.thumbnailModel?.toString(),
                                        mediaUri = it.uri,
                                        durationSec = it.durationSec,
                                        sizeBytes = it.sizeBytes,
                                        isAudio = it.mediaType == MediaType.MUSIC,
                                        position = pos++,
                                        addedAtEpochMs = now
                                    )
                                }
                            )
                        }
                        showAddVideosDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.buttonTextOnAccent)
                ) {
                    Text("Add (${selectedKeys.size})", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddVideosDialog = false }) { Text("Cancel") }
            }
        )
    }
}
