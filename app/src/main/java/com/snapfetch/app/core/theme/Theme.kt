package com.snapfetch.app.core.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.snapfetch.app.core.datastore.ThemeMode

@Immutable
data class SnapFetchColors(
    val accent: Color,
    val wordmarkYellow: Color,
    val background: Color,
    val cardSurface: Color,
    val elevatedSurface: Color,
    val textPrimary: Color,
    val textBodyDark: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val iconDark: Color,
    val bottomNavInactive: Color,
    val borderLight: Color,
    val dividerSubtle: Color,
    val unselectedRadioStroke: Color,
    val buttonTextOnAccent: Color,
    val isDark: Boolean
)

val LocalSnapFetchColors = staticCompositionLocalOf {
    SnapFetchColors(
        accent = PrimaryYellow,
        wordmarkYellow = PrimaryYellowAlt,
        background = BackgroundWhite,
        cardSurface = SurfaceCardGray,
        elevatedSurface = BackgroundWhite,
        textPrimary = TextPrimary,
        textBodyDark = TextBodyDark,
        textSecondary = TextSecondary,
        textMuted = TextMuted,
        iconDark = IconGrayDark,
        bottomNavInactive = BottomNavInactive,
        borderLight = BorderLight,
        dividerSubtle = DividerSubtle,
        unselectedRadioStroke = UnselectedRadioStroke,
        buttonTextOnAccent = TextPrimary,
        isDark = false
    )
}

@Composable
fun SnapFetchTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    accentColorHex: String = "#FFCC21",
    content: @Composable () -> Unit
) {
    val accent = remember(accentColorHex) { parseHexColor(accentColorHex, PrimaryYellow) }
    val wordmark = remember(accentColorHex) {
        if (accentColorHex.equals("#FFCC21", ignoreCase = true)) PrimaryYellowAlt else accent
    }

    val snapColors = remember(themeMode, accent, wordmark) {
        when (themeMode) {
            ThemeMode.LIGHT -> SnapFetchColors(
                accent = accent,
                wordmarkYellow = wordmark,
                background = BackgroundWhite,
                cardSurface = SurfaceCardGray,
                elevatedSurface = BackgroundWhite,
                textPrimary = TextPrimary,
                textBodyDark = TextBodyDark,
                textSecondary = TextSecondary,
                textMuted = TextMuted,
                iconDark = IconGrayDark,
                bottomNavInactive = BottomNavInactive,
                borderLight = BorderLight,
                dividerSubtle = DividerSubtle,
                unselectedRadioStroke = UnselectedRadioStroke,
                buttonTextOnAccent = TextPrimary,
                isDark = false
            )
            ThemeMode.DARK -> SnapFetchColors(
                accent = accent,
                wordmarkYellow = wordmark,
                background = DarkSurface,
                cardSurface = DarkCard,
                elevatedSurface = DarkCard,
                textPrimary = Color(0xFFF2F2F2),
                textBodyDark = Color(0xFFD6D6D6),
                textSecondary = Color(0xFF9E9E9E),
                textMuted = Color(0xFF787878),
                iconDark = Color(0xFFD0D0D0),
                bottomNavInactive = Color(0xFF8E8E8E),
                borderLight = Color(0xFF333333),
                dividerSubtle = DarkDivider,
                unselectedRadioStroke = Color(0xFF666666),
                buttonTextOnAccent = TextPrimary,
                isDark = true
            )
            ThemeMode.AMOLED -> SnapFetchColors(
                accent = accent,
                wordmarkYellow = wordmark,
                background = AmoledSurface,
                cardSurface = AmoledCard,
                elevatedSurface = Color(0xFF141414),
                textPrimary = Color(0xFFF5F5F5),
                textBodyDark = Color(0xFFD8D8D8),
                textSecondary = Color(0xFF9E9E9E),
                textMuted = Color(0xFF757575),
                iconDark = Color(0xFFD5D5D5),
                bottomNavInactive = Color(0xFF8A8A8A),
                borderLight = Color(0xFF282828),
                dividerSubtle = Color(0xFF1C1C1C),
                unselectedRadioStroke = Color(0xFF5E5E5E),
                buttonTextOnAccent = TextPrimary,
                isDark = true
            )
        }
    }

    val m3Scheme = remember(snapColors) {
        if (snapColors.isDark) {
            darkColorScheme(
                primary = snapColors.accent,
                onPrimary = TextPrimary,
                background = snapColors.background,
                onBackground = snapColors.textPrimary,
                surface = snapColors.background,
                onSurface = snapColors.textPrimary,
                surfaceVariant = snapColors.cardSurface,
                onSurfaceVariant = snapColors.textSecondary,
                outline = snapColors.borderLight
            )
        } else {
            lightColorScheme(
                primary = snapColors.accent,
                onPrimary = TextPrimary,
                background = BackgroundWhite,
                onBackground = TextPrimary,
                surface = BackgroundWhite,
                onSurface = TextPrimary,
                surfaceVariant = SurfaceCardGray,
                onSurfaceVariant = TextSecondary,
                outline = BorderLight
            )
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = snapColors.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = snapColors.background.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !snapColors.isDark
            controller.isAppearanceLightNavigationBars = !snapColors.isDark
        }
    }

    CompositionLocalProvider(LocalSnapFetchColors provides snapColors) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = SnapFetchMaterialTypography,
            content = content
        )
    }
}
