package com.pluk.reader.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pluk.reader.R
import com.pluk.reader.ui.theme.MarginColors

/** Iniciar sesión, O7 (ONB-014 a ONB-016). */
@Composable
fun SignInScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onVerifyEmail: () -> Unit,
    onForgot: (email: String) -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.next) {
        when (state.next) {
            SignInViewModel.Next.Home -> onHome()
            SignInViewModel.Next.VerifyEmail -> onVerifyEmail()
            null -> return@LaunchedEffect
        }
        viewModel.onNavigated()
    }
    val google = rememberGoogleSignIn(viewModel::onGoogleToken, viewModel::onGoogleFailed)
    OnboardingPage(
        background = MarginColors.Paper,
        bottom = {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.onb_new_here), style = onbText(13.sp, color = OnbColors.Soft))
                TextLink(stringResource(R.string.onb_create_account), onCreateAccount, size = 13.sp)
            }
        },
    ) {
        StepHeader(onBack)
        OnbTitle(stringResource(R.string.onb_signin_title), Modifier.padding(top = 20.dp), size = 44.sp)
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnbTextField(
                stringResource(R.string.onb_email),
                state.email,
                viewModel::onEmail,
                keyboardType = KeyboardType.Email,
                testTag = "signin-email",
            )
            OnbTextField(
                stringResource(R.string.onb_password),
                state.password,
                viewModel::onPassword,
                password = true,
                passwordVisible = state.passwordVisible,
                onTogglePasswordVisible = viewModel::togglePasswordVisible,
                imeAction = ImeAction.Done,
                onImeAction = viewModel::submit,
                // ONB-015: el error va bajo la contraseña.
                error = state.error?.message(),
                testTag = "signin-password",
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextLink(stringResource(R.string.onb_forgot), { onForgot(state.email) }, size = 13.sp)
            }
        }
        PrimaryButton(
            stringResource(R.string.onb_signin),
            viewModel::submit,
            Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).testTag("signin-submit"),
            enabled = state.canSubmit,
            busy = state.busy,
            trailing = null,
        )
        OrDivider(stringResource(R.string.onb_or), Modifier.padding(top = 18.dp))
        GoogleButton(google, enabled = !state.busy, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp))
    }
}

/** Olvidé mi contraseña, O8 (ONB-017). */
@Composable
fun ForgotPasswordScreen(onBack: () -> Unit, viewModel: ForgotPasswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    OnboardingPage(
        background = MarginColors.Paper,
        bottom = {
            if (state.sent) {
                ConfirmationToast(
                    stringResource(R.string.onb_link_sent),
                    stringResource(R.string.onb_check_spam),
                    Modifier.padding(bottom = 12.dp),
                )
            }
        },
    ) {
        StepHeader(onBack)
        IconBadge(OnboardingIcons.LockReset, Modifier.padding(top = 16.dp))
        OnbTitle(stringResource(R.string.onb_forgot_title), Modifier.padding(top = 16.dp))
        OnbBody(stringResource(R.string.onb_forgot_body), Modifier.padding(top = 10.dp))
        OnbTextField(
            stringResource(R.string.onb_email),
            state.email,
            viewModel::onEmail,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Send,
            onImeAction = viewModel::send,
            error = state.error?.message(),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
            testTag = "forgot-email",
        )
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!state.sent) {
                PrimaryButton(
                    stringResource(R.string.onb_send_link),
                    viewModel::send,
                    Modifier.testTag("forgot-send"),
                    enabled = state.canSend,
                    busy = state.busy,
                )
            } else {
                PrimaryButton(
                    stringResource(R.string.onb_open_email_app),
                    { openEmailApp(context) },
                    leading = OnboardingIcons.OpenInNew,
                    trailing = null,
                )
                if (state.resendSeconds > 0) {
                    Text(
                        stringResource(R.string.onb_resend_link_in, formatSeconds(state.resendSeconds)),
                        style = onbText(14.sp, FontWeight.SemiBold, MarginColors.Muted).copy(textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    )
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextLink(stringResource(R.string.onb_resend_link), viewModel::send)
                    }
                }
            }
        }
    }
}

/** Nueva contraseña desde el enlace del email, O9 (ONB-018). */
@Composable
fun ResetPasswordScreen(
    onClose: () -> Unit,
    onHome: () -> Unit,
    onSignIn: () -> Unit,
    onRequestLink: (email: String) -> Unit,
    viewModel: ResetPasswordViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.next) {
        when (state.next) {
            ResetPasswordViewModel.Next.Home -> onHome()
            ResetPasswordViewModel.Next.SignIn -> onSignIn()
            null -> return@LaunchedEffect
        }
        viewModel.onNavigated()
    }
    OnboardingPage(
        background = MarginColors.Paper,
        bottom = {
            if (!state.linkInvalid && !state.loading) {
                PrimaryButton(
                    stringResource(R.string.onb_save_and_signin),
                    viewModel::save,
                    Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp).testTag("reset-save"),
                    enabled = state.canSave,
                    busy = state.busy,
                    trailing = null,
                )
            }
        },
    ) {
        StepHeader(onClose, backIcon = OnboardingIcons.Close, backLabel = stringResource(R.string.onb_close))
        when {
            state.loading -> Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MarginColors.Ink)
            }
            state.linkInvalid -> {
                IconBadge(OnboardingIcons.LockReset, Modifier.padding(top = 16.dp))
                OnbTitle(stringResource(R.string.onb_link_invalid_title), Modifier.padding(top = 16.dp))
                OnbBody(stringResource(R.string.onb_link_invalid_body), Modifier.padding(top = 10.dp))
                PrimaryButton(
                    stringResource(R.string.onb_request_link),
                    { onRequestLink(state.email) },
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp),
                )
            }
            else -> {
                OnbTitle(stringResource(R.string.onb_reset_title), Modifier.padding(top = 20.dp))
                OnbBody(stringResource(R.string.onb_reset_for, state.email), Modifier.padding(top = 10.dp))
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OnbTextField(
                        stringResource(R.string.onb_new_password),
                        state.password,
                        viewModel::onPassword,
                        password = true,
                        passwordVisible = state.passwordVisible,
                        onTogglePasswordVisible = viewModel::togglePasswordVisible,
                        testTag = "reset-password",
                    )
                    val check = state.passwordCheck
                    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuleRow(stringResource(R.string.onb_rule_length), check.longEnough)
                        RuleRow(stringResource(R.string.onb_rule_number), check.hasNumber)
                        RuleRow(stringResource(R.string.onb_rule_symbol), check.hasSymbol)
                        RuleRow(stringResource(R.string.onb_rule_upper), check.hasUppercase)
                        if (state.password.isNotEmpty() && !check.isValid) {
                            Text(stringResource(R.string.onb_rules_needed), style = onbText(12.sp, color = MarginColors.Muted))
                        }
                    }
                    OnbTextField(
                        stringResource(R.string.onb_confirm_password),
                        state.confirm,
                        viewModel::onConfirm,
                        placeholder = stringResource(R.string.onb_repeat),
                        password = true,
                        passwordVisible = state.passwordVisible,
                        onTogglePasswordVisible = viewModel::togglePasswordVisible,
                        imeAction = ImeAction.Done,
                        onImeAction = viewModel::save,
                        error = when {
                            state.mismatch -> stringResource(R.string.onb_mismatch)
                            state.error != null -> state.error!!.message()
                            else -> null
                        },
                        testTag = "reset-confirm",
                    )
                }
            }
        }
    }
}
