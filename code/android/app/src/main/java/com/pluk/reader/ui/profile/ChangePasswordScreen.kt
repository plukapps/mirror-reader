package com.pluk.reader.ui.profile

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pluk.reader.R
import com.pluk.reader.domain.account.AuthError
import com.pluk.reader.ui.onboarding.OnbTextField
import com.pluk.reader.ui.onboarding.OnboardingPage
import com.pluk.reader.ui.onboarding.PrimaryButton
import com.pluk.reader.ui.onboarding.RuleRow
import com.pluk.reader.ui.onboarding.StepHeader
import com.pluk.reader.ui.onboarding.StrengthMeter
import com.pluk.reader.ui.onboarding.label
import com.pluk.reader.ui.onboarding.message
import com.pluk.reader.ui.onboarding.onbText
import com.pluk.reader.ui.theme.MarginColors

/** PRF-020: cambiar contraseña. Al guardar bien vuelve a Perfil con el aviso; atrás descarta. */
@Composable
fun ChangePasswordScreen(onBack: () -> Unit, viewModel: ChangePasswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.done) {
        if (state.done) {
            Toast.makeText(context, R.string.profile_password_updated, Toast.LENGTH_SHORT).show()
            onBack()
        }
    }
    ChangePasswordContent(
        state = state,
        onBack = onBack,
        onCurrent = viewModel::onCurrent,
        onNew = viewModel::onNew,
        onConfirm = viewModel::onConfirm,
        onToggleCurrent = viewModel::toggleCurrentVisible,
        onToggleNew = viewModel::toggleNewVisible,
        onSave = viewModel::save,
    )
}

/** Sin ViewModel, para poder probarla. */
@Composable
fun ChangePasswordContent(
    state: ChangePasswordViewModel.UiState,
    onBack: () -> Unit,
    onCurrent: (String) -> Unit,
    onNew: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onToggleCurrent: () -> Unit,
    onToggleNew: () -> Unit,
    onSave: () -> Unit,
) {
    OnboardingPage(
        background = MarginColors.Paper,
        bottom = {
            PrimaryButton(
                stringResource(R.string.profile_password_save),
                onSave,
                Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp).testTag("password-save"),
                enabled = state.canSave,
                busy = state.busy,
                trailing = null,
            )
        },
    ) {
        StepHeader(onBack)
        Text(
            stringResource(R.string.profile_change_password),
            style = profileText(22, FontWeight.Bold, letterSpacing = (-0.02).em).copy(lineHeight = 1.2.em),
            modifier = Modifier
                .padding(start = 24.dp, end = 24.dp, top = 12.dp)
                .fillMaxWidth()
                .semantics { heading() },
        )
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnbTextField(
                stringResource(R.string.profile_password_current),
                state.current,
                onCurrent,
                password = true,
                passwordVisible = state.currentVisible,
                onTogglePasswordVisible = onToggleCurrent,
                error = if (state.currentWrong) stringResource(R.string.profile_password_wrong) else null,
                testTag = "password-current",
            )
            OnbTextField(
                stringResource(R.string.onb_new_password),
                state.new,
                onNew,
                password = true,
                passwordVisible = state.newVisible,
                onTogglePasswordVisible = onToggleNew,
                error = when {
                    state.sameAsCurrent -> stringResource(R.string.profile_password_same)
                    state.error == AuthError.WeakPassword -> state.error.message()
                    else -> null
                },
                testTag = "password-new",
            )
            val check = state.newCheck
            Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.new.isNotEmpty()) StrengthMeter(check.score, check.strength.label())
                RuleRow(stringResource(R.string.onb_rule_length), check.longEnough)
                RuleRow(stringResource(R.string.onb_rule_number), check.hasNumber)
                RuleRow(stringResource(R.string.onb_rule_symbol), check.hasSymbol)
                RuleRow(stringResource(R.string.onb_rule_upper), check.hasUppercase)
                if (state.new.isNotEmpty() && !check.isValid) {
                    Text(stringResource(R.string.onb_rules_needed), style = onbText(12.sp, color = MarginColors.Muted))
                }
            }
            OnbTextField(
                stringResource(R.string.onb_confirm_password),
                state.confirm,
                onConfirm,
                placeholder = stringResource(R.string.onb_repeat),
                password = true,
                passwordVisible = state.newVisible,
                onTogglePasswordVisible = onToggleNew,
                imeAction = ImeAction.Done,
                onImeAction = onSave,
                error = when {
                    state.mismatch -> stringResource(R.string.onb_mismatch)
                    state.error != null && state.error != AuthError.WeakPassword -> state.error.message()
                    else -> null
                },
                testTag = "password-confirm",
            )
        }
    }
}
