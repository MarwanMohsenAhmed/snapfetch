package com.snapfetch.app.core.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapSpacing
import com.snapfetch.app.core.theme.SnapTypography
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class BottomNavTab(val label: String) {
    DOWNLOAD("Download"),
    PLAY("Play"),
    SETTINGS("Settings")
}

@Composable
fun BottomNavBar(
    selectedTab: BottomNavTab,
    onSelectTab: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSnapFetchColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = colors.dividerSubtle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SnapSpacing.BottomNavHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val tint = if (selected) colors.textPrimary else colors.bottomNavInactive
                val interactionSource = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onSelectTab(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (tab) {
                        BottomNavTab.DOWNLOAD -> SearchNavIcon(
                            tint = tint,
                            selected = selected,
                            modifier = Modifier.size(SnapSpacing.BottomNavIconSize)
                        )
                        BottomNavTab.PLAY -> PlayStackNavIcon(
                            tint = tint,
                            selected = selected,
                            background = colors.background,
                            modifier = Modifier.size(SnapSpacing.BottomNavIconSize)
                        )
                        BottomNavTab.SETTINGS -> SettingsGearNavIcon(
                            tint = tint,
                            selected = selected,
                            background = colors.background,
                            modifier = Modifier.size(SnapSpacing.BottomNavIconSize)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        style = if (selected) SnapTypography.BottomNavActive else SnapTypography.BottomNavInactive,
                        color = tint,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchNavIcon(tint: Color, selected: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = if (selected) 2.3.dp.toPx() else 1.7.dp.toPx()
        val r = size.minDimension * 0.31f
        val cx = size.width * 0.44f
        val cy = size.height * 0.44f
        drawCircle(
            color = tint,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = strokeWidth)
        )
        val start = Offset(cx + r * 0.72f, cy + r * 0.72f)
        val end = Offset(size.width * 0.84f, size.height * 0.84f)
        drawLine(
            color = tint,
            start = start,
            end = end,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun PlayStackNavIcon(
    tint: Color,
    selected: Boolean,
    background: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.6.dp.toPx()
        val w = size.width
        val h = size.height
        // Back L-bracket at bottom-left matching screenshot
        val backPath = Path().apply {
            moveTo(w * 0.16f, h * 0.34f)
            lineTo(w * 0.16f, h * 0.79f)
            quadraticTo(w * 0.16f, h * 0.85f, w * 0.22f, h * 0.85f)
            lineTo(w * 0.68f, h * 0.85f)
        }
        drawPath(
            path = backPath,
            color = tint,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val rectLeft = w * 0.28f
        val rectTop = h * 0.16f
        val rectW = w * 0.56f
        val rectH = h * 0.56f
        val corner = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        if (selected) {
            drawRoundRect(
                color = tint,
                topLeft = Offset(rectLeft, rectTop),
                size = Size(rectW, rectH),
                cornerRadius = corner
            )
            val tri = Path().apply {
                val cx = rectLeft + rectW * 0.54f
                val cy = rectTop + rectH * 0.5f
                val s = rectW * 0.22f
                moveTo(cx - s * 0.65f, cy - s * 0.85f)
                lineTo(cx + s * 0.9f, cy)
                lineTo(cx - s * 0.65f, cy + s * 0.85f)
                close()
            }
            drawPath(path = tri, color = background)
        } else {
            drawRoundRect(
                color = tint,
                topLeft = Offset(rectLeft, rectTop),
                size = Size(rectW, rectH),
                cornerRadius = corner,
                style = Stroke(width = strokeWidth)
            )
            val tri = Path().apply {
                val cx = rectLeft + rectW * 0.53f
                val cy = rectTop + rectH * 0.5f
                val s = rectW * 0.18f
                moveTo(cx - s * 0.6f, cy - s * 0.85f)
                lineTo(cx + s * 0.9f, cy)
                lineTo(cx - s * 0.6f, cy + s * 0.85f)
                close()
            }
            drawPath(path = tri, color = tint)
        }
    }
}

@Composable
private fun SettingsGearNavIcon(
    tint: Color,
    selected: Boolean,
    background: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerR = size.minDimension * 0.42f
        val innerR = size.minDimension * 0.30f
        val holeR = size.minDimension * 0.14f
        val teeth = 8
        val gearPath = Path()
        for (i in 0 until teeth) {
            val baseAngle = (i * 2.0 * PI / teeth)
            val step = (2.0 * PI / teeth) / 4.0
            val p1 = Offset(cx + (innerR * cos(baseAngle - step * 1.4)).toFloat(), cy + (innerR * sin(baseAngle - step * 1.4)).toFloat())
            val p2 = Offset(cx + (outerR * cos(baseAngle - step * 0.6)).toFloat(), cy + (outerR * sin(baseAngle - step * 0.6)).toFloat())
            val p3 = Offset(cx + (outerR * cos(baseAngle + step * 0.6)).toFloat(), cy + (outerR * sin(baseAngle + step * 0.6)).toFloat())
            val p4 = Offset(cx + (innerR * cos(baseAngle + step * 1.4)).toFloat(), cy + (innerR * sin(baseAngle + step * 1.4)).toFloat())
            if (i == 0) gearPath.moveTo(p1.x, p1.y) else gearPath.lineTo(p1.x, p1.y)
            gearPath.lineTo(p2.x, p2.y)
            gearPath.lineTo(p3.x, p3.y)
            gearPath.lineTo(p4.x, p4.y)
        }
        gearPath.close()

        if (selected) {
            drawPath(path = gearPath, color = tint)
            drawCircle(color = background, radius = holeR, center = Offset(cx, cy))
        } else {
            val stroke = 1.6.dp.toPx()
            drawPath(path = gearPath, color = tint, style = Stroke(width = stroke, join = StrokeJoin.Round))
            drawCircle(color = tint, radius = holeR, center = Offset(cx, cy), style = Stroke(width = stroke))
        }
    }
}
