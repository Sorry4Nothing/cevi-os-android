package io.github.sorry4nothing.cevios.lager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.ui.ActionBanner
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.NoticeKind

/** Der Rahmen eines Bildschirms von «Ablage und Querschnitt»: wie [LivePage], aber mit dem Schreiber und den Hinweisen der Ablage. */
@Composable
fun ArchivePage(
    frame: LiveFrame,
    archive: ArchiveViewModel,
    title: String,
    subtitle: String? = frame.camp?.title,
    action: HeaderButton? = null,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    CampPage(
        title = title,
        subtitle = subtitle,
        camp = frame.camp, queue = frame.queue, online = frame.online, onOpenQueue = frame.onOpenQueue, writer = archive.writer,
        back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_camp), frame.onBack),
        action = action,
        banners = { ArchiveNoticeBanner(archive) },
        content = content,
    )
}

/** Der Hinweis nach einer Aktion; er bleibt, bis die Person ihn schliesst, und wird angesagt. */
@Composable
fun ArchiveNoticeBanner(archive: ArchiveViewModel) {
    val notice by archive.notice.collectAsState()
    val text = when (notice) {
        null -> return
        ArchiveNotice.SCHEDULE_ADOPTED -> R.string.archive_notice_schedule_adopted
        ArchiveNotice.DOCUMENT_ADDED -> R.string.archive_notice_document_added
        ArchiveNotice.DOCUMENT_DELETED -> R.string.archive_notice_document_deleted
        ArchiveNotice.CONTACT_ADDED -> R.string.archive_notice_contact_added
        ArchiveNotice.CONTACT_DELETED -> R.string.archive_notice_contact_deleted
    }
    ActionBanner(NoticeKind.INFO, stringResource(text), null, null, archive::dismissNotice)
}
