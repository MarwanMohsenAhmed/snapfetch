package com.snapfetch.app.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfetch.app.AppContainer
import com.snapfetch.app.core.components.DownloadArrowButton
import com.snapfetch.app.core.components.VideoRowItem
import com.snapfetch.app.core.datastore.UserSettings
import com.snapfetch.app.core.perf.SnapThumbnail
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapSpacing
import com.snapfetch.app.core.theme.SnapTypography
import com.snapfetch.app.data.local.entities.SubscribedChannelEntity
import com.snapfetch.app.domain.model.ChannelInfo
import com.snapfetch.app.domain.model.MediaPlatform
import com.snapfetch.app.domain.model.VideoItem
import kotlinx.coroutines.launch

enum class ResultFilterTab(val label: String, val platform: MediaPlatform?) {
    ALL("All", null),
    PLAYLIST("Playlist", null),
    GOOGLE("Google", MediaPlatform.OTHER),
    FACEBOOK("Facebook", MediaPlatform.FACEBOOK),
    TIKTOK("Tiktok", MediaPlatform.TIKTOK),
    INSTAGRAM("Instagram", MediaPlatform.INSTAGRAM)
}

@Composable
fun SearchResultsScreen(
    query: String,
    initialFilter: String?,
    container: AppContainer,
    onBack: () -> Unit,
    onEditQuery: (String) -> Unit,
    onClearToSearch: () -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onOpenFormatSheet: (VideoItem) -> Unit,
    onOpenBatchSheet: (List<VideoItem>) -> Unit
) {
    val colors = LocalSnapFetchColors.current
    val scope = rememberCoroutineScope()
    val settings by container.settingsDataStore.settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())
    var selectedFilter by remember(initialFilter) {
        mutableStateOf(
            ResultFilterTab.entries.firstOrNull { it.name.equals(initialFilter, ignoreCase = true) } ?: ResultFilterTab.ALL
        )
    }

    var isLoading by remember { mutableStateOf(true) }
    var channel by remember { mutableStateOf<ChannelInfo?>(null) }
    var results by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    val selectedIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(query, selectedFilter) {
        isLoading = true
        selectedIds.clear()
        val bundle = container.searchMediaUseCase(query, selectedFilter.platform)
        channel = if (selectedFilter == ResultFilterTab.ALL) bundle.channel else null
        results = if (selectedFilter == ResultFilterTab.PLAYLIST) {
            bundle.videos.filter { it.isPlaylist }.ifEmpty { bundle.videos }
        } else {
            bundle.videos
        }
        isLoading = false
    }

    val thumbPx = container.lowRamConfig.effectiveThumbnailPx(settings.thumbnailQuality, settings.dataSaverEnabled)
    val preferRgb565 = container.lowRamConfig.isLowRamDevice || settings.dataSaverEnabled
    val isSelectionMode = selectedIds.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // Floating search bar matching 04-results.webp
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SnapSpacing.ResultsSearchBarMargin, vertical = 8.dp)
                .height(SnapSpacing.ResultsSearchBarHeight)
                .clickable { onEditQuery(query) },
            shape = RoundedCornerShape(SnapSpacing.ResultsSearchBarRadius),
            color = colors.elevatedSurface,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                }
                Text(
                    text = query,
                    style = SnapTypography.ListRowTitle,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClearToSearch) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = colors.textSecondary)
                }
            }
        }

        // Horizontal filter tabs: All, Playlist, Google, Facebook, Tiktok, Instagram
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            ResultFilterTab.entries.forEach { tab ->
                val active = tab == selectedFilter
                Column(
                    modifier = Modifier
                        .clickable { selectedFilter = tab }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tab.label,
                        style = if (active) SnapTypography.TopTabActive else SnapTypography.TopTabInactive,
                        color = if (active) colors.textPrimary else colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (active) colors.accent else Color.Transparent)
                    )
                }
            }
        }

        if (selectedFilter.platform != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { onEditQuery("https://") },
                shape = RoundedCornerShape(10.dp),
                color = colors.accent.copy(alpha = 0.16f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Paste a public ${selectedFilter.label} link to extract & download directly",
                        style = SnapTypography.Metadata,
                        color = colors.textPrimary
                    )
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.accent)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                channel?.let { ch ->
                    item(key = "channel_${ch.channelId}") {
                        val isSubscribed by container.database.subscribedChannelDao()
                            .observeIsSubscribed(ch.channelId)
                            .collectAsStateWithLifecycle(initialValue = false)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SnapThumbnail(
                                model = ch.avatarUrl,
                                contentDescription = ch.name,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(colors.cardSurface),
                                targetWidthPx = 150,
                                preferRgb565 = preferRgb565
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ch.name, style = SnapTypography.ListRowTitle.copy(fontSize = 15.sp), color = colors.textPrimary)
                                Text(ch.subscriberText, style = SnapTypography.Metadata, color = colors.textSecondary)
                            }
                            Text(
                                text = if (isSubscribed) "SUBSCRIBED" else "SUBSCRIBE",
                                style = SnapTypography.ListRowTitle.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
                                color = if (isSubscribed) colors.textSecondary else colors.textPrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        scope.launch {
                                            val dao = container.database.subscribedChannelDao()
                                            if (isSubscribed) {
                                                dao.delete(ch.channelId)
                                            } else {
                                                dao.insert(
                                                    SubscribedChannelEntity(
                                                        channelId = ch.channelId,
                                                        name = ch.name,
                                                        avatarUrl = ch.avatarUrl,
                                                        subscriberText = ch.subscriberText,
                                                        url = ch.url,
                                                        subscribedAtEpochMs = System.currentTimeMillis()
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                val mixPlaylists = results.filter { it.isPlaylist }
                items(mixPlaylists, key = { "mix_${it.id}" }) { mix ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenVideo(mix) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(SnapSpacing.ResultThumbWidth)
                                .height(SnapSpacing.ResultThumbHeight)
                                .clip(RoundedCornerShape(SnapSpacing.ResultThumbRadius))
                                .background(colors.cardSurface)
                        ) {
                            SnapThumbnail(
                                model = mix.thumbnailUrl,
                                contentDescription = mix.title,
                                modifier = Modifier.fillMaxSize(),
                                targetWidthPx = thumbPx,
                                preferRgb565 = preferRgb565
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(52.dp)
                                    .align(Alignment.CenterEnd)
                                    .background(Color(0x99000000)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mix.title, style = SnapTypography.ListRowTitle, color = colors.textPrimary, maxLines = 2)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(mix.channelName, style = SnapTypography.Metadata, color = colors.textSecondary)
                        }
                        DownloadArrowButton(
                            onClick = {
                                val batch = results.filter { !it.isPlaylist }.ifEmpty { listOf(mix) }
                                onOpenBatchSheet(batch)
                            }
                        )
                    }
                }

                val regularVideos = results.filter { !it.isPlaylist }
                if (channel != null && regularVideos.isNotEmpty()) {
                    item(key = "section_title") {
                        Text(
                            text = channel!!.name,
                            style = SnapTypography.SectionHeading,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }

                items(regularVideos, key = { it.id }) { video ->
                    val isSel = video.id in selectedIds
                    VideoRowItem(
                        video = video,
                        thumbPx = thumbPx,
                        preferRgb565 = preferRgb565,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSel,
                        onClick = {
                            if (isSelectionMode) {
                                if (isSel) selectedIds.remove(video.id) else selectedIds.add(video.id)
                            } else {
                                onOpenVideo(video)
                            }
                        },
                        onLongClick = {
                            if (!isSel) selectedIds.add(video.id)
                        },
                        onDownloadClick = { onOpenFormatSheet(video) }
                    )
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
                    Text("${selectedIds.size} selected", style = SnapTypography.ListRowTitle, color = colors.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                val batch = results.filter { it.id in selectedIds }
                                selectedIds.clear()
                                onOpenBatchSheet(batch)
                            },
                            shape = RoundedCornerShape(20.dp),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
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
