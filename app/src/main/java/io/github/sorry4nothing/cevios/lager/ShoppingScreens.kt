package io.github.sorry4nothing.cevios.lager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.ActionBanner
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.LabeledTextField
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PickPill
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.RoundButton
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Einkaufsliste (Entwurf L28): nach Abteilung, gleiche Artikel zusammengezählt, «Nur was ich einkaufe», Haken beim Einkaufen.
 * Wer `einkauf.bearbeiten` hat, legt Artikel an (mit Vorschlägen beim Tippen), ändert und löscht sie; der Haken springt zurück, wenn der
 * Server ihn nicht annimmt. «Als Text kopieren» legt die offenen Artikel in die Zwischenablage des Geräts (ohne Namen).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShoppingScreen(frame: LiveFrame) {
    val vm = frame.vm
    val state by vm.shopping.collectAsState()
    val onlyMine by vm.onlyMine.collectAsState()
    val undo by vm.shopUndo.collectAsState()
    val busy by vm.busy.collectAsState()
    val context = LocalContext.current
    var editor by remember { mutableStateOf<ShopLine?>(null) }
    var creating by remember { mutableStateOf(false) }
    var actions by remember { mutableStateOf<ShopLine?>(null) }
    var deleting by remember { mutableStateOf<ShopLine?>(null) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(frame.camp?.id) { vm.loadShopping(silent = vm.shopping.value is ApiResult.Ok) }
    val edit = frame.rights.editShopping
    val titles = ShopGroup.entries.associateWith { stringResource(shopGroupRes(it)) }

    LivePage(frame, stringResource(R.string.shop_title)) {
        when (val result = state) {
            null -> CardSkeleton(stringResource(R.string.shop_loading))
            is ApiResult.Failure -> ErrorState(result.error, { vm.loadShopping() })
            is ApiResult.Ok -> {
                val sections = LiveRules.shopSections(result.value)
                if (sections.isEmpty() && !onlyMine) {
                    StatePage(R.drawable.ic_list_check, stringResource(R.string.shop_empty_title), stringResource(R.string.shop_empty_text)) {
                        if (edit) PrimaryButton(stringResource(R.string.shop_add), { creating = true }, icon = R.drawable.ic_plus)
                    }
                } else {
                    LiveColumn(bottom = if (edit) BOTTOM_SPACE else Spacing.s6) {
                        undo?.let { item ->
                            ActionBanner(
                                NoticeKind.INFO, stringResource(R.string.shop_added, item.name), stringResource(R.string.undo), vm::undoShopAdd, vm::dismissShopUndo,
                            )
                        }
                        if (copied) ActionBanner(NoticeKind.INFO, stringResource(R.string.shop_copied), null, null, onClose = { copied = false })
                        ToggleRow(stringResource(R.string.shop_only_mine), onlyMine, vm::setOnlyMine)
                        if (sections.isEmpty()) {
                            Text(stringResource(R.string.shop_none_mine), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        sections.forEach { section ->
                            LiveSection(titles.getValue(section.group)) {
                                section.lines.forEach { line ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        CheckRow(line.name, shopDetail(line), line.done, { vm.toggleShopLine(line) }, Modifier.weight(1f), enabled = edit)
                                        line.amount?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = Spacing.s2)) }
                                        if (edit) RoundButton(HeaderButton(R.drawable.ic_more, stringResource(R.string.shop_actions_named, line.name)) { actions = line })
                                    }
                                }
                            }
                        }
                        if (sections.isNotEmpty()) {
                            TextActionButton(stringResource(R.string.shop_copy), {
                                copyText(context, LiveRules.shopText(sections) { titles.getValue(it) })
                                copied = true
                            })
                        }
                    }
                    if (edit) {
                        PrimaryButton(stringResource(R.string.shop_add), { creating = true }, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.s3), icon = R.drawable.ic_plus)
                    }
                }
            }
        }
    }
    actions?.let { line ->
        val entries = buildList {
            if (line.entries.size == 1) add(stringResource(R.string.shop_change) to { editor = line })
            add(stringResource(R.string.task_delete) to { deleting = line })
        }
        ActionSheet(line.name, entries) { actions = null }
    }
    if (creating || editor != null) {
        val line = editor
        ShopEditorSheet(
            vm, line, frame.people, frame.self, busy,
            onSave = { name, amount, unit, buyer ->
                if (line == null) {
                    vm.addShopItem(name, amount, unit, buyer) { creating = false }
                } else {
                    vm.changeShopItem(line, shopChange(name, amount, unit, buyer)) { editor = null }
                }
            },
            onDismiss = { creating = false; editor = null; vm.clearSuggestions() },
        )
    }
    deleting?.let { line ->
        ConfirmDialog(
            stringResource(R.string.shop_delete_title, line.name), stringResource(R.string.shop_delete_text), stringResource(R.string.task_delete),
            onConfirm = { deleting = null; vm.deleteShopLine(line) }, onDismiss = { deleting = null },
        )
    }
}

private val BOTTOM_SPACE = 96.dp

@androidx.annotation.StringRes
private fun shopGroupRes(group: ShopGroup): Int = when (group) {
    ShopGroup.PRODUCE -> R.string.shop_group_produce
    ShopGroup.BREAD -> R.string.shop_group_bread
    ShopGroup.DAIRY -> R.string.shop_group_dairy
    ShopGroup.MEAT -> R.string.shop_group_meat
    ShopGroup.PANTRY -> R.string.shop_group_pantry
    ShopGroup.DRINKS -> R.string.shop_group_drinks
    ShopGroup.HOUSEHOLD -> R.string.shop_group_household
    ShopGroup.OTHER -> R.string.shop_group_other
}

/** Die Angabe unter dem Artikel: wer einkauft, und bei mehreren Einträgen, woraus die Zeile besteht. */
@Composable
private fun shopDetail(line: ShopLine): String? {
    val buyer = line.buyer?.let { stringResource(R.string.shop_buys, it) }
    val sum = if (line.count > 1) stringResource(R.string.shop_summed, line.count) else null
    return joinDetail(buyer, sum)
}

/** Die Felder einer Änderung am einen Artikel: leere Menge oder Einheit leert das Feld beim Server. */
private fun shopChange(name: String, amount: Double?, unit: String, buyer: String?): Map<String, JsonElement> = mapOf(
    "name" to JsonPrimitive(name.trim()),
    "menge" to (amount?.let { JsonPrimitive(it) } ?: JsonNull),
    "einheit" to textField(unit),
    "einkaeuferPersonId" to (buyer?.let { JsonPrimitive(it) } ?: JsonNull),
)

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.shop_title), text))
}

/** Das Blatt zum Anlegen und Ändern: Name mit Vorschlägen, Menge, Einheit und wer einkauft. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ShopEditorSheet(
    vm: LiveViewModel,
    line: ShopLine?,
    people: List<RessortPerson>,
    self: TaskPerson,
    busy: Boolean,
    onSave: (name: String, amount: Double?, unit: String, buyer: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(line?.name.orEmpty()) }
    var amount by remember { mutableStateOf(line?.amount?.substringBefore(' ').orEmpty()) }
    var unit by remember { mutableStateOf(line?.amount?.substringAfter(' ', "").orEmpty()) }
    var buyer by remember { mutableStateOf(line?.buyerId) }
    var showErrors by remember { mutableStateOf(false) }
    val suggestions by vm.suggestions.collectAsState()
    val amountInvalid = amount.isNotBlank() && LiveRules.parseAmount(amount) == null
    val choices = listOf(Choice(null, stringResource(R.string.shop_buyer_nobody)), Choice(self.id, stringResource(R.string.shop_buyer_me))) +
        people.filter { it.id != self.id }.map { Choice(it.id, it.display) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5).padding(bottom = Spacing.s5),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Text(
                stringResource(if (line == null) R.string.shop_add else R.string.shop_change), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            LabeledTextField(
                stringResource(R.string.shop_name), name, { name = it; if (line == null) vm.suggest(it) }, NAME_MAX, Modifier.fillMaxWidth(),
                hint = if (showErrors && name.isBlank()) stringResource(R.string.shop_name_missing) else null,
            )
            if (line == null) {
                val shown = LiveRules.matching(suggestions, name).take(SUGGESTIONS_SHOWN)
                if (shown.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        shown.forEach { s -> PickPill(s.name, selected = false, onClick = { name = s.name; vm.clearSuggestions() }, role = androidx.compose.ui.semantics.Role.Button) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                LabeledTextField(
                    stringResource(R.string.shop_amount), amount, { amount = it }, AMOUNT_MAX, Modifier.weight(1f),
                    keyboardType = KeyboardType.Decimal,
                    hint = if (amountInvalid) stringResource(R.string.shop_amount_invalid) else null,
                )
                LabeledTextField(stringResource(R.string.shop_unit), unit, { unit = it }, UNIT_MAX, Modifier.weight(1f))
            }
            Text(stringResource(R.string.shop_buyer), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                choices.forEach { choice -> PickPill(choice.label, buyer == choice.key, { buyer = choice.key }) }
            }
            PrimaryButton(
                stringResource(R.string.save),
                {
                    if (name.isBlank() || amountInvalid) showErrors = true else onSave(name, LiveRules.parseAmount(amount), unit, buyer)
                },
                Modifier.fillMaxWidth(), loading = busy,
            )
            OutlinedPillButton(stringResource(R.string.cancel), onDismiss, Modifier.fillMaxWidth())
        }
    }
    LaunchedEffect(Unit) { if (line == null) vm.clearSuggestions() }
}

private const val NAME_MAX = 100
private const val AMOUNT_MAX = 12
private const val UNIT_MAX = 20
private const val SUGGESTIONS_SHOWN = 6
