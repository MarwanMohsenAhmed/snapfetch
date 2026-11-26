package com.snapfetch.app.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.snapfetch.app.core.theme.LocalSnapFetchColors

@Composable
fun WatermarkBackground(
    modifier: Modifier = Modifier
) {
    val bgColor = LocalSnapFetchColors.current.background
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp)
    ) {
        val tileSize = 68.dp.toPx()
        val gap = 16.dp.toPx()
        val step = tileSize + gap
        val corner = 18.dp.toPx()

        val palette = listOf(
            Color(0xFFFF0000), // YouTube red
            Color(0xFF1877F2), // Facebook blue
            Color(0xFFE1306C), // Instagram magenta
            Color(0xFF25D366), // WhatsApp green
            Color(0xFF111111)  // TikTok dark
        )

        rotate(degrees = -25f, pivot = Offset(size.width * 0.5f, size.height * 0.4f)) {
            var row = -2
            while (row * step < size.height * 1.4f) {
                var col = -2
                while (col * step < size.width * 1.4f) {
                    val idx = ((row * 3 + col * 2) % palette.size + palette.size) % palette.size
                    val x = col * step + (if (row % 2 != 0) step * 0.5f else 0f)
                    val y = row * step
                    val tint = palette[idx].copy(alpha = 0.07f)

                    drawRoundRect(
                        color = tint,
                        topLeft = Offset(x, y),
                        size = Size(tileSize, tileSize),
                        cornerRadius = CornerRadius(corner, corner)
                    )
                    drawGlyph(idx, x, y, tileSize, Color.White.copy(alpha = 0.7f))
                    col++
                }
                row++
            }
        }

        // Soft vertical fade into background so middle/lower home screen stays clean
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to bgColor.copy(alpha = 0.0f),
                    0.55f to bgColor.copy(alpha = 0.55f),
                    1.0f to bgColor
                ),
                startY = 0f,
                endY = size.height
            )
        )
    }
}

private fun DrawScope.drawGlyph(
    kind: Int,
    x: Float,
    y: Float,
    s: Float,
    color: Color
) {
    val cx = x + s * 0.5f
    val cy = y + s * 0.5f
    when (kind) {
        0 -> {
            // Play triangle
            val p = Path().apply {
                moveTo(cx - s * 0.14f, cy - s * 0.18f)
                lineTo(cx + s * 0.22f, cy)
                lineTo(cx - s * 0.14f, cy + s * 0.18f)
                close()
            }
            drawPath(p, color = color)
        }
        1 -> {
            // Stylized 'f' vertical stem + crossbar
            val stroke = s * 0.11f
            drawLine(color, Offset(cx + s * 0.02f, cy - s * 0.24f), Offset(cx + s * 0.02f, cy + s * 0.28f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(cx - s * 0.14f, cy), Offset(cx + s * 0.18f, cy), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        2 -> {
            // Rounded camera outline
            val box = s * 0.44f
            drawRoundRect(
                color = color,
                topLeft = Offset(cx - box / 2f, cy - box / 2f),
                size = Size(box, box),
                cornerRadius = CornerRadius(s * 0.12f, s * 0.12f),
                style = Stroke(width = s * 0.07f)
            )
            drawCircle(color, radius = s * 0.10f, center = Offset(cx, cy), style = Stroke(width = s * 0.07f))
        }
        3 -> {
            // Bubble ring
            drawCircle(color, radius = s * 0.22f, center = Offset(cx, cy), style = Stroke(width = s * 0.08f))
        }
        else -> {
            // Music note stem
            val stroke = s * 0.09f
            drawLine(color, Offset(cx + s * 0.06f, cy - s * 0.22f), Offset(cx + s * 0.06f, cy + s * 0.16f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawCircle(color, radius = s * 0.11f, center = Offset(cx - s * 0.04f, cy + s * 0.16f))
        }
    }
}
