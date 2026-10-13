package com.pluk.reader.ui.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pluk.reader.R
import com.pluk.reader.domain.plans.PlanCatalog
import com.pluk.reader.domain.plans.PlanPrompt
import com.pluk.reader.domain.plans.PlanPrompts
import com.pluk.reader.domain.plans.QuotaBlock
import com.pluk.reader.ui.theme.LocalMarginPalette
import com.pluk.reader.ui.theme.MarginColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Da a la raíz la hoja de planes pedida por cualquier pantalla (P02, P03). */
@HiltViewModel
class PlanPromptViewModel @Inject constructor(private val prompts: PlanPrompts) : ViewModel() {
    val prompt = prompts.current

    fun dismiss() = prompts.dismiss()
}

/**
 * Hojas de planes encima de todo (P02 bloqueos de cuota, P03 funciones pagas). "Ver planes" cierra la hoja y abre
 * P01; "Ahora no" la cierra y el libro queda en el dispositivo (PLN-009).
 */
@Composable
fun PlanPromptHost(onSeePlans: () -> Unit, viewModel: PlanPromptViewModel = hiltViewModel()) {
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    prompt?.let { current ->
        PlanSheet(onDismiss = viewModel::dismiss) {
            PlanPromptContent(
                current,
                onSeePlans = {
                    viewModel.dismiss()
                    onSeePlans()
                },
                onNotNow = viewModel::dismiss,
            )
        }
    }
}

/** Hoja inferior de Planes: radio 28 arriba, fondo [MarginColors.Field] y manija (diseño P02, P03). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MarginColors.Field,
        contentColor = MarginColors.Ink,
        scrimColor = Color(0x8C130000),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MarginColors.Off),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        ) { content() }
    }
}

/** Contenido de la hoja según el pedido. Sin la hoja alrededor, para poder probarlo. */
@Composable
fun PlanPromptContent(prompt: PlanPrompt, onSeePlans: () -> Unit, onNotNow: () -> Unit) {
    when (prompt) {
        is PlanPrompt.Quota -> when (val block = prompt.block) {
            QuotaBlock.Full -> QuotaFullContent(prompt.title, prompt.quotaBytes, onSeePlans, onNotNow)
            is QuotaBlock.DoesNotFit -> DoesNotFitContent(prompt.title, block, onSeePlans, onNotNow)
        }
        // PLN-010: llega con la hoja "Importar libros" (K-220).
        is PlanPrompt.Paid -> Unit
    }
}

/** Título de hoja que TalkBack anuncia al abrirse (PLN-013). */
@Composable
private fun AnnouncedTitle(text: String, modifier: Modifier = Modifier) {
    SheetTitle(
        text,
        modifier
            .semantics {
                heading()
                paneTitle = text
            }
            .testTag("plan-sheet-title"),
    )
}

/** "{Título} no se subió…" con el título en negrita. */
@Composable
private fun boldTitle(template: String, title: String): AnnotatedString {
    val start = template.indexOf(title)
    return buildAnnotatedString {
        if (start < 0) {
            append(template)
        } else {
            append(template.substring(0, start))
            withStyle(SpanStyle(color = MarginColors.Ink, fontWeight = FontWeight.Bold)) { append(title) }
            append(template.substring(start + title.length))
        }
    }
}

/** PLN-007 (P02a): el espacio ya está lleno. */
@Composable
private fun QuotaFullContent(title: String, quotaBytes: Long, onSeePlans: () -> Unit, onNotNow: () -> Unit) {
    val readerBytes = PlanCatalog.reader.cloudBytes ?: 1
    val free = formatCloud(quotaBytes)
    val reader = formatCloud(readerBytes)
    Column(Modifier.testTag("plan-sheet-full")) {
        SheetIcon(PlanIcons.CloudOff)
        AnnouncedTitle(stringResource(R.string.plans_sheet_full_title), Modifier.padding(top = 16.dp))
        SheetBody(boldTitle(stringResource(R.string.plans_sheet_full_body, title), title), Modifier.padding(top = 10.dp))
        Column(
            Modifier
                .padding(top = 18.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MarginColors.Paper)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val freeLabel = stringResource(R.string.plans_free)
            val readerLabel = stringResource(R.string.plans_reader)
            CompareRow(freeLabel, free, (quotaBytes.toFloat() / readerBytes).coerceIn(0.02f, 1f), bold = false)
            CompareRow(readerLabel, reader, 1f, bold = true)
            Text(stringResource(R.string.plans_with_reader, reader), style = planText(14, FontWeight.SemiBold))
        }
        SheetActions(stringResource(R.string.plans_see_plans), stringResource(R.string.plans_not_now), onSeePlans, onNotNow)
    }
}

/** Fila "Gratis ▬ 15 MB" de la comparación de P02a. */
@Composable
private fun CompareRow(label: String, value: String, fraction: Float, bold: Boolean) {
    val mono = rememberMonoFamily()
    Row(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "$label, $value" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            style = planText(13, if (bold) FontWeight.Bold else FontWeight.Medium, if (bold) MarginColors.Ink else MarginColors.Muted),
            modifier = Modifier.width(56.dp),
        )
        Box(Modifier.weight(1f)) {
            if (bold) UsageBar(1f, track = MarginColors.Yellow, fill = MarginColors.Yellow)
            else UsageBar(fraction, track = MarginColors.Off, fill = MarginColors.Ink)
        }
        Text(value, style = planText(12, FontWeight.Medium).copy(fontFamily = mono), modifier = Modifier.width(44.dp), maxLines = 1)
    }
}

/** PLN-008 (P02b): queda espacio, pero este libro no entra. */
@Composable
private fun DoesNotFitContent(title: String, block: QuotaBlock.DoesNotFit, onSeePlans: () -> Unit, onNotNow: () -> Unit) {
    val size = formatCloud(block.bookBytes)
    val free = formatCloud(block.freeBytes)
    val mono = rememberMonoFamily()
    val warning = if (LocalMarginPalette.current.isDark) Color(0xFFE8A0A0) else Color(0xFF8B1E1E)
    Column(Modifier.testTag("plan-sheet-does-not-fit")) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MarginColors.Paper)
                .semantics(mergeDescendants = true) {}
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .width(40.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MarginColors.Field)
                    .border(1.dp, MarginColors.Line, RoundedCornerShape(5.dp))
                    .padding(4.dp)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.BottomStart,
            ) {
                Text("EPUB", style = planText(8, FontWeight.SemiBold, MarginColors.Muted).copy(fontFamily = mono))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = planText(14, FontWeight.Bold), maxLines = 2)
                Text(
                    stringResource(R.string.plans_sheet_book_size, size, free),
                    style = planText(12, FontWeight.Medium, warning).copy(fontFamily = mono),
                )
            }
        }
        AnnouncedTitle(stringResource(R.string.plans_sheet_too_big_title, size), Modifier.padding(top = 18.dp))
        SheetBody(AnnotatedString(stringResource(R.string.plans_sheet_too_big_body, free)), Modifier.padding(top = 10.dp))
        Row(
            Modifier
                .padding(top = 18.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MarginColors.Yellow)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(PlanIcons.UploadFile, null, tint = MarginColors.OnYellow, modifier = Modifier.size(22.dp))
            Text(
                stringResource(R.string.plans_with_reader, formatCloud(PlanCatalog.reader.cloudBytes ?: 0)),
                style = planText(14, FontWeight.SemiBold, MarginColors.OnYellow).copy(lineHeight = 1.35.em),
            )
        }
        SheetActions(stringResource(R.string.plans_see_plans), stringResource(R.string.plans_not_now), onSeePlans, onNotNow)
    }
}
