package com.snapfetch.app.feature.batch

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.snapfetch.app.domain.model.DownloadStatus
import com.snapfetch.app.domain.model.FormatSizeCalculator
import kotlinx.coroutines.launch

@Composable
fun DownloadQueueScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val colors = LocalSnapFetchColors.current
    val scope = rememberCoroutineScope()
    val queue by container.database.downloadDao().observeActiveQueue().collectAsStateWithLifecycle(initialValue = emptyList())

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
            Text(
                text = "Download Queue (${queue.size})",
                style = SnapTypography.ScreenTitle,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        if (queue.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { scope.launch { container.downloadCoordinator.pauseAll() } },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Pause All", color = colors.textPrimary)
                }
                Button(
                    onClick = { scope.launch { container.downloadCoordinator.resumeAll() } },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.buttonTextOnAccent)
                ) {
                    Text("Resume All", fontWeight = FontWeight.Bold)
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = colors.dividerSubtle)
        }

        if (queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No active or queued downloads", style = SnapTypography.ListRowTitle, color = colors.textSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                itemsIndexed(queue, key = { _, item -> item.downloadId }) { index, entity ->
                    val domain = entity.toDomain()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SnapThumbnail(
                                model = domain.thumbnailUrl,
                                contentDescription = domain.title,
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.cardSurface),
                                targetWidthPx = 160
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = domain.title,
                                    style = SnapTypography.ListRowTitle,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val statusText = when (domain.status) {
                                    DownloadStatus.DOWNLOADING ->
                                        "${FormatSizeCalculator.formatBytes(domain.downloadedBytes)} / ${FormatSizeCalculator.formatBytes(domain.totalBytes)} • ${FormatSizeCalculator.formatBytes(domain.speedBytesPerSec)}/s"
                                    DownloadStatus.PAUSED ->
                                        domain.errorMessage ?: "Paused (${(domain.progressFraction * 100).toInt()}%)"
                                    DownloadStatus.FAILED ->
                                        domain.errorMessage ?: "Failed"
                                    DownloadStatus.MERGING -> "Muxing streams..."
                                    else -> "Queued (${domain.formatLabel})"
                                }
                                Text(
                                    text = statusText,
                                    style = SnapTypography.Metadata,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (index > 0) {
                                IconButton(
                                    onClick = {
                                        val ids = queue.map { it.downloadId }.toMutableList()
                                        val moved = ids.removeAt(index)
                                        ids.add(index - 1, moved)
                                        scope.launch { container.downloadCoordinator.reorderQueue(ids) }
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up", tint = colors.textMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                            if (index < queue.lastIndex) {
                                IconButton(
                                    onClick = {
                                        val ids = queue.map { it.downloadId }.toMutableList()
                                        val moved = ids.removeAt(index)
                                        ids.add(index + 1, moved)
                                        scope.launch { container.downloadCoordinator.reorderQueue(ids) }
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down", tint = colors.textMuted, modifier = Modifier.size(16.dp))
                                }
                            }

                            IconButton(
                                onClick = {
                                    scope.launch {
                                        if (domain.status == DownloadStatus.DOWNLOADING || domain.status == DownloadStatus.QUEUED) {
                                            container.downloadCoordinator.pauseDownload(domain.downloadId)
                                        } else {
                                            container.downloadCoordinator.resumeDownload(domain.downloadId)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (domain.status == DownloadStatus.DOWNLOADING || domain.status == DownloadStatus.QUEUED) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "Pause or Resume",
                                    tint = colors.textPrimary
                                )
                            }

                            IconButton(onClick = {
                                scope.launch { container.downloadCoordinator.cancelDownload(domain.downloadId) }
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = colors.textMuted)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { domain.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = colors.accent,
                            trackColor = colors.dividerSubtle
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = colors.dividerSubtle)
                }
            }
        }
    }
}
