package com.snapfetch.app.feature.youtube

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfetch.app.AppContainer
import com.snapfetch.app.core.components.DownloadArrowButton
import com.snapfetch.app.core.datastore.UserSettings
import com.snapfetch.app.core.perf.SnapThumbnail
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapTypography
import com.snapfetch.app.domain.model.VideoItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YouTubeFeedScreen(
    container: AppContainer,
    onOpenVideo: (VideoItem) -> Unit,
    onOpenFormatSheet: (VideoItem) -> Unit,
    onOpenBatchSheet: (List<VideoItem>) -> Unit
) {
    val colors = LocalSnapFetchColors.current
    val settings by container.settingsDataStore.settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())
    var feed by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val selectedIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        isLoading = true
        feed = container.extractorRepository.getTrendingFeed()
        isLoading = false
    }

    val thumbPx = container.lowRamConfig.effectiveThumbnailPx(settings.thumbnailQuality, settings.dataSaverEnabled)
    val preferRgb565 = container.lowRamConfig.isLowRamDevice || settings.dataSaverEnabled
    val isSelectionMode = selectedIds.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(feed, key = { it.id }) { video ->
                    val isSel = video.id in selectedIds
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSel) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        if (isSel) selectedIds.remove(video.id) else selectedIds.add(video.id)
                                    } else onOpenVideo(video)
                                },
                                onLongClick = { if (!isSel) selectedIds.add(video.id) }
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.cardSurface)
                        ) {
                            SnapThumbnail(
                                model = video.thumbnailUrl,
                                contentDescription = video.title,
                                modifier = Modifier.fillMaxSize(),
                                targetWidthPx = thumbPx,
                                preferRgb565 = preferRgb565
                            )
                            if (video.durationSec > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xAA000000))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(video.formattedDuration(), color = Color.White, fontSize = 11.5.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = video.title,
                                    style = SnapTypography.ListRowTitle.copy(fontSize = 15.sp),
                                    color = colors.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = video.channelName,
                                    style = SnapTypography.Metadata,
                                    color = colors.textSecondary
                                )
                            }
                            DownloadArrowButton(onClick = { onOpenFormatSheet(video) })
                        }
                    }
                }
            }

            if (isSelectionMode) {
                HorizontalDivider(thickness = 0.5.dp, color = colors.dividerSubtle)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${selectedIds.size} selected", style = SnapTypography.ListRowTitle, color = colors.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                val batch = feed.filter { it.id in selectedIds }
                                selectedIds.clear()
                                onOpenBatchSheet(batch)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.buttonTextOnAccent)
                        ) {
                            Text("Batch Download", fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { selectedIds.clear() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = colors.textPrimary)
                        }
                    }
                }
            }
        }
    }
}
