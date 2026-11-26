package com.snapfetch.app.core.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snapfetch.app.core.perf.SnapThumbnail
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapSpacing
import com.snapfetch.app.core.theme.SnapTypography
import com.snapfetch.app.domain.model.VideoItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoRowItem(
    video: VideoItem,
    thumbPx: Int,
    preferRgb565: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    resumeRatio: Float = 0f
) {
    val colors = LocalSnapFetchColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isSelected) colors.accent.copy(alpha = 0.10f) else Color.Transparent
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = SnapSpacing.ScreenHorizontal, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(SnapSpacing.ResultThumbWidth)
                .height(SnapSpacing.ResultThumbHeight)
                .clip(RoundedCornerShape(SnapSpacing.ResultThumbRadius))
                .background(colors.cardSurface)
        ) {
            SnapThumbnail(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                targetWidthPx = thumbPx,
                preferRgb565 = preferRgb565
            )
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0x88000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) colors.accent else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            ResumeProgressBar(
                ratio = resumeRatio,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .height(SnapSpacing.ResultThumbHeight),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = video.title,
                    style = SnapTypography.ListRowTitle,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = video.channelName,
                    style = SnapTypography.Metadata,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = video.formattedDuration(),
                    style = SnapTypography.Metadata,
                    color = colors.textSecondary,
                    maxLines = 1
                )
                if (!isSelectionMode) {
                    DownloadArrowButton(
                        onClick = onDownloadClick,
                        modifier = Modifier.size(28.dp),
                        iconSize = 18.dp
                    )
                }
            }
        }
    }
}
