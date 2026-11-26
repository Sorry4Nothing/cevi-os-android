package io.github.sorry4nothing.cevios.lager

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.LabeledTextField
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PickPill
import io.github.sorry4nothing.cevios.ui.pickGroup
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.rowAction
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Eine Person oder ein Kind in einer Gruppe, das die Person gerade verwaltet. */
private data class GroupMemberRef(val group: Group, val participantId: String?, val personId: String?, val name: String)

/**
 * Programmgruppen (Entwurf L22): Karten mit Kindern und Leitung, «Automatisch mischen» als Vorschau und die Zähler «Ohne Gruppe»
 * und «Nicht dabei». Wer einteilen darf, ordnet Kinder und Leitende zu, verschiebt sie («Verschieben nach …») und wählt Kinder ab.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupsScreen(
    vm: PlanningViewModel,
    camp: Camp?,
    rights: PlanRights,
    queue: QueueState,
    online: Boolean,
    onOpenQueue: () -> Unit,
    onBack: () -> Unit,
) {
    val state by vm.groups.collectAsState()
    val mix by vm.mix.collectAsState()
    val busy by vm.busy.collectAsState()
    var editor by remember { mutableStateOf<Group?>(null) }
    var creating by remember { mutableStateOf(false) }
    var assigning by remember { mutableStateOf<Group?>(null) }
    var memberActions by remember { mutableStateOf<GroupMemberRef?>(null) }
    var moving by remember { mutableStateOf<GroupMemberRef?>(null) }
    var deleting by remember { mutableStateOf<Group?>(null) }
    var showExcluded by remember { mutableStateOf(false) }
    var mixCount by remember { mutableStateOf("") }
    LaunchedEffect(camp?.id) { vm.loadGroups() }

    CampPage(
        title = stringResource(R.string.groups_title),
        camp = camp, queue = queue, online = online, onOpenQueue = onOpenQueue, writer = vm.writer,
        back = HeaderButton(R.drawable.ic_back, stringResource(R.string.back_to_camp), onBack),
        banners = { PlanNoticeBanner(vm) },
    ) {
        when (val result = state) {
            null -> CardSkeleton(stringResource(R.string.groups_loading))
            is ApiResult.Failure -> ErrorState(result.error, { vm.loadGroups() })
            is ApiResult.Ok -> {
                val list = result.value
                val count = mixCount.ifBlank { maxOf(2, list.groups.size).toString() }
                val countValue = count.toIntOrNull()?.takeIf { it in 2..20 }
                if (list.groups.isEmpty() && !rights.edit) {
                    StatePage(R.drawable.ic_group, stringResource(R.string.groups_empty_title), stringResource(R.string.groups_empty_team))
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(start = Spacing.s3, end = Spacing.s3, bottom = if (rights.edit) 96.dp else Spacing.s4),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                    ) {
                        if (list.groups.isEmpty()) item {
                            Column(Modifier.padding(vertical = Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                                Text(stringResource(R.string.groups_empty_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                                Text(stringResource(R.string.groups_empty_text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(list.groups, key = { it.id }) { group ->
                            GroupCard(
                                group, rights,
                                onAssign = { assigning = group },
                                onEdit = { editor = group },
                                onMember = { memberActions = it },
                            )
                        }
                        item {
                            InfoCard {
                                Text(pluralStringResource(R.plurals.groups_without, list.withoutGroup, list.withoutGroup), style = MaterialTheme.typography.bodyLarge)
                                Text(pluralStringResource(R.plurals.groups_excluded, list.excluded, list.excluded), style = MaterialTheme.typography.bodyLarge)
                                if (rights.edit && list.excluded > 0) TextActionButton(stringResource(R.string.groups_show_excluded), { showExcluded = true })
                            }
                        }
                        if (rights.edit) item {
                            InfoCard {
                                Text(stringResource(R.string.groups_mix_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                                Text(stringResource(R.string.groups_mix_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                LabeledTextField(
                                    stringResource(R.string.groups_mix_count), count, { mixCount = it.filter(Char::isDigit) }, 2, Modifier.fillMaxWidth(),
                                    keyboardType = KeyboardType.Number,
                                    hint = stringResource(R.string.groups_mix_count_hint),
                                )
                                OutlinedPillButton(
                                    stringResource(R.string.plan_preview), { countValue?.let(vm::previewMix) }, Modifier.fillMaxWidth(),
                                    enabled = countValue != null, loading = busy, loadingText = stringResource(R.string.plan_loading_preview),
                                )
                            }
                        }
                    }
                    if (rights.edit) {
                        PrimaryButton(stringResource(R.string.groups_add), { creating = true }, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.s3), icon = R.drawable.ic_plus)
                    }
                }
            }
        }
    }

    mix?.let { result ->
        MixPreviewSheet(
            result, busy,
            onApply = { vm.applyMix(result.groups.size, result.seed) },
            onDismiss = vm::clearMix,
        )
    }
    assigning?.let { group ->
        val groups = (state as? ApiResult.Ok)?.value
        GroupAssignSheet(vm, group, groups, busy, onDismiss = { assigning = null })
    }
    if (showExcluded) ExcludedSheet(vm, onDismiss = { showExcluded = false })
    memberActions?.let { ref ->
        val others = (state as? ApiResult.Ok)?.value?.groups.orEmpty().filter { it.id != ref.group.id }
        val entries = buildList {
            add(stringResource(R.string.groups_take_out) to { vm.assignToGroup(null, listOfNotNull(ref.participantId), listOfNotNull(ref.personId), leader = false) })
            if (others.isNotEmpty()) add(stringResource(R.string.rooms_move) to { moving = ref })
        }
        ActionSheet(ref.name, entries) { memberActions = null }
    }
    moving?.let { ref ->
        val leader = ref.personId != null && ref.group.people.firstOrNull { it.personId == ref.personId }?.role == "leitung"
        val choices = (state as? ApiResult.Ok)?.value?.groups.orEmpty().filter { it.id != ref.group.id }.map { Choice(it.id, it.name) }
        ChoiceSheet(
            stringResource(R.string.rooms_move_title, ref.name), choices, initial = null,
            onApply = { key -> moving = null; key?.let { vm.assignToGroup(it, listOfNotNull(ref.participantId), listOfNotNull(ref.personId), leader) } },
            onDismiss = { moving = null },
        )
    }
    if (creating) {
        TextDialog(
            stringResource(R.string.groups_add), stringResource(R.string.groups_field_name), "", 100, stringResource(R.string.save),
            onConfirm = { creating = false; vm.createGroup(it) }, onDismiss = { creating = false },
        )
    }
    editor?.let { group ->
        GroupEditDialog(
            group,
            onSave = { name ->
                editor = null
                if (name.trim() != group.name) vm.changeGroup(group, mapOf<String, JsonElement>("name" to JsonPrimitive(name.trim())), group.name)
            },
            onDelete = { deleting = group; editor = null },
            onDismiss = { editor = null },
        )
    }
    deleting?.let { group ->
        ConfirmDialog(
            stringResource(R.string.groups_delete_title, group.name), stringResource(R.string.groups_delete_text), stringResource(R.string.task_delete),
            onConfirm = { deleting = null; vm.deleteGroup(group) }, onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun GroupCard(group: Group, rights: PlanRights, onAssign: () -> Unit, onEdit: () -> Unit, onMember: (GroupMemberRef) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val leaders = group.people.filter { it.role == "leitung" }
    val members = group.people.filter { it.role != "leitung" }
    fun personText(p: GroupPerson) = p.ceviName?.takeIf { it.isNotBlank() }?.let { "${p.name} ($it)" } ?: p.name
    val count = pluralStringResource(R.plurals.groups_children, group.participants.size, group.participants.size)
    val leadership = if (leaders.isEmpty()) null else stringResource(R.string.groups_leadership, leaders.joinToString(", ") { personText(it) })
    val others = if (members.isEmpty()) null else stringResource(R.string.groups_members, members.joinToString(", ") { personText(it) })
    InfoCard {
        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = joinDetail(group.name, count, leadership, others).orEmpty() }, verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(count, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOfNotNull(leadership, others).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (group.participants.isNotEmpty() || group.people.isNotEmpty()) {
            val toggle = stringResource(if (expanded) R.string.groups_hide_members else R.string.groups_show_members)
            TextActionButton(toggle, { expanded = !expanded }, Modifier.rowAction(toggle, group.name))
        }
        if (expanded) {
            group.people.forEach { p ->
                MemberRow(personText(p), joinDetail(stringResource(if (p.role == "leitung") R.string.groups_role_leader else R.string.groups_role_member)), rights.edit) {
                    onMember(GroupMemberRef(group, null, p.personId, p.name))
                }
            }
            group.participants.forEach { c ->
                MemberRow("${c.firstName} ${c.lastName}", joinDetail(c.stage), rights.edit) { onMember(GroupMemberRef(group, c.id, null, "${c.firstName} ${c.lastName}")) }
            }
        }
        if (rights.edit) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                val assign = stringResource(R.string.groups_assign)
                val edit = stringResource(R.string.groups_edit)
                OutlinedPillButton(assign, onAssign, Modifier.rowAction(assign, group.name), icon = R.drawable.ic_plus)
                TextActionButton(edit, onEdit, Modifier.rowAction(edit, group.name))
            }
        }
    }
}

@Composable
private fun MemberRow(name: String, detail: String?, editable: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.small
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .focusRing(shape)
            .then(if (editable) Modifier.clickable(onClickLabel = stringResource(R.string.rooms_member_actions), role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = joinDetail(name, detail).orEmpty() },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3), verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = Spacing.s1)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun GroupEditDialog(group: Group, onSave: (String) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(group.name) }
    io.github.sorry4nothing.cevios.ui.CeviDialog(
        title = stringResource(R.string.groups_edit),
        onDismiss = onDismiss,
        confirmButton = { PrimaryButton(stringResource(R.string.save), { onSave(name) }, enabled = name.isNotBlank()) },
        dismissButton = {
            TextActionButton(stringResource(R.string.groups_delete), onDelete)
            TextActionButton(stringResource(R.string.cancel), onDismiss)
        },
    ) { LabeledTextField(stringResource(R.string.groups_field_name), name, { name = it }, 100, Modifier.fillMaxWidth()) }
}

/** Kinder oder Leitende wählen und einer Gruppe zuordnen; wer in einer anderen Gruppe steht, wird verschoben (die Zeile nennt die bisherige). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GroupAssignSheet(vm: PlanningViewModel, group: Group, groups: GroupList?, busy: Boolean, onDismiss: () -> Unit) {
    var leaders by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(emptySet<String>()) }
    val participants by vm.participants.collectAsState()
    val people by vm.people.collectAsState()
    LaunchedEffect(Unit) { vm.loadParticipants(silent = true) }
    val where = buildMap {
        groups?.groups?.forEach { g ->
            g.participants.forEach { put(it.id, g) }
            g.people.forEach { put(it.personId, g) }
        }
    }
    val noGroup = stringResource(R.string.groups_no_group)
    val items = if (leaders) {
        people.map { PickItem(it.id, it.display, where[it.id]?.let { g -> stringResource(R.string.groups_now_in, g.name) }) }
    } else {
        PlanRules.sortedParticipants((participants as? ApiResult.Ok)?.value.orEmpty().filter { !it.excluded })
            .map { PickItem(it.id, it.fullName, joinDetail(it.stage, where[it.id]?.name?.let { name -> stringResource(R.string.groups_now_in, name) } ?: noGroup)) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Text(stringResource(R.string.groups_assign_title, group.name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            FlowRow(Modifier.pickGroup(stringResource(R.string.group_assign_kind)), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                PickPill(stringResource(R.string.groups_tab_children), !leaders, { leaders = false; picked = emptySet() })
                PickPill(stringResource(R.string.groups_tab_leaders), leaders, { leaders = true; picked = emptySet() })
            }
            if (items.isEmpty()) Text(stringResource(if (leaders) R.string.groups_nobody_known else R.string.groups_nobody_children), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            items.forEach { item -> CheckRow(item.label, item.detail, item.key in picked, { picked = picked.toggled(item.key) }) }
            PrimaryButton(
                stringResource(R.string.groups_assign_confirm),
                { vm.assignToGroup(group.id, if (leaders) emptyList() else picked.toList(), if (leaders) picked.toList() else emptyList(), leaders, onDismiss) },
                Modifier.fillMaxWidth().padding(top = Spacing.s2), enabled = picked.isNotEmpty(), loading = busy, loadingText = stringResource(R.string.plan_saving),
            )
            OutlinedPillButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
}

/** Die abgewählten Kinder («nicht dabei») mit «Wieder dabei». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExcludedSheet(vm: PlanningViewModel, onDismiss: () -> Unit) {
    val participants by vm.participants.collectAsState()
    LaunchedEffect(Unit) { vm.loadParticipants(silent = true) }
    val excluded = PlanRules.sortedParticipants((participants as? ApiResult.Ok)?.value.orEmpty().filter { it.excluded })
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Text(stringResource(R.string.groups_excluded_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (excluded.isEmpty()) Text(stringResource(R.string.groups_excluded_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            excluded.forEach { person ->
                Row(Modifier.fillMaxWidth().heightIn(min = Sizes.touchTarget), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Column(Modifier.weight(1f)) {
                        Text(person.fullName, style = MaterialTheme.typography.bodyLarge)
                        person.stage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    val include = stringResource(R.string.participants_include)
                    TextActionButton(include, { vm.setExcluded(listOf(person.id), false) }, Modifier.rowAction(include, person.fullName))
                }
            }
            OutlinedPillButton(stringResource(R.string.close), onDismiss, Modifier.fillMaxWidth())
        }
    }
}

/** Die Vorschau des Mischens: je Gruppe die Zahl und die Namen. «Übernehmen» schreibt genau diese Einteilung (gleicher Startwert). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MixPreviewSheet(result: MixResult, busy: Boolean, onApply: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(stringResource(R.string.groups_preview_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.groups_preview_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            result.groups.forEach { group ->
                val count = pluralStringResource(R.plurals.groups_children, group.count, group.count)
                Column(
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription = group.name + ", " + count + ", " + group.participants.joinToString(", ") { "${it.firstName} ${it.lastName}" }
                    },
                    verticalArrangement = Arrangement.spacedBy(Spacing.s1),
                ) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium)
                    Text(count, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(group.participants.joinToString(", ") { "${it.firstName} ${it.lastName.take(1)}." }, style = MaterialTheme.typography.bodyMedium)
                }
            }
            PrimaryButton(stringResource(R.string.task_apply), onApply, Modifier.fillMaxWidth(), loading = busy, loadingText = stringResource(R.string.plan_saving))
            OutlinedPillButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
}
