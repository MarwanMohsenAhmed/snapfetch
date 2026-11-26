package com.snapfetch.app.feature.batch

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.snapfetch.app.AppContainer
import com.snapfetch.app.core.theme.ErrorRed
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapSpacing
import com.snapfetch.app.core.theme.SnapTypography
import com.snapfetch.app.data.downloader.EnqueueOutcome
import com.snapfetch.app.domain.model.FormatCategory
import com.snapfetch.app.domain.model.FormatSizeCalculator
import com.snapfetch.app.domain.model.MediaFormatOption
import com.snapfetch.app.domain.model.VideoItem
import kotlinx.coroutines.launch

enum class BatchPreset(val label: String, val container: String, val resolution: String, val category: FormatCategory) {
    FAST_M4A("Fast Music (M4A)", "m4a", "", FormatCategory.MUSIC),
    ULTRA_SMALL_240P("Ultra Small (240p)", "mp4", "240p", FormatCategory.VIDEO),
    FAST_360P("Fast (360p)", "mp4", "360p", FormatCategory.VIDEO),
    HD_720P("High quality (720p)", "mp4", "720p", FormatCategory.VIDEO)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDownloadSheet(
    videos: List<VideoItem>,
    container: AppContainer,
    onDismiss: () -> Unit,
    onEnqueued: (String) -> Unit
) {
    val colors = LocalSnapFetchColors.current
    val scope = rememberCoroutineScope()
    var chosenPreset by remember { mutableStateOf(BatchPreset.FAST_360P) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val estimatedTotalBytes = remember(videos, chosenPreset) {
        videos.sumOf {
            FormatSizeCalculator.estimateFallbackBytes(it.durationSec, chosenPreset.container, chosenPreset.resolution)
        }
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
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Batch download (${videos.size} items)",
                style = SnapTypography.SectionHeading,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Estimated total: ${FormatSizeCalculator.formatBytes(estimatedTotalBytes)}",
                style = SnapTypography.Metadata,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(14.dp))

            BatchPreset.entries.forEach { preset ->
                val selected = preset == chosenPreset
                val totalForPreset = videos.sumOf {
                    FormatSizeCalculator.estimateFallbackBytes(it.durationSec, preset.container, preset.resolution)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clickable { chosenPreset = preset },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(preset.label, style = SnapTypography.ListRowTitle, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Text(FormatSizeCalculator.formatBytes(totalForPreset), style = SnapTypography.Metadata, color = colors.textSecondary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(SnapSpacing.RadioIndicatorSize)
                            .clip(CircleShape)
                            .background(if (selected) colors.accent else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.buttonTextOnAccent, modifier = Modifier.size(14.dp))
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

            if (errorMsg != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(errorMsg.orEmpty(), style = SnapTypography.Metadata, color = ErrorRed)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        val pairs = videos.map { v ->
                            val size = FormatSizeCalculator.estimateFallbackBytes(v.durationSec, chosenPreset.container, chosenPreset.resolution)
                            v to MediaFormatOption(
                                formatId = "batch_${chosenPreset.name}",
                                category = chosenPreset.category,
                                displayLabel = chosenPreset.label,
                                container = chosenPreset.container,
                                resolution = chosenPreset.resolution,
                                streamUrl = "",
                                estimatedSizeBytes = size
                            )
                        }
                        when (val res = container.enqueueDownloadUseCase.enqueueBatch(pairs)) {
                            is EnqueueOutcome.BlockedLowStorage -> {
                                errorMsg = "Insufficient storage: requires ${FormatSizeCalculator.formatBytes(res.requiredBytes)} (includes 100 MB safety margin)."
                            }
                            is EnqueueOutcome.Enqueued -> {
                                onEnqueued("Queued ${res.count} items for sequential download")
                                onDismiss()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SnapSpacing.SheetDownloadButtonHeight),
                shape = RoundedCornerShape(SnapSpacing.PillButtonRadius),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.buttonTextOnAccent)
            ) {
                Text("Download All (${videos.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
