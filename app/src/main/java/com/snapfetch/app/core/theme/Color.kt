package com.snapfetch.app.core.theme

import androidx.compose.ui.graphics.Color

// Exact pixel-sampled palette from reference screenshots
val PrimaryYellow = Color(0xFFFFCC21)
val PrimaryYellowAlt = Color(0xFFFDCE23)
val BackgroundWhite = Color(0xFFFFFFFF)
val SurfaceCardGray = Color(0xFFF6F6F6)
val TextPrimary = Color(0xFF1A1A1A)
val TextBodyDark = Color(0xFF3B3B3B)
val IconGrayDark = Color(0xFF424242)
val TextSecondary = Color(0xFF8E8E8E)
val TextMuted = Color(0xFFA2A2A2)
val BottomNavInactive = Color(0xFF9E9E9E)
val UnselectedRadioStroke = Color(0xFFBDBDBD)
val BorderLight = Color(0xFFE0E0E0)
val DividerSubtle = Color(0xFFF0F0F0)
val ScrimOverlay = Color(0x99000000)

// Optional Dark & AMOLED themes preserving yellow accent
val DarkSurface = Color(0xFF121212)
val DarkCard = Color(0xFF1E1E1E)
val DarkDivider = Color(0xFF2B2B2B)
val AmoledSurface = Color(0xFF000000)
val AmoledCard = Color(0xFF101010)

val SaveSpaceBadgeBg = Color(0xFFE8F5E9)
val SaveSpaceBadgeText = Color(0xFF2E7D32)
val ErrorRed = Color(0xFFD32F2F)
val SubscribeBlack = Color(0xFF1A1A1A)

val AccentPresets: List<Pair<String, String>> = listOf(
    "Snap Yellow" to "#FFCC21",
    "Amber Gold" to "#FFB300",
    "Coral Orange" to "#FF7043",
    "Teal Mint" to "#26A69A",
    "Sky Blue" to "#29B6F6"
)

fun parseHexColor(hex: String, fallback: Color = PrimaryYellow): Color {
    return try {
        val clean = hex.trim().removePrefix("#")
        when (clean.length) {
            6 -> Color(0xFF000000L or clean.toLong(16))
            8 -> Color(clean.toLong(16))
            else -> fallback
        }
    } catch (_: Throwable) {
        fallback
    }
}
