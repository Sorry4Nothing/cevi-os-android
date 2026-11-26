package io.github.sorry4nothing.cevios.info

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.Header
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi

@Composable
private fun InfoPage.titleText(): String = stringResource(
    when (this) {
        InfoPage.PRIVACY -> R.string.info_privacy_title
        InfoPage.PRIVACY_APP -> R.string.info_privacy_app_title
        InfoPage.SUPPORT -> R.string.info_support_title
        InfoPage.DELETE_ACCOUNT -> R.string.info_delete_title
    },
)

/**
 * Die oberste offene Seite von [InfoViewModel] samt Zurück. `firstBackLabel` ist die Beschriftung des Zurück-Knopfs der ersten
 * Seite («Zurück zu Profil»); liegt eine Seite darunter, nennt der Knopf sie.
 */
@Composable
fun InfoHost(info: InfoViewModel, firstBackLabel: String) {
    val stack by info.stack.collectAsState()
    val state by info.state.collectAsState()
    val context = LocalContext.current
    val page = stack.lastOrNull() ?: return
    BackHandler { info.back() }
    val previous = stack.getOrNull(stack.lastIndex - 1)
    InfoScreen(
        page, state,
        backLabel = previous?.let { stringResource(R.string.info_back_to, it.titleText()) } ?: firstBackLabel,
        onBack = { info.back() },
        onRetry = info::retry,
        onLink = { openInfoLink(context, it, info::open) },
    )
}

/**
 * Datenschutz, Support oder «Konto löschen lassen»: Kopf mit Zurück, darunter der Text des Servers. Laden mit Skelett, Fehler mit
 * «Nochmals versuchen», leere Seite; ging das Neuladen nicht, steht der zuletzt geladene Text mit einem Hinweis da.
 */
@Composable
fun InfoScreen(
    page: InfoPage,
    state: InfoState,
    backLabel: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLink: (LinkTarget) -> Unit,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Header(
            title = page.titleText(),
            back = HeaderButton(R.drawable.ic_back, backLabel, onBack),
            titleStyle = MaterialTheme.typography.headlineMedium,
            focusOnTitle = true,
        )
        Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            when (state) {
                InfoState.Loading -> CardSkeleton(stringResource(R.string.info_loading))
                // Eine fehlende Seite ist hier ein Fehler des Servers, kein «nicht gefunden»: Es gibt immer einen neuen Versuch.
                is InfoState.Failed -> ErrorState(if (state.error == ApiError.NOT_FOUND) ApiError.UNKNOWN else state.error, onRetry)
                is InfoState.Content ->
                    if (state.document.blocks.isEmpty()) {
                        StatePage(R.drawable.ic_info, stringResource(R.string.info_empty_title), stringResource(R.string.info_empty_text)) {
                            PrimaryButton(stringResource(R.string.retry), onRetry, icon = R.drawable.ic_refresh)
                        }
                    } else {
                        InfoText(state, onRetry, onLink)
                    }
            }
        }
    }
}

@Composable
private fun InfoText(state: InfoState.Content, onRetry: () -> Unit, onLink: (LinkTarget) -> Unit) {
    val linkColor = MaterialTheme.cevi.accent
    LazyColumn(
        Modifier.fillMaxHeight().widthIn(max = Sizes.contentMaxWidth),
        contentPadding = PaddingValues(start = Spacing.s5, end = Spacing.s5, bottom = Spacing.s8),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        if (state.stale) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    NoticeBanner(NoticeKind.WARNING, stringResource(R.string.info_stale))
                    TextActionButton(stringResource(R.string.retry), onRetry)
                }
            }
        }
        items(state.document.blocks) { block -> BlockView(block, linkColor, onLink) }
    }
}

@Composable
private fun BlockView(block: Block, linkColor: Color, onLink: (LinkTarget) -> Unit) {
    when (block) {
        is Block.Heading -> Text(
            remember(block, linkColor, onLink) { annotated(block.spans, linkColor, onLink) },
            style = when (block.level) {
                1 -> MaterialTheme.typography.headlineMedium
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            },
            // Über einem Abschnitt mehr Luft als darunter: Der Abstand ordnet die Überschrift ihrem Text zu.
            modifier = Modifier.padding(top = Spacing.s3).semantics { heading() },
        )
        is Block.Paragraph -> Text(
            remember(block, linkColor, onLink) { annotated(block.spans, linkColor, onLink) },
            style = MaterialTheme.typography.bodyLarge,
        )
        is Block.ListItem -> Row(Modifier.padding(start = ((block.depth - 1) * 16).dp), horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            // Der Punkt wird nicht vorgelesen; die Zahl schon.
            Text(
                block.marker.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.widthIn(min = 20.dp).then(if (block.marker == "•") Modifier.clearAndSetSemantics { } else Modifier),
            )
            Text(
                remember(block, linkColor, onLink) { annotated(block.spans, linkColor, onLink) },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Links sind unterstrichen und in der Akzentfarbe: erkennbar auch ohne Farbe. */
private fun annotated(spans: List<Span>, linkColor: Color, onLink: (LinkTarget) -> Unit): AnnotatedString = buildAnnotatedString {
    for (span in spans) {
        val style = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
        )
        val link = span.link
        if (link == null) {
            withStyle(style) { append(span.text) }
        } else {
            val linkStyle = style.merge(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
            withLink(LinkAnnotation.Clickable("link", TextLinkStyles(linkStyle)) { onLink(link) }) { append(span.text) }
        }
    }
}

/**
 * Öffnet ein Ziel aus dem Text: eine andere Seite der App bleibt in der App; Web, Mail und Telefon gehen über das System. Gibt es
 * dafür keine App auf dem Gerät, passiert nichts.
 */
fun openInfoLink(context: Context, target: LinkTarget, onPage: (InfoPage) -> Unit) {
    val intent = when (target) {
        is LinkTarget.Page -> return onPage(target.page)
        is LinkTarget.Web -> Intent(Intent.ACTION_VIEW, target.url.toUri())
        is LinkTarget.Mail -> Intent(Intent.ACTION_SENDTO, "mailto:${target.address}".toUri())
        is LinkTarget.Phone -> Intent(Intent.ACTION_DIAL, "tel:${target.number}".toUri())
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Kein Browser, Mailprogramm oder Telefon: Der Link lässt sich dann nicht öffnen.
    }
}
