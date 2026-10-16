package com.pluk.reader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pluk.reader.R
import com.pluk.reader.ui.plans.PlanIcons
import com.pluk.reader.ui.theme.MarginColors

/**
 * GST-010: "Tus libros están solo en este dispositivo." con "Crear cuenta" y una cruz (diseños H0 y L1), con el
 * aspecto del aviso del 80 % (PLN-011). GST-018: el texto se lee entero y la cruz tiene nombre.
 */
@Composable
fun GuestNoticeCard(onCreateAccount: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val createAccount = stringResource(R.string.guest_notice_create_account)
    Row(
        modifier
            .padding(start = 20.dp, end = 20.dp, top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MarginColors.Field)
            .border(1.5.dp, MarginColors.Off, RoundedCornerShape(20.dp))
            .padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 12.dp)
            .testTag("guest-notice"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.guest_notice_title),
                color = MarginColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 1.3.em,
            )
            Text(
                buildAnnotatedString {
                    append(stringResource(R.string.guest_notice_body))
                    append(' ')
                    withLink(
                        LinkAnnotation.Clickable(
                            "create-account",
                            TextLinkStyles(SpanStyle(color = MarginColors.Ink, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)),
                        ) { onCreateAccount() },
                    ) { append(createAccount) }
                },
                color = MarginColors.Ink2,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 1.4.em,
            )
        }
        val close = stringResource(R.string.plans_notice_close)
        Box(
            Modifier
                .offset(y = (-10).dp)
                .size(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .clickable(role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = close }
                .testTag("guest-notice-close"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(PlanIcons.Close, null, tint = MarginColors.Muted, modifier = Modifier.size(22.dp))
        }
    }
}
