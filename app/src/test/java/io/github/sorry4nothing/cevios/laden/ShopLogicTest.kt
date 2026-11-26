package io.github.sorry4nothing.cevios.laden

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

class ShopLogicTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private val shirt = ShopItem("shirt", "Beispiel-Shirt", 2500, SIZE_LIST, listOf("S", "M", "L"), listOf("L"))
    private val mug = ShopItem("mug", "Beispiel-Tasse", 800)
    private val gone = ShopItem("gone", "Ausverkauft", 100, available = false)
    private val limits = ShopLimits(positions = 3, quantity = 2, openOrders = 5)
    private val view = ShopView(listOf(shirt, mug, gone), listOf("Abholung", "Anlass"), limits, ShopSettings(minimumCents = 1000, terms = "Eins\n\n  Zwei  \n"))

    @Test
    fun theSameItemAndSizeCountsUpOtherSizesAreOwnLines() {
        var cart = Cart()
        cart = cart.change("shirt", "M", +1, limits).cart
        cart = cart.change("shirt", "M", +1, limits).cart
        cart = cart.change("shirt", "S", +1, limits).cart
        assertEquals(listOf(CartLine("shirt", "M", 2), CartLine("shirt", "S", 1)), cart.lines)
        assertEquals(3, cart.count)
        assertEquals(3, cart.quantityOf("shirt"))
    }

    @Test
    fun theLimitsOfTheDepartmentHoldAndTheCartStaysAsItWas() {
        var cart = Cart(listOf(CartLine("shirt", "M", 2)))
        val tooMuch = cart.change("shirt", "M", +1, limits)
        assertEquals(CartResult.TOO_MUCH, tooMuch.result)
        assertEquals(cart, tooMuch.cart)

        cart = Cart(listOf(CartLine("a", null, 1), CartLine("b", null, 1), CartLine("c", null, 1)))
        val tooMany = cart.change("mug", null, +1, limits)
        assertEquals(CartResult.TOO_MANY_LINES, tooMany.result)
        assertEquals(cart, tooMany.cart)
        // Eine Zeile, die schon da ist, wächst trotzdem.
        assertEquals(CartResult.OK, cart.change("a", null, +1, limits).result)
    }

    @Test
    fun lessOfTheLastPieceAndRemovingTakeTheLineOut() {
        val cart = Cart(listOf(CartLine("mug", null, 1), CartLine("shirt", "S", 2)))
        assertEquals(listOf(CartLine("shirt", "S", 2)), cart.change("mug", null, -1, limits).cart.lines)
        assertEquals(listOf(CartLine("mug", null, 1)), cart.change("shirt", "S", -2, limits).cart.lines)
        // Weniger von etwas, das nicht im Warenkorb liegt, ändert nichts.
        assertEquals(cart, cart.change("none", null, -1, limits).cart)
    }

    @Test
    fun aSizeThatIsNoLongerAvailableLeavesTheCartAfterReloading() {
        val cart = Cart(listOf(CartLine("shirt", "L", 1), CartLine("shirt", "M", 1), CartLine("gone", null, 1), CartLine("deleted", null, 1), CartLine("mug", null, 1)))
        assertEquals(listOf(CartLine("shirt", "M", 1), CartLine("mug", null, 1)), cart.pruned(view.items).lines)
    }

    @Test
    fun canOrderNeedsASizeOnlyForListsAndNeverForUnits() {
        assertTrue(shirt.canOrder("M"))
        assertFalse(shirt.canOrder("L")) // nicht zu haben
        assertFalse(shirt.canOrder("XL")) // gibt es nicht
        assertFalse(shirt.canOrder(null))
        assertTrue(mug.canOrder(null))
        assertFalse(mug.canOrder("M"))
        assertFalse(gone.canOrder(null))
        assertEquals(emptyList<String>(), gone.orderableVariants())
    }

    @Test
    fun theTotalComesFromThePricesOfTheShop() {
        val cart = Cart(listOf(CartLine("shirt", "M", 2), CartLine("mug", null, 1)))
        assertEquals(5800L, cart.totalCents(view.items))
    }

    @Test
    fun anOrderNeedsItemsAHandoverAndTheMinimumAndAnOpenShop() {
        val cart = Cart(listOf(CartLine("mug", null, 1)))
        assertEquals(setOf(OrderProblem.BELOW_MINIMUM), orderProblems(cart, view, "Abholung", 800))
        assertEquals(setOf(OrderProblem.NO_HANDOVER, OrderProblem.BELOW_MINIMUM), orderProblems(cart, view, null, 800))
        assertEquals(setOf(OrderProblem.NO_HANDOVER, OrderProblem.BELOW_MINIMUM), orderProblems(cart, view, "Gibt es nicht", 800))
        assertEquals(setOf(OrderProblem.EMPTY, OrderProblem.NO_HANDOVER), orderProblems(Cart(), view, null, 0))
        assertEquals(emptySet<OrderProblem>(), orderProblems(Cart(listOf(CartLine("shirt", "M", 1))), view, "Anlass", 2500))
        val closed = view.copy(settings = view.settings.copy(open = false))
        assertTrue(OrderProblem.CLOSED in orderProblems(cart, closed, "Abholung", 5000))
    }

    @Test
    fun theRequestListsLinesWithoutPrices() {
        val request = orderRequest(Cart(listOf(CartLine("shirt", "M", 2), CartLine("mug", null, 1))), "Anlass", 5800)
        assertEquals(listOf(ShopOrderLineRequest("shirt", "M", 2), ShopOrderLineRequest("mug", null, 1)), request.lines)
        assertEquals(5800L, request.expectedCents)
    }

    @Test
    fun aSingleHandoverIsChosenForThePerson() {
        assertNull(defaultHandover(view))
        assertEquals("Abholung", defaultHandover(view.copy(handovers = listOf("Abholung"))))
    }

    @Test
    fun theConfirmationNamesTheHandoverOrIsMissing() {
        assertEquals("Danke! Übergabe: Anlass.", confirmationText(ShopSettings(confirmation = "Danke! Übergabe: {uebergabe}."), "Anlass"))
        assertNull(confirmationText(ShopSettings(confirmation = "  "), "Anlass"))
    }

    @Test
    fun termsAreOneParagraphPerLineWithoutEmptyOnes() {
        assertEquals(listOf("Eins", "Zwei"), termsParagraphs("Eins\n\n  Zwei  \n"))
        assertEquals(emptyList<String>(), termsParagraphs(""))
    }

    @Test
    fun onlyAnOpenOrderCanBeCancelled() {
        fun order(status: String) = ShopOrder("1", status, "Abholung", 100, "2026-12-12T10:00:00Z")
        assertTrue(canCancelOrder(order("offen")))
        assertFalse(canCancelOrder(order("bereit")))
        assertFalse(canCancelOrder(order("uebergeben")))
        assertFalse(canCancelOrder(order("storniert")))
        assertFalse(canCancelOrder(order("neu")))
        assertEquals(OrderStatus.UNKNOWN, orderStatus("neu"))
    }

    @Test
    fun pricesAreSwissFrancs() {
        assertEquals("CHF 12.50", formatChf(1250))
        assertEquals("CHF 12.05", formatChf(1205))
        assertEquals("CHF 0.50", formatChf(50))
        assertEquals("CHF 1'250.00", formatChf(125000))
        assertEquals("CHF 12'345'678.90", formatChf(1234567890))
        assertEquals("gratis", formatChf(0))
        assertEquals("12 Franken", spokenChf(1200))
        assertEquals("12 Franken 50 Rappen", spokenChf(1250))
        assertEquals("gratis", spokenChf(0))
    }

    @Test
    fun theOrderDayIsInSwissTime() {
        assertEquals("12.12.2026", orderDay("2026-12-12T10:00:00Z"))
        // 23:30 UTC ist in Zürich schon der nächste Tag.
        assertEquals("13.12.2026", orderDay("2026-12-12T23:30:00Z"))
        assertEquals("13. Dezember 2026", orderDaySpoken("2026-12-12T23:30:00Z"))
        assertEquals("", orderDay("kaputt"))
    }
}
