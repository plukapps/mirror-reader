package com.pluk.reader.ui.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pluk.reader.R
import com.pluk.reader.ui.onboarding.Brand
import com.pluk.reader.ui.onboarding.OnbColors
import com.pluk.reader.ui.onboarding.OnboardingPage
import com.pluk.reader.ui.onboarding.PrimaryButton
import com.pluk.reader.ui.onboarding.SecondaryButton
import com.pluk.reader.ui.onboarding.TermsText
import com.pluk.reader.ui.onboarding.onbText
import com.pluk.reader.ui.theme.MarginColors
import com.pluk.reader.ui.theme.ReaderTheme

/**
 * Bienvenida, diseño O1 (ONB-001): "Crear cuenta" y "Ya tengo una cuenta" (ONB-002). Si no entra en la pantalla
 * (apaisado), se desplaza (WEL-007, ONB-022).
 */
@Composable
fun WelcomeScreen(onCreateAccount: () -> Unit, onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    OnboardingPage(
        background = MarginColors.Yellow,
        modifier = modifier,
        bottom = {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(stringResource(R.string.onb_create_account), onCreateAccount, Modifier.testTag("welcome-create"))
                SecondaryButton(stringResource(R.string.onb_have_account), onSignIn, Modifier.testTag("welcome-signin"))
            }
            TermsText(
                R.string.onb_terms_notice,
                style = onbText(11.sp, color = OnbColors.SoftOnYellow, lineHeight = 1.45.em).copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 16.dp),
            )
            Text(
                stringResource(R.string.welcome_footer),
                style = onbText(11.sp, color = OnbColors.SoftOnYellow).copy(textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp),
            )
        },
    ) {
        Brand(Modifier.padding(start = 28.dp, end = 28.dp, top = 28.dp))
        BasicText(
            text = stringResource(R.string.welcome_headline),
            style = onbText(54.sp, FontWeight.Normal, letterSpacing = (-0.045).em, lineHeight = 1.02.em),
            // "Scrolleá menos." no entra a 54 sp en un teléfono angosto: se achica antes de cortar la línea.
            maxLines = 3,
            autoSize = TextAutoSize.StepBased(minFontSize = 32.sp, maxFontSize = 54.sp),
            modifier = Modifier
                .padding(start = 28.dp, end = 28.dp, top = 64.dp)
                .semantics { heading() }
                .testTag("welcome-headline"),
        )
        Text(
            text = stringResource(R.string.welcome_body),
            style = onbText(15.sp, color = OnbColors.SoftOnYellow, lineHeight = 1.45.em),
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 24.dp).widthIn(max = 260.dp),
        )
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun WelcomeScreenPreview() {
    ReaderTheme { WelcomeScreen(onCreateAccount = {}, onSignIn = {}) }
}
