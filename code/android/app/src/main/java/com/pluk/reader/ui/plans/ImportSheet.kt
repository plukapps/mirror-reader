package com.pluk.reader.ui.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.pluk.reader.R
import com.pluk.reader.domain.plans.ImportSource
import com.pluk.reader.domain.plans.PlanId
import com.pluk.reader.ui.theme.MarginColors

/** PLN-010 (P03): hoja "Importar libros". Elegir un origen cierra la hoja y avisa con [onSource]. */
@Composable
fun ImportSheet(onSource: (ImportSource) -> Unit, onDismiss: () -> Unit) {
    PlanSheet(onDismiss = onDismiss) {
        ImportSheetContent(onSource)
    }
}

/** Contenido de la hoja, sin la hoja alrededor, para poder probarlo. */
@Composable
fun ImportSheetContent(onSource: (ImportSource) -> Unit) {
    val title = stringResource(R.string.plans_import_title)
    Column(Modifier.testTag("import-sheet")) {
        Text(
            title,
            style = planText(34, letterSpacing = (-0.04).em).copy(lineHeight = 1.em),
            modifier = Modifier.semantics {
                heading()
                paneTitle = title
            },
        )
        Column(
            Modifier
                .padding(top = 18.dp, bottom = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MarginColors.Paper),
        ) {
            ImportSource.entries.forEachIndexed { i, source ->
                if (i > 0) HorizontalDivider(thickness = 1.dp, color = MarginColors.Hairline)
                SourceRow(source, onClick = { onSource(source) })
            }
        }
    }
}

@Composable
private fun SourceRow(source: ImportSource, onClick: () -> Unit) {
    val (icon, label) = when (source) {
        ImportSource.DEVICE -> PlanIcons.Smartphone to R.string.plans_import_device
        ImportSource.DRIVE -> PlanIcons.AddToDrive to R.string.plans_import_drive
        ImportSource.CALIBRE -> PlanIcons.Dns to R.string.plans_import_calibre
    }
    val text = stringResource(label)
    val badge = if (source.paidFeature != null) planName(PlanId.LIBRARY) else null
    val description = if (badge != null) stringResource(R.string.plans_import_badge_description, text, badge) else text
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(horizontal = 16.dp)
            .testTag("import-source-${source.name}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = MarginColors.Ink, modifier = Modifier.size(22.dp))
        Text(text, style = planText(15, FontWeight.SemiBold), modifier = Modifier.weight(1f))
        if (badge != null) {
            Text(
                badge,
                style = planText(10, FontWeight.Bold),
                modifier = Modifier
                    .border(1.5.dp, MarginColors.Ink, RoundedCornerShape(99.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}
