package com.pluk.reader.ui.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pluk.reader.R
import com.pluk.reader.ui.onboarding.OnbColors
import com.pluk.reader.ui.onboarding.OnbTitle
import com.pluk.reader.ui.onboarding.OnboardingIcons
import com.pluk.reader.ui.onboarding.OnboardingPage
import com.pluk.reader.ui.onboarding.PrimaryButton
import com.pluk.reader.ui.onboarding.SecondaryButton
import com.pluk.reader.ui.onboarding.onbText
import com.pluk.reader.ui.theme.MarginColors
import com.pluk.reader.ui.theme.ReaderTheme

/**
 * "Listo" sin cuenta, diseño L0 (GST-019): las portadas son ilustración, no libros de regalo (la app no trae
 * contenido). Se llega desde "Empezar a leer" de la bienvenida.
 */
@Composable
fun GuestReadyScreen(onStartReading: () -> Unit, onImportBooks: () -> Unit, modifier: Modifier = Modifier) {
    OnboardingPage(
        background = MarginColors.Yellow,
        modifier = modifier,
        bottom = {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(stringResource(R.string.onb_start_reading), onStartReading, Modifier.testTag("guest-ready-start"))
                SecondaryButton(
                    stringResource(R.string.onb_import_books),
                    onImportBooks,
                    Modifier.testTag("guest-ready-import"),
                    leading = { Icon(OnboardingIcons.Add, contentDescription = null, tint = MarginColors.Ink, modifier = Modifier.size(20.dp)) },
                )
            }
        },
    ) {
        Spacer(Modifier.height(56.dp))
        OnbTitle(stringResource(R.string.guest_ready_title), size = 54.sp, padding = 28.dp)
        Text(
            stringResource(R.string.guest_ready_body),
            style = onbText(15.sp, color = OnbColors.SoftOnYellow, lineHeight = 1.45.em),
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 16.dp).widthIn(max = 270.dp),
        )
        // GST-019: ilustración, no libros de la biblioteca.
        ReadyCovers(Modifier.padding(start = 28.dp, end = 28.dp, top = 28.dp))
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun GuestReadyScreenPreview() {
    ReaderTheme { GuestReadyScreen(onStartReading = {}, onImportBooks = {}) }
}
