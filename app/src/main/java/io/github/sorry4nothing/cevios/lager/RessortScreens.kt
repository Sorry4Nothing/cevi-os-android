package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.push.SwitchRow
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.ActionBanner
import io.github.sorry4nothing.cevios.ui.Avatar
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.Header
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi

/** Alle Ressorts des Lagers als Baum (Entwurf: Einstieg über «Mehr»). Tippen öffnet die Seite des Ressorts. */
@Composable
fun RessortListScreen(vm: TasksViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val state by vm.ressorts.collectAsState()
    LaunchedEffect(Unit) { vm.loadRessortsIfNeeded() }
    Column(Modifier.fillMaxSize()) {
        Header(
            title = stringResource(R.string.ressorts_title),
            back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_more)) { onBack() },
            focusOnTitle = true,
        )
        when (val result = state) {
            null -> CardSkeleton(stringResource(R.string.ressorts_loading))
            is ApiResult.Failure -> ErrorState(result.error, vm::retryRessorts)
            is ApiResult.Ok ->
                if (result.value.isEmpty()) {
                    StatePage(R.drawable.ic_group, stringResource(R.string.ressorts_empty_title), stringResource(R.string.ressorts_empty_text))
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = Spacing.s3, end = Spacing.s3, bottom = Spacing.s4),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                    ) {
                        items(ressortTree(result.value), key = { it.first.id }) { (ressort, depth) -> RessortCard(ressort, depth, onOpen) }
                    }
                }
        }
    }
}

@Composable
private fun RessortCard(ressort: Ressort, depth: Int, onOpen: (String) -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val lead = ressort.leader?.display?.takeIf { it.isNotBlank() }
    val quiet = if (ressort.quiet) stringResource(R.string.ressort_quiet_label) else null
    val sentence = listOfNotNull(ressort.name, lead?.let { stringResource(R.string.ressort_lead_line, it) }, quiet).joinToString(", ")
    Row(
        Modifier
            .padding(start = (depth * 16).coerceAtMost(48).dp)
            .fillMaxWidth()
            .alpha(if (ressort.quiet) QUIET_ALPHA else 1f)
            .focusRing(shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClickLabel = stringResource(R.string.ressort_open), role = Role.Button) { onOpen(ressort.id) }
            .semantics(mergeDescendants = true) { contentDescription = sentence }
            .padding(Spacing.s4),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RessortCode(ressort.abbreviation)
        Column(Modifier.weight(1f)) {
            Text(ressort.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            lead?.let { Text(stringResource(R.string.ressort_lead_line, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            quiet?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Die Seite eines Ressorts (Entwurf L35): Verantwortliche, Notizen, Links, Tipps und die Aufgaben des Ressorts. «Feedback» und
 * «Dokumente» folgen mit den Schnitten A2 und A5; die Route für Notizen und Links kennt die Warteschlange nicht, darum geht
 * «Notiz» nur mit Netz.
 */
@Composable
fun RessortScreen(
    vm: TasksViewModel,
    ressortId: String,
    rights: TaskRights,
    onBack: () -> Unit,
    onOpenTask: (String) -> Unit,
    /** Die Meldungen aus dem Eingang, die diesem Ressort zugeordnet sind; `null`, wenn die Rolle den Eingang nicht lesen darf. */
    feedback: List<InboxEntry>? = null,
) {
    val bundle by vm.ressortBundle.collectAsState()
    val tasks by vm.tasks.collectAsState()
    val error by vm.error.collectAsState()
    var writing by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val today = remember { todayInCamp() }
    val back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_ressorts)) { onBack() }

    when (val result = bundle) {
        null -> Column(Modifier.fillMaxSize()) {
            Header(title = stringResource(R.string.ressort_title_loading), back = back, focusOnTitle = true)
            CardSkeleton(stringResource(R.string.ressort_loading), count = 2)
        }
        is ApiResult.Failure -> Column(Modifier.fillMaxSize()) {
            Header(title = stringResource(R.string.ressort_title_loading), back = back, focusOnTitle = true)
            ErrorState(result.error, vm::retryRessort, onToPrograms = onBack, homeLabel = R.string.back_to_ressorts)
        }
        is ApiResult.Ok -> {
            val ressort = result.value.ressort
            val own = (tasks as? ApiResult.Ok)?.value.orEmpty().filter { it.ressortId == ressort.id || it.ressort?.id == ressort.id }.filterNot { it.done }
            Column(Modifier.fillMaxSize()) {
                Header(title = ressort.name, subtitle = stringResource(R.string.ressort_subtitle, ressort.abbreviation), back = back, focusOnTitle = true)
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s3).padding(bottom = Spacing.s4),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s4),
                ) {
                    error?.let { failure ->
                        ActionBanner(
                            NoticeKind.ERROR, stringResource(R.string.task_error),
                            failure.retry?.let { stringResource(R.string.retry) }, failure.retry?.let { retry -> { vm.dismissError(); retry() } },
                            vm::dismissError,
                        )
                    }
                    if (rights.editRessorts) {
                        SwitchRow(
                            R.drawable.ic_group, stringResource(R.string.ressort_quiet), stringResource(R.string.ressort_quiet_hint), ressort.quiet, true,
                        ) { vm.setRessortQuiet(ressort, it) }
                    }
                    SectionBlock(R.string.ressort_people) {
                        val lead = ressort.leader
                        val deputy = ressort.deputy
                        if (lead == null && deputy == null) {
                            Text(stringResource(R.string.ressort_no_people), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        lead?.let { PersonRow(it.display, stringResource(R.string.task_role_lead), it.imageUrl) }
                        deputy?.let { PersonRow(it.display, stringResource(R.string.ressort_role_deputy), it.imageUrl) }
                    }
                    SectionBlock(R.string.ressort_notes) {
                        if (result.value.notes.isEmpty()) {
                            Text(
                                stringResource(if (rights.editRessorts) R.string.ressort_notes_empty_edit else R.string.ressort_notes_empty),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        result.value.notes.forEach { NoteCard(it) }
                    }
                    if (result.value.links.isNotEmpty()) {
                        SectionBlock(R.string.ressort_links) {
                            result.value.links.forEach { link ->
                                val host = link.url.removePrefix("https://").substringBefore('/')
                                val shape = MaterialTheme.shapes.small
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = Sizes.touchTarget)
                                        .focusRing(shape)
                                        .clip(shape)
                                        .clickable(onClickLabel = stringResource(R.string.task_link_open), role = Role.Button) { openLink(uriHandler, link.url) },
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(link.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.cevi.accent)
                                    Text(host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    ressort.tips?.takeIf { it.isNotBlank() }?.let { tips ->
                        SectionBlock(R.string.ressort_tips) { Text(tips, style = MaterialTheme.typography.bodyLarge) }
                    }
                    feedback?.let { entries ->
                        SectionBlock(R.string.ressort_feedback) {
                            if (entries.isEmpty()) {
                                Text(stringResource(R.string.ressort_feedback_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            entries.forEach { entry ->
                                // Bei einer anonymen Meldung steht nur «anonym» und der Tag, nie ein Name.
                                val who = if (entry.anonymous) stringResource(R.string.inbox_anonymous) else entry.from?.display?.takeIf { it.isNotBlank() } ?: stringResource(R.string.inbox_unknown_sender)
                                InfoCard(description = stringResource(R.string.inbox_meta, who, formatDay(entry.createdOn)) + ", " + entry.text) {
                                    Text(stringResource(R.string.inbox_meta, who, formatDay(entry.createdOn)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(entry.text, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                    if (own.isNotEmpty()) {
                        SectionBlock(R.string.ressort_tasks) {
                            own.forEach { task ->
                                TaskCard(
                                    task, today, TaskActions(), canTick = false, selecting = false, selected = false,
                                    onOpen = { onOpenTask(task.id) }, onTick = {}, onToggleSelected = {},
                                )
                            }
                        }
                    }
                }
                if (rights.editRessorts) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.s3).padding(bottom = Spacing.s3)) {
                        PrimaryButton(stringResource(R.string.ressort_note_add), { writing = true }, Modifier.fillMaxWidth(), icon = R.drawable.ic_plus)
                    }
                }
            }
            if (writing) {
                TextDialog(
                    title = stringResource(R.string.ressort_note_add), label = stringResource(R.string.ressort_note_label), initial = "", maxChars = 10000,
                    confirmText = stringResource(R.string.save), onConfirm = { vm.addRessortNote(ressort.id, it); writing = false },
                    onDismiss = { writing = false }, multiline = true,
                )
            }
        }
    }
}

@Composable
private fun SectionBlock(title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun PersonRow(name: String, role: String, imageUrl: String?) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Avatar(name, size = 32.dp, imageUrl = imageUrl)
        Text(stringResource(R.string.task_person_role, name, role), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

/** Eine Notiz: lange Texte stehen auf drei Zeilen und klappen auf. */
@Composable
private fun NoteCard(note: RessortNote) {
    var expanded by remember { mutableStateOf(false) }
    val long = note.text.length > NOTE_FOLD || note.text.count { it == '\n' } >= 3
    val author = note.author?.display?.takeIf { it.isNotBlank() }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Text(note.text, style = MaterialTheme.typography.bodyLarge, maxLines = if (expanded || !long) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(author, formatDay(note.createdAt.take(10))).joinToString(", "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (long) TextActionButton(stringResource(if (expanded) R.string.note_collapse else R.string.note_expand), { expanded = !expanded })
    }
}

private const val NOTE_FOLD = 160

/** Ruhige Ressorts erscheinen blasser; der Text «Ruhig» steht dabei, die Farbe allein sagt es nicht. Bei 0,85 bleibt `onSurfaceVariant` im Tag-Thema über 4,5:1 (bei 0,72 nur 4,1:1). */
private const val QUIET_ALPHA = 0.85f
