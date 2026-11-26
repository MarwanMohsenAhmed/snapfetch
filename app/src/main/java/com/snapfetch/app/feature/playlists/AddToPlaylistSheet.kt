package com.snapfetch.app.feature.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfetch.app.AppContainer
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapSpacing
import com.snapfetch.app.core.theme.SnapTypography
import com.snapfetch.app.data.local.entities.PlaylistEntity
import com.snapfetch.app.data.local.entities.PlaylistItemEntity
import com.snapfetch.app.domain.model.LibraryMediaItem
import com.snapfetch.app.domain.model.MediaType
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    itemsToAdd: List<LibraryMediaItem>,
    container: AppContainer,
    onDismiss: () -> Unit,
    onAdded: (String) -> Unit
) {
    val colors = LocalSnapFetchColors.current
    val scope = rememberCoroutineScope()
    val playlists by container.database.playlistDao().observePlaylists().collectAsStateWithLifecycle(initialValue = emptyList())
    val allItems by container.database.playlistDao().observeAllPlaylistItems().collectAsStateWithLifecycle(initialValue = emptyList())
    var showCreateDialog by remember { mutableStateOf(false) }

    suspend fun insertIntoPlaylist(playlistId: String, playlistName: String) {
        val dao = container.database.playlistDao()
        var nextPos = dao.getMaxPosition(playlistId) + 1
        val now = System.currentTimeMillis()
        val entities = itemsToAdd.map { media ->
            PlaylistItemEntity(
                playlistId = playlistId,
                mediaKey = media.mediaKey,
                title = media.title,
                subtitle = media.subtitle,
                thumbnailUri = media.thumbnailModel?.toString(),
                mediaUri = media.uri,
                durationSec = media.durationSec,
                sizeBytes = media.sizeBytes,
                isAudio = media.mediaType == MediaType.MUSIC,
                position = nextPos++,
                addedAtEpochMs = now
            )
        }
        dao.insertItems(entities)
        if (itemsToAdd.firstOrNull()?.thumbnailModel != null) {
            dao.updateCover(playlistId, itemsToAdd.first().thumbnailModel.toString(), now)
        }
        onAdded(playlistName)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.elevatedSurface,
        shape = RoundedCornerShape(topStart = SnapSpacing.SheetTopCornerRadius, topEnd = SnapSpacing.SheetTopCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add to playlist",
                    style = SnapTypography.SectionHeading,
                    color = colors.textPrimary
                )
                TextButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = colors.textPrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New playlist", color = colors.textPrimary)
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = colors.dividerSubtle)

            if (playlists.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No playlists yet", style = SnapTypography.ListRowTitle, color = colors.textSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = { showCreateDialog = true }) {
                        Text("Create first playlist", color = colors.textPrimary)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    items(playlists, key = { it.playlistId }) { playlist ->
                        val count = allItems.count { it.playlistId == playlist.playlistId }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { insertIntoPlaylist(playlist.playlistId, playlist.name) }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = colors.textPrimary)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(playlist.name, style = SnapTypography.ListRowTitle, color = colors.textPrimary)
                                Text("$count tracks", style = SnapTypography.Metadata, color = colors.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateRenamePlaylistDialog(
            title = "New playlist",
            confirmLabel = "Create & Add",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                scope.launch {
                    val now = System.currentTimeMillis()
                    val id = UUID.randomUUID().toString()
                    container.database.playlistDao().upsertPlaylist(
                        PlaylistEntity(
                            playlistId = id,
                            name = name,
                            coverUri = null,
                            createdAtEpochMs = now,
                            updatedAtEpochMs = now
                        )
                    )
                    insertIntoPlaylist(id, name)
                }
            }
        )
    }
}
