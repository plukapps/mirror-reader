package com.pluk.reader.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pluk.reader.R
import com.pluk.reader.ui.theme.MarginColors

/** Destinos de la barra inferior (HOM-005). */
enum class MainDestination(@StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home(R.string.nav_home, NavIcons.Home, NavIcons.HomeFilled),
    Search(R.string.nav_search, NavIcons.Search, NavIcons.Search),
    Shelves(R.string.nav_shelves, NavIcons.Shelves, NavIcons.ShelvesFilled),
    Profile(R.string.nav_profile, NavIcons.Person, NavIcons.PersonFilled),
}

/**
 * Barra inferior del diseño (pantallas 02, 03 y 05): píldora tinta flotante, solo íconos, y el
 * destino activo en una píldora amarilla. El nombre de cada destino queda para TalkBack (HOM-007).
 */
@Composable
fun MarginBottomBar(selected: MainDestination, onDestinationClick: (MainDestination) -> Unit) {
    Box(Modifier.fillMaxWidth().background(MarginColors.Paper).navigationBarsPadding()) {
        Row(
            Modifier
                .padding(start = 40.dp, end = 40.dp, bottom = 12.dp)
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(MarginColors.Ink)
                .padding(horizontal = 8.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainDestination.entries.forEach { destination ->
                BarItem(destination, destination == selected) { onDestinationClick(destination) }
            }
        }
    }
}

@Composable
private fun BarItem(destination: MainDestination, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 52.dp, height = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) MarginColors.Yellow else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .testTag("nav-${destination.name}"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (selected) destination.selectedIcon else destination.icon,
            contentDescription = stringResource(destination.label),
            tint = if (selected) MarginColors.Ink else MarginColors.InkMuted,
        )
    }
}
