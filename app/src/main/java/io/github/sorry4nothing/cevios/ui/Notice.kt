package io.github.sorry4nothing.cevios.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi

enum class NoticeKind(@DrawableRes val icon: Int) {
    /** Bleibt stehen und wird angesagt. */
    ERROR(R.drawable.ic_warning),
    INFO(R.drawable.ic_info),
    WARNING(R.drawable.ic_warning),

    /** Eine Aktion hat geklappt; mit Symbol und Wort, nie nur Farbe. */
    SUCCESS(R.drawable.ic_check),
}

/** Banner im Inhalt: erklärt, warum etwas so ist, oder sagt, was schiefging. Immer Icon und Text. */
@Composable
fun NoticeBanner(kind: NoticeKind, text: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (surfaceColor, contentColor) = when (kind) {
        NoticeKind.ERROR -> scheme.errorContainer to scheme.onErrorContainer
        NoticeKind.INFO -> scheme.secondaryContainer to scheme.onSecondaryContainer
        NoticeKind.WARNING -> MaterialTheme.cevi.warningContainer to MaterialTheme.cevi.onWarningContainer
        NoticeKind.SUCCESS -> MaterialTheme.cevi.successContainer to MaterialTheme.cevi.onSuccessContainer
    }
    Row(
        modifier
            .fillMaxWidth()
            .background(surfaceColor, MaterialTheme.shapes.medium)
            .padding(horizontal = Spacing.s4, vertical = Spacing.s3)
            .semantics { if (kind == NoticeKind.ERROR) liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(painterResource(kind.icon), contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = contentColor)
    }
}

/**
 * Banner mit einer Aktion («Rückgängig», «Nochmals versuchen») und einem Knopf zum Schliessen (Entwurf `cv-note__close`). Er
 * verschwindet nie von selbst: Er bleibt, bis die Person ihn schliesst oder die App ihn ersetzt. Wird als Live-Region angesagt.
 */
@Composable
fun ActionBanner(
    kind: NoticeKind,
    text: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (surfaceColor, contentColor) = when (kind) {
        NoticeKind.ERROR -> scheme.errorContainer to scheme.onErrorContainer
        NoticeKind.INFO -> scheme.secondaryContainer to scheme.onSecondaryContainer
        NoticeKind.WARNING -> MaterialTheme.cevi.warningContainer to MaterialTheme.cevi.onWarningContainer
        NoticeKind.SUCCESS -> MaterialTheme.cevi.successContainer to MaterialTheme.cevi.onSuccessContainer
    }
    val closeLabel = stringResource(R.string.notice_close)
    Row(
        modifier
            .fillMaxWidth()
            .background(surfaceColor, MaterialTheme.shapes.medium)
            .padding(start = Spacing.s4, top = Spacing.s2, bottom = Spacing.s2)
            .semantics { liveRegion = if (kind == NoticeKind.ERROR) LiveRegionMode.Assertive else LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(painterResource(kind.icon), contentDescription = null, tint = contentColor, modifier = Modifier.padding(top = Spacing.s3).size(18.dp))
        Column(Modifier.weight(1f).padding(top = Spacing.s3), verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = contentColor)
            if (actionLabel != null && onAction != null) {
                Text(
                    actionLabel,
                    style = MaterialTheme.typography.labelLarge.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                    color = contentColor,
                    modifier = Modifier
                        .focusRing(MaterialTheme.shapes.small)
                        .clickable(role = Role.Button, onClick = onAction)
                        .padding(vertical = 12.dp),
                )
            }
        }
        Box(
            Modifier
                .size(48.dp)
                .focusRing(androidx.compose.foundation.shape.CircleShape)
                .clickable(role = Role.Button, onClick = onClose)
                .semantics { contentDescription = closeLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_cross), contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        }
    }
}
