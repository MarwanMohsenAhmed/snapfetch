package com.snapfetch.app.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfetch.app.AppContainer
import com.snapfetch.app.core.datastore.CompressionPreset
import com.snapfetch.app.core.datastore.DownloadSizeMode
import com.snapfetch.app.core.datastore.UserSettings
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapTypography
import com.snapfetch.app.domain.model.FormatSizeCalculator
import kotlinx.coroutines.launch

@Composable
fun DownloadSettingsScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val colors = LocalSnapFetchColors.current
    val scope = rememberCoroutineScope()
    val settings by container.settingsDataStore.settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())

    var showConcurrentDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showMobileCapDialog by remember { mutableStateOf(false) }
    var showPresetDialog by remember { mutableStateOf(false) }
    var pluginNotice by remember { mutableStateOf<String?>(null) }

    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            container.safHelper.takePersistablePermission(uri)
            scope.launch { container.settingsDataStore.setSaveFolderUri(uri.toString()) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
            }
            Text("Download settings", style = SnapTypography.ScreenTitle, color = colors.textPrimary)
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                SettingChoiceRow(
                    title = "Download path",
                    subtitle = container.safHelper.describeTreeUri(settings.saveFolderUri),
                    onClick = { safLauncher.launch(null) }
                )
            }
            item {
                SettingChoiceRow(
                    title = "Max download tasks",
                    subtitle = "${settings.maxConcurrentDownloads} concurrent task(s) (1 recommended for 2 GB RAM)",
                    onClick = { showConcurrentDialog = true }
                )
            }
            item {
                SettingChoiceRow(
                    title = "Download speed limit",
                    subtitle = if (settings.speedLimitKbps <= 0) "Unlimited" else "${settings.speedLimitKbps} KB/s",
                    onClick = { showSpeedDialog = true }
                )
            }
            item {
                SettingSwitchRow(
                    title = "Download via mobile data",
                    subtitle = "Media will be downloaded using cellular data",
                    checked = !settings.wifiOnlyEnabled,
                    onCheckedChange = { allowCellular ->
                        scope.launch { container.settingsDataStore.setWifiOnly(!allowCellular) }
                    }
                )
            }
            item {
                SettingSwitchRow(
                    title = "Data Saver mode",
                    subtitle = "Prefer 240p/360p or Fast M4A & 320px thumbnails",
                    checked = settings.dataSaverEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            container.settingsDataStore.setDataSaver(enabled)
                            if (enabled) {
                                container.settingsDataStore.setDefaultDownloadSizeMode(DownloadSizeMode.DATA_SAVER_360P)
                            }
                        }
                    }
                )
            }
            item {
                val capText = if (settings.mobileDataLimitMbPerDay <= 0) "Unlimited" else "${settings.mobileDataLimitMbPerDay} MB / day"
                val usedText = FormatSizeCalculator.formatBytes(settings.mobileBytesUsedToday)
                SettingChoiceRow(
                    title = "Daily mobile data cap",
                    subtitle = "$capText • Used today: $usedText",
                    onClick = { showMobileCapDialog = true }
                )
            }
            item {
                SettingSwitchRow(
                    title = "Auto-resume on network restore",
                    subtitle = "Continue paused downloads when connection returns",
                    checked = settings.autoResumeOnNetworkRestore,
                    onCheckedChange = { scope.launch { container.settingsDataStore.setAutoResume(it) } }
                )
            }
            item {
                SettingChoiceRow(
                    title = "Default video size reduction preset",
                    subtitle = settings.compressionPreset.label,
                    onClick = { showPresetDialog = true }
                )
            }
            item {
                SettingSwitchRow(
                    title = "Delete original after compression",
                    subtitle = "Only removes original after compressed file verifies",
                    checked = settings.deleteOriginalAfterCompress,
                    onCheckedChange = { scope.launch { container.settingsDataStore.setDeleteOriginalAfterCompress(it) } }
                )
            }
            item {
                val availBytes = container.storageGuard.availableInternalBytes()
                val has250Mb = availBytes >= 250L * 1024 * 1024
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.cardSurface
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Extra Sites & MP3 Engine (Optional Plugin)",
                            style = SnapTypography.ListRowTitle.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (settings.proEngineInstalled) {
                                "Pro Engine active for legacy MP3 transcode & extended sites."
                            } else {
                                "Base APK keeps size ≤ 15 MB using pure-JVM NewPipeExtractor & hardware Media3. Opt-in requires >= 250 MB free (${FormatSizeCalculator.formatBytes(availBytes)} free)."
                            },
                            style = SnapTypography.Metadata,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                if (!has250Mb && !settings.proEngineInstalled) {
                                    pluginNotice = "Requires at least 250 MB free internal storage."
                                } else {
                                    scope.launch {
                                        val next = !settings.proEngineInstalled
                                        container.settingsDataStore.setProEngineInstalled(next)
                                        pluginNotice = if (next) "Pro Engine preference enabled (on-demand download ready)." else "Pro Engine disabled."
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.buttonTextOnAccent
                            )
                        ) {
                            Text(if (settings.proEngineInstalled) "Remove Plugin" else "Enable On-Demand Plugin", fontWeight = FontWeight.Bold)
                        }
                        if (pluginNotice != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(pluginNotice.orEmpty(), style = SnapTypography.Metadata, color = colors.textPrimary)
                        }
                    }
                }
            }
        }
    }

    if (showConcurrentDialog) {
        OptionPickerDialog(
            title = "Max concurrent downloads",
            options = listOf(1 to "1 task (Best for 2 GB RAM)", 2 to "2 tasks", 3 to "3 tasks"),
            current = settings.maxConcurrentDownloads,
            onDismiss = { showConcurrentDialog = false },
            onSelect = { scope.launch { container.settingsDataStore.setMaxConcurrentDownloads(it) } }
        )
    }

    if (showSpeedDialog) {
        OptionPickerDialog(
            title = "Download speed limit",
            options = listOf(0 to "Unlimited", 256 to "256 KB/s", 512 to "512 KB/s", 1024 to "1 MB/s", 2048 to "2 MB/s"),
            current = settings.speedLimitKbps,
            onDismiss = { showSpeedDialog = false },
            onSelect = { scope.launch { container.settingsDataStore.setSpeedLimitKbps(it) } }
        )
    }

    if (showMobileCapDialog) {
        OptionPickerDialog(
            title = "Daily mobile data cap",
            options = listOf(0 to "Unlimited", 100 to "100 MB", 250 to "250 MB", 500 to "500 MB", 1024 to "1 GB"),
            current = settings.mobileDataLimitMbPerDay,
            onDismiss = { showMobileCapDialog = false },
            onSelect = { scope.launch { container.settingsDataStore.setMobileDataLimitMb(it) } }
        )
    }

    if (showPresetDialog) {
        OptionPickerDialog(
            title = "Default compression preset",
            options = CompressionPreset.entries.map { it to it.label },
            current = settings.compressionPreset,
            onDismiss = { showPresetDialog = false },
            onSelect = { scope.launch { container.settingsDataStore.setCompressionPreset(it) } }
        )
    }
}

@Composable
fun SettingChoiceRow(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = LocalSnapFetchColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = SnapTypography.ListRowTitle.copy(fontSize = 15.5.sp), color = colors.textPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = SnapTypography.Metadata, color = colors.textSecondary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.textMuted)
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalSnapFetchColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = SnapTypography.ListRowTitle.copy(fontSize = 15.5.sp), color = colors.textPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = SnapTypography.Metadata, color = colors.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent
            )
        )
    }
}

@Composable
fun <T> OptionPickerDialog(
    title: String,
    options: List<Pair<T, String>>,
    current: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit
) {
    val colors = LocalSnapFetchColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedSurface,
        title = { Text(title, color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(value)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = value == current, onClick = null)
                        Text(label, color = colors.textPrimary, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = colors.textSecondary) }
        }
    )
}
