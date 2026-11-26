package io.github.sorry4nothing.cevios.laden

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.lager.ConfirmDialog
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.ui.ActionBanner
import io.github.sorry4nothing.cevios.ui.CardSkeleton
import io.github.sorry4nothing.cevios.ui.ChipKind
import io.github.sorry4nothing.cevios.ui.ErrorState
import io.github.sorry4nothing.cevios.ui.Header
import io.github.sorry4nothing.cevios.ui.HeaderButton
import io.github.sorry4nothing.cevios.ui.NoticeBanner
import io.github.sorry4nothing.cevios.ui.NoticeKind
import io.github.sorry4nothing.cevios.ui.OutlinedPillButton
import io.github.sorry4nothing.cevios.ui.PickPill
import io.github.sorry4nothing.cevios.ui.PrimaryButton
import io.github.sorry4nothing.cevios.ui.SlowHint
import io.github.sorry4nothing.cevios.ui.Spinner
import io.github.sorry4nothing.cevios.ui.StatePage
import io.github.sorry4nothing.cevios.ui.StatusChip
import io.github.sorry4nothing.cevios.ui.TextActionButton
import io.github.sorry4nothing.cevios.ui.focusRing
import io.github.sorry4nothing.cevios.ui.theme.Sizes
import io.github.sorry4nothing.cevios.ui.theme.Spacing
import io.github.sorry4nothing.cevios.ui.theme.cevi

private const val TNUM = "tnum"

/**
 * Das Lädeli: die Artikel der Abteilung mit Preis und Grösse. Ist das Lädeli geschlossen oder ein Artikel nicht zu haben, fehlt der
 * Knopf «In den Warenkorb» (er wird nicht ausgegraut). Der Warenkorb liegt nur im Arbeitsspeicher; «Warenkorb ansehen» steht unten,
 * sobald etwas drin ist.
 */
@Composable
fun ShopScreen(
    view: ApiResult<ShopView>?,
    cart: Cart,
    picked: Map<String, String>,
    sizeMissing: String?,
    cartNotice: CartResult?,
    onPick: (String, String) -> Unit,
    onAdd: (ShopItem) -> Unit,
    onDismissNotice: () -> Unit,
    onOpenCart: () -> Unit,
    onOrders: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val back = HeaderButton(R.drawable.ic_back, stringResource(R.string.store_back_profile), onBack)
    val orders = HeaderButton(R.drawable.ic_list_check, stringResource(R.string.store_orders_action), onOrders)
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Header(title = stringResource(R.string.store_title), back = back, action = orders, titleStyle = MaterialTheme.typography.headlineMedium, focusOnTitle = true)
        when (view) {
            null -> CardSkeleton(stringResource(R.string.store_loading))
            is ApiResult.Failure -> ErrorState(view.error, onRetry, onToPrograms = onBack, homeLabel = R.string.store_back_profile)
            is ApiResult.Ok -> {
                val shop = view.value
                Box(Modifier.weight(1f)) {
                    if (shop.items.isEmpty()) {
                        StatePage(R.drawable.ic_bag, stringResource(R.string.store_empty_title), stringResource(R.string.store_empty_text))
                    } else {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5),
                            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                        ) {
                            ShopBanners(shop.settings)
                            cartNotice?.let { CartNoticeBanner(it, shop.limits, onDismissNotice) }
                            shop.items.forEach { item ->
                                ItemCard(item, shop.settings.open, picked[item.id], sizeMissing == item.id, cart.quantityOf(item.id), onPick, { onAdd(item) })
                            }
                            Spacer(Modifier.height(Spacing.s2))
                        }
                    }
                }
                if (shop.settings.open && cart.count > 0) {
                    val total = cart.totalCents(shop.items)
                    val summary = stringResource(R.string.store_cart_summary, cart.count, formatChf(total))
                    val spoken = stringResource(R.string.store_cart_summary, cart.count, spokenChf(total))
                    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.s5, vertical = Spacing.s3), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        Text(
                            summary, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TNUM), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { contentDescription = spoken; liveRegion = LiveRegionMode.Polite },
                        )
                        PrimaryButton(stringResource(R.string.store_cart_open), onOpenCart, Modifier.fillMaxWidth(), icon = R.drawable.ic_bag)
                    }
                }
            }
        }
    }
}

/** Ist das Lädeli zu, steht der Text der Abteilung da; der Hinweis zur Bezahlung steht, wenn es einen gibt. */
@Composable
private fun ShopBanners(settings: ShopSettings) {
    if (!settings.open) {
        NoticeBanner(NoticeKind.WARNING, settings.closedText.ifBlank { stringResource(R.string.store_closed) })
    }
    if (settings.paymentNote.isNotBlank()) NoticeBanner(NoticeKind.INFO, settings.paymentNote)
}

@Composable
private fun CartNoticeBanner(notice: CartResult, limits: ShopLimits, onClose: () -> Unit) {
    val text = when (notice) {
        CartResult.TOO_MANY_LINES -> stringResource(R.string.store_cart_too_many, limits.positions)
        CartResult.TOO_MUCH -> stringResource(R.string.store_cart_too_much, limits.quantity)
        CartResult.OK -> return
    }
    ActionBanner(NoticeKind.ERROR, text, null, null, onClose)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemCard(
    item: ShopItem,
    open: Boolean,
    picked: String?,
    sizeMissing: Boolean,
    inCart: Int,
    onPick: (String, String) -> Unit,
    onAdd: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLow, shape)
            .border(1.dp, scheme.outlineVariant, shape)
            .padding(horizontal = 14.dp, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        // Name, Preis und Hinweis lesen sich als ein Satz; die Bedienelemente darunter bleiben einzeln. Das Bild steht daneben und ist dekorativ.
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3), verticalAlignment = Alignment.Top) {
            item.mediumId?.let { ItemImage(it) }
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3), verticalAlignment = Alignment.Top) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    val spoken = spokenChf(item.priceCents)
                    Text(
                        formatChf(item.priceCents),
                        Modifier.semantics { contentDescription = spoken },
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TNUM),
                    )
                }
                item.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant) }
            }
        }
        if (!item.available) {
            StatusChip(ChipKind.REVIEW, stringResource(R.string.store_item_gone))
            return@Column
        }
        if (item.sizeKind == SIZE_LIST) {
            Text(stringResource(R.string.store_size), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                item.orderableVariants().forEach { size -> PickPill(size, size == picked, { onPick(item.id, size) }, enabled = open) }
            }
            if (item.unavailableVariants.isNotEmpty()) {
                Text(
                    stringResource(R.string.store_sizes_gone, item.unavailableVariants.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                )
            }
            if (sizeMissing) {
                Row(Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, horizontalArrangement = Arrangement.spacedBy(Spacing.s1), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = scheme.error, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.store_size_missing), style = MaterialTheme.typography.bodySmall, color = scheme.error)
                }
            }
        }
        // Ohne Bestellung fehlt der Knopf, statt ausgegraut zu sein.
        if (open) {
            val label = stringResource(R.string.store_add_label, item.name)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3), verticalAlignment = Alignment.CenterVertically) {
                OutlinedPillButton(stringResource(R.string.store_add), onAdd, Modifier.semantics { contentDescription = label }, icon = R.drawable.ic_plus)
                if (inCart > 0) {
                    Text(
                        stringResource(R.string.store_in_cart, inCart), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TNUM),
                        color = scheme.onSurfaceVariant, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}

/**
 * Der Warenkorb: Zeilen mit Menge, Übergabeart, Summe und «Bestellen». Der Knopf ist die eine Hauptaktion; während die Bestellung läuft,
 * nimmt er keinen zweiten Tipp an. Nach dem Senden steht die Bestätigung da (sie nennt die Übergabe und die Summe).
 */
@Composable
fun CartScreen(
    view: ApiResult<ShopView>?,
    cart: Cart,
    handover: String?,
    send: ShopViewModel.SendState,
    cartNotice: CartResult?,
    onChange: (String, String?, Int) -> Unit,
    onHandover: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismissNotice: () -> Unit,
    onDismissSend: () -> Unit,
    onOrders: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val back = HeaderButton(R.drawable.ic_back, stringResource(R.string.store_back), onBack)
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Header(title = stringResource(R.string.store_cart_title), back = back, titleStyle = MaterialTheme.typography.headlineMedium, focusOnTitle = true)
        val sent = send.sent
        when {
            sent != null -> SentPage(sent, (view as? ApiResult.Ok)?.value?.settings, onOrders, onBack)
            view == null -> CardSkeleton(stringResource(R.string.store_cart_loading))
            view is ApiResult.Failure -> ErrorState(view.error, onRetry, onToPrograms = onBack, homeLabel = R.string.store_back)
            view is ApiResult.Ok -> if (cart.lines.isEmpty()) {
                StatePage(R.drawable.ic_bag, stringResource(R.string.store_cart_empty_title), stringResource(R.string.store_cart_empty_text)) {
                    OutlinedPillButton(stringResource(R.string.store_cart_empty_action), onBack)
                }
            } else {
                CartBody(view.value, cart, handover, send, cartNotice, onChange, onHandover, onSubmit, onDismissNotice, onDismissSend, onOrders)
            }
        }
    }
}

@Composable
private fun CartBody(
    shop: ShopView,
    cart: Cart,
    handover: String?,
    send: ShopViewModel.SendState,
    cartNotice: CartResult?,
    onChange: (String, String?, Int) -> Unit,
    onHandover: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismissNotice: () -> Unit,
    onDismissSend: () -> Unit,
    onOrders: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val problems = send.problems.orEmpty()
    val total = cart.totalCents(shop.items)
    val items = shop.items.associateBy { it.id }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        send.notice?.let { SendBanner(it, onDismissSend, onOrders) }
        if (problems.isNotEmpty()) NoticeBanner(NoticeKind.ERROR, stringResource(R.string.store_check))
        if (!shop.settings.open) NoticeBanner(NoticeKind.WARNING, shop.settings.closedText.ifBlank { stringResource(R.string.store_closed_cart) })
        cartNotice?.let { CartNoticeBanner(it, shop.limits, onDismissNotice) }
        if (shop.settings.paymentNote.isNotBlank()) NoticeBanner(NoticeKind.INFO, shop.settings.paymentNote)
        cart.lines.forEach { line ->
            val item = items[line.itemId] ?: return@forEach
            LineCard(item, line, shop.limits.quantity, !send.running, onChange)
        }

        // Übergabe: Gibt es nur eine Art, steht sie als Zeile da; gibt es mehrere, wählt die Person.
        Text(
            stringResource(R.string.store_handover), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.s2).semantics { heading() },
        )
        if (shop.handovers.size == 1) {
            Text(shop.handovers.first(), style = MaterialTheme.typography.bodyLarge)
        } else {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                shop.handovers.forEach { HandoverChoice(it, it == handover, !send.running) { onHandover(it) } }
            }
            if (OrderProblem.NO_HANDOVER in problems) FieldError(stringResource(R.string.store_handover_error))
        }
        if (shop.settings.handoverNote.isNotBlank()) Text(shop.settings.handoverNote, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)

        // Summe, Bezahlung und Mindestbestellung
        val spokenTotal = spokenChf(total)
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.s2).semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.store_total), style = MaterialTheme.typography.titleMedium)
            Text(
                formatChf(total), Modifier.semantics { contentDescription = spokenTotal },
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = TNUM, fontWeight = FontWeight.ExtraBold),
            )
        }
        if (shop.settings.paymentMethod.isNotBlank()) {
            Text(stringResource(R.string.store_payment, shop.settings.paymentMethod), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
        if (OrderProblem.BELOW_MINIMUM in problems || (shop.settings.minimumCents > total && shop.settings.minimumCents > 0)) {
            FieldError(stringResource(R.string.store_minimum, formatChf(shop.settings.minimumCents), formatChf(shop.settings.minimumCents - total)))
        }
        // Ohne Bestellung (Lädeli zu) fehlt der Knopf.
        if (shop.settings.open) {
            PrimaryButton(
                stringResource(R.string.store_order), onSubmit, Modifier.fillMaxWidth(),
                loading = send.running, loadingText = stringResource(R.string.store_ordering),
            )
            SlowHint(send.running)
        }
        val terms = termsParagraphs(shop.settings.terms)
        if (terms.isNotEmpty()) {
            Text(
                stringResource(R.string.store_terms), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s2).semantics { heading() },
            )
            terms.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(Spacing.s5))
    }
}

@Composable
private fun SendBanner(notice: OrderNotice, onClose: () -> Unit, onOrders: () -> Unit) {
    val (kind, text) = when (notice) {
        OrderNotice.CLOSED -> NoticeKind.ERROR to R.string.store_notice_closed
        OrderNotice.PRICE_CHANGED -> NoticeKind.WARNING to R.string.store_notice_price
        OrderNotice.MINIMUM -> NoticeKind.ERROR to R.string.store_notice_minimum
        OrderNotice.UNAVAILABLE -> NoticeKind.ERROR to R.string.store_notice_unavailable
        OrderNotice.LIMIT -> NoticeKind.ERROR to R.string.store_notice_limit
        OrderNotice.TOO_MANY_LINES -> NoticeKind.ERROR to R.string.store_notice_lines
        OrderNotice.TOO_MUCH -> NoticeKind.ERROR to R.string.store_notice_much
        OrderNotice.CHANGED -> NoticeKind.WARNING to R.string.store_notice_changed
        OrderNotice.TOO_FAST -> NoticeKind.ERROR to R.string.store_notice_fast
        OrderNotice.UNSURE -> NoticeKind.WARNING to R.string.store_notice_unsure
        OrderNotice.FAILED -> NoticeKind.ERROR to R.string.store_notice_failed
    }
    val action = if (notice == OrderNotice.UNSURE) stringResource(R.string.store_notice_unsure_action) else null
    ActionBanner(kind, stringResource(text), action, if (action != null) onOrders else null, onClose)
}

@Composable
private fun SentPage(order: ShopOrder, settings: ShopSettings?, onOrders: () -> Unit, onBack: () -> Unit) {
    // Der Text der Abteilung, sonst ein eigener Satz mit Übergabe und Summe.
    val text = settings?.let { confirmationText(it, order.handover) }
        ?: stringResource(R.string.store_sent_text, order.handover, formatChf(order.totalCents))
    StatePage(R.drawable.ic_check, stringResource(R.string.store_sent_title), text, iconColor = MaterialTheme.cevi.accent) {
        PrimaryButton(stringResource(R.string.store_sent_orders), onOrders)
        TextActionButton(stringResource(R.string.store_sent_back), onBack)
    }
}

@Composable
private fun FieldError(text: String) {
    Row(Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, horizontalArrangement = Arrangement.spacedBy(Spacing.s1), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** Eine Zeile des Warenkorbs: Name, Grösse, Menge mit «weniger» und «mehr», «Entfernen». «Mehr» fehlt bei der höchsten Menge. */
@Composable
private fun LineCard(item: ShopItem, line: CartLine, maxQuantity: Int, enabled: Boolean, onChange: (String, String?, Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val sum = item.priceCents * line.quantity
    Column(
        Modifier.fillMaxWidth().background(scheme.surfaceContainerLow, shape).border(1.dp, scheme.outlineVariant, shape).padding(horizontal = 14.dp, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        val spokenSum = spokenChf(sum)
        val size = line.variant?.let { stringResource(R.string.store_line_size, it) }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3), verticalAlignment = Alignment.Top) {
            item.mediumId?.let { ItemImage(it, size = 56.dp) }
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3), verticalAlignment = Alignment.Top) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        formatChf(sum), Modifier.semantics { contentDescription = spokenSum },
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TNUM),
                    )
                }
                size?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant) }
                if (line.quantity > 1) {
                    Text(stringResource(R.string.store_line_each, formatChf(item.priceCents)), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TNUM), color = scheme.onSurfaceVariant)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", stringResource(R.string.store_less_label, item.name), enabled) { onChange(line.itemId, line.variant, -1) }
            Text(
                line.quantity.toString(),
                Modifier.widthIn(min = 32.dp).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TNUM),
            )
            if (line.quantity < maxQuantity) {
                StepButton("+", stringResource(R.string.store_more_label, item.name), enabled) { onChange(line.itemId, line.variant, +1) }
            } else {
                Spacer(Modifier.size(Sizes.touchTarget))
            }
            Spacer(Modifier.weight(1f))
            val removeLabel = stringResource(R.string.store_remove_label, item.name)
            TextActionButton(stringResource(R.string.store_remove), { onChange(line.itemId, line.variant, -line.quantity) }, Modifier.semantics { contentDescription = removeLabel })
        }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(Sizes.touchTarget)
            .focusRing(CircleShape)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Eine Übergabeart als Auswahlzeile; Rand und Punkt in `accent` zeigen die Wahl. */
@Composable
private fun HandoverChoice(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val scheme = MaterialTheme.colorScheme
    val accent = MaterialTheme.cevi.accent
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .focusRing(shape)
            .background(scheme.surfaceContainerLow, shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else scheme.outlineVariant, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = Spacing.s2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = accent))
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
    }
}

/**
 * «Meine Bestellungen»: jede mit Datum, Stand (Icon und Wort), Positionen, Übergabe und Summe. «Stornieren» gibt es nur bei einer
 * offenen Bestellung (nach dem Bereitstellen fehlt der Knopf) und fragt vorher nach.
 */
@Composable
fun OrdersScreen(
    orders: ApiResult<ShopOrderPage>?,
    list: ShopViewModel.ListState,
    onCancel: (ShopOrder) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val back = HeaderButton(R.drawable.ic_back, stringResource(R.string.store_back), onBack)
    var confirming by remember { mutableStateOf<ShopOrder?>(null) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Header(title = stringResource(R.string.store_orders_title), back = back, titleStyle = MaterialTheme.typography.headlineMedium, focusOnTitle = true)
        when (orders) {
            null -> CardSkeleton(stringResource(R.string.store_orders_loading))
            is ApiResult.Failure -> ErrorState(orders.error, onRetry, onToPrograms = onBack, homeLabel = R.string.store_back)
            is ApiResult.Ok -> if (orders.value.orders.isEmpty() && list.notice == null) {
                StatePage(R.drawable.ic_bag, stringResource(R.string.store_orders_empty_title), stringResource(R.string.store_orders_empty_text))
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.s5),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                ) {
                    ListBanner(list.notice, onDismiss)
                    orders.value.orders.forEach { order -> OrderCard(order, running = list.cancelling == order.id, onCancel = { confirming = order }) }
                    Spacer(Modifier.height(Spacing.s5))
                }
            }
        }
    }
    confirming?.let { order ->
        ConfirmDialog(
            stringResource(R.string.store_order_cancel_title),
            stringResource(R.string.store_order_cancel_text, orderDaySpoken(order.createdAt), formatChf(order.totalCents)),
            stringResource(R.string.store_order_cancel_confirm),
            onConfirm = { confirming = null; onCancel(order) },
            onDismiss = { confirming = null },
        )
    }
}

@Composable
private fun ListBanner(notice: ShopViewModel.ListNotice?, onClose: () -> Unit) {
    when (notice) {
        null -> Unit
        ShopViewModel.ListNotice.CANCELLED -> ActionBanner(NoticeKind.SUCCESS, stringResource(R.string.store_list_cancelled), null, null, onClose)
        ShopViewModel.ListNotice.FAILED -> ActionBanner(NoticeKind.ERROR, stringResource(R.string.store_list_failed), null, null, onClose)
        ShopViewModel.ListNotice.NOT_POSSIBLE -> ActionBanner(NoticeKind.ERROR, stringResource(R.string.store_list_not_possible), null, null, onClose)
    }
}

@Composable
private fun OrderCard(order: ShopOrder, running: Boolean, onCancel: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val status = orderStatus(order.status)
    val day = orderDay(order.createdAt)
    val daySpoken = orderDaySpoken(order.createdAt)
    val dateSpoken = stringResource(R.string.store_order_date, daySpoken)
    val spokenTotal = spokenChf(order.totalCents)
    Column(
        Modifier.fillMaxWidth().background(scheme.surfaceContainerLow, shape).border(1.dp, scheme.outlineVariant, shape).padding(horizontal = 14.dp, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        // Der Text der Karte ist ein Satz für den Screenreader; der Knopf darunter bleibt eigenständig.
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.Top) {
                Text(
                    stringResource(R.string.store_order_date, day), Modifier.weight(1f).semantics { contentDescription = dateSpoken },
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TNUM),
                )
                val (kind, label) = when (status) {
                    OrderStatus.READY -> ChipKind.YES to R.string.store_status_ready
                    OrderStatus.HANDED_OVER -> ChipKind.SENT to R.string.store_status_done
                    OrderStatus.CANCELLED -> ChipKind.NO to R.string.store_status_cancelled
                    else -> ChipKind.PENDING to R.string.store_status_open
                }
                StatusChip(kind, stringResource(label))
            }
            order.lines.forEach { line ->
                Text(
                    if (line.variant != null) stringResource(R.string.store_order_line_size, line.quantity, line.name, line.variant)
                    else stringResource(R.string.store_order_line, line.quantity, line.name),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(stringResource(R.string.store_order_handover, order.handover), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            Text(
                formatChf(order.totalCents), Modifier.semantics { contentDescription = spokenTotal },
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TNUM, fontWeight = FontWeight.ExtraBold),
            )
        }
        when {
            running -> Row(
                Modifier.heightIn(min = 48.dp).semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically,
            ) {
                Spinner(size = 20.dp, color = MaterialTheme.cevi.accent)
                Text(stringResource(R.string.store_order_cancelling), style = MaterialTheme.typography.bodyMedium)
            }
            canCancelOrder(order) -> {
                val description = stringResource(R.string.store_order_cancel_label, daySpoken)
                TextActionButton(stringResource(R.string.store_order_cancel), onCancel, Modifier.semantics { contentDescription = description })
            }
        }
    }
}
