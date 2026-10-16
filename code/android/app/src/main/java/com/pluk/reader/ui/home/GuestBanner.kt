package com.pluk.reader.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pluk.reader.R
import com.pluk.reader.domain.account.GuestBannerVariant
import com.pluk.reader.ui.onboarding.OnboardingIcons
import com.pluk.reader.ui.plans.PlanIcons
import com.pluk.reader.ui.welcome.BannerCovers
import com.pluk.reader.ui.welcome.CoverColors

/** Colores fijos del banner, como la cabecera amarilla de Perfil: se ve igual en modo claro y oscuro. */
private object BannerColors {
    val Background = CoverColors.Bone
    val Ink = CoverColors.Ink
    val Soft = Color(0xFF3A3830)
    val Muted = Color(0xFF6B6650)
}

/**
 * GST-023 (diseños H0b "6e" y H0 "6b"): banner sin cuenta de Inicio. Toda la tarjeta abre el alta corta; la cruz lo
 * descarta (GST-010). La opción la elige GST-025.
 */
@Composable
fun GuestBanner(variant: GuestBannerVariant, onCreateAccount: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val (badge, title) = when (variant) {
        GuestBannerVariant.AllDevices -> BannerIcons.Sync to stringResource(R.string.guest_banner_all_title)
        GuestBannerVariant.NoBackup -> BannerIcons.CloudUpload to stringResource(R.string.guest_banner_backup_title)
    }
    Box(
        modifier
            .padding(start = 20.dp, end = 20.dp, top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(BannerColors.Background)
            .testTag("guest-banner"),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onCreateAccount)
                .semantics(mergeDescendants = true) { }
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BannerCovers(
                badge = { Icon(badge, contentDescription = null, tint = BannerColors.Ink, modifier = Modifier.size(16.dp)) },
                borderColor = BannerColors.Background,
            )
            Column(Modifier.weight(1f).padding(end = 34.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = BannerColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 1.1.em, letterSpacing = (-0.025).em)
                when (variant) {
                    GuestBannerVariant.AllDevices -> Devices()
                    GuestBannerVariant.NoBackup -> Text(
                        stringResource(R.string.guest_banner_backup_body),
                        color = BannerColors.Soft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 1.35.em,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.guest_notice_create_account), color = BannerColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Icon(OnboardingIcons.ArrowForward, contentDescription = null, tint = BannerColors.Ink, modifier = Modifier.size(18.dp))
                }
            }
        }
        val close = stringResource(R.string.plans_notice_close)
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .clickable(role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = close }
                .testTag("guest-banner-close"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(PlanIcons.Close, contentDescription = null, tint = BannerColors.Muted, modifier = Modifier.size(20.dp))
        }
    }
}

/** Opción A: teléfono, tablet y computadora, "gratis". */
@Composable
private fun Devices() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf<ImageVector>(BannerIcons.Smartphone, BannerIcons.Tablet, BannerIcons.Computer).forEach {
            Icon(it, contentDescription = null, tint = BannerColors.Soft, modifier = Modifier.size(20.dp))
        }
        Text(
            stringResource(R.string.guest_banner_free),
            color = BannerColors.Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}
