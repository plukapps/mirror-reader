package com.pluk.reader.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pluk.reader.R
import com.pluk.reader.domain.account.AuthError
import com.pluk.reader.ui.theme.HostGrotesk
import com.pluk.reader.ui.theme.MarginColors

/** Colores del onboarding que no están en [MarginColors] (diseño O1 a O9). */
internal object OnbColors {
    val Line = Color(0xFFDAD6CA)
    val Soft = Color(0xFF3A3830)
    val SoftOnYellow = Color(0xFF3A3115)
    val Error = Color(0xFF8B1E1E)
    val Caption = Color(0xFFA8A496)
}

/** En tablet el contenido no se estira (ONB-022). */
private val MaxContentWidth = 560.dp

internal fun onbText(size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = MarginColors.Ink, letterSpacing: TextUnit = 0.sp, lineHeight: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = HostGrotesk, fontSize = size, fontWeight = weight, color = color, letterSpacing = letterSpacing, lineHeight = lineHeight)

/**
 * Pantalla del onboarding: fondo, barras del sistema y teclado respetados, columna de ancho máximo y desplazable
 * si no entra (ONB-022). [top] va arriba y [bottom] queda pegado abajo cuando sobra lugar.
 */
@Composable
internal fun OnboardingPage(
    background: Color,
    modifier: Modifier = Modifier,
    bottom: @Composable ColumnScope.() -> Unit = {},
    top: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(background)
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(content = top)
            Column(Modifier.padding(top = 24.dp), content = bottom)
        }
    }
}

/** Logo y "margin.", anunciados juntos (WEL-007). */
@Composable
internal fun Brand(modifier: Modifier = Modifier) {
    val name = stringResource(R.string.welcome_brand)
    Row(modifier.clearAndSetSemantics { contentDescription = name }, verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_margin_logo), contentDescription = null, modifier = Modifier.size(width = 30.dp, height = 24.dp))
        Text(name, style = onbText(20.sp, FontWeight.Medium, letterSpacing = (-0.03).em), modifier = Modifier.padding(start = 10.dp))
    }
}

/** Título grande de cada paso (34 sp, O7 44 sp). */
@Composable
internal fun OnbTitle(text: String, modifier: Modifier = Modifier, size: TextUnit = 34.sp, padding: Dp = 20.dp) {
    Text(
        text,
        style = onbText(size, letterSpacing = (-0.04).em, lineHeight = 1.02.em),
        modifier = modifier.padding(horizontal = padding).semantics { heading() },
    )
}

@Composable
internal fun OnbBody(text: String, modifier: Modifier = Modifier) {
    Text(text, style = onbText(14.sp, color = OnbColors.Soft, lineHeight = 1.45.em), modifier = modifier.padding(horizontal = 20.dp))
}

/** Flecha atrás (o cruz) a la izquierda y "Paso N de 4" u "Omitir" a la derecha (ONB-003). */
@Composable
internal fun StepHeader(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backIcon: ImageVector = OnboardingIcons.ArrowBack,
    backLabel: String = stringResource(R.string.onb_back),
    stepLabel: String? = null,
    onSkip: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Sin [onBack] (primer paso de la pila) queda el lugar vacío.
        if (onBack == null) {
            Spacer(Modifier.size(44.dp))
        } else {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = backLabel, role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = backLabel }
                    .testTag("onb-back"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(backIcon, contentDescription = null, tint = MarginColors.Ink, modifier = Modifier.size(24.dp))
            }
        }
        when {
            onSkip != null -> Text(
                stringResource(R.string.onb_skip),
                style = onbText(14.sp, FontWeight.SemiBold).copy(textDecoration = TextDecoration.Underline),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onSkip)
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .testTag("onb-skip"),
            )
            stepLabel != null -> Text(stepLabel, style = onbText(12.sp, FontWeight.SemiBold, MarginColors.Muted), modifier = Modifier.padding(end = 10.dp))
        }
    }
}

/** Barra de cuatro tramos (ONB-003). */
@Composable
internal fun StepBar(step: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp).clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(STEPS) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < step) MarginColors.Ink else OnbColors.Line),
            )
        }
    }
}

internal const val STEPS = 4

/** Píldora tinta con texto amarillo (y flecha). Mientras [busy], muestra que trabaja y no responde (ONB-021). */
@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    leading: ImageVector? = null,
    trailing: ImageVector? = OnboardingIcons.ArrowForward,
) {
    val active = enabled && !busy
    val background = if (enabled || busy) MarginColors.Ink else OnbColors.Line
    val content = if (enabled || busy) MarginColors.Yellow else MarginColors.Muted
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(background)
            .clickable(enabled = active, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { if (!active) disabled() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            return@Row
        }
        if (leading != null) Icon(leading, contentDescription = null, tint = content, modifier = Modifier.padding(end = 8.dp).size(20.dp))
        Text(text, style = onbText(16.sp, FontWeight.Bold, content))
        if (trailing != null) Icon(trailing, contentDescription = null, tint = content, modifier = Modifier.padding(start = 8.dp).size(20.dp))
    }
}

/** Píldora con borde. */
@Composable
internal fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    enabled: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .border(BorderStroke(1.5.dp, MarginColors.Ink), RoundedCornerShape(height / 2))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = onbText(if (height < 56.dp) 14.sp else 16.sp, FontWeight.SemiBold))
    }
}

/** Botón de Google con el círculo "G" del diseño (ONB-004, ONB-014). */
@Composable
internal fun GoogleButton(onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    SecondaryButton(
        text = stringResource(R.string.onb_google),
        onClick = onClick,
        enabled = enabled,
        height = 48.dp,
        modifier = modifier.testTag("onb-google"),
        leading = {
            Box(Modifier.size(20.dp).clip(CircleShape).background(MarginColors.Ink), contentAlignment = Alignment.Center) {
                Text("G", style = onbText(11.sp, FontWeight.Bold, MarginColors.Field))
            }
        },
    )
}

/** Línea, texto y línea ("o con email", "o"). */
@Composable
internal fun OrDivider(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(OnbColors.Line))
        Text(text, style = onbText(12.sp, FontWeight.Medium, MarginColors.Muted), modifier = Modifier.padding(horizontal = 10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(OnbColors.Line))
    }
}

/**
 * Campo con su rótulo arriba (diseño O2, O7, O8, O9). Borde de 2 dp tinta con foco y rojo con [error];
 * las contraseñas tienen el botón de ver u ocultar (ONB-005).
 */
@Composable
internal fun OnbTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    password: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePasswordVisible: () -> Unit = {},
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = when {
        error != null -> OnbColors.Error
        focused -> MarginColors.Ink
        else -> OnbColors.Line
    }
    val borderWidth = if (error != null || focused) 2.dp else 1.5.dp
    val hidden = password && !passwordVisible
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = onbText(12.sp, FontWeight.SemiBold, if (error != null) OnbColors.Error else MarginColors.Ink))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            interactionSource = interaction,
            textStyle = onbText(15.sp, if (hidden) FontWeight.Bold else FontWeight.Medium, letterSpacing = if (hidden) 0.2.em else 0.sp),
            cursorBrush = SolidColor(MarginColors.Ink),
            visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
                autoCorrectEnabled = !password && keyboardType == KeyboardType.Text,
            ),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = label
                    if (error != null) error(error)
                }
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MarginColors.Field)
                        .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
                        .padding(start = 16.dp, end = if (password) 8.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) Text(placeholder, style = onbText(15.sp, FontWeight.Medium, MarginColors.Muted))
                        inner()
                    }
                    if (password) {
                        val toggleLabel = stringResource(if (passwordVisible) R.string.onb_hide_password else R.string.onb_show_password)
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable(onClickLabel = toggleLabel, role = Role.Button, onClick = onTogglePasswordVisible)
                                .semantics { contentDescription = toggleLabel },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (passwordVisible) OnboardingIcons.VisibilityOff else OnboardingIcons.Visibility,
                                contentDescription = null,
                                tint = MarginColors.Muted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            },
        )
        if (error != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(OnboardingIcons.Error, contentDescription = null, tint = OnbColors.Error, modifier = Modifier.size(16.dp))
                Text(error, style = onbText(12.sp, FontWeight.Medium, OnbColors.Error))
            }
        }
    }
}

/** Medidor de cuatro tramos con la etiqueta (ONB-005). */
@Composable
internal fun StrengthMeter(score: Int, label: String, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onb_password_strength, label)
    Row(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(4) { index ->
                Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (index < score) MarginColors.Ink else OnbColors.Line))
            }
        }
        Text(label, style = onbText(11.sp, FontWeight.SemiBold), modifier = Modifier.padding(start = 8.dp))
    }
}

/** Una regla de la contraseña con su círculo marcado o vacío (O9, ONB-018). */
@Composable
internal fun RuleRow(text: String, met: Boolean) {
    val state = stringResource(if (met) R.string.onb_rule_met else R.string.onb_rule_pending)
    Row(
        Modifier.clearAndSetSemantics { contentDescription = "$text, $state" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (met) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(MarginColors.Ink), contentAlignment = Alignment.Center) {
                Icon(OnboardingIcons.Check, contentDescription = null, tint = MarginColors.Yellow, modifier = Modifier.size(14.dp))
            }
        } else {
            Box(Modifier.size(18.dp).border(1.5.dp, MarginColors.Muted, CircleShape))
        }
        Text(text, style = onbText(13.sp, FontWeight.Medium, if (met) MarginColors.Ink else MarginColors.Muted))
    }
}

/** Ícono sobre un cuadrado amarillo (O3, O8). */
@Composable
internal fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(modifier.padding(horizontal = 20.dp).size(56.dp).clip(RoundedCornerShape(16.dp)).background(MarginColors.Yellow), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = MarginColors.Ink, modifier = Modifier.size(28.dp))
    }
}

/** Casilla tinta con ✓ amarillo y su texto: todo es un solo control para TalkBack (ONB-004). */
@Composable
internal fun TermsCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, text: @Composable () -> Unit) {
    val label = stringResource(R.string.onb_terms_accept)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            // toggleable anuncia "marcada" o "no marcada" (ONB-022).
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .testTag("onb-terms"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .then(if (checked) Modifier.background(MarginColors.Ink) else Modifier.border(1.5.dp, MarginColors.Ink, RoundedCornerShape(6.dp))),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(OnboardingIcons.Check, contentDescription = null, tint = MarginColors.Yellow, modifier = Modifier.size(16.dp))
        }
        Box(Modifier.weight(1f)) { text() }
    }
}

/** Fila de texto con un enlace subrayado al final ("¿Nuevo en margin.? Crear cuenta"). */
@Composable
internal fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: TextUnit = 14.sp, color: Color = MarginColors.Ink) {
    Text(
        text,
        style = onbText(size, FontWeight.SemiBold, color).copy(textDecoration = TextDecoration.Underline, textAlign = TextAlign.Center),
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
    )
}

/** Aviso en tinta abajo de la pantalla ("Enlace enviado", O8). */
@Composable
internal fun ConfirmationToast(title: String, body: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MarginColors.Ink)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(MarginColors.Yellow), contentAlignment = Alignment.Center) {
            Icon(OnboardingIcons.Check, contentDescription = null, tint = MarginColors.Ink, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = onbText(14.sp, FontWeight.SemiBold, MarginColors.Field))
            Text(body, style = onbText(12.sp, color = OnbColors.Caption))
        }
    }
}

/** Texto de cada [AuthError] (ONB-015). */
@Composable
internal fun AuthError.message(): String = stringResource(
    when (this) {
        AuthError.InvalidCredentials -> R.string.onb_error_credentials
        AuthError.EmailInUse -> R.string.onb_error_email_in_use
        AuthError.UserDisabled -> R.string.onb_error_disabled
        AuthError.TooManyRequests -> R.string.onb_error_too_many
        AuthError.Network -> R.string.onb_error_network
        AuthError.InvalidLink -> R.string.onb_error_link
        AuthError.WeakPassword -> R.string.onb_error_weak
        AuthError.Unknown -> R.string.onb_error_unknown
    },
)

/** "0:42" */
internal fun formatSeconds(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** Botón accesible sin fondo (por ejemplo "Abrir email"), con un ícono. */
@Composable
internal fun IconTextButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MarginColors.Ink) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, style = onbText(13.sp, FontWeight.SemiBold, color))
    }
}

/**
 * Texto con "Términos" y "Política de privacidad" subrayados (ONB-001, ONB-004). Son enlaces solo si sus URL
 * están configuradas (CMP-002, CMP-003).
 */
@Composable
internal fun TermsText(format: Int, style: TextStyle, modifier: Modifier = Modifier) {
    val terms = stringResource(R.string.onb_terms)
    val privacy = stringResource(R.string.onb_privacy)
    val termsUrl = stringResource(R.string.onb_terms_url)
    val privacyUrl = stringResource(R.string.onb_privacy_url)
    val template = stringResource(format, TERMS_MARK, PRIVACY_MARK)
    val linkStyle = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline, color = MarginColors.Ink))
    val text = buildAnnotatedString {
        var rest = template
        while (rest.isNotEmpty()) {
            val t = rest.indexOf(TERMS_MARK).takeIf { it >= 0 } ?: Int.MAX_VALUE
            val p = rest.indexOf(PRIVACY_MARK).takeIf { it >= 0 } ?: Int.MAX_VALUE
            val next = minOf(t, p)
            if (next == Int.MAX_VALUE) {
                append(rest)
                break
            }
            append(rest.substring(0, next))
            val (label, url, mark) = if (next == t) Triple(terms, termsUrl, TERMS_MARK) else Triple(privacy, privacyUrl, PRIVACY_MARK)
            if (url.isNotBlank()) {
                withLink(LinkAnnotation.Url(url, linkStyle)) { append(label) }
            } else {
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = MarginColors.Ink)) { append(label) }
            }
            rest = rest.substring(next + mark.length)
        }
    }
    Text(text, style = style, modifier = modifier)
}

private const val TERMS_MARK = "\u0001"
private const val PRIVACY_MARK = "\u0002"
