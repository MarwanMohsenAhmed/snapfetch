package com.snapfetch.app.core.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.snapfetch.app.core.theme.LocalSnapFetchColors

@Composable
fun DownloadArrowButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    tint: Color? = null,
    contentDescription: String = "Download"
) {
    val iconColor = tint ?: LocalSnapFetchColors.current.iconDark
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        DownloadArrowIcon(color = iconColor, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun DownloadArrowIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = 1.8.dp.toPx()
        val cx = size.width / 2f
        val topY = size.height * 0.14f
        val tipY = size.height * 0.66f
        val wingX = size.width * 0.24f
        val wingY = size.height * 0.44f
        val trayY = size.height * 0.84f

        // Vertical shaft
        drawLine(
            color = color,
            start = Offset(cx, topY),
            end = Offset(cx, tipY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // Left & right arrowhead
        drawLine(
            color = color,
            start = Offset(cx - wingX, wingY),
            end = Offset(cx, tipY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(cx + wingX, wingY),
            end = Offset(cx, tipY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // Bottom tray bar
        drawLine(
            color = color,
            start = Offset(size.width * 0.2f, trayY),
            end = Offset(size.width * 0.8f, trayY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
