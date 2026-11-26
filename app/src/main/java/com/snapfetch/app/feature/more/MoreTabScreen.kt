package com.snapfetch.app.feature.more

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapfetch.app.core.components.DownloadArrowIcon
import com.snapfetch.app.core.theme.LocalSnapFetchColors
import com.snapfetch.app.core.theme.SnapSpacing
import com.snapfetch.app.core.theme.SnapTypography

data class SupportedSiteGuide(
    val id: String,
    val name: String,
    val badgeColor: Color,
    val symbol: String,
    val launchUrl: String
)

private val SupportedSites = listOf(
    SupportedSiteGuide("yt", "YouTube", Color(0xFFFF0000), "▶", "https://m.youtube.com"),
    SupportedSiteGuide("wa", "WhatsApp Status", Color(0xFF25D366), "W", "https://www.whatsapp.com"),
    SupportedSiteGuide("tt", "TikTok", Color(0xFF111111), "♪", "https://www.tiktok.com"),
    SupportedSiteGuide("ig", "Instagram", Color(0xFFE1306C), "IG", "https://www.instagram.com"),
    SupportedSiteGuide("fb", "Facebook", Color(0xFF1877F2), "f", "https://m.facebook.com"),
    SupportedSiteGuide("sc", "SoundCloud", Color(0xFFFF5500), "☁", "https://m.soundcloud.com")
)

@Composable
fun MoreTabScreen(
    onOpenWhatsAppSaver: () -> Unit
) {
    val colors = LocalSnapFetchColors.current
    var activeGuide by remember { mutableStateOf<SupportedSiteGuide?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(SupportedSites, key = { it.id }) { site ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = colors.elevatedSurface,
                border = BorderStroke(0.5.dp, colors.dividerSubtle),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DownloadArrowIcon(color = colors.textSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = site.name,
                                style = SnapTypography.ListRowTitle.copy(fontSize = 16.sp),
                                color = colors.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                if (site.id == "wa") onOpenWhatsAppSaver() else activeGuide = site
                            },
                            shape = RoundedCornerShape(20.dp),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.buttonTextOnAccent
                            )
                        ) {
                            Text("How to start", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .rotate(12f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(site.badgeColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(site.badgeColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(site.symbol, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                    }
                }
            }
        }
    }

    activeGuide?.let { guide ->
        SiteGuideSheet(
            guide = guide,
            onDismiss = { activeGuide = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteGuideSheet(
    guide: SupportedSiteGuide,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalSnapFetchColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.elevatedSurface,
        shape = RoundedCornerShape(topStart = SnapSpacing.SheetTopCornerRadius, topEnd = SnapSpacing.SheetTopCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "How to download",
                style = SnapTypography.ScreenTitle,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(18.dp))

            GuideStepCard("1. Open ${guide.name}, click \"Share\"", "Tap the Share arrow on any public video or track.")
            Spacer(modifier = Modifier.height(12.dp))
            GuideStepCard("2. Click \"More\" or \"Copy link\"", "Select More in the system share menu or copy the link.")
            Spacer(modifier = Modifier.height(12.dp))
            GuideStepCard("3. Find and click \"SnapFetch\"", "SnapFetch opens the format picker automatically.")

            Spacer(modifier = Modifier.height(22.dp))
            Button(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(guide.launchUrl)))
                    }
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(SnapSpacing.PillButtonRadius),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.buttonTextOnAccent
                )
            ) {
                Text("Open ${guide.name}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun GuideStepCard(title: String, detail: String) {
    val colors = LocalSnapFetchColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.cardSurface)
            .padding(14.dp)
    ) {
        Text(title, style = SnapTypography.ListRowTitle.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(detail, style = SnapTypography.Metadata, color = colors.textSecondary)
    }
}
