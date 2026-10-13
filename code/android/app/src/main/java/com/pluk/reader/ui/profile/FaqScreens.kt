package com.pluk.reader.ui.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.pluk.reader.R
import com.pluk.reader.domain.help.Faq
import com.pluk.reader.domain.help.FaqId
import com.pluk.reader.ui.onboarding.OnboardingIcons
import com.pluk.reader.ui.theme.MarginColors

@get:StringRes
internal val FaqId.question: Int
    get() = when (this) {
        FaqId.EPUB -> R.string.faq_epub_question
        FaqId.BOOKS -> R.string.faq_books_question
        FaqId.SPACE -> R.string.faq_space_question
        FaqId.ADD -> R.string.faq_add_question
        FaqId.SYNC -> R.string.faq_sync_question
        FaqId.REMOVE -> R.string.faq_remove_question
    }

@get:StringRes
private val FaqId.answer: Int
    get() = when (this) {
        FaqId.EPUB -> R.string.faq_epub_answer
        FaqId.BOOKS -> R.string.faq_books_answer
        FaqId.SPACE -> R.string.faq_space_answer
        FaqId.ADD -> R.string.faq_add_answer
        FaqId.SYNC -> R.string.faq_sync_answer
        FaqId.REMOVE -> R.string.faq_remove_answer
    }

/** Filas de preguntas sobre la tarjeta tinta de ayuda (PRF-017): 52 de alto, separador, flecha. */
@Composable
internal fun FaqRows(questions: List<FaqId>, onOpen: (FaqId) -> Unit) {
    questions.forEachIndexed { index, id ->
        if (index > 0) CardDivider(MarginColors.InkLine)
        ProfileRow(onClick = { onOpen(id) }, minHeight = 52.dp, modifier = Modifier.testTag("faq-${id.key}")) {
            Text(
                stringResource(id.question),
                style = profileText(15, FontWeight.Medium, MarginColors.Field),
                modifier = Modifier.weight(1f),
            )
            RowIcon(ProfileIcons.ChevronRight, MarginColors.InkMuted)
        }
    }
}

/** PRF-018: todas las preguntas, en la misma tarjeta tinta de Perfil. */
@Composable
fun FaqListScreen(onBack: () -> Unit, onOpen: (FaqId) -> Unit) {
    FaqPage(stringResource(R.string.profile_faq), onBack) {
        ProfileCard(background = MarginColors.Ink) {
            FaqRows(Faq.all, onOpen)
        }
    }
}

/** PRF-017: la respuesta de una pregunta. */
@Composable
fun FaqAnswerScreen(id: FaqId, onBack: () -> Unit) {
    FaqPage(stringResource(id.question), onBack) {
        ProfileCard {
            Text(
                stringResource(id.answer),
                style = profileText(15).copy(lineHeight = 1.5.em),
                modifier = Modifier
                    .padding(16.dp)
                    .testTag("faq-answer"),
            )
        }
    }
}

/** Pantalla de ayuda: fondo papel, flecha atrás, título de 22 bold y el contenido debajo. */
@Composable
private fun FaqPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MarginColors.Paper),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Box(
                Modifier
                    .padding(start = 12.dp, top = 8.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    OnboardingIcons.ArrowBack,
                    contentDescription = stringResource(R.string.faq_back),
                    tint = MarginColors.Ink,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                title,
                style = profileText(22, FontWeight.Bold, letterSpacing = (-0.02).em).copy(lineHeight = 1.2.em),
                modifier = Modifier
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp)
                    .fillMaxWidth()
                    .semantics { heading() },
            )
            content()
        }
    }
}
