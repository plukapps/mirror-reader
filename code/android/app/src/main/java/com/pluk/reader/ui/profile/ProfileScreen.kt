package com.pluk.reader.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pluk.reader.BuildConfig
import com.pluk.reader.R
import com.pluk.reader.domain.appearance.Appearance
import com.pluk.reader.domain.help.Faq
import com.pluk.reader.domain.help.FaqId
import com.pluk.reader.domain.model.LineSpacing
import com.pluk.reader.domain.model.ReaderFont
import com.pluk.reader.domain.model.ReadingTheme
import com.pluk.reader.ui.navigation.LocalBottomBarPadding
import com.pluk.reader.ui.theme.MarginColors

/** Lo que se puede tocar en Perfil. Lo que todavía no existe va a [onComingSoon] (PRF-003). */
data class ProfileActions(
    val onComingSoon: () -> Unit = {},
    val onSignOut: () -> Unit = onComingSoon,
    val onFaq: (FaqId) -> Unit = { onComingSoon() },
    val onAllFaq: () -> Unit = onComingSoon,
    val onFont: (ReaderFont) -> Unit = {},
    val onSmallerFont: () -> Unit = {},
    val onBiggerFont: () -> Unit = {},
    val onTheme: (ReadingTheme) -> Unit = {},
    val onLineSpacing: (LineSpacing) -> Unit = {},
    val onAppearance: (Appearance) -> Unit = {},
)

/** Perfil: cuenta, espacio, ajustes y ayuda (PRF-001 a PRF-019). Diseño: "09 — Profile". */
@Composable
fun ProfileScreen(
    onSignedOut: () -> Unit,
    onFaq: (FaqId) -> Unit,
    onAllFaq: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // PRF-009: con la sesión cerrada, a la bienvenida.
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }
    LaunchedEffect(state.signOutFailed) {
        if (state.signOutFailed) {
            Toast.makeText(context, R.string.profile_sign_out_failed, Toast.LENGTH_SHORT).show()
            viewModel.onSignOutFailureShown()
        }
    }
    state.signOutDialog?.let { dialog ->
        SignOutConfirmation(dialog, onConfirm = viewModel::onSignOutConfirmed, onDismiss = viewModel::onSignOutDismissed)
    }
    ProfileContent(
        state = state,
        actions = ProfileActions(
            onComingSoon = { Toast.makeText(context, R.string.nav_coming_soon, Toast.LENGTH_SHORT).show() },
            onSignOut = viewModel::onSignOutRequested,
            onFaq = onFaq,
            onAllFaq = onAllFaq,
            onFont = viewModel::onFont,
            onSmallerFont = viewModel::onSmallerFont,
            onBiggerFont = viewModel::onBiggerFont,
            onTheme = viewModel::onTheme,
            onLineSpacing = viewModel::onLineSpacing,
            onAppearance = viewModel::onAppearance,
        ),
    )
}

/** Sin ViewModel, para poder probarla y previsualizarla. */
@Composable
fun ProfileContent(
    state: ProfileUiState,
    actions: ProfileActions,
    version: String = BuildConfig.VERSION_NAME,
    isTablet: Boolean = LocalConfiguration.current.smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP,
) {
    val bottomBarPadding = LocalBottomBarPadding.current
    var fontSheetOpen by rememberSaveable { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(MarginColors.Paper)
            .testTag("profile"),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = MAX_CONTENT_WIDTH)
                .fillMaxSize()
                // Fuera del desplazamiento: el contenido no pasa por debajo de la barra de estado.
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Header(state, actions.onComingSoon)
            SectionTitle(stringResource(R.string.profile_section_account))
            AccountCard(state, isTablet, actions)
            SectionTitle(stringResource(R.string.profile_section_reading), bottom = 4.dp)
            Text(
                stringResource(R.string.profile_reading_hint),
                style = profileText(12, color = MarginColors.Muted).copy(lineHeight = 1.4.em),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp),
            )
            ReadingDefaultsCard(state.reading, actions, onOpenFont = { fontSheetOpen = true })
            SectionTitle(stringResource(R.string.profile_section_app))
            AppCard(state.appearance, actions)
            SectionTitle(stringResource(R.string.profile_section_help))
            HelpCard(actions)
            Footer(version, actions.onComingSoon, Modifier.padding(bottom = 24.dp + bottomBarPadding))
        }
    }
    if (fontSheetOpen) FontSheet(state.reading, actions, onDismiss = { fontSheetOpen = false })
}

/** PRF-009: confirmación de cerrar sesión, con el mismo estilo que la de quitar un libro (LIB-020). */
@Composable
private fun SignOutConfirmation(dialog: SignOutDialog, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_sign_out_title)) },
        text = {
            Text(
                stringResource(
                    if (dialog.hasPendingChanges) R.string.profile_sign_out_body_pending else R.string.profile_sign_out_body,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm-sign-out")) {
                Text(stringResource(R.string.profile_sign_out), color = MarginColors.Danger, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_sign_out_cancel), color = MarginColors.Ink)
            }
        },
        // Colores explícitos: el tema de Material no es el de la app y el texto salía casi blanco.
        containerColor = MarginColors.Field,
        titleContentColor = MarginColors.Ink,
        textContentColor = MarginColors.Ink,
    )
}

/** PRF-005: tarjeta amarilla con la inicial, el nombre y el email. */
@Composable
private fun Header(state: ProfileUiState, onClick: () -> Unit) {
    val title = state.name ?: state.email.orEmpty()
    val description = stringResource(R.string.profile_account_header, listOfNotNull(state.name, state.email).joinToString(", "))
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MarginColors.Yellow)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(state.initial)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = profileText(20, FontWeight.Bold, MarginColors.OnYellow, (-0.02).em).copy(lineHeight = 1.1.em),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.name != null && state.email != null) {
                Text(
                    state.email,
                    style = profileText(13, color = MarginColors.OnYellowMuted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        RowIcon(ProfileIcons.ChevronRight, MarginColors.OnYellow, 24.dp)
    }
}

/** PRF-006 a PRF-010: espacio, este dispositivo, contraseña, cerrar sesión y eliminar cuenta. */
@Composable
private fun AccountCard(state: ProfileUiState, isTablet: Boolean, actions: ProfileActions) {
    ProfileCard {
        Storage(state, actions.onComingSoon)
        CardDivider()
        Text(
            stringResource(R.string.profile_devices),
            style = profileText(15, FontWeight.SemiBold),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        )
        ThisDevice(state.deviceName, isTablet)
        if (state.canChangePassword) {
            CardDivider()
            NavigationRow(stringResource(R.string.profile_change_password), actions.onComingSoon, ProfileIcons.Lock)
        }
        CardDivider()
        ActionRow(stringResource(R.string.profile_sign_out), ProfileIcons.Logout, MarginColors.Ink, actions.onSignOut)
        CardDivider()
        ActionRow(stringResource(R.string.profile_delete_account), ProfileIcons.Delete, MarginColors.Danger, actions.onComingSoon)
    }
}

@Composable
private fun Storage(state: ProfileUiState, onUpgrade: () -> Unit) {
    val storage = state.storage
    Column(
        Modifier
            .padding(16.dp)
            .testTag("profile-storage"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.profile_storage), style = profileText(15, FontWeight.SemiBold))
                if (storage?.isFreePlan == true) PlanChip(stringResource(R.string.profile_plan_free))
            }
            if (storage?.isFreePlan == true) {
                Text(
                    stringResource(R.string.profile_upgrade),
                    style = profileText(13, FontWeight.SemiBold).copy(textDecoration = TextDecoration.Underline),
                    modifier = Modifier.clickable(role = Role.Button, onClick = onUpgrade),
                )
            }
        }
        StorageBar(storage?.fraction ?: 0f)
        val detail = profileText(12, FontWeight.Medium, MarginColors.Muted)
        when {
            storage != null -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.profile_storage_used, storage.used, storage.total), style = detail)
                Text(stringResource(R.string.profile_storage_free, storage.free), style = detail)
            }
            state.storageFailed -> Text(stringResource(R.string.profile_storage_failed), style = detail)
            // Mientras carga por primera vez, una línea vacía del mismo alto para que nada salte.
            else -> Text(" ", style = detail)
        }
    }
}

/** PRF-007: por ahora solo este dispositivo. */
@Composable
private fun ThisDevice(name: String, isTablet: Boolean) {
    ProfileRow(onClick = null, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        DeviceIcon(if (isTablet) ProfileIcons.TabletAndroid else ProfileIcons.Smartphone)
        val suffix = stringResource(R.string.profile_this_device)
        Text(
            buildAnnotatedString {
                append(name)
                append(' ')
                withStyle(SpanStyle(color = MarginColors.Muted)) { append(suffix) }
            },
            style = profileText(14, FontWeight.Medium),
            modifier = Modifier.weight(1f),
        )
    }
}

/** PRF-016, PRF-021: apariencia; los interruptores muestran el comportamiento de hoy y avisan al tocarlos. */
@Composable
private fun AppCard(appearance: Appearance, actions: ProfileActions) {
    val onComingSoon = actions.onComingSoon
    ProfileCard {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.profile_appearance), style = profileText(15, FontWeight.Medium))
            SegmentedControl(
                segments = listOf(
                    Segment(stringResource(R.string.profile_appearance_light), ProfileIcons.LightMode),
                    Segment(stringResource(R.string.profile_appearance_dark), ProfileIcons.DarkMode),
                    Segment(stringResource(R.string.profile_appearance_system), ProfileIcons.Contrast),
                ),
                selected = APPEARANCE_ORDER.indexOf(appearance),
                onSelect = { actions.onAppearance(APPEARANCE_ORDER[it]) },
            )
        }
        CardDivider()
        SwitchRow(stringResource(R.string.profile_wifi_only), stringResource(R.string.profile_wifi_only_hint), false, onComingSoon)
        CardDivider()
        SwitchRow(stringResource(R.string.profile_auto_download), stringResource(R.string.profile_auto_download_hint), false, onComingSoon)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onClick: () -> Unit) {
    val state = stringResource(if (checked) R.string.state_on else R.string.state_off)
    ProfileRow(
        onClick = onClick,
        role = Role.Switch,
        modifier = Modifier.semantics(mergeDescendants = true) { stateDescription = state },
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = profileText(15, FontWeight.Medium))
            Text(subtitle, style = profileText(12, color = MarginColors.Muted))
        }
        ProfileSwitch(checked)
    }
}

/** PRF-017: tarjeta tinta con tres preguntas y "Todas las preguntas". */
@Composable
private fun HelpCard(actions: ProfileActions) {
    ProfileCard(background = MarginColors.InkFill) {
        Text(
            stringResource(R.string.profile_faq).uppercase(),
            style = profileText(11, FontWeight.SemiBold, MarginColors.Yellow, 0.08.em),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        )
        FaqRows(Faq.card, actions.onFaq)
        CardDivider(MarginColors.InkLine)
        ProfileRow(onClick = actions.onAllFaq, minHeight = 52.dp) {
            Text(
                stringResource(R.string.profile_faq_all),
                style = profileText(15, FontWeight.SemiBold, MarginColors.Yellow),
                modifier = Modifier.weight(1f),
            )
            RowIcon(ProfileIcons.ArrowForward, MarginColors.Yellow)
        }
    }
}

/** PRF-019: versión a la izquierda, Términos y Privacidad a la derecha. */
@Composable
private fun Footer(version: String, onComingSoon: () -> Unit, modifier: Modifier = Modifier) {
    val style = profileText(11, color = MarginColors.Muted)
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(R.string.profile_version, version), style = style)
        Row {
            Text(stringResource(R.string.profile_terms), style = style, modifier = Modifier.clickable(role = Role.Button, onClick = onComingSoon))
            Text(" · ", style = style)
            Text(stringResource(R.string.profile_privacy), style = style, modifier = Modifier.clickable(role = Role.Button, onClick = onComingSoon))
        }
    }
}

/** Orden de los segmentos de "Apariencia": Claro, Oscuro, Sistema (diseño 09). */
private val APPEARANCE_ORDER = listOf(Appearance.LIGHT, Appearance.DARK, Appearance.SYSTEM)

private val MAX_CONTENT_WIDTH = 640.dp
private const val TABLET_MIN_WIDTH_DP = 600
