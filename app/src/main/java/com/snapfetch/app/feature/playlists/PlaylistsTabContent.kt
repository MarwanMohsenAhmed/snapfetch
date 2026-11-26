package com.snapfetch.app.feature.playlists

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfetch.app.AppContainer
import com.snapfetch.app.core.perf.SnapThumbnail
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapTypography
import com.snapfetch.app.data.local.entities.PlaylistEntity
import com.snapfetch.app.domain.model.FormatSizeCalculator
import com.snapfetch.app.domain.model.formatDurationSeconds
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun PlaylistsTabContent(
    container: AppContainer,
    onOpenPlaylist: (String) -> Unit
) {
    val colors = LocalSnapFetchColors.current
    val scope = rememberCoroutineScope()
    val dao = container.database.playlistDao()
    val playlists by dao.observePlaylists().collectAsStateWithLifecycle(initialValue = emptyList())
    val allItems by dao.observeAllPlaylistItems().collectAsStateWithLifecycle(initialValue = emptyList())

    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PlaylistEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Unlimited Playlists (${playlists.size})",
                style = SnapTypography.SectionHeading,
                color = colors.textPrimary
            )
            Button(
                onClick = { showCreateDialog = true },
                shape = RoundedCornerShape(20.dp),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.buttonTextOnAccent
                )
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New playlist", fontWeight = FontWeight.Bold)
            }
        }

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Create unlimited offline playlists", style = SnapTypography.ListRowTitle, color = colors.textSecondary)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(playlists, key = { it.playlistId }) { playlist ->
                    val items = allItems.filter { it.playlistId == playlist.playlistId }
                    val totalDur = items.sumOf { it.durationSec }
                    val totalBytes = items.sumOf { it.sizeBytes }
                    var menuOpen by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPlaylist(playlist.playlistId) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.cardSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            val cover = playlist.coverUri ?: items.firstOrNull()?.thumbnailUri
                            if (!cover.isNullOrBlank()) {
                                SnapThumbnail(
                                    model = cover,
                                    contentDescription = playlist.name,
                                    modifier = Modifier.fillMaxSize(),
                                    targetWidthPx = 200
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                style = SnapTypography.ListRowTitle.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${items.size} items • ${formatDurationSeconds(totalDur)} • ${FormatSizeCalculator.formatBytes(totalBytes)}",
                                style = SnapTypography.Metadata,
                                color = colors.textSecondary
                            )
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Options", tint = colors.textMuted)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        menuOpen = false
                                        renameTarget = playlist
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        menuOpen = false
                                        scope.launch { dao.deletePlaylist(playlist.playlistId) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateRenamePlaylistDialog(
            title = "Create playlist",
            confirmLabel = "Create",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                scope.launch {
                    val now = System.currentTimeMillis()
                    dao.upsertPlaylist(
                        PlaylistEntity(
                            playlistId = UUID.randomUUID().toString(),
                            name = name,
                            coverUri = null,
                            createdAtEpochMs = now,
                            updatedAtEpochMs = now
                        )
                    )
                }
            }
        )
    }

    renameTarget?.let { target ->
        CreateRenamePlaylistDialog(
            title = "Rename playlist",
            initialName = target.name,
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                scope.launch { dao.renamePlaylist(target.playlistId, newName, System.currentTimeMillis()) }
            }
        )
    }
}
