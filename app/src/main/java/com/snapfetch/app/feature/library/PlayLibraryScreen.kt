package com.snapfetch.app.feature.library

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfetch.app.AppContainer
import com.snapfetch.app.core.components.ResumeProgressBar
import com.snapfetch.app.core.datastore.LibraryLayoutDensity
import com.snapfetch.app.core.datastore.UserSettings
import com.snapfetch.app.core.perf.SnapThumbnail
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapTypography
import com.snapfetch.app.domain.model.DownloadStatus
import com.snapfetch.app.domain.model.FormatSizeCalculator
import com.snapfetch.app.domain.model.LibraryMediaItem
import com.snapfetch.app.domain.model.MediaType
import com.snapfetch.app.domain.model.formatDurationSeconds
import com.snapfetch.app.feature.player.QueueTrack
import com.snapfetch.app.feature.playlists.AddToPlaylistSheet
import com.snapfetch.app.feature.playlists.PlaylistsTabContent
import kotlinx.coroutines.launch
import java.io.File

enum class PlaySubTab(val label: String) {
    ALL("All"),
    VIDEO("Video"),
    MUSIC("Music"),
    PICTURES("Pictures"),
    PLAYLISTS("Playlists")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayLibraryScreen(
    container: AppContainer,
    onOpenPlayer: (String, Boolean) -> Unit,
    onOpenDownloadQueue: () -> Unit,
    onOpenPlaylistDetail: (String) -> Unit,
    onOpenCompressSheet: (String, String, Int, Long, String?) -> Unit,
    onOpenTrashCleaner: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalSnapFetchColors.current
    val scope = rememberCoroutineScope()

    val settings by container.settingsDataStore.settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())
    val completedDownloads by container.database.downloadDao().observeCompletedDownloads().collectAsStateWithLifecycle(initialValue = emptyList())
    val activeQueue by container.database.downloadDao().observeActiveQueue().collectAsStateWithLifecycle(initialValue = emptyList())
    val playbackPositions by container.database.playbackPositionDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedTab by remember { mutableStateOf(PlaySubTab.ALL) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var filterQuery by remember { mutableStateOf("") }
    var deviceMedia by remember { mutableStateOf<List<LibraryMediaItem>>(emptyList()) }
    var addToPlaylistTargets by remember { mutableStateOf<List<LibraryMediaItem>?>(null) }
    val selectedKeys = remember { mutableStateListOf<String>() }

    val mediaPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    var hasStoragePerm by remember {
        mutableStateOf(
            mediaPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasStoragePerm = result.values.any { it }
    }

    LaunchedEffect(hasStoragePerm) {
        if (hasStoragePerm) {
            deviceMedia = container.storageScanner.scanDeviceMedia()
        }
    }

    val posMap = remember(playbackPositions) { playbackPositions.associateBy { it.mediaKey } }

    val combinedMedia = remember(completedDownloads, deviceMedia, posMap) {
        val dbItems = completedDownloads.mapNotNull { d ->
            val uri = d.localFileUri ?: return@mapNotNull null
            val pos = posMap[d.downloadId] ?: posMap[uri]
            LibraryMediaItem(
                mediaKey = d.downloadId,
                title = d.title,
                subtitle = d.channelName,
                uri = uri,
                thumbnailModel = d.thumbnailUrl.ifBlank { uri },
                mediaType = d.mediaType,
                durationSec = d.durationSec,
                sizeBytes = d.totalBytes,
                dateAddedMs = d.completedAtEpochMs ?: d.createdAtEpochMs,
                isAppDownload = true,
                downloadId = d.downloadId,
                resumePositionMs = pos?.positionMs ?: 0L,
                resumeDurationMs = pos?.durationMs ?: 0L
            )
        }
        val dbUris = dbItems.map { it.uri }.toSet()
        val deviceMerged = deviceMedia.filter { it.uri !in dbUris }.map { item ->
            val pos = posMap[item.mediaKey]
            item.copy(
                resumePositionMs = pos?.positionMs ?: 0L,
                resumeDurationMs = pos?.durationMs ?: 0L
            )
        }
        (dbItems + deviceMerged).sortedByDescending { it.dateAddedMs }
    }

    val filteredMedia = remember(combinedMedia, selectedTab, filterQuery) {
        val byTab = when (selectedTab) {
            PlaySubTab.ALL -> combinedMedia
            PlaySubTab.VIDEO -> combinedMedia.filter { it.mediaType == MediaType.VIDEO }
            PlaySubTab.MUSIC -> combinedMedia.filter { it.mediaType == MediaType.MUSIC }
            PlaySubTab.PICTURES -> combinedMedia.filter { it.mediaType == MediaType.PICTURE }
            PlaySubTab.PLAYLISTS -> emptyList()
        }
        if (filterQuery.isBlank()) byTab
        else byTab.filter { it.title.contains(filterQuery, ignoreCase = true) }
    }

    val thumbPx = container.lowRamConfig.effectiveThumbnailPx(settings.thumbnailQuality, settings.dataSaverEnabled)
    val preferRgb565 = container.lowRamConfig.isLowRamDevice || settings.dataSaverEnabled

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // Header matching 07-downloaded.webp ("Downloaded" + Filter, Trash, Search icons)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = 16.dp, end = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Downloaded",
                style = SnapTypography.ScreenTitle,
                color = colors.textPrimary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    IconButton(onClick = { filterMenuOpen = true }) {
                        Icon(Icons.Filled.FilterAlt, contentDescription = "Filter", tint = colors.textPrimary)
                    }
                    // Filter popup menu matching 08-filter.webp
                    DropdownMenu(expanded = filterMenuOpen, onDismissRequest = { filterMenuOpen = false }) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
                            text = { Text("Music only") },
                            onClick = {
                                selectedTab = PlaySubTab.MUSIC
                                filterMenuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Filled.VideoFile, contentDescription = null) },
                            text = { Text("Video only") },
                            onClick = {
                                selectedTab = PlaySubTab.VIDEO
                                filterMenuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                            text = { Text("Picture etc.") },
                            onClick = {
                                selectedTab = PlaySubTab.PICTURES
                                filterMenuOpen = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Show all") },
                            onClick = {
                                selectedTab = PlaySubTab.ALL
                                filterMenuOpen = false
                            }
                        )
                    }
                }
                IconButton(onClick = onOpenTrashCleaner) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Recover or Clean", tint = colors.textPrimary)
                }
                IconButton(onClick = { searchVisible = !searchVisible }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search library", tint = colors.textPrimary)
                }
            }
        }

        if (searchVisible) {
            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                placeholder = { Text("Filter downloaded media...") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { filterQuery = ""; searchVisible = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close search")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Segmented sub-tabs: All | Video | Music | Pictures | Playlists
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlaySubTab.entries.forEach { tab ->
                val active = tab == selectedTab
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (active) colors.accent else colors.cardSurface,
                    modifier = Modifier.clickable { selectedTab = tab }
                ) {
                    Text(
                        text = tab.label,
                        style = SnapTypography.Metadata.copy(
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        ),
                        color = if (active) colors.buttonTextOnAccent else colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // Compact active-queue status banner -> DownloadQueueScreen
        if (activeQueue.isNotEmpty()) {
            val activeCount = activeQueue.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.MERGING }
            val totalProgress = activeQueue.map { it.progressFraction }.average().toFloat()
            val pct = (totalProgress * 100f).toInt().coerceIn(0, 100)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { onOpenDownloadQueue() },
                shape = RoundedCornerShape(10.dp),
                color = colors.accent.copy(alpha = 0.16f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (activeCount > 0) "Downloading $activeCount/${activeQueue.size} • $pct%" else "Download queue (${activeQueue.size} paused/queued)",
                            style = SnapTypography.ListRowTitle.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary
                        )
                        Text("View queue >", style = SnapTypography.Metadata, color = colors.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { totalProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = colors.accent,
                        trackColor = colors.borderLight
                    )
                }
            }
        }

        if (!hasStoragePerm && selectedTab != PlaySubTab.PLAYLISTS) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { permLauncher.launch(mediaPermissions) },
                shape = RoundedCornerShape(8.dp),
                color = colors.cardSurface
            ) {
                Text(
                    text = "Grant media permission to scan existing device videos & music >",
                    style = SnapTypography.Metadata,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (selectedTab == PlaySubTab.PLAYLISTS) {
            PlaylistsTabContent(
                container = container,
                onOpenPlaylist = onOpenPlaylistDetail
            )
        } else if (filteredMedia.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No media items here yet",
                    style = SnapTypography.ListRowTitle,
                    color = colors.textSecondary
                )
            }
        } else {
            val isSelectionMode = selectedKeys.isNotEmpty()
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(
                    items = filteredMedia,
                    key = { it.mediaKey },
                    contentType = { settings.libraryLayoutDensity }
                ) { item ->
                    val isSelected = item.mediaKey in selectedKeys
                    var itemMenuOpen by remember { mutableStateOf(false) }

                    fun openInPlayer() {
                        val tracks = filteredMedia.map {
                            QueueTrack(
                                mediaKey = it.mediaKey,
                                title = it.title,
                                subtitle = it.subtitle,
                                uri = it.uri,
                                thumbnailUrl = it.thumbnailModel?.toString().orEmpty(),
                                durationSec = it.durationSec,
                                isLocal = true
                            )
                        }
                        val idx = tracks.indexOfFirst { it.mediaKey == item.mediaKey }.coerceAtLeast(0)
                        container.playbackQueueController.setQueue(tracks, startIndex = idx)
                        onOpenPlayer(item.mediaKey, true)
                    }

                    if (settings.libraryLayoutDensity == LibraryLayoutDensity.LARGE_CARDS) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            if (isSelected) selectedKeys.remove(item.mediaKey) else selectedKeys.add(item.mediaKey)
                                        } else openInPlayer()
                                    },
                                    onLongClick = {
                                        if (!isSelected) selectedKeys.add(item.mediaKey)
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.cardSurface)
                            ) {
                                SnapThumbnail(
                                    model = item.thumbnailModel,
                                    contentDescription = item.title,
                                    modifier = Modifier.fillMaxSize(),
                                    targetWidthPx = thumbPx,
                                    preferRgb565 = preferRgb565
                                )
                                ResumeProgressBar(
                                    ratio = item.resumeRatio,
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = SnapTypography.ListRowTitle, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = "${formatDurationSeconds(item.durationSec)} | ${FormatSizeCalculator.formatBytes(item.sizeBytes)}",
                                        style = SnapTypography.Metadata,
                                        color = colors.textSecondary
                                    )
                                }
                                IconButton(onClick = { itemMenuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = colors.textMuted)
                                }
                            }
                        }
                    } else {
                        // Compact row matching 07-downloaded.webp
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            if (isSelected) selectedKeys.remove(item.mediaKey) else selectedKeys.add(item.mediaKey)
                                        } else openInPlayer()
                                    },
                                    onLongClick = {
                                        if (!isSelected) selectedKeys.add(item.mediaKey)
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.cardSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                SnapThumbnail(
                                    model = item.thumbnailModel,
                                    contentDescription = item.title,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (item.mediaType == MediaType.MUSIC) Modifier.padding(6.dp).clip(CircleShape) else Modifier
                                        ),
                                    targetWidthPx = thumbPx,
                                    preferRgb565 = preferRgb565
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(20.dp)
                                    )
                                }
                                ResumeProgressBar(
                                    ratio = item.resumeRatio,
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = SnapTypography.ListRowTitle,
                                    color = colors.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when (item.mediaType) {
                                            MediaType.MUSIC -> Icons.Filled.AudioFile
                                            MediaType.PICTURE -> Icons.Filled.Image
                                            MediaType.VIDEO -> Icons.Filled.VideoFile
                                        },
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${formatDurationSeconds(item.durationSec)}  |  ${FormatSizeCalculator.formatBytes(item.sizeBytes)}",
                                        style = SnapTypography.Metadata,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                            Box {
                                IconButton(onClick = { itemMenuOpen = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = colors.textMuted)
                                }
                                DropdownMenu(expanded = itemMenuOpen, onDismissRequest = { itemMenuOpen = false }) {
                                    if (item.mediaType == MediaType.VIDEO) {
                                        DropdownMenuItem(
                                            text = { Text("Reduce video size (Compress)") },
                                            onClick = {
                                                itemMenuOpen = false
                                                onOpenCompressSheet(item.uri, item.title, item.durationSec, item.sizeBytes, item.downloadId)
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Add to playlist") },
                                        onClick = {
                                            itemMenuOpen = false
                                            addToPlaylistTargets = listOf(item)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        onClick = {
                                            itemMenuOpen = false
                                            val share = Intent(Intent.ACTION_SEND).apply {
                                                type = if (item.mediaType == MediaType.MUSIC) "audio/*" else "video/*"
                                                putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri))
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(share, "Share"))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Move to Vault") },
                                        onClick = {
                                            itemMenuOpen = false
                                            scope.launch {
                                                val vaultFile = File(container.storageScanner.vaultDir, "${System.currentTimeMillis()}_${item.title.take(40)}")
                                                runCatching {
                                                    context.contentResolver.openInputStream(Uri.parse(item.uri))?.use { inp ->
                                                        vaultFile.outputStream().use { out -> inp.copyTo(out) }
                                                    }
                                                }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            itemMenuOpen = false
                                            scope.launch {
                                                container.storageScanner.moveUriToTrash(item.uri, item.title)
                                                item.downloadId?.let { container.database.downloadDao().deleteById(it) }
                                                if (hasStoragePerm) deviceMedia = container.storageScanner.scanDeviceMedia()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isSelectionMode) {
                HorizontalDivider(thickness = 0.5.dp, color = colors.dividerSubtle)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.background)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${selectedKeys.size} selected", style = SnapTypography.ListRowTitle, color = colors.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val targets = filteredMedia.filter { it.mediaKey in selectedKeys }
                                addToPlaylistTargets = targets
                                selectedKeys.clear()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.buttonTextOnAccent)
                        ) {
                            Text("Add to Playlist", fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { selectedKeys.clear() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = colors.textPrimary)
                        }
                    }
                }
            }
        }
    }

    addToPlaylistTargets?.let { targets ->
        AddToPlaylistSheet(
            itemsToAdd = targets,
            container = container,
            onDismiss = { addToPlaylistTargets = null },
            onAdded = {}
        )
    }
}
