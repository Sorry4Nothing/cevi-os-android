package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.CeviDialog
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.LabeledTextField
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.RoundButton
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi

private val BOTTOM_SPACE = Spacing.s8 + Spacing.s8 + Spacing.s8

// ---- Dokumente (L33) ----

/**
 * Dokumente (Entwurf L33): die Dateien des Lagers mit Art und Stichworten. Eine Datei öffnet sich über den Link, den der Server
 * liefert (https), dort, wo sie liegt: Die App lädt nichts herunter und baut keine Adresse selbst. Wer ändern darf, fügt einen
 * Link hinzu oder löscht den Eintrag; die Datei bleibt immer am Ablageort. Hochladen gibt es in der API nicht.
 */
@Composable
fun DocumentsScreen(frame: LiveFrame, archive: ArchiveViewModel, rights: ArchiveRights) {
    val state by archive.documents.collectAsState()
    val busy by archive.busy.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var actions by remember { mutableStateOf<CampDocument?>(null) }
    var deleting by remember { mutableStateOf<CampDocument?>(null) }
    val open = rememberLinkOpener()
    val downloads = LocalDownloads.current
    LaunchedEffect(frame.camp?.id) { archive.loadDocuments(silent = archive.documents.value is ApiResult.Ok) }

    ArchivePage(frame, archive, stringResource(R.string.docs_title)) {
        when (val result = state) {
            null -> CardSkeleton(stringResource(R.string.docs_loading))
            is ApiResult.Failure -> ErrorState(result.error, { archive.loadDocuments() }, onToPrograms = frame.onBack, homeLabel = R.string.back_to_camp)
            is ApiResult.Ok -> {
                if (result.value.isEmpty()) {
                    StatePage(R.drawable.ic_file, stringResource(R.string.docs_empty_title), stringResource(if (rights.editDocuments) R.string.docs_empty_text else R.string.docs_empty_text_read)) {
                        if (rights.editDocuments) PrimaryButton(stringResource(R.string.docs_add), { creating = true }, icon = R.drawable.ic_plus)
                    }
                } else {
                    LiveColumn(bottom = if (rights.editDocuments) BOTTOM_SPACE else Spacing.s6) {
                        result.value.forEach { document ->
                            DocumentRow(document, (rights.editDocuments && document.editable) || (downloads != null && document.file?.link != null), onOpen = { document.file?.link?.let(open) }, onActions = { actions = document })
                        }
                        Text(stringResource(R.string.docs_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (rights.editDocuments) {
                        PrimaryButton(stringResource(R.string.docs_add), { creating = true }, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.s3), icon = R.drawable.ic_plus)
                    }
                }
            }
        }
    }
    actions?.let { document ->
        val link = document.file?.link
        val delete = stringResource(R.string.docs_delete)
        ActionSheet(
            document.title.clip(TITLE_CLIP),
            buildList {
                if (downloads != null && link != null) add(stringResource(R.string.dl_action) to { downloads.downloadLink(document.id, document.title, link) })
                if (rights.editDocuments && document.editable) add(delete to { deleting = document })
            },
        ) { actions = null }
    }
    if (creating) {
        DocumentDialog(busy, onSave = { title, link, team -> archive.addDocument(title, link, team) { creating = false } }, onDismiss = { creating = false })
    }
    deleting?.let { document ->
        ConfirmDialog(
            stringResource(R.string.docs_delete_title, document.title.clip(TITLE_CLIP)), stringResource(R.string.docs_delete_text), stringResource(R.string.task_delete),
            onConfirm = { deleting = null; archive.deleteDocument(document) }, onDismiss = { deleting = null },
        )
    }
}

private const val TITLE_CLIP = 40

@Composable
private fun documentKindText(kind: DocumentKind): String = stringResource(
    when (kind) {
        DocumentKind.PDF -> R.string.docs_kind_pdf
        DocumentKind.WORD -> R.string.docs_kind_word
        DocumentKind.EXCEL -> R.string.docs_kind_excel
        DocumentKind.IMAGE -> R.string.docs_kind_image
        DocumentKind.LINK -> R.string.docs_kind_link
    },
)

/** Eine Zeile: Titel, Art und Stichworte; tippen öffnet die Datei, «Aktionen» bietet «Herunterladen» und (nur mit Recht) das Löschen an. */
@Composable
private fun DocumentRow(document: CampDocument, hasActions: Boolean, onOpen: () -> Unit, onActions: () -> Unit) {
    val link = document.file?.link
    val detail = if (link == null) stringResource(R.string.docs_file_missing) else (listOf(documentKindText(DocumentKind.from(document.kindWire))) + document.keywords).joinToString(", ")
    val shape = MaterialTheme.shapes.small
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = Sizes.touchTarget)
                .focusRing(shape)
                .clip(shape)
                .then(if (link != null) Modifier.clickable(onClickLabel = stringResource(R.string.docs_open_named, document.title), role = Role.Button, onClick = onOpen) else Modifier)
                .semantics(mergeDescendants = true) { contentDescription = document.title + ", " + detail }
                .padding(vertical = Spacing.s1),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_file), contentDescription = null, tint = MaterialTheme.cevi.accent, modifier = Modifier.size(Sizes.icon))
            Column(Modifier.weight(1f)) {
                Text(document.title, style = MaterialTheme.typography.bodyLarge)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (hasActions) RoundButton(HeaderButton(R.drawable.ic_more, stringResource(R.string.docs_actions_named, document.title), onActions))
    }
}

/** Titel, Link und Sichtbarkeit für das Team. Der Server prüft den Link streng; ein anderer als https geht gar nicht erst hinaus. */
@Composable
private fun DocumentDialog(busy: Boolean, onSave: (String, String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var team by remember { mutableStateOf(false) }
    CeviDialog(
        title = stringResource(R.string.docs_add),
        onDismiss = onDismiss,
        confirmButton = { PrimaryButton(stringResource(R.string.save), { onSave(title, link, team) }, enabled = title.isNotBlank() && link.trim().startsWith("https://"), loading = busy) },
        dismissButton = { TextActionButton(stringResource(R.string.cancel), onDismiss) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            LabeledTextField(stringResource(R.string.docs_field_title), title, { title = it }, DOC_TITLE_MAX, Modifier.fillMaxWidth())
            LabeledTextField(stringResource(R.string.docs_field_link), link, { link = it }, LINK_MAX, Modifier.fillMaxWidth(), hint = stringResource(R.string.docs_field_link_hint), keyboardType = KeyboardType.Uri)
            ToggleRow(stringResource(R.string.docs_team_visible), team, { team = it })
        }
    }
}

private const val DOC_TITLE_MAX = 200
private const val LINK_MAX = 2048

// ---- Adressbuch (L34) ----

/**
 * Adressbuch (Entwurf L34): externe Kontakte des Lagers, nur für die Lagerleitung (Personendaten Dritter). Die Daten bleiben im
 * Arbeitsspeicher, stehen in keinem Log und gehen beim Schliessen des Lagers weg. Anrufen öffnet die Telefon-App mit der Nummer;
 * «Fürs nächste Lager behalten» nimmt den Kontakt aus der Aufräumfrist.
 */
@Composable
fun AddressBookScreen(frame: LiveFrame, archive: ArchiveViewModel, rights: ArchiveRights) {
    val state by archive.contacts.collectAsState()
    val busy by archive.busy.collectAsState()
    val context = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    var actions by remember { mutableStateOf<Contact?>(null) }
    var deleting by remember { mutableStateOf<Contact?>(null) }
    LaunchedEffect(frame.camp?.id, rights.readAddressBook) { if (rights.readAddressBook) archive.loadContacts(silent = archive.contacts.value is ApiResult.Ok) }

    ArchivePage(frame, archive, stringResource(R.string.ab_title)) {
        if (!rights.readAddressBook) {
            ErrorState(ApiError.FORBIDDEN, {}, onToPrograms = frame.onBack, noAccessText = R.string.ab_no_access, homeLabel = R.string.back_to_camp)
            return@ArchivePage
        }
        when (val result = state) {
            null -> CardSkeleton(stringResource(R.string.ab_loading))
            is ApiResult.Failure -> ErrorState(result.error, { archive.loadContacts() }, onToPrograms = frame.onBack, noAccessText = R.string.ab_no_access, homeLabel = R.string.back_to_camp)
            is ApiResult.Ok -> {
                if (result.value.isEmpty()) {
                    StatePage(R.drawable.ic_book, stringResource(R.string.ab_empty_title), stringResource(R.string.ab_empty_text)) {
                        if (rights.editAddressBook) PrimaryButton(stringResource(R.string.ab_add), { creating = true }, icon = R.drawable.ic_plus)
                    }
                } else {
                    LiveColumn(bottom = if (rights.editAddressBook) BOTTOM_SPACE else Spacing.s6) {
                        StatusChip(ChipKind.AGE_GROUP, stringResource(R.string.ab_leadership_only))
                        NoticeBanner(NoticeKind.INFO, stringResource(R.string.ab_cleanup_note))
                        result.value.forEach { contact ->
                            ContactCard(contact, rights.editAddressBook, { dial(context, it) }, { archive.setKeep(contact, it) }, { actions = contact })
                        }
                    }
                    if (rights.editAddressBook) {
                        PrimaryButton(stringResource(R.string.ab_add), { creating = true }, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.s3), icon = R.drawable.ic_plus)
                    }
                }
            }
        }
    }
    actions?.let { contact -> ActionSheet(contact.name.clip(TITLE_CLIP), listOf(stringResource(R.string.ab_delete) to { deleting = contact })) { actions = null } }
    if (creating) ContactDialog(busy, onSave = { draft -> archive.addContact(draft) { creating = false } }, onDismiss = { creating = false })
    deleting?.let { contact ->
        ConfirmDialog(
            stringResource(R.string.ab_delete_title, contact.name.clip(TITLE_CLIP)), stringResource(R.string.ab_delete_text), stringResource(R.string.task_delete),
            onConfirm = { deleting = null; archive.deleteContact(contact) }, onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ContactCard(contact: Contact, edit: Boolean, onCall: (String) -> Unit, onKeep: (Boolean) -> Unit, onActions: () -> Unit) {
    InfoCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { }) {
                Text(contact.name, style = MaterialTheme.typography.titleMedium)
                contact.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (edit) RoundButton(HeaderButton(R.drawable.ic_more, stringResource(R.string.ab_actions_named, contact.name), onActions))
        }
        contact.phone?.takeIf { it.isNotBlank() }?.let { phone ->
            val shape = MaterialTheme.shapes.small
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.touchTarget)
                    .focusRing(shape)
                    .clip(shape)
                    .clickable(onClickLabel = stringResource(R.string.ab_call, contact.name), role = Role.Button) { onCall(phone) },
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_phone), contentDescription = null, tint = MaterialTheme.cevi.accent, modifier = Modifier.size(Sizes.icon))
                Text(phone, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.cevi.accent)
            }
        }
        contact.email?.takeIf { it.isNotBlank() }?.let { email ->
            val spoken = stringResource(R.string.ab_email_line, email)
            Text(email, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { contentDescription = spoken })
        }
        contact.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (edit) ToggleRow(stringResource(R.string.ab_keep), contact.keep, onKeep)
    }
}

/** Die Angaben zu einem neuen Kontakt; nur der Name ist Pflicht. Der Server prüft Telefon und E-Mail. */
@Composable
private fun ContactDialog(busy: Boolean, onSave: (ContactDraft) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(ContactDraft()) }
    CeviDialog(
        title = stringResource(R.string.ab_add),
        onDismiss = onDismiss,
        confirmButton = { PrimaryButton(stringResource(R.string.save), { onSave(draft) }, enabled = draft.name.isNotBlank(), loading = busy) },
        dismissButton = { TextActionButton(stringResource(R.string.cancel), onDismiss) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            LabeledTextField(stringResource(R.string.ab_field_name), draft.name, { draft = draft.copy(name = it) }, NAME_MAX, Modifier.fillMaxWidth())
            LabeledTextField(stringResource(R.string.ab_field_role), draft.role, { draft = draft.copy(role = it) }, NAME_MAX, Modifier.fillMaxWidth())
            LabeledTextField(stringResource(R.string.ab_field_group), draft.group, { draft = draft.copy(group = it) }, NAME_MAX, Modifier.fillMaxWidth())
            LabeledTextField(stringResource(R.string.ab_field_phone), draft.phone, { draft = draft.copy(phone = it) }, PHONE_MAX, Modifier.fillMaxWidth(), keyboardType = KeyboardType.Phone)
            LabeledTextField(stringResource(R.string.ab_field_email), draft.email, { draft = draft.copy(email = it) }, EMAIL_MAX, Modifier.fillMaxWidth(), keyboardType = KeyboardType.Email)
            LabeledTextField(stringResource(R.string.ab_field_note), draft.note, { draft = draft.copy(note = it) }, NOTE_MAX, Modifier.fillMaxWidth(), minLines = 3)
            ToggleRow(stringResource(R.string.ab_keep), draft.keep, { draft = draft.copy(keep = it) })
        }
    }
}

private const val NAME_MAX = 100
private const val PHONE_MAX = 30
private const val EMAIL_MAX = 254
private const val NOTE_MAX = 2000

// ---- Suche (L36) ----

/**
 * Suche (Entwurf L36): Der Server sucht in den Texten des Lagers, versteht Wortstämme und kleine Tippfehler und liefert nur, was die
 * Rolle auch in den Listen sieht. Die App filtert nichts selbst. Ein Treffer öffnet das Objekt über das Modul, dem es gehört.
 */
@Composable
fun SearchScreen(frame: LiveFrame, archive: ArchiveViewModel, onOpen: (SearchDestination) -> Unit) {
    val query by archive.query.collectAsState()
    val result by archive.search.collectAsState()
    val searching by archive.searching.collectAsState()
    ArchivePage(frame, archive, stringResource(R.string.search_title)) {
        Column(Modifier.fillMaxSize()) {
            LabeledTextField(
                stringResource(R.string.search_field), query, archive::setQuery, SEARCH_MAX,
                Modifier.fillMaxWidth().padding(horizontal = Spacing.s3),
                hint = stringResource(R.string.search_hint),
                imeAction = ImeAction.Search, onImeAction = archive::runSearch,
                trailing = { RoundButton(HeaderButton(R.drawable.ic_search, stringResource(R.string.search_submit), archive::runSearch)) },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val state = result
                if (searching) {
                    CardSkeleton(stringResource(R.string.search_loading))
                } else when (state) {
                    null -> Text(
                        stringResource(R.string.search_start_text), Modifier.padding(Spacing.s3), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is ApiResult.Failure -> ErrorState(state.error, archive::runSearch)
                    is ApiResult.Ok -> if (state.value.hits.isEmpty()) {
                        StatePage(R.drawable.ic_search, stringResource(R.string.search_empty_title), stringResource(R.string.search_empty_text))
                    } else {
                        LiveColumn {
                            SearchRules.grouped(state.value.hits).forEach { (type, hits) ->
                                LiveSection(stringResource(searchTypeText(type))) { hits.forEach { SearchRow(it, onOpen) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val SEARCH_MAX = 100

private fun searchTypeText(type: String): Int = when (type) {
    "aufgabe" -> R.string.search_type_aufgabe
    "hoeck" -> R.string.search_type_hoeck
    "traktandum" -> R.string.search_type_traktandum
    "protokoll" -> R.string.search_type_protokoll
    "beschluss" -> R.string.search_type_beschluss
    "pinnwand" -> R.string.search_type_pinnwand
    "eingang" -> R.string.search_type_eingang
    "ressort" -> R.string.search_type_ressort
    "tagesseite" -> R.string.search_type_tagesseite
    "meilenstein" -> R.string.search_type_meilenstein
    "dokument" -> R.string.search_type_dokument
    "info" -> R.string.search_type_info
    "teilnehmer" -> R.string.search_type_teilnehmer
    "zimmer" -> R.string.search_type_zimmer
    "fahrt" -> R.string.search_type_fahrt
    "gruppe" -> R.string.search_type_gruppe
    "dienst" -> R.string.search_type_dienst
    "einkauf" -> R.string.search_type_einkauf
    "packliste" -> R.string.search_type_packliste
    "notfall" -> R.string.search_type_notfall
    "adressbuch" -> R.string.search_type_adressbuch
    else -> R.string.search_type_weiteres
}

@Composable
private fun SearchRow(hit: SearchHit, onOpen: (SearchDestination) -> Unit) {
    val destination = SearchRules.destination(hit)
    val shape = MaterialTheme.shapes.small
    val spoken = listOfNotNull(hit.title, hit.excerpt).joinToString(", ")
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .focusRing(shape)
            .clip(shape)
            .then(if (destination != null) Modifier.clickable(onClickLabel = stringResource(R.string.search_open_named, hit.title), role = Role.Button) { onOpen(destination) } else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .padding(vertical = Spacing.s1),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(hit.title, style = MaterialTheme.typography.bodyLarge)
        hit.excerpt?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

// ---- Ausdrucke ----

private fun printoutText(printout: Printout): Int = when (printout) {
    Printout.SHIFTS -> R.string.print_schichtplan
    Printout.ROOMS -> R.string.print_zimmerliste
    Printout.DOOR_SIGNS -> R.string.print_tuerschilder
    Printout.GROUPS -> R.string.print_gruppenlisten
    Printout.TRIPS -> R.string.print_fahrtenliste
    Printout.ORGCHART -> R.string.print_organigramm
}

/**
 * Ausdrucke: Der Server erzeugt den Aushang als PDF (er speichert ihn nirgends); die App teilt ihn über die Systemauswahl, etwa zum
 * Drucken. Dafür liegt die Datei kurz im Cache. Wer das Recht zu einem Aushang nicht hat, bekommt vom Server «Kein Recht».
 */
@Composable
fun PrintoutsScreen(frame: LiveFrame, archive: ArchiveViewModel) {
    val printing by archive.printing.collectAsState()
    val downloads = LocalDownloads.current
    ArchivePage(frame, archive, stringResource(R.string.print_title)) {
        LiveColumn {
            Text(stringResource(R.string.print_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Printout.entries.forEach { printout ->
                val name = stringResource(printoutText(printout))
                val working = printing == printout
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
                InfoCard(
                    Modifier.weight(1f),
                    description = stringResource(R.string.print_share_named, name),
                    onClick = if (printing == null) ({ archive.loadPrintout(printout) }) else null,
                    onClickLabel = stringResource(R.string.print_share_named, name),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s3), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_send), contentDescription = null, tint = MaterialTheme.cevi.accent, modifier = Modifier.size(Sizes.icon))
                        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (working) Text(stringResource(R.string.print_working), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (downloads != null && printing == null) {
                    RoundButton(HeaderButton(R.drawable.ic_download, stringResource(R.string.dl_action_named, name)) { archive.loadPrintout(printout, download = true) })
                }
                }
            }
        }
    }
}
