package com.pluk.reader.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pluk.reader.R
import com.pluk.reader.ui.theme.MarginColors

/** Destinos de la barra inferior (HOM-005). */
enum class MainDestination(@StringRes val label: Int, val icon: ImageVector) {
    Home(R.string.nav_home, NavIcons.Home),
    Search(R.string.nav_search, NavIcons.Search),
    Shelves(R.string.nav_shelves, NavIcons.Shelves),
    Profile(R.string.nav_profile, NavIcons.Person),
}

@Composable
fun MarginBottomBar(selected: MainDestination, onDestinationClick: (MainDestination) -> Unit) {
    Column(Modifier.fillMaxWidth().background(MarginColors.Paper).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MarginColors.Line))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MainDestination.entries.forEach { destination ->
                BarItem(destination, destination == selected, Modifier.weight(1f)) { onDestinationClick(destination) }
            }
        }
    }
}

@Composable
private fun BarItem(destination: MainDestination, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val label = stringResource(destination.label)
    Column(
        modifier
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .testTag("nav-${destination.name}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier
                .width(52.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) MarginColors.Yellow else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(destination.icon, contentDescription = null, tint = if (selected) MarginColors.Ink else MarginColors.Muted)
        }
        Text(
            label,
            color = if (selected) MarginColors.Ink else MarginColors.Muted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
