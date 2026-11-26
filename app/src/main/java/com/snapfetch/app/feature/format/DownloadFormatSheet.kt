package com.snapfetch.app.feature.format

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfetch.app.AppContainer
import com.snapfetch.app.core.datastore.UserSettings
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SaveSpaceBadgeBg
import com.snapfetch.app.core.theme.SaveSpaceBadgeText
import com.snapfetch.app.core.theme.SnapSpacing
import com.snapfetch.app.core.theme.SnapTypography
import com.snapfetch.app.data.downloader.EnqueueOutcome
import com.snapfetch.app.domain.model.FormatCategory
import com.snapfetch.app.domain.model.FormatSizeCalculator
import com.snapfetch.app.domain.model.MediaFormatOption
import com.snapfetch.app.domain.model.VideoItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadFormatSheet(
    video: VideoItem,
    container: AppContainer,
    onDismiss: () -> Unit,
    onDownloadEnqueued: (String) -> Unit
) {
    val colors = LocalSnapFetchColors.current
    val scope = rememberCoroutineScope()
    val settings by container.settingsDataStore.settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var formats by remember(video.id) { mutableStateOf<List<MediaFormatOption>>(emptyList()) }
    var resolvedVideo by remember(video.id) { mutableStateOf(video) }
    var isLoading by remember(video.id) { mutableStateOf(true) }
    var selectedFormatId by remember(video.id) { mutableStateOf("video_fast_360") }
    var showMoreFormats by remember { mutableStateOf(false) }
    var errorNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(video.id) {
        isLoading = true
        val bundle = container.resolveFormatsUseCase(video.streamPageUrl.ifBlank { video.id })
        resolvedVideo = bundle.video
        formats = bundle.formats
        selectedFormatId = if (settings.dataSaverEnabled) {
            bundle.formats.firstOrNull { it.resolution == "240p" || it.resolution == "360p" }?.formatId
                ?: bundle.formats.firstOrNull()?.formatId.orEmpty()
        } else {
            bundle.formats.firstOrNull { it.formatId == "video_fast_360" }?.formatId
                ?: bundle.formats.firstOrNull { it.category == FormatCategory.VIDEO }?.formatId
                ?: bundle.formats.firstOrNull()?.formatId.orEmpty()
        }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.elevatedSurface,
        shape = RoundedCornerShape(topStart = SnapSpacing.SheetTopCornerRadius, topEnd = SnapSpacing.SheetTopCornerRadius),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .width(SnapSpacing.SheetHandleWidth)
                    .height(SnapSpacing.SheetHandleHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.borderLight)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Download video as",
                    style = SnapTypography.SectionHeading,
                    color = colors.textPrimary
                )
                FilterChip(
                    selected = settings.dataSaverEnabled,
                    onClick = {
                        val next = !settings.dataSaverEnabled
                        scope.launch { container.settingsDataStore.setDataSaver(next) }
                        if (next) {
                            formats.firstOrNull { it.resolution == "240p" || it.resolution == "360p" }?.let {
                                selectedFormatId = it.formatId
                                if (it.resolution == "240p") showMoreFormats = true
                            }
                        }
                    },
                    label = { Text("Data Saver", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accent.copy(alpha = 0.25f),
                        selectedLabelColor = colors.textPrimary
                    )
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.accent)
                }
            } else {
                val quickMusic = formats.filter { it.category == FormatCategory.MUSIC && it.isQuickPreset }
                val quickVideo = formats.filter { it.category == FormatCategory.VIDEO && it.isQuickPreset }
                val extraFormats = formats.filter { !it.isQuickPreset }

                if (quickMusic.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Music", style = SnapTypography.Metadata.copy(fontSize = 13.5.sp), color = colors.textSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    quickMusic.forEach { option ->
                        FormatOptionRow(
                            option = option,
                            selected = option.formatId == selectedFormatId,
                            onSelect = { selectedFormatId = option.formatId }
                        )
                    }
                }

                if (quickVideo.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Video", style = SnapTypography.Metadata.copy(fontSize = 13.5.sp), color = colors.textSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    quickVideo.forEach { option ->
                        FormatOptionRow(
                            option = option,
                            selected = option.formatId == selectedFormatId,
                            onSelect = { selectedFormatId = option.formatId }
                        )
                    }
                }

                if (extraFormats.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        thickness = 0.5.dp,
                        color = colors.dividerSubtle
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clickable { showMoreFormats = !showMoreFormats },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("More formats", style = SnapTypography.ListRowTitle, color = colors.textPrimary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("All", style = SnapTypography.Metadata, color = colors.textSecondary)
                            Icon(
                                imageVector = if (showMoreFormats) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = colors.textSecondary
                            )
                        }
                    }
                    if (showMoreFormats) {
                        extraFormats.forEach { option ->
                            FormatOptionRow(
                                option = option,
                                selected = option.formatId == selectedFormatId,
                                onSelect = { selectedFormatId = option.formatId }
                            )
                        }
                    }
                }

                if (errorNote != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(errorNote.orEmpty(), style = SnapTypography.Metadata, color = Color(0xFFD32F2F))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val chosen = formats.firstOrNull { it.formatId == selectedFormatId } ?: formats.firstOrNull()
                        if (chosen != null) {
                            scope.launch {
                                when (val res = container.enqueueDownloadUseCase(resolvedVideo, chosen)) {
                                    is EnqueueOutcome.BlockedLowStorage -> {
                                        errorNote = "Need ${FormatSizeCalculator.formatBytes(res.requiredBytes)} free (100 MB safety buffer)."
                                    }
                                    is EnqueueOutcome.Enqueued -> {
                                        onDownloadEnqueued("Added \"${resolvedVideo.title}\" (${chosen.displayLabel})")
                                        onDismiss()
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SnapSpacing.SheetDownloadButtonHeight),
                    shape = RoundedCornerShape(SnapSpacing.PillButtonRadius),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.buttonTextOnAccent
                    )
                ) {
                    Text("Download", fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
                }
            }
        }
    }
}

@Composable
private fun FormatOptionRow(
    option: MediaFormatOption,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val colors = LocalSnapFetchColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SnapSpacing.SheetRowHeight)
            .clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (option.category == FormatCategory.MUSIC) Icons.Filled.MusicNote else Icons.Filled.SmartDisplay,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = option.displayLabel,
                style = SnapTypography.ListRowTitle,
                color = colors.textPrimary
            )
            if (option.saveSpaceBadge != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SaveSpaceBadgeBg
                ) {
                    Text(
                        text = option.saveSpaceBadge,
                        color = SaveSpaceBadgeText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Text(
            text = FormatSizeCalculator.formatBytes(option.estimatedSizeBytes),
            style = SnapTypography.Metadata,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(SnapSpacing.RadioIndicatorSize)
                .clip(CircleShape)
                .background(if (selected) colors.accent else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.buttonTextOnAccent,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Surface(
                    modifier = Modifier.size(SnapSpacing.RadioIndicatorSize),
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(1.5.dp, colors.unselectedRadioStroke)
                ) {}
            }
        }
    }
}
