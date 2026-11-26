package com.snapfetch.app.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Whatsapp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfetch.app.AppContainer
import com.snapfetch.app.core.components.DownloadArrowIcon
import com.snapfetch.app.core.datastore.UserSettings
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapTypography
import com.snapfetch.app.domain.model.FormatSizeCalculator

@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenDownloadSettings: () -> Unit,
    onOpenPlayerSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenCustomization: () -> Unit,
    onOpenTool: (String) -> Unit
) {
    val context = LocalContext.current
    val colors = LocalSnapFetchColors.current
    val settings by container.settingsDataStore.settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())
    var junkBytes by remember { mutableLongStateOf(0L) }
    var largeFilesBytes by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        val activeParts = container.database.downloadDao().getActivePartPaths().toSet()
        junkBytes = container.storageScanner.scanJunk(activeParts).totalBytes
        largeFilesBytes = container.storageScanner.scanLargeFiles().sumOf { it.sizeBytes }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        Text(
            text = "Settings",
            style = SnapTypography.ScreenTitle,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SettingsSectionHeader("General") }
            item {
                SettingsRowCustomIcon(
                    title = "Download settings",
                    icon = { DownloadArrowIcon(color = colors.textSecondary, modifier = Modifier.size(21.dp)) },
                    onClick = onOpenDownloadSettings
                )
            }
            item {
                SettingsRow(
                    title = "Player settings",
                    icon = Icons.Filled.PlayCircleOutline,
                    onClick = onOpenPlayerSettings
                )
            }
            item {
                SettingsRow(
                    title = "Notification settings",
                    icon = Icons.Filled.NotificationsNone,
                    onClick = onOpenNotificationSettings
                )
            }
            item {
                SettingsRow(
                    title = "Theme & Customization",
                    icon = Icons.Filled.DarkMode,
                    onClick = onOpenCustomization
                )
            }

            item { SettingsSectionHeader("Download tools") }
            item {
                SettingsRow(
                    title = "Recover deleted files",
                    icon = Icons.Filled.RestorePage,
                    onClick = { onOpenTool("trash") }
                )
            }
            item {
                SettingsRow(
                    title = "WhatsApp status saver",
                    icon = Icons.Filled.Whatsapp,
                    onClick = { onOpenTool("whatsapp") }
                )
            }
            item {
                SettingsRow(
                    title = "Vault",
                    icon = Icons.Filled.Lock,
                    onClick = { onOpenTool("vault") }
                )
            }

            item { SettingsSectionHeader("Phone clean") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clickable { onOpenTool("junk") }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CleaningServices, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(18.dp))
                    Text("Junk clean", style = SnapTypography.ListRowTitle.copy(fontSize = 15.5.sp), color = colors.textBodyDark, modifier = Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = colors.wordmarkYellow
                    ) {
                        Text(
                            text = FormatSizeCalculator.formatBytes(junkBytes),
                            color = colors.buttonTextOnAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            item {
                SettingsRow(
                    title = "Boost",
                    icon = Icons.Filled.RocketLaunch,
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                        }.onFailure { onOpenTool("junk") }
                    }
                )
            }
            item {
                SettingsRow(
                    title = "Battery saver",
                    icon = Icons.Filled.BatteryChargingFull,
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
                        }.onFailure { onOpenTool("about") }
                    }
                )
            }
            item {
                SettingsRow(
                    title = "Large files clean",
                    icon = Icons.Filled.FolderOpen,
                    trailingValue = if (largeFilesBytes > 0L) FormatSizeCalculator.formatBytes(largeFilesBytes) else "0.0 MB",
                    onClick = { onOpenTool("large_files") }
                )
            }

            item { SettingsSectionHeader("Info") }
            item {
                SettingsRow(
                    title = "Account",
                    icon = Icons.Filled.AccountCircle,
                    trailingValue = "Local",
                    onClick = { onOpenTool("account") }
                )
            }
            item {
                SettingsRow(
                    title = "Language",
                    icon = Icons.Filled.Language,
                    trailingValue = settings.appLanguageTag.ifBlank { "English (US)" },
                    onClick = { onOpenTool("language") }
                )
            }
            item {
                SettingsRow(
                    title = "Feedback",
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    onClick = {
                        val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@snapfetch.app")).apply {
                            putExtra(Intent.EXTRA_SUBJECT, "SnapFetch Feedback")
                        }
                        runCatching { context.startActivity(Intent.createChooser(mail, "Send Feedback")) }
                    }
                )
            }
            item {
                SettingsRow(
                    title = "Share SnapFetch",
                    icon = Icons.Filled.Share,
                    onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "SnapFetch lightweight offline-first media downloader & player")
                        }
                        context.startActivity(Intent.createChooser(share, "Share SnapFetch"))
                    }
                )
            }
            item {
                SettingsRow(
                    title = "About",
                    icon = Icons.Filled.Info,
                    onClick = { onOpenTool("about") }
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    val colors = LocalSnapFetchColors.current
    Text(
        text = title,
        style = SnapTypography.Metadata.copy(fontSize = 13.sp),
        color = colors.textMuted,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    icon: ImageVector,
    trailingValue: String? = null,
    onClick: () -> Unit
) {
    val colors = LocalSnapFetchColors.current
    SettingsRowCustomIcon(
        title = title,
        icon = { Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp)) },
        trailingValue = trailingValue,
        onClick = onClick
    )
}

@Composable
private fun SettingsRowCustomIcon(
    title: String,
    icon: @Composable () -> Unit,
    trailingValue: String? = null,
    onClick: () -> Unit
) {
    val colors = LocalSnapFetchColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = title,
            style = SnapTypography.ListRowTitle.copy(fontSize = 15.5.sp),
            color = colors.textBodyDark,
            modifier = Modifier.weight(1f)
        )
        if (!trailingValue.isNullOrBlank()) {
            Text(
                text = trailingValue,
                style = SnapTypography.Metadata.copy(fontSize = 13.5.sp),
                color = colors.textMuted
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}
