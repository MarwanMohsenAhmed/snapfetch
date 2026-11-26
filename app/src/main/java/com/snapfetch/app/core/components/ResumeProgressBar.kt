package com.snapfetch.app.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapSpacing

@Composable
fun ResumeProgressBar(
    ratio: Float,
    modifier: Modifier = Modifier
) {
    if (ratio <= 0.02f || ratio >= 0.95f) return
    val accent = LocalSnapFetchColors.current.accent
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SnapSpacing.ResumeProgressHeight)
            .background(Color(0x66000000))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(accent)
        )
    }
}
