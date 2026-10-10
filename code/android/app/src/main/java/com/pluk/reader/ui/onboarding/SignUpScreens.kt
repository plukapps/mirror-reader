package com.pluk.reader.ui.onboarding

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pluk.reader.R
import com.pluk.reader.domain.account.AuthError
import com.pluk.reader.domain.onboarding.DailyReminder
import com.pluk.reader.domain.onboarding.Genre
import com.pluk.reader.domain.onboarding.PasswordStrength
import com.pluk.reader.domain.onboarding.ReadingGoal
import com.pluk.reader.ui.theme.MarginColors

/** Crear cuenta, O2 (ONB-003 a ONB-006). */
@Composable
fun SignUpScreen(
    onBack: () -> Unit,
    onVerifyEmail: () -> Unit,
    onInterests: () -> Unit,
    onSignIn: () -> Unit,
    viewModel: SignUpViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.next) {
        when (state.next) {
            SignUpViewModel.Next.VerifyEmail -> onVerifyEmail()
            SignUpViewModel.Next.Interests -> onInterests()
            null -> return@LaunchedEffect
        }
        viewModel.onNavigated()
    }
    val google = rememberGoogleSignIn(viewModel::onGoogleToken, viewModel::onGoogleFailed)
    OnboardingPage(
        background = MarginColors.Paper,
        bottom = {
            PrimaryButton(
                stringResource(R.string.onb_create_account),
                viewModel::submit,
                Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp).testTag("signup-submit"),
                enabled = state.canSubmit,
                busy = state.busy,
            )
        },
    ) {
        StepHeader(onBack, stepLabel = stringResource(R.string.onb_step, 1, STEPS))
        StepBar(step = 1)
        OnbTitle(stringResource(R.string.onb_signup_title), Modifier.padding(top = 20.dp))
        GoogleButton(google, enabled = !state.busy, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp))
        OrDivider(stringResource(R.string.onb_or_email), Modifier.padding(top = 16.dp))
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnbTextField(stringResource(R.string.onb_name), state.name, viewModel::onName, testTag = "signup-name")
            OnbTextField(
                stringResource(R.string.onb_email),
                state.email,
                viewModel::onEmail,
                keyboardType = KeyboardType.Email,
                error = if (state.error == AuthError.EmailInUse) AuthError.EmailInUse.message() else null,
                testTag = "signup-email",
            )
            if (state.error == AuthError.EmailInUse) TextLink(stringResource(R.string.onb_email_in_use_signin), onSignIn, size = 13.sp)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OnbTextField(
                    stringResource(R.string.onb_password),
                    state.password,
                    viewModel::onPassword,
                    password = true,
                    passwordVisible = state.passwordVisible,
                    onTogglePasswordVisible = viewModel::togglePasswordVisible,
                    imeAction = ImeAction.Done,
                    onImeAction = viewModel::submit,
                    testTag = "signup-password",
                )
                if (state.password.isNotEmpty()) {
                    StrengthMeter(state.passwordCheck.score, state.passwordCheck.strength.label())
                }
                if (state.password.isNotEmpty() && !state.passwordCheck.isValid) {
                    Text(stringResource(R.string.onb_password_hint), style = onbText(12.sp, color = MarginColors.Muted))
                }
            }
        }
        TermsCheckbox(state.termsAccepted, viewModel::onTermsAccepted, Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) {
            TermsText(R.string.onb_terms_agree, style = onbText(12.sp, color = OnbColors.Soft, lineHeight = 1.4.em))
        }
        val otherError = state.error?.takeIf { it != AuthError.EmailInUse }
        if (otherError != null) {
            Text(otherError.message(), style = onbText(13.sp, FontWeight.Medium, OnbColors.Error), modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
        }
    }
}

@Composable
internal fun PasswordStrength.label(): String = when (this) {
    PasswordStrength.Empty, PasswordStrength.Weak -> stringResource(R.string.onb_strength_weak)
    PasswordStrength.Fair -> stringResource(R.string.onb_strength_fair)
    PasswordStrength.Good -> stringResource(R.string.onb_strength_good)
    PasswordStrength.Strong -> stringResource(R.string.onb_strength_strong)
}

/** Verificar email, O3 (ONB-007, ONB-008). */
@Composable
fun VerifyEmailScreen(
    onVerified: () -> Unit,
    onChange: (name: String, email: String) -> Unit,
    viewModel: VerifyEmailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(viewModel) {
        viewModel.onVisible()
        onPauseOrDispose { viewModel.onHidden() }
    }
    LaunchedEffect(state.next) {
        when (val next = state.next) {
            VerifyEmailViewModel.Next.Interests -> onVerified()
            is VerifyEmailViewModel.Next.Change -> onChange(next.name, next.email)
            null -> return@LaunchedEffect
        }
        viewModel.onNavigated()
    }
    BackHandler(onBack = viewModel::change)
    OnboardingPage(background = MarginColors.Paper) {
        StepHeader(viewModel::change, stepLabel = stringResource(R.string.onb_step, 2, STEPS))
        StepBar(step = 2)
        IconBadge(OnboardingIcons.MarkEmailUnread, Modifier.padding(top = 22.dp))
        OnbTitle(stringResource(R.string.onb_verify_title), Modifier.padding(top = 16.dp))
        OnbBody(stringResource(R.string.onb_verify_body, state.email), Modifier.padding(top = 10.dp))
        TextLink(stringResource(R.string.onb_change), viewModel::change, Modifier.padding(start = 14.dp), size = 14.sp)
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.canResend) {
                IconTextButton(stringResource(R.string.onb_resend), OnboardingIcons.Schedule, viewModel::resend)
            } else {
                Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(OnboardingIcons.Schedule, contentDescription = null, tint = MarginColors.Muted, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.onb_resend_in, formatSeconds(state.resendSeconds)), style = onbText(13.sp, FontWeight.Medium, MarginColors.Muted))
                }
            }
            IconTextButton(stringResource(R.string.onb_open_email), OnboardingIcons.OpenInNew, { openEmailApp(context) })
        }
        Text(
            when {
                state.error != null -> state.error!!.message()
                state.resent -> stringResource(R.string.onb_resent)
                else -> stringResource(R.string.onb_verify_waiting)
            },
            style = onbText(13.sp, FontWeight.Medium, if (state.error != null) OnbColors.Error else MarginColors.Muted),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
        )
    }
}

/** Intereses, O4 (ONB-009). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestsScreen(onBack: (() -> Unit)?, onDone: () -> Unit, viewModel: InterestsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) {
        if (state.done) {
            onDone()
            viewModel.onNavigated()
        }
    }
    OnboardingPage(
        background = MarginColors.Paper,
        bottom = {
            Text(
                pluralStringResource(R.plurals.onb_interests_selected, state.selected.size, state.selected.size),
                style = onbText(12.sp, FontWeight.Medium, MarginColors.Muted).copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton(
                stringResource(R.string.onb_continue),
                viewModel::continueOn,
                Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 14.dp).testTag("interests-continue"),
                enabled = state.canContinue,
            )
        },
    ) {
        StepHeader(onBack, onSkip = viewModel::skip)
        StepBar(step = 3)
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.onb_email_verified), style = onbText(12.sp, FontWeight.SemiBold, MarginColors.Muted))
            Icon(OnboardingIcons.CheckCircle, contentDescription = null, tint = MarginColors.Ink, modifier = Modifier.padding(start = 4.dp).size(14.dp))
        }
        OnbTitle(stringResource(R.string.onb_interests_title), Modifier.padding(top = 8.dp))
        OnbBody(stringResource(R.string.onb_interests_body), Modifier.padding(top = 10.dp))
        FlowRow(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Genre.entries.forEach { genre ->
                GenreChip(genre.label(), selected = genre in state.selected, onToggle = { viewModel.toggle(genre) })
            }
        }
    }
}

@Composable
private fun GenreChip(text: String, selected: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .height(40.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(MarginColors.Ink) else Modifier.border(1.5.dp, MarginColors.Ink, shape))
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(start = if (selected) 10.dp else 16.dp, end = if (selected) 14.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (selected) Icon(OnboardingIcons.Check, contentDescription = null, tint = MarginColors.Yellow, modifier = Modifier.size(18.dp))
        Text(text, style = onbText(14.sp, FontWeight.SemiBold, if (selected) MarginColors.Yellow else MarginColors.Ink))
    }
}

@Composable
private fun Genre.label(): String = stringResource(
    when (this) {
        Genre.Philosophy -> R.string.genre_philosophy
        Genre.Classics -> R.string.genre_classics
        Genre.Poetry -> R.string.genre_poetry
        Genre.SciFi -> R.string.genre_scifi
        Genre.History -> R.string.genre_history
        Genre.Essays -> R.string.genre_essays
        Genre.Mystery -> R.string.genre_mystery
        Genre.Romance -> R.string.genre_romance
        Genre.Biography -> R.string.genre_biography
        Genre.Horror -> R.string.genre_horror
        Genre.Science -> R.string.genre_science
        Genre.Travel -> R.string.genre_travel
        Genre.Drama -> R.string.genre_drama
    },
)

/** Meta diaria y recordatorio, O5 (ONB-010, ONB-011). */
@Composable
fun GoalScreen(onBack: () -> Unit, onDone: () -> Unit, viewModel: GoalViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.done) {
        if (state.done) {
            onDone()
            viewModel.onNavigated()
        }
    }
    // ONB-011: se pide el permiso al terminar; con o sin él, se guarda.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, R.string.onb_notifications_denied, Toast.LENGTH_LONG).show()
        viewModel.finish()
    }
    val finish = {
        if (viewModel.needsNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            if (viewModel.needsNotificationPermission) Toast.makeText(context, R.string.onb_notifications_denied, Toast.LENGTH_LONG).show()
            viewModel.finish()
        }
    }
    OnboardingPage(
        background = MarginColors.Paper,
        bottom = {
            PrimaryButton(stringResource(R.string.onb_finish), finish, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp).testTag("goal-finish"))
        },
    ) {
        StepHeader(onBack, onSkip = viewModel::skip)
        StepBar(step = 4)
        Text(stringResource(R.string.onb_step, 4, STEPS), style = onbText(12.sp, FontWeight.SemiBold, MarginColors.Muted), modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp))
        OnbTitle(stringResource(R.string.onb_goal_title), Modifier.padding(top = 8.dp))
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReadingGoal.entries.forEach { goal ->
                GoalOption(goal, selected = goal == state.goal, onSelect = { viewModel.selectGoal(goal) })
            }
        }
        ReminderCard(
            state.reminder,
            onEnabledChange = viewModel::setReminderEnabled,
            onPickTime = { pickingTime = true },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
        )
    }
    if (pickingTime) {
        ReminderTimeDialog(
            state.reminder,
            onDismiss = { pickingTime = false },
            onConfirm = { hour, minute ->
                viewModel.setReminderTime(hour, minute)
                pickingTime = false
            },
        )
    }
}

@Composable
private fun GoalOption(goal: ReadingGoal, selected: Boolean, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(shape)
            .background(if (selected) MarginColors.Yellow else MarginColors.Field)
            .border(if (selected) 2.dp else 1.5.dp, if (selected) MarginColors.Ink else OnbColors.Line, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MarginColors.Field)
                .border(if (selected) 6.dp else 1.5.dp, if (selected) MarginColors.Ink else MarginColors.Muted, CircleShape),
        )
        Text(
            if (goal == ReadingGoal.Devoted) stringResource(R.string.goal_hour) else stringResource(R.string.goal_minutes, goal.minutes),
            style = onbText(16.sp, FontWeight.Bold),
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(
                when (goal) {
                    ReadingGoal.Casual -> R.string.goal_casual
                    ReadingGoal.Regular -> R.string.goal_regular
                    ReadingGoal.Serious -> R.string.goal_serious
                    ReadingGoal.Devoted -> R.string.goal_devoted
                },
            ),
            style = onbText(13.sp, if (selected) FontWeight.SemiBold else FontWeight.Medium, if (selected) MarginColors.Ink else MarginColors.Muted),
        )
    }
}

@Composable
private fun ReminderCard(reminder: DailyReminder, onEnabledChange: (Boolean) -> Unit, onPickTime: () -> Unit, modifier: Modifier = Modifier) {
    val time = formatTime(reminder.hour, reminder.minute)
    val changeTime = stringResource(R.string.onb_reminder_change_time, time)
    val reminderLabel = stringResource(R.string.onb_reminder)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MarginColors.Ink)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(OnboardingIcons.Notifications, contentDescription = null, tint = MarginColors.Yellow, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.onb_reminder), style = onbText(14.sp, FontWeight.SemiBold, MarginColors.Field))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.onb_reminder_at) + " ", style = onbText(12.sp, color = OnbColors.Caption))
                Text(
                    time,
                    style = onbText(12.sp, FontWeight.SemiBold, MarginColors.Field).copy(textDecoration = TextDecoration.Underline),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClickLabel = changeTime, role = Role.Button, onClick = onPickTime)
                        .semantics { contentDescription = changeTime }
                        .padding(vertical = 6.dp, horizontal = 2.dp)
                        .testTag("goal-time"),
                )
            }
        }
        Switch(
            checked = reminder.enabled,
            onCheckedChange = onEnabledChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MarginColors.Ink,
                checkedTrackColor = MarginColors.Yellow,
                checkedBorderColor = MarginColors.Yellow,
                uncheckedThumbColor = OnbColors.Caption,
                uncheckedTrackColor = MarginColors.Ink,
                uncheckedBorderColor = OnbColors.Caption,
            ),
            modifier = Modifier.semantics { contentDescription = reminderLabel }.testTag("goal-reminder"),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(reminder: DailyReminder, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val picker = rememberTimePickerState(initialHour = reminder.hour, initialMinute = reminder.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MarginColors.Paper,
        confirmButton = {
            TextButton(onClick = { onConfirm(picker.hour, picker.minute) }) { Text(stringResource(R.string.onb_time_ok), color = MarginColors.Ink) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.onb_time_cancel), color = MarginColors.Ink) }
        },
        text = {
            TimePicker(
                state = picker,
                colors = TimePickerDefaults.colors(
                    selectorColor = MarginColors.Ink,
                    timeSelectorSelectedContainerColor = MarginColors.Yellow,
                    timeSelectorSelectedContentColor = MarginColors.Ink,
                    clockDialSelectedContentColor = MarginColors.Yellow,
                ),
            )
        },
    )
}

internal fun formatTime(hour: Int, minute: Int) = "%02d:%02d".format(hour, minute)

/** "Ya estás adentro", O6 (ONB-013). */
@Composable
fun AllSetScreen(onStartReading: () -> Unit, onImportBooks: () -> Unit, viewModel: AllSetViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnboardingPage(
        background = MarginColors.Yellow,
        bottom = {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(stringResource(R.string.onb_start_reading), onStartReading, Modifier.testTag("allset-start"))
                SecondaryButton(
                    stringResource(R.string.onb_import_books),
                    onImportBooks,
                    Modifier.testTag("allset-import"),
                    leading = { Icon(OnboardingIcons.Add, contentDescription = null, tint = MarginColors.Ink, modifier = Modifier.size(20.dp)) },
                )
            }
        },
    ) {
        Spacer(Modifier.height(56.dp))
        OnbTitle(
            state.name?.let { stringResource(R.string.onb_allset_title, it) } ?: stringResource(R.string.onb_allset_title_anonymous),
            size = 54.sp,
            padding = 28.dp,
        )
        val goal = state.goal
        val reminder = state.reminder
        Text(
            when {
                goal != null && reminder?.enabled == true ->
                    stringResource(R.string.onb_allset_goal_reminder, goal.minutes, formatTime(reminder.hour, reminder.minute))
                goal != null -> stringResource(R.string.onb_allset_goal, goal.minutes)
                else -> stringResource(R.string.onb_allset_generic)
            },
            style = onbText(15.sp, color = OnbColors.SoftOnYellow, lineHeight = 1.45.em),
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 16.dp).widthIn(max = 270.dp),
        )
    }
}
