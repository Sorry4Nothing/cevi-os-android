package io.github.sorry4nothing.cevios.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.chat.initials
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.programs.EditState
import io.github.sorry4nothing.cevios.programs.TabPill
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.Header
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.InitialText
import io.github.sorry4nothing.cevios.ui.LoadingSkeleton
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.scaledWithFont
import io.github.sorry4nothing.cevios.ui.theme.Pill
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing

/**
 * Wer bei einem Programm da war, für Leitende, Stufenleitungen und Admins: Reiter Kinder und Leitende, je Person «Da» oder
 * «Nicht da», ein zweiter Tipp auf die gewählte Antwort nimmt sie zurück. Erfassen geht ab Beginn bis 14 Tage danach, sonst
 * ist der Bildschirm nur zum Lesen. Die Zusage ändert er nie. `eyebrow` nennt Programm und Tag.
 */
@Composable
fun AttendanceScreen(
    eyebrow: String?,
    programId: String,
    state: ApiResult<AttendanceOverview>?,
    save: EditState,
    onRecord: (personId: String, leader: Boolean, present: Boolean?) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Header(
            title = stringResource(R.string.attendance_title),
            eyebrow = eyebrow,
            back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_program), onBack),
            focusOnTitle = true,
        )
        when (state) {
            null -> LoadingSkeleton(stringResource(R.string.attendance_loading))
            is ApiResult.Failure -> ErrorState(state.error, onRetry, onToPrograms = onBack)
            is ApiResult.Ok -> AttendanceContent(state.value, programId, save, onRecord)
        }
    }
}

@Composable
private fun AttendanceContent(
    data: AttendanceOverview,
    programId: String,
    save: EditState,
    onRecord: (String, Boolean, Boolean?) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val leaders = tab == 1
    val people = if (leaders) data.leaders else data.children
    val index = data.indexOf(programId)
    val occasion = data.occasions.getOrNull(index)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        Row(Modifier.padding(horizontal = Spacing.s5).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            TabPill(stringResource(R.string.registrations_tab_children, data.children.size), !leaders) { tab = 0 }
            TabPill(stringResource(R.string.registrations_tab_leaders, data.leaders.size), leaders) { tab = 1 }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.s4, end = Spacing.s4, bottom = Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val notice = when {
                occasion == null -> R.string.attendance_not_listed
                !occasion.past -> R.string.attendance_not_started
                !occasion.recordable -> R.string.attendance_closed
                else -> null
            }
            notice?.let { item { NoticeBanner(NoticeKind.INFO, stringResource(it)) } }
            save.error?.let { item { NoticeBanner(NoticeKind.ERROR, stringResource(it.text)) } }
            if (occasion != null) {
                if (people.isEmpty()) {
                    item { NoticeBanner(NoticeKind.INFO, stringResource(if (leaders) R.string.attendance_empty_leaders else R.string.attendance_empty_children)) }
                }
                items(people, key = { it.id }) { person ->
                    PersonRow(
                        person,
                        mark = person.marks.getOrNull(index),
                        canRecord = occasion.recordable,
                        onSet = { onRecord(person.id, leaders, it) },
                    )
                }
            }
        }
    }
}

/**
 * Eine Person: Initialen, Name (bei Leitenden mit Rolle) und der Stand als Chip. Darunter, solange sich erfassen lässt, die
 * zwei Antworten. Name und Stand lesen sich als ein Element; jede Antwort heisst «Lena Meier, da».
 */
@Composable
private fun PersonRow(person: AttendancePerson, mark: String?, canRecord: Boolean, onSet: (Boolean?) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val name = person.displayName
    val present = mark == "da"
    val absent = mark == "nicht_da"
    val kind = if (present) ChipKind.YES else if (absent) ChipKind.NO else ChipKind.PENDING
    val word = stringResource(if (present) R.string.attendance_present else if (absent) R.string.attendance_absent else R.string.attendance_unrecorded)
    Column(
        Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLow, MaterialTheme.shapes.medium)
            .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(
            Modifier.semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(scaledWithFont(36.dp)).background(scheme.secondaryContainer, Pill), contentAlignment = Alignment.Center) {
                InitialText(initials(name), 13.sp, scheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                if (person.role != null) {
                    Text(
                        stringResource(if (person.isGroupLeader) R.string.role_group_leader else R.string.role_leader),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            StatusChip(kind, word)
        }
        if (canRecord) {
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Answer(stringResource(R.string.attendance_present), stringResource(R.string.attendance_answer_present, name), present) { onSet(if (present) null else true) }
                Answer(stringResource(R.string.attendance_absent), stringResource(R.string.attendance_answer_absent, name), absent) { onSet(if (absent) null else false) }
            }
        }
    }
}

/** Eine Antwort als Pille (mindestens 48 dp hoch): gewählt hell gefüllt, sonst mit Rand. Rolle «Auswahl», der Name steht in der Beschreibung. */
@Composable
private fun RowScope.Answer(text: String, description: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = Sizes.touchTarget)
            .focusRing(Pill)
            .clip(Pill)
            .then(if (selected) Modifier.background(scheme.onSecondaryContainer) else Modifier.border(1.dp, scheme.outline, Pill))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) scheme.secondaryContainer else scheme.onSurfaceVariant,
        )
    }
}
