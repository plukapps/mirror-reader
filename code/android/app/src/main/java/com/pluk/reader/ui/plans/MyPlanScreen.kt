package com.pluk.reader.ui.plans

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pluk.reader.R
import com.pluk.reader.domain.plans.PlanCatalog
import com.pluk.reader.domain.plans.PlanId
import com.pluk.reader.ui.theme.MarginColors

/** PLN-012 (P04 Gratis): Mi plan, sin barra inferior, con "Perfil" para volver. */
@Composable
fun MyPlanScreen(onBack: () -> Unit, onChangePlan: () -> Unit, viewModel: MyPlanViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    MyPlanContent(
        state,
        onBack = onBack,
        onChangePlan = onChangePlan,
        onRestore = { Toast.makeText(context, R.string.nav_coming_soon, Toast.LENGTH_SHORT).show() },
    )
}

/** Sin ViewModel, para poder probarla. */
@Composable
fun MyPlanContent(state: MyPlanUiState, onBack: () -> Unit, onChangePlan: () -> Unit, onRestore: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MarginColors.Paper)
            .testTag("my-plan"),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = MAX_CONTENT_WIDTH)
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                BackRow(onBack)
                Text(
                    stringResource(R.string.plans_my_plan),
                    style = planText(40, letterSpacing = (-0.045).em).copy(lineHeight = 1.em),
                    modifier = Modifier
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp)
                        .semantics { heading() },
                )
                PlanCard(state)
                val reader = formatCloud(PlanCatalog.plus.cloudBytes ?: 0)
                Column(
                    Modifier
                        .padding(start = 20.dp, end = 20.dp, top = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MarginColors.Yellow)
                        .semantics(mergeDescendants = true) {}
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.plans_with_reader, reader), style = planText(15, FontWeight.Bold, MarginColors.OnYellow))
                    Text(
                        stringResource(R.string.plans_my_plan_upsell, formatCloud(PlanCatalog.plus.maxFileBytes)),
                        style = planText(13, FontWeight.Medium, MarginColors.OnYellowMuted).copy(lineHeight = 1.4.em),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PrimaryPlanButton(stringResource(R.string.plans_change_plan), onChangePlan, Modifier.testTag("my-plan-change"), icon = PlanIcons.ArrowForward)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(role = Role.Button, onClick = onRestore),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.plans_restore),
                        style = planText(14, FontWeight.SemiBold).copy(textDecoration = TextDecoration.Underline),
                    )
                }
            }
        }
    }
}

@Composable
private fun BackRow(onBack: () -> Unit) {
    val label = stringResource(R.string.plans_back_profile)
    Row(
        Modifier
            .padding(start = 10.dp, top = 4.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onBack)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
            }
            .padding(end = 12.dp)
            .testTag("my-plan-back"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(PlanIcons.ArrowBack, null, tint = MarginColors.Ink, modifier = Modifier.size(24.dp))
        }
        Text(label, style = planText(14, FontWeight.SemiBold, MarginColors.Muted))
    }
}

/** Tarjeta del plan: "Gratis" con "Tu plan", espacio con barra y dispositivos. */
@Composable
private fun PlanCard(state: MyPlanUiState) {
    val mono = rememberMonoFamily()
    Column(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MarginColors.Field)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(planName(PlanId.FREE), style = planText(22, FontWeight.Bold))
            YourPlanChip(stringResource(R.string.plans_your_plan), withCheck = false)
        }
        val usage = state.usage
        val label = stringResource(R.string.plans_row_cloud)
        val value = usage?.let { stringResource(R.string.plans_my_plan_usage, it.used, it.total) } ?: "—"
        Column(
            Modifier
                .clearAndSetSemantics { contentDescription = "$label, $value" }
                .testTag("my-plan-usage"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = planText(14, FontWeight.SemiBold))
                Text(value, style = planText(13, FontWeight.Medium).copy(fontFamily = mono))
            }
            UsageBar(usage?.fraction ?: 0f)
        }
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.plans_devices), style = planText(14, FontWeight.Medium, MarginColors.Ink2))
            Text(stringResource(R.string.plans_devices_value), style = planText(14, FontWeight.Medium, MarginColors.Ink2))
        }
        // PLN-012, PLN-014: tamaño máximo por archivo del plan.
        Text(
            stringResource(R.string.plans_includes_max_file, formatCloud(PlanCatalog.free.maxFileBytes)),
            style = planText(14, FontWeight.Medium, MarginColors.Ink2),
        )
    }
}

private val MAX_CONTENT_WIDTH = 640.dp
