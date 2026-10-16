package com.pluk.reader.ui.plans

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pluk.reader.R
import com.pluk.reader.domain.plans.Billing
import com.pluk.reader.domain.plans.PlanCatalog
import com.pluk.reader.domain.plans.PlanCta
import com.pluk.reader.domain.plans.PlanId
import com.pluk.reader.ui.theme.MarginColors

/** Lo que se puede tocar en Planes. Lo que todavía no existe (comprar, restaurar, términos) va a [onComingSoon]. */
data class PlansActions(
    val onClose: () -> Unit = {},
    val onBilling: (Billing) -> Unit = {},
    val onSelect: (PlanId) -> Unit = {},
    val onToggleTable: () -> Unit = {},
    val onComingSoon: () -> Unit = {},
)

/** P01 Planes (PLN-002 a PLN-006, PLN-013): pantalla completa, sin barra inferior, con cruz para volver. */
@Composable
fun PlansScreen(onClose: () -> Unit, viewModel: PlansViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    PlansContent(
        state,
        PlansActions(
            onClose = onClose,
            onBilling = viewModel::onBilling,
            onSelect = viewModel::onSelect,
            onToggleTable = viewModel::onToggleTable,
            onComingSoon = { Toast.makeText(context, R.string.nav_coming_soon, Toast.LENGTH_SHORT).show() },
        ),
    )
}

/** Sin ViewModel, para poder probarla. Tres columnas desde 600 dp de ancho (PLN-005). */
@Composable
fun PlansContent(state: PlansUiState, actions: PlansActions) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MarginColors.Paper)
            .testTag("plans"),
    ) {
        val tablet = maxWidth >= TABLET_MIN_WIDTH
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (tablet) TabletBody(state, actions) else PhoneBody(state, actions)
            }
            if (tablet) TabletPurchaseBar(state.cta, actions) else PhonePurchaseBar(state.cta, actions.onComingSoon)
        }
    }
}

@Composable
private fun PhoneBody(state: PlansUiState, actions: PlansActions) {
    Box(Modifier.padding(start = 10.dp, top = 4.dp)) { CloseButton(actions.onClose, filled = false) }
    state.usage?.let { usage ->
        UsageRow(usage.used, usage.total, usage.percent, usage.fraction, Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp))
    }
    Text(
        stringResource(R.string.plans_title),
        style = planText(38, letterSpacing = (-0.04).em).copy(lineHeight = 1.02.em),
        modifier = Modifier
            .padding(start = 20.dp, end = 20.dp, top = 26.dp)
            .semantics { heading() },
    )
    BillingToggle(state.billing, actions.onBilling, height = 52.dp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp))
    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlanId.entries.forEach { PlanCard(it, state, actions.onSelect, tablet = false) }
    }
    Row(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 16.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .border(1.5.dp, MarginColors.Ink, RoundedCornerShape(26.dp))
            .clickable(role = Role.Button, onClick = actions.onToggleTable)
            .testTag("plans-see-all"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Text(stringResource(R.string.plans_see_all), style = planText(15, FontWeight.SemiBold))
        Icon(if (state.tableOpen) PlanIcons.ExpandLess else PlanIcons.ExpandMore, null, tint = MarginColors.Ink, modifier = Modifier.size(22.dp))
    }
    if (state.tableOpen) ComparisonTable(state.freeCloud, tablet = false, Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
    Reassurance(iconSize = 22.dp, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp))
    LegalLinks(
        actions.onComingSoon,
        linkHeight = 44.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 16.dp),
        arrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    )
}

@Composable
private fun TabletBody(state: PlansUiState, actions: PlansActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CloseButton(actions.onClose, filled = true)
        state.usage?.let { usage ->
            UsageRow(usage.used, usage.total, usage.percent, usage.fraction, Modifier.width(240.dp), gap = 6.dp)
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            stringResource(R.string.plans_title),
            style = planText(52, letterSpacing = (-0.045).em).copy(lineHeight = 1.em, textAlign = TextAlign.Center),
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .semantics { heading() },
        )
        BillingToggle(state.billing, actions.onBilling, height = 48.dp, modifier = Modifier.width(340.dp))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .padding(start = 28.dp, end = 28.dp, top = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PlanId.entries.forEach {
            Box(Modifier.weight(1f).fillMaxHeight()) { PlanCard(it, state, actions.onSelect, tablet = true) }
        }
    }
    Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .clickable(role = Role.Button, onClick = actions.onToggleTable)
                .padding(horizontal = 18.dp)
                .testTag("plans-see-all"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.plans_see_all),
                style = planText(15, FontWeight.SemiBold).copy(textDecoration = TextDecoration.Underline),
            )
            Icon(if (state.tableOpen) PlanIcons.ExpandLess else PlanIcons.ExpandMore, null, tint = MarginColors.Ink, modifier = Modifier.size(22.dp))
        }
    }
    if (state.tableOpen) ComparisonTable(state.freeCloud, tablet = true, Modifier.padding(start = 28.dp, end = 28.dp, bottom = 20.dp))
}

@Composable
private fun CloseButton(onClose: () -> Unit, filled: Boolean) {
    val description = stringResource(R.string.plans_close)
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(MarginColors.Hairline) else Modifier)
            .clickable(role = Role.Button, onClick = onClose)
            .semantics { contentDescription = description }
            .testTag("plans-close"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(PlanIcons.Close, null, tint = MarginColors.Ink, modifier = Modifier.size(if (filled) 24.dp else 26.dp))
    }
}

/** PLN-003: "Usás X de Y", la proporción en monoespaciada y la barra. */
@Composable
private fun UsageRow(used: String, total: String, percent: Int, fraction: Float, modifier: Modifier = Modifier, gap: Dp = 8.dp) {
    val mono = rememberMonoFamily()
    val text = stringResource(R.string.plans_usage, used, total)
    val ratio = stringResource(R.string.plans_usage_percent, percent)
    Column(
        modifier
            .clearAndSetSemantics { contentDescription = "$text, $ratio" }
            .testTag("plans-usage"),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(text, style = planText(14, FontWeight.SemiBold))
            Text(ratio, style = planText(12, FontWeight.Medium, MarginColors.Muted).copy(fontFamily = mono))
        }
        UsageBar(fraction)
    }
}

/** PLN-004, PLN-013: Mensual / Anual; anuncia la opción elegida. */
@Composable
private fun BillingToggle(billing: Billing, onBilling: (Billing) -> Unit, height: Dp, modifier: Modifier = Modifier) {
    val inner = height - 8.dp
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(MarginColors.Hairline)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        listOf(Billing.MONTHLY, Billing.ANNUAL).forEach { option ->
            val selected = option == billing
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(inner / 2))
                    .background(if (selected) MarginColors.InkFill else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onBilling(option) })
                    .testTag("billing-${option.name}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                val label = stringResource(if (option == Billing.ANNUAL) R.string.plans_billing_annual else R.string.plans_billing_monthly)
                Text(
                    label,
                    style = planText(14, if (selected) FontWeight.Bold else FontWeight.SemiBold, if (selected) MarginColors.OnInkFill else MarginColors.Ink),
                )
                if (option == Billing.ANNUAL) {
                    Text(
                        stringResource(R.string.plans_annual_saving, PlanCatalog.ANNUAL_SAVING_PERCENT),
                        style = planText(11, FontWeight.Bold, MarginColors.OnYellow),
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(MarginColors.Yellow)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

/** Colores de una tarjeta: la elegida va en amarillo con borde tinta (PLN-005). */
private data class CardColors(val background: Color, val border: Color, val borderWidth: Dp, val ink: Color, val muted: Color, val rule: Color)

@Composable
private fun cardColors(highlighted: Boolean): CardColors = if (highlighted) {
    CardColors(MarginColors.Yellow, MarginColors.OnYellow, 2.dp, MarginColors.OnYellow, MarginColors.OnYellowMuted, MarginColors.OnYellow.copy(alpha = 0.2f))
} else {
    CardColors(MarginColors.Field, MarginColors.Off, 1.5.dp, MarginColors.Ink, MarginColors.Ink2, MarginColors.Line)
}

/** PLN-005, PLN-013: tarjeta de un plan. Plus y Pro se eligen tocándola; se anuncia nombre, espacio, precio. */
@Composable
private fun PlanCard(plan: PlanId, state: PlansUiState, onSelect: (PlanId) -> Unit, tablet: Boolean) {
    val highlighted = state.isHighlighted(plan)
    val colors = cardColors(highlighted)
    val radius = if (tablet) 28.dp else 24.dp
    val name = planName(plan)
    val info = catalogPlan(plan)
    val cloud = if (plan == PlanId.FREE) state.freeCloud else formatCloud(info.cloudBytes)
    val period = stringResource(if (state.billing == Billing.ANNUAL) R.string.plans_per_year else R.string.plans_per_month)
    val price = info.price(state.billing)
    val description = stringResource(R.string.plans_card_description, name, cloud, price, period)
    val selectable = plan != PlanId.FREE
    val selected = selectable && plan == state.selected
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (tablet) Modifier.fillMaxHeight() else Modifier)
            .clip(RoundedCornerShape(radius))
            .background(colors.background)
            .border(colors.borderWidth, colors.border, RoundedCornerShape(radius))
            .then(
                if (selectable) Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(plan) })
                else Modifier,
            )
            .clearAndSetSemantics { contentDescription = description }
            .padding(if (tablet) 24.dp else 18.dp)
            .testTag("plan-${plan.name}"),
        verticalArrangement = Arrangement.spacedBy(if (tablet) 14.dp else 12.dp),
    ) {
        CardHeader(plan, name, state, selected, colors.ink, tablet)
        Text(
            stringResource(planTagline(plan)),
            style = planText(if (tablet) 15 else 14, color = colors.muted),
            modifier = Modifier.offsetUp(if (tablet) 10.dp else 8.dp),
        )
        CloudAmount(cloud, colors.ink, tablet)
        Column {
            planIncludes(plan).forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(PlanIcons.Check, null, tint = colors.ink, modifier = Modifier.size(if (tablet) 19.dp else 18.dp))
                    Text(item, style = planText(if (tablet) 15 else 14, FontWeight.Medium, colors.ink).copy(lineHeight = 1.35.em))
                }
            }
        }
        if (tablet) Spacer(Modifier.weight(1f))
        // PLN-005: las tres tarjetas cierran igual: separador, precio grande con el período y una línea más chica.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            HorizontalDivider(thickness = 1.dp, color = colors.rule)
            Row(
                Modifier.padding(top = if (tablet) 10.dp else 6.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(price, style = planText(if (tablet) 28 else 26, FontWeight.Bold, colors.ink, (-0.02).em))
                Text(period, style = planText(if (tablet) 15 else 14, FontWeight.SemiBold, colors.ink), modifier = Modifier.padding(bottom = 3.dp))
            }
            Text(
                when {
                    plan == PlanId.FREE -> stringResource(R.string.plans_free_forever)
                    state.billing == Billing.ANNUAL -> stringResource(R.string.plans_annual_equivalent, info.annualPerMonth)
                    else -> stringResource(R.string.plans_monthly_note)
                },
                style = planText(if (tablet) 13 else 12, FontWeight.Medium, if (highlighted) colors.muted else MarginColors.Muted),
            )
        }
    }
}

/** El diseño sube la bajada pegándola al nombre (margin-top negativo). */
private fun Modifier.offsetUp(by: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = by.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}

@Composable
private fun CardHeader(plan: PlanId, name: String, state: PlansUiState, selected: Boolean, ink: Color, tablet: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = planText(if (tablet) 24 else 20, FontWeight.Bold, ink, (-0.02).em))
            // PLN-005, PLN-017: en un plan pago de la cuenta, "Tu plan" junto al nombre (en Plus, en lugar de "Más elegido").
            if (plan != PlanId.FREE && plan == state.accountPlan) {
                YourPlanChip(stringResource(R.string.plans_your_plan), large = tablet, withCheck = false, color = ink)
            } else if (plan == PlanId.PLUS) {
                Text(
                    stringResource(R.string.plans_most_chosen),
                    style = planText(if (tablet) 12 else 11, FontWeight.Bold, MarginColors.Yellow),
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        // Sobre amarillo, tinta fija; sobre la tarjeta normal, el relleno tinta de la paleta (se ve en oscuro).
                        .background(if (selected) MarginColors.OnYellow else MarginColors.InkFill)
                        .padding(horizontal = if (tablet) 9.dp else 8.dp, vertical = 4.dp),
                )
            }
        }
        when {
            plan == PlanId.FREE -> if (state.accountPlan == PlanId.FREE) YourPlanChip(stringResource(R.string.plans_your_plan), large = tablet)
            else -> Icon(
                if (selected) PlanIcons.RadioChecked else PlanIcons.RadioUnchecked,
                null,
                tint = ink,
                modifier = Modifier.size(if (tablet) 28.dp else 26.dp),
            )
        }
    }
}

/** "15" grande y "MB en la nube" al lado, sobre la misma línea de base. */
@Composable
private fun CloudAmount(cloud: String, ink: Color, tablet: Boolean) {
    val number = cloud.substringBeforeLast(' ')
    val unit = cloud.substringAfterLast(' ')
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            number,
            style = planText(if (tablet) 64 else 48, FontWeight.Light, ink, if (tablet) (-0.045).em else (-0.04).em).copy(lineHeight = 1.em),
            modifier = Modifier.alignByBaseline(),
        )
        Text(stringResource(R.string.plans_in_cloud, unit), style = planText(if (tablet) 15 else 14, FontWeight.SemiBold, ink), modifier = Modifier.alignByBaseline())
    }
}

/** PLN-006, PLN-013: tabla comparativa; cada fila se lee entera. */
@Composable
private fun ComparisonTable(freeCloud: String, tablet: Boolean, modifier: Modifier = Modifier) {
    val widths = if (tablet) listOf(160.dp, 160.dp, 160.dp) else listOf(54.dp, 70.dp, 62.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (tablet) 24.dp else 20.dp))
            .background(MarginColors.Field)
            .padding(start = if (tablet) 24.dp else 14.dp, end = if (tablet) 24.dp else 14.dp, top = if (tablet) 8.dp else 6.dp, bottom = if (tablet) 12.dp else 8.dp)
            .testTag("plans-table"),
    ) {
        val header = planText(if (tablet) 12 else 11, FontWeight.Bold, MarginColors.Muted, if (tablet) 0.06.em else 0.04.em)
        Row(
            Modifier
                .height(if (tablet) 44.dp else 40.dp)
                .clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            listOf(
                stringResource(R.string.plans_free) to MarginColors.Muted,
                stringResource(R.string.plans_plus) to MarginColors.Ink,
                stringResource(if (tablet) R.string.plans_pro else R.string.plans_pro_short) to MarginColors.Muted,
            ).forEachIndexed { i, (text, color) ->
                Text(text.uppercase(), style = header.copy(color = color, textAlign = TextAlign.Center), modifier = Modifier.width(widths[i]))
            }
        }
        comparisonRows(freeCloud, catalogPlans()).forEach { row ->
            HorizontalDivider(thickness = 1.dp, color = MarginColors.Hairline)
            val feature = stringResource(row.feature)
            val values = row.values.map { it.text() }
            val spoken = row.values.map { it.spoken() }
            val description = stringResource(R.string.plans_row_description, feature, spoken[0], spoken[1], spoken[2])
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clearAndSetSemantics { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val size = if (tablet) 14 else 13
                Text(
                    feature,
                    style = planText(size, FontWeight.Medium).copy(lineHeight = 1.3.em),
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp),
                )
                values.forEachIndexed { i, value ->
                    Text(
                        value,
                        style = planText(size, if (i == 1) FontWeight.Bold else FontWeight.Medium, if (i == 1) MarginColors.Ink else MarginColors.Ink2)
                            .copy(textAlign = TextAlign.Center, lineHeight = 1.3.em),
                        modifier = Modifier.width(widths[i]),
                    )
                }
            }
        }
    }
}

/** PLN-006: "Si cancelás, no perdés nada…", en serif itálica. */
@Composable
private fun Reassurance(iconSize: Dp, modifier: Modifier = Modifier) {
    val serif = rememberSerifItalicFamily()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (iconSize > 20.dp) 10.dp else 8.dp), verticalAlignment = Alignment.Top) {
        Icon(PlanIcons.VerifiedUser, null, tint = MarginColors.Ink, modifier = Modifier.size(iconSize))
        Text(
            stringResource(R.string.plans_reassurance),
            style = planText(17).copy(fontFamily = serif, lineHeight = 1.35.em),
        )
    }
}

/** PLN-006: "Restaurar compras · Términos · Privacidad"; todos avisan (PRF-003). */
@Composable
private fun LegalLinks(onComingSoon: () -> Unit, linkHeight: Dp, modifier: Modifier, arrangement: Arrangement.Horizontal) {
    val style = planText(13, FontWeight.Medium, MarginColors.Ink2)
    Row(modifier, horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically) {
        listOf(R.string.plans_restore, R.string.profile_terms, R.string.profile_privacy).forEachIndexed { i, label ->
            if (i > 0) Text("·", style = style, modifier = Modifier.clearAndSetSemantics {})
            Box(
                Modifier
                    .height(linkHeight)
                    .clickable(role = Role.Button, onClick = onComingSoon)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(label), style = style.copy(textDecoration = TextDecoration.Underline))
            }
        }
    }
}

@Composable
private fun ctaLabel(cta: PlanCta): String {
    val period = stringResource(if (cta.billing == Billing.ANNUAL) R.string.plans_per_year else R.string.plans_per_month)
    return stringResource(R.string.plans_cta_choose, planName(cta.plan.id), cta.price, period)
}

@Composable
private fun ctaNote(cta: PlanCta): String =
    stringResource(if (cta.billing == Billing.ANNUAL) R.string.plans_cta_note_year else R.string.plans_cta_note_month)

/** PLN-006: barra de compra fija al pie. Avisa (PRF-003). */
@Composable
private fun PhonePurchaseBar(cta: PlanCta, onBuy: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MarginColors.Paper)) {
        HorizontalDivider(thickness = 1.dp, color = MarginColors.Off)
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryPlanButton(ctaLabel(cta), onBuy, Modifier.testTag("plans-cta"))
            Text(
                ctaNote(cta),
                style = planText(13, FontWeight.Medium, MarginColors.Ink2).copy(textAlign = TextAlign.Center, lineHeight = 1.35.em),
                modifier = Modifier.fillMaxWidth().testTag("plans-cta-note"),
            )
        }
    }
}

@Composable
private fun TabletPurchaseBar(cta: PlanCta, actions: PlansActions) {
    Column(Modifier.fillMaxWidth().background(MarginColors.Paper)) {
        HorizontalDivider(thickness = 1.dp, color = MarginColors.Off)
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Reassurance(iconSize = 20.dp)
                LegalLinks(actions.onComingSoon, linkHeight = 32.dp, modifier = Modifier.padding(start = 0.dp), arrangement = Arrangement.spacedBy(2.dp))
            }
            Text(
                ctaNote(cta),
                style = planText(14, FontWeight.Medium, MarginColors.Ink2).copy(textAlign = TextAlign.End, lineHeight = 1.35.em),
                modifier = Modifier.widthIn(max = 260.dp).testTag("plans-cta-note"),
            )
            Box(
                Modifier
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MarginColors.InkFill)
                    .clickable(role = Role.Button, onClick = actions.onComingSoon)
                    .padding(horizontal = 28.dp)
                    .testTag("plans-cta"),
                contentAlignment = Alignment.Center,
            ) {
                Text(ctaLabel(cta), style = planText(16, FontWeight.Bold, MarginColors.Yellow), maxLines = 1)
            }
        }
    }
}

@Composable
internal fun planName(plan: PlanId): String = stringResource(
    when (plan) {
        PlanId.FREE -> R.string.plans_free
        PlanId.PLUS -> R.string.plans_plus
        PlanId.PRO -> R.string.plans_pro
    },
)

private fun planTagline(plan: PlanId): Int = when (plan) {
    PlanId.FREE -> R.string.plans_free_tagline
    PlanId.PLUS -> R.string.plans_plus_tagline
    PlanId.PRO -> R.string.plans_pro_tagline
}

/** Lo que incluye cada tarjeta (PLN-001). Solo funciones hechas o en camino (PLN-016). */
@Composable
private fun planIncludes(plan: PlanId): List<String> {
    val maxFile = stringResource(R.string.plans_includes_max_file, formatCloud(catalogPlan(plan).maxFileBytes))
    return when (plan) {
        PlanId.FREE -> listOf(stringResource(R.string.plans_free_includes_1), stringResource(R.string.plans_free_includes_2), maxFile)
        PlanId.PLUS -> listOf(stringResource(R.string.plans_plus_includes_1), stringResource(R.string.plans_plus_includes_2), maxFile)
        PlanId.PRO -> listOf(stringResource(R.string.plans_pro_includes_1), maxFile)
    }
}

/** Valor de una celda de la tabla. */
private sealed interface Cell {
    data class Plain(val value: String) : Cell
    data class Res(val id: Int) : Cell
    data object Yes : Cell
    data object No : Cell
}

@Composable
private fun Cell.text(): String = when (this) {
    is Cell.Plain -> value
    is Cell.Res -> stringResource(id)
    Cell.Yes -> "✓"
    Cell.No -> "—"
}

@Composable
private fun Cell.spoken(): String = when (this) {
    Cell.Yes -> stringResource(R.string.plans_value_yes)
    Cell.No -> stringResource(R.string.plans_value_no)
    else -> text()
}

private data class ComparisonRow(val feature: Int, val values: List<Cell>)

/** Filas de la tabla (PLN-001); sin funciones en definición (PLN-016). */
private fun comparisonRows(freeCloud: String, plans: List<com.pluk.reader.domain.plans.Plan>): List<ComparisonRow> {
    fun plan(id: PlanId) = plans.first { it.id == id }
    val unlimited = Cell.Res(R.string.plans_value_unlimited)
    fun cloud(plan: com.pluk.reader.domain.plans.Plan) = Cell.Plain(formatCloud(plan.cloudBytes))
    fun file(plan: com.pluk.reader.domain.plans.Plan) = Cell.Plain(formatCloud(plan.maxFileBytes))
    return listOf(
        ComparisonRow(R.string.plans_row_cloud, listOf(Cell.Plain(freeCloud), cloud(plan(PlanId.PLUS)), cloud(plan(PlanId.PRO)))),
        ComparisonRow(R.string.plans_row_file_size, listOf(file(plan(PlanId.FREE)), file(plan(PlanId.PLUS)), file(plan(PlanId.PRO)))),
        ComparisonRow(R.string.plans_row_reading, listOf(unlimited, unlimited, unlimited)),
        ComparisonRow(R.string.plans_row_sync, listOf(Cell.Yes, Cell.Yes, Cell.Yes)),
        ComparisonRow(R.string.plans_row_themes, listOf(Cell.No, Cell.Yes, Cell.Yes)),
        ComparisonRow(R.string.plans_row_export, listOf(Cell.No, Cell.Yes, Cell.Yes)),
    )
}

private val TABLET_MIN_WIDTH = 600.dp
