package com.snapfetch.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.snapfetch.app.core.datastore.UserSettings
import com.snapfetch.app.core.theme.SnapFetchTheme
import com.snapfetch.app.navigation.AppNavHost

class MainActivity : AppCompatActivity() {

    private var sharedIncomingUrl by mutableStateOf<String?>(null)
    private var isInPipMode by mutableStateOf(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleIncomingIntent(intent)
        requestNotificationPermissionIfNeeded()

        val appContainer = (application as SnapFetchApp).container

        setContent {
            val settings by appContainer.settingsDataStore.settingsFlow.collectAsStateWithLifecycle(
                initialValue = UserSettings()
            )
            SnapFetchTheme(
                themeMode = settings.themeMode,
                accentColorHex = settings.accentColorHex
            ) {
                AppNavHost(
                    container = appContainer,
                    sharedIncomingUrl = sharedIncomingUrl,
                    onClearSharedUrl = { sharedIncomingUrl = null },
                    isInPipMode = isInPipMode
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                extractFirstUrl(text)?.let { sharedIncomingUrl = it }
            }
            Intent.ACTION_VIEW -> {
                intent.dataString?.let { sharedIncomingUrl = it }
            }
        }
    }

    private fun extractFirstUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return Regex("https?://\\S+").find(raw)?.value
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
