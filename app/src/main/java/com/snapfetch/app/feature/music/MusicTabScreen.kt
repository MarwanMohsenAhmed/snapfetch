package com.snapfetch.app.feature.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfetch.app.AppContainer
import com.snapfetch.app.core.components.DownloadArrowButton
import com.snapfetch.app.core.datastore.UserSettings
import com.snapfetch.app.core.perf.SnapThumbnail
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapSpacing
import com.snapfetch.app.core.theme.SnapTypography
import com.snapfetch.app.domain.model.MusicShelf
import com.snapfetch.app.domain.model.VideoItem

@Composable
fun MusicTabScreen(
    container: AppContainer,
    onOpenVideo: (VideoItem) -> Unit,
    onOpenFormatSheet: (VideoItem) -> Unit
) {
    val colors = LocalSnapFetchColors.current
    val settings by container.settingsDataStore.settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())
    var shelves by remember { mutableStateOf<List<MusicShelf>>(emptyList()) }

    LaunchedEffect(Unit) {
        shelves = container.extractorRepository.getMusicShelves()
    }

    val thumbPx = container.lowRamConfig.effectiveThumbnailPx(settings.thumbnailQuality, settings.dataSaverEnabled)
    val preferRgb565 = container.lowRamConfig.isLowRamDevice || settings.dataSaverEnabled

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(shelves, key = { it.id }) { shelf ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = shelf.title,
                    style = SnapTypography.SectionHeading,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = SnapSpacing.ScreenHorizontal, vertical = 12.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = SnapSpacing.ScreenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(SnapSpacing.MusicCardGap)
                ) {
                    items(shelf.items, key = { "${shelf.id}_${it.video.id}" }) { entry ->
                        Column(
                            modifier = Modifier
                                .width(SnapSpacing.MusicCardSize)
                                .clickable { onOpenVideo(entry.video) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(SnapSpacing.MusicCardSize)
                                    .clip(RoundedCornerShape(SnapSpacing.MusicCardRadius))
                                    .background(colors.cardSurface)
                            ) {
                                SnapThumbnail(
                                    model = entry.video.thumbnailUrl,
                                    contentDescription = entry.video.title,
                                    modifier = Modifier.fillMaxSize(),
                                    targetWidthPx = thumbPx,
                                    preferRgb565 = preferRgb565
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x88000000)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Album, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                                if (entry.isMixOrAlbum) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = entry.video.title,
                                style = SnapTypography.ListRowTitle,
                                color = colors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = entry.artistSubtitle,
                                    style = SnapTypography.Metadata,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                DownloadArrowButton(
                                    onClick = { onOpenFormatSheet(entry.video) },
                                    modifier = Modifier.size(26.dp),
                                    iconSize = 18.dp
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
