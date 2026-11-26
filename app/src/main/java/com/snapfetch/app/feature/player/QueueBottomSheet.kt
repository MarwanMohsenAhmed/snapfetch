package com.snapfetch.app.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapSpacing
import com.snapfetch.app.core.theme.SnapTypography
import com.snapfetch.app.domain.model.formatDurationSeconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queueState: PlaybackQueueState,
    onDismiss: () -> Unit,
    onSelectTrack: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onReorder: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    val colors = LocalSnapFetchColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val rowHeightPx = with(LocalDensity.current) { 58.dp.toPx() }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragDeltaY by remember { mutableFloatStateOf(0f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.elevatedSurface,
        shape = RoundedCornerShape(topStart = SnapSpacing.SheetTopCornerRadius, topEnd = SnapSpacing.SheetTopCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Up Next (${queueState.tracks.size})",
                    style = SnapTypography.SectionHeading,
                    color = colors.textPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (queueState.shuffleEnabled) colors.accent else colors.textSecondary
                        )
                    }
                    IconButton(onClick = onCycleRepeat) {
                        Icon(
                            imageVector = if (queueState.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = "Repeat",
                            tint = if (queueState.repeatMode != RepeatMode.OFF) colors.accent else colors.textSecondary
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = colors.dividerSubtle)

            if (queueState.tracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Queue is empty",
                        style = SnapTypography.ListRowTitle,
                        color = colors.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    itemsIndexed(
                        items = queueState.tracks,
                        key = { idx, t -> "${t.mediaKey}_$idx" }
                    ) { index, track ->
                        val isCurrent = index == queueState.currentIndex
                        val isDragged = index == draggingIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .background(
                                    when {
                                        isDragged -> colors.cardSurface
                                        isCurrent -> colors.accent.copy(alpha = 0.14f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onSelectTrack(index) }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DragHandle,
                                contentDescription = "Reorder",
                                tint = colors.textMuted,
                                modifier = Modifier
                                    .size(24.dp)
                                    .pointerInput(index, queueState.tracks.size) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingIndex = index
                                                dragDeltaY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragDeltaY += dragAmount.y
                                                val steps = (dragDeltaY / rowHeightPx).toInt()
                                                if (steps != 0) {
                                                    val target = (draggingIndex + steps).coerceIn(0, queueState.tracks.lastIndex)
                                                    if (target != draggingIndex) {
                                                        onReorder(draggingIndex, target)
                                                        draggingIndex = target
                                                        dragDeltaY = 0f
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                draggingIndex = -1
                                                dragDeltaY = 0f
                                            },
                                            onDragCancel = {
                                                draggingIndex = -1
                                                dragDeltaY = 0f
                                            }
                                        )
                                    }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = SnapTypography.ListRowTitle.copy(
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${track.subtitle} • ${formatDurationSeconds(track.durationSec)}",
                                    style = SnapTypography.Metadata,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onRemove(index) }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
