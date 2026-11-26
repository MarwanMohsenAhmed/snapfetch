package com.snapfetch.app.feature.compress

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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapfetch.app.AppContainer
import com.snapfetch.app.core.datastore.CompressionPreset
import com.snapfetch.app.core.theme.ErrorRed
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapSpacing
import com.snapfetch.app.core.theme.SnapTypography
import com.snapfetch.app.domain.model.FormatSizeCalculator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCompressSheet(
    sourceUri: String,
    title: String,
    durationSec: Int,
    originalSizeBytes: Long,
    container: AppContainer,
    initialPreset: CompressionPreset,
    initialDeleteOriginal: Boolean,
    onDismiss: () -> Unit,
    onStartCompression: (CompressionPreset, Boolean) -> Unit
) {
    val colors = LocalSnapFetchColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedPreset by remember(initialPreset) { mutableStateOf(initialPreset) }
    var deleteOriginal by remember(initialDeleteOriginal) { mutableStateOf(initialDeleteOriginal) }

    val estOutputBytes = remember(durationSec, selectedPreset) {
        container.videoCompressor.estimateOutputBytes(durationSec.coerceAtLeast(30), selectedPreset)
    }
    val guardResult = remember(estOutputBytes) {
        container.storageGuard.checkFreeSpace(estOutputBytes)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.elevatedSurface,
        shape = RoundedCornerShape(topStart = SnapSpacing.SheetTopCornerRadius, topEnd = SnapSpacing.SheetTopCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Reduce video size",
                style = SnapTypography.SectionHeading,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = SnapTypography.Metadata,
                color = colors.textSecondary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(14.dp))

            CompressionPreset.entries.forEach { preset ->
                val est = container.videoCompressor.estimateOutputBytes(durationSec.coerceAtLeast(30), preset)
                val selected = preset == selectedPreset
                val desc = when (preset) {
                    CompressionPreset.SMALL_360P_500K -> "360p H.264 • ~500 kbps • Best data & space savings"
                    CompressionPreset.COMPACT_480P_900K -> "480p H.264 • ~900 kbps • Balanced clarity & size"
                    CompressionPreset.AUDIO_M4A_96K -> "Audio only • M4A 96 kbps • Zero video track"
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { selectedPreset = preset },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) colors.accent.copy(alpha = 0.12f) else colors.cardSurface,
                    border = if (selected) BorderStroke(1.dp, colors.accent) else null
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.label,
                                style = SnapTypography.ListRowTitle,
                                color = colors.textPrimary
                            )
                            Text(
                                text = desc,
                                style = SnapTypography.Metadata,
                                color = colors.textSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "~${FormatSizeCalculator.formatBytes(est)}",
                            style = SnapTypography.Metadata,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
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
                                    modifier = Modifier.size(20.dp),
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    border = BorderStroke(1.5.dp, colors.unselectedRadioStroke)
                                ) {}
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Delete original after verifying",
                        style = SnapTypography.ListRowTitle,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Original stays safe if transcoding fails",
                        style = SnapTypography.Metadata,
                        color = colors.textSecondary
                    )
                }
                Switch(
                    checked = deleteOriginal,
                    onCheckedChange = { deleteOriginal = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.accent)
                )
            }

            if (!guardResult.hasEnoughSpace) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Insufficient space: requires ${FormatSizeCalculator.formatBytes(guardResult.requiredBytes)} free (includes 100 MB safety buffer).",
                    style = SnapTypography.Metadata,
                    color = ErrorRed
                )
            } else if (originalSizeBytes > estOutputBytes) {
                val saved = originalSizeBytes - estOutputBytes
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Estimated savings: ${FormatSizeCalculator.formatBytes(saved)} • Uses hardware MediaCodec",
                    style = SnapTypography.Metadata,
                    color = colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    onStartCompression(selectedPreset, deleteOriginal)
                    onDismiss()
                },
                enabled = guardResult.hasEnoughSpace,
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
                Text("Compress now", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
