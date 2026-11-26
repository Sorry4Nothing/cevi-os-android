package io.github.sorry4nothing.cevios.laden

import io.github.sorry4nothing.cevios.programs.SWISS_ZONE
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Eine Zeile des Warenkorbs: Artikel, Grösse (nur bei einem Artikel mit Liste) und Menge. Der Warenkorb lebt nur im Arbeitsspeicher. */
data class CartLine(val itemId: String, val variant: String?, val quantity: Int)

data class Cart(val lines: List<CartLine> = emptyList()) {
    val count: Int get() = lines.sumOf { it.quantity }

    fun quantityOf(itemId: String): Int = lines.filter { it.itemId == itemId }.sumOf { it.quantity }
}

enum class CartResult { OK, TOO_MANY_LINES, TOO_MUCH }

/** Das Ergebnis einer Änderung am Warenkorb; bei einem Verstoss gegen die Grenzen bleibt der Warenkorb, wie er war. */
data class CartChange(val cart: Cart, val result: CartResult = CartResult.OK)

/**
 * Ändert die Menge der Zeile (Artikel, Grösse) um `delta`: eine neue Zeile bei +1, Wegfall bei 0. Die Grenzen sind die der Abteilung
 * (`grenzen`); der Server prüft sie bei jeder Bestellung noch einmal.
 */
fun Cart.change(itemId: String, variant: String?, delta: Int, limits: ShopLimits): CartChange {
    val index = lines.indexOfFirst { it.itemId == itemId && it.variant == variant }
    if (index < 0) {
        if (delta <= 0) return CartChange(this)
        if (lines.size >= limits.positions) return CartChange(this, CartResult.TOO_MANY_LINES)
        if (limits.quantity < 1) return CartChange(this, CartResult.TOO_MUCH)
        return CartChange(Cart(lines + CartLine(itemId, variant, 1)))
    }
    val next = lines[index].quantity + delta
    if (next > limits.quantity) return CartChange(this, CartResult.TOO_MUCH)
    val changed = if (next < 1) lines.filterIndexed { i, _ -> i != index } else lines.mapIndexed { i, line -> if (i == index) line.copy(quantity = next) else line }
    return CartChange(Cart(changed))
}

/** Ob der Artikel in dieser Grösse zu haben ist. */
fun ShopItem.canOrder(variant: String?): Boolean = when {
    !available -> false
    sizeKind == SIZE_LIST -> variant != null && variant in variants && variant !in unavailableVariants
    else -> variant == null
}

/** Die Grössen, die sich wählen lassen (ohne die, die nicht zu haben sind). */
fun ShopItem.orderableVariants(): List<String> = if (available) variants.filter { it !in unavailableVariants } else emptyList()

/** Der Warenkorb ohne Zeilen, die es nicht mehr gibt oder die nicht mehr zu haben sind (nach dem Neuladen des Lädelis). */
fun Cart.pruned(items: List<ShopItem>): Cart {
    val byId = items.associateBy { it.id }
    return Cart(lines.filter { line -> byId[line.itemId]?.canOrder(line.variant) == true })
}

/** Die Summe in Rappen aus den Preisen des Lädelis; der Server rechnet bei der Bestellung selbst. */
fun Cart.totalCents(items: List<ShopItem>): Long {
    val byId = items.associateBy { it.id }
    return lines.sumOf { line -> (byId[line.itemId]?.priceCents ?: 0L) * line.quantity }
}

/** Was vor dem Senden noch fehlt. */
enum class OrderProblem { CLOSED, EMPTY, NO_HANDOVER, BELOW_MINIMUM }

fun orderProblems(cart: Cart, view: ShopView, handover: String?, totalCents: Long): Set<OrderProblem> = buildSet {
    if (!view.settings.open) add(OrderProblem.CLOSED)
    if (cart.lines.isEmpty()) add(OrderProblem.EMPTY)
    if (handover == null || handover !in view.handovers) add(OrderProblem.NO_HANDOVER)
    if (cart.lines.isNotEmpty() && totalCents < view.settings.minimumCents) add(OrderProblem.BELOW_MINIMUM)
}

fun orderRequest(cart: Cart, handover: String, totalCents: Long): ShopOrderRequest =
    ShopOrderRequest(cart.lines.map { ShopOrderLineRequest(it.itemId, it.variant, it.quantity) }, handover, totalCents)

/** Die Übergabeart, die gilt, wenn die Person keine wählt: die einzige, sonst keine. */
fun defaultHandover(view: ShopView): String? = view.handovers.singleOrNull()

/** Die Meldung nach dem Bestellen mit der gewählten Übergabeart; `null`, wenn die Abteilung keinen Text hinterlegt hat. */
fun confirmationText(settings: ShopSettings, handover: String): String? =
    settings.confirmation.replace("{uebergabe}", handover).trim().takeIf { it.isNotEmpty() }

/** Die Bedingungen als Absätze (ein Absatz je Zeile). */
fun termsParagraphs(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

enum class OrderStatus { OPEN, READY, HANDED_OVER, CANCELLED, UNKNOWN }

fun orderStatus(status: String): OrderStatus = when (status) {
    "offen" -> OrderStatus.OPEN
    "bereit" -> OrderStatus.READY
    "uebergeben" -> OrderStatus.HANDED_OVER
    "storniert" -> OrderStatus.CANCELLED
    else -> OrderStatus.UNKNOWN
}

/** Stornieren geht nur bei einer offenen Bestellung; ist sie bereit oder übergeben, gibt es den Knopf nicht. */
fun canCancelOrder(order: ShopOrder): Boolean = orderStatus(order.status) == OrderStatus.OPEN

/** «CHF 12.50», ab tausend mit Hochkomma («CHF 1'250.00»); Preis null heisst «gratis». */
fun formatChf(cents: Long): String {
    if (cents == 0L) return "gratis"
    val francs = (cents / 100).toString().reversed().chunked(3).joinToString("'").reversed()
    return "CHF $francs.${(cents % 100).toString().padStart(2, '0')}"
}

/** Wie [formatChf], aber zum Vorlesen: TalkBack liest «CHF» sonst Buchstabe für Buchstabe. */
fun spokenChf(cents: Long): String {
    if (cents == 0L) return "gratis"
    val francs = cents / 100
    val rest = cents % 100
    return if (rest == 0L) "$francs Franken" else "$francs Franken $rest Rappen"
}

private val ORDER_DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)
private val ORDER_DAY_SPOKEN = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.GERMAN)

/** «12.12.2026» in der Zone der Abteilung; leer, wenn die Zeit nicht lesbar ist. */
fun orderDay(iso: String): String = formatDay(iso, ORDER_DAY)

/** «12. Dezember 2026» zum Vorlesen. */
fun orderDaySpoken(iso: String): String = formatDay(iso, ORDER_DAY_SPOKEN)

private fun formatDay(iso: String, format: DateTimeFormatter): String = try {
    format.format(Instant.parse(iso).atZone(SWISS_ZONE))
} catch (e: DateTimeParseException) {
    ""
}
