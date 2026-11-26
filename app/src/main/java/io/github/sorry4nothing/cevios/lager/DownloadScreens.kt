package io.github.sorry4nothing.cevios.lager

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.downloads.DownloadError
import io.github.sorry4nothing.cevios.downloads.DownloadNotice
import io.github.sorry4nothing.cevios.downloads.DownloadedFile
import io.github.sorry4nothing.cevios.downloads.DownloadsViewModel
import io.github.sorry4nothing.cevios.ui.ActionBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.rowAction
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Die Downloads der Sitzung; `null` ausserhalb der App (Vorschau, Tests): Dann gibt es keine Aktion «Herunterladen». */
val LocalDownloads = compositionLocalOf<DownloadsViewModel?> { null }

private val DOWNLOAD_TIME = DateTimeFormatter.ofPattern("d.M. HH:mm").withZone(ZoneId.systemDefault())

/**
 * Der Hinweis nach «Herunterladen» oder «Entfernen»: Er steht in jedem Lager-Bildschirm, bleibt, bis ihn die Person schliesst, und geht
 * weg, wenn der Bildschirm verlassen wird. «Öffnen» zeigt die geladene Datei.
 */
@Composable
fun DownloadBanner() {
    val vm = LocalDownloads.current ?: return
    val notice by vm.notice.collectAsState()
    val context = LocalContext.current
    DisposableEffect(Unit) { onDispose { vm.dismissNotice() } }
    when (val n = notice) {
        null -> Unit
        is DownloadNotice.Done -> ActionBanner(
            NoticeKind.SUCCESS, stringResource(R.string.dl_done, n.file.name), stringResource(R.string.dl_open), { vm.open(context, n.file) }, vm::dismissNotice,
        )
        is DownloadNotice.Removed -> ActionBanner(NoticeKind.INFO, stringResource(R.string.dl_removed, n.name), null, null, vm::dismissNotice)
        is DownloadNotice.Failed -> ActionBanner(NoticeKind.ERROR, stringResource(downloadErrorText(n.error)), null, null, vm::dismissNotice)
    }
}

private fun downloadErrorText(error: DownloadError): Int = when (error) {
    DownloadError.NOT_HTTPS -> R.string.dl_error_not_https
    DownloadError.NETWORK -> R.string.dl_error_network
    DownloadError.NOT_A_FILE -> R.string.dl_error_not_a_file
    DownloadError.TOO_BIG -> R.string.dl_error_too_big
    DownloadError.STORAGE -> R.string.dl_error_storage
    DownloadError.NO_APP -> R.string.dl_error_no_app
}

/**
 * Heruntergeladene Dateien des Lagers (ohne Entwurf, aus Zeile, Knopf und Hinweis der Ablage gebaut): tippen öffnet die Datei auch ohne
 * Netz, «Entfernen» löscht sie vom Gerät. Die Dateien liegen nur in der App und gehen mit dem Lager und beim Abmelden weg.
 */
@Composable
fun DownloadsScreen(frame: LiveFrame, vm: DownloadsViewModel) {
    val files by vm.files.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(frame.camp?.id) { vm.refresh() }
    LivePage(frame, stringResource(R.string.dl_title)) {
        if (files.isEmpty()) {
            StatePage(R.drawable.ic_download, stringResource(R.string.dl_empty_title), stringResource(R.string.dl_empty_text))
        } else {
            LiveColumn {
                files.forEach { file ->
                    DownloadRow(file, context, onOpen = { vm.open(context, file) }, onRemove = { vm.remove(file) })
                }
                Text(stringResource(R.string.dl_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DownloadRow(file: DownloadedFile, context: android.content.Context, onOpen: () -> Unit, onRemove: () -> Unit) {
    val detail = stringResource(R.string.dl_detail, Formatter.formatShortFileSize(context, file.size), DOWNLOAD_TIME.format(Instant.ofEpochMilli(file.savedAt)))
    val shape = MaterialTheme.shapes.small
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = Sizes.touchTarget)
                .focusRing(shape)
                .clip(shape)
                .clickable(onClickLabel = stringResource(R.string.dl_open_named, file.name), role = Role.Button, onClick = onOpen)
                .semantics(mergeDescendants = true) { contentDescription = file.name + ", " + detail }
                .padding(vertical = Spacing.s1),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_file), contentDescription = null, tint = MaterialTheme.cevi.accent, modifier = Modifier.size(Sizes.icon))
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyLarge)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val remove = stringResource(R.string.dl_remove)
        TextActionButton(remove, onRemove, Modifier.rowAction(remove, file.name))
    }
}
