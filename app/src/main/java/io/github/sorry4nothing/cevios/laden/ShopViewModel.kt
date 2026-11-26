package io.github.sorry4nothing.cevios.laden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Was nach einer Bestellung, die nicht angekommen ist, in der Meldung steht. */
enum class OrderNotice {
    CLOSED, PRICE_CHANGED, MINIMUM, UNAVAILABLE, LIMIT, TOO_MANY_LINES, TOO_MUCH, CHANGED, TOO_FAST,
    /** Ohne Antwort: Die Bestellung kann angekommen sein. Die App sendet nie von selbst noch einmal. */
    UNSURE, FAILED,
}

/**
 * Das Lädeli (Artikel, Warenkorb, Bestellen) und «Meine Bestellungen». Der Warenkorb liegt nur im Arbeitsspeicher. Eine Bestellung
 * geht nie zweimal hinaus: Solange eine läuft, nimmt [order] nichts an, und nach einem Fehler ohne Antwort sendet nur die Person von
 * Hand noch einmal, nachdem sie in «Meine Bestellungen» nachgesehen hat.
 */
class ShopViewModel(private val api: ShopApi) : ViewModel() {
    data class SendState(
        val running: Boolean = false,
        val notice: OrderNotice? = null,
        /** Nach dem Absenden der Fehlversuch an der Eingabe, bevor etwas gesendet wurde. */
        val problems: Set<OrderProblem>? = null,
        /** Die angenommene Bestellung, solange die Bestätigung steht. */
        val sent: ShopOrder? = null,
    )

    enum class ListNotice { CANCELLED, FAILED, NOT_POSSIBLE }

    data class ListState(val cancelling: String? = null, val notice: ListNotice? = null)

    private val _view = MutableStateFlow<ApiResult<ShopView>?>(null)
    val view: StateFlow<ApiResult<ShopView>?> = _view.asStateFlow()

    private val _cart = MutableStateFlow(Cart())
    val cart: StateFlow<Cart> = _cart.asStateFlow()

    /** Die gewählte Grösse je Artikel (nur bei Artikeln mit Liste). */
    private val _picked = MutableStateFlow<Map<String, String>>(emptyMap())
    val picked: StateFlow<Map<String, String>> = _picked.asStateFlow()

    /** Der Artikel, bei dem die Grösse noch fehlt, nachdem die Person «In den Warenkorb» getippt hat. */
    private val _sizeMissing = MutableStateFlow<String?>(null)
    val sizeMissing: StateFlow<String?> = _sizeMissing.asStateFlow()

    /** Hinweis zum Warenkorb («Mehr als … geht nicht»), bis die Person ihn schliesst. */
    private val _cartNotice = MutableStateFlow<CartResult?>(null)
    val cartNotice: StateFlow<CartResult?> = _cartNotice.asStateFlow()

    private val _handover = MutableStateFlow<String?>(null)
    val handover: StateFlow<String?> = _handover.asStateFlow()

    private val _send = MutableStateFlow(SendState())
    val send: StateFlow<SendState> = _send.asStateFlow()

    private val _orders = MutableStateFlow<ApiResult<ShopOrderPage>?>(null)
    val orders: StateFlow<ApiResult<ShopOrderPage>?> = _orders.asStateFlow()

    private val _list = MutableStateFlow(ListState())
    val list: StateFlow<ListState> = _list.asStateFlow()

    /** Lädt das Lädeli neu und zeigt bis dahin das Skelett. Der Warenkorb bleibt, was nicht mehr zu haben ist, fällt heraus. */
    fun reload() {
        _view.value = null
        viewModelScope.launch { refreshView() }
    }

    /** Mit `keep` bleibt der alte Stand stehen, wenn das Neuladen scheitert (der Warenkorb mit seiner Meldung soll nicht verschwinden). */
    private suspend fun refreshView(keep: Boolean = false) {
        val result = api.shop()
        if (result is ApiResult.Ok || !keep) _view.value = result
        if (result !is ApiResult.Ok) return
        val shop = result.value
        _cart.update { it.pruned(shop.items) }
        _picked.update { picks -> picks.filter { (id, size) -> shop.items.any { item -> item.id == id && item.canOrder(size) } } }
        // Die Wahl der Übergabeart bleibt nur, wenn es sie noch gibt; gibt es nur eine, ist sie gewählt.
        _handover.update { current -> current?.takeIf { it in shop.handovers } ?: defaultHandover(shop) }
    }

    fun pick(itemId: String, size: String) {
        _picked.update { it + (itemId to size) }
        if (_sizeMissing.value == itemId) _sizeMissing.value = null
    }

    fun chooseHandover(handover: String) {
        _handover.value = handover
        _send.update { it.copy(problems = it.problems?.minus(OrderProblem.NO_HANDOVER)) }
    }

    /** «In den Warenkorb»: bei einem Artikel mit Liste braucht es zuerst die Grösse. */
    fun add(item: ShopItem) {
        val shop = (_view.value as? ApiResult.Ok)?.value ?: return
        if (!shop.settings.open || !item.available) return
        val size = if (item.sizeKind == SIZE_LIST) _picked.value[item.id] else null
        if (item.sizeKind == SIZE_LIST && size == null) {
            _sizeMissing.value = item.id
            return
        }
        applyChange(item.id, size, +1, shop)
    }

    fun change(itemId: String, size: String?, delta: Int) {
        val shop = (_view.value as? ApiResult.Ok)?.value ?: return
        applyChange(itemId, size, delta, shop)
    }

    private fun applyChange(itemId: String, size: String?, delta: Int, shop: ShopView) {
        val change = _cart.value.change(itemId, size, delta, shop.limits)
        _cart.value = change.cart
        _cartNotice.value = change.result.takeIf { it != CartResult.OK }
        // Eine geänderte Bestellung braucht eine neue Prüfung der Eingabe.
        _send.update { state -> state.problems?.let { state.copy(problems = emptySet()) } ?: state }
    }

    fun dismissCartNotice() {
        _cartNotice.value = null
    }

    fun submit() {
        if (_send.value.running) return
        val shop = (_view.value as? ApiResult.Ok)?.value ?: return
        val cart = _cart.value
        val handover = _handover.value
        val total = cart.totalCents(shop.items)
        val problems = orderProblems(cart, shop, handover, total)
        if (problems.isNotEmpty() || handover == null) {
            _send.value = SendState(problems = problems)
            return
        }
        _send.value = SendState(running = true, problems = emptySet())
        viewModelScope.launch {
            when (val result = api.order(orderRequest(cart, handover, total))) {
                is ApiResult.Ok -> {
                    _cart.value = Cart()
                    _send.value = SendState(sent = result.value)
                    // «Meine Bestellungen» lädt beim Öffnen neu; bis dahin gilt der alte Stand nicht mehr.
                    _orders.value = null
                }
                is ApiResult.Failure -> {
                    val notice = noticeFor(result)
                    // Was sich beim Server geändert hat (Preise, Grössen, Regeln), zeigt die Liste gleich neu.
                    if (notice != OrderNotice.UNSURE && notice != OrderNotice.FAILED && notice != OrderNotice.TOO_FAST) refreshView(keep = true)
                    _send.value = SendState(notice = notice, problems = emptySet())
                }
            }
        }
    }

    fun dismissSendNotice() {
        if (!_send.value.running) _send.update { it.copy(notice = null) }
    }

    /** Verlässt die Bestätigung oder den Warenkorb: Eine angenommene Bestellung bleibt nicht als Meldung stehen. */
    fun leaveCart() {
        if (!_send.value.running) _send.value = SendState()
    }

    fun reloadOrders() {
        _orders.value = null
        _list.value = ListState()
        refreshOrders()
    }

    private fun refreshOrders() {
        viewModelScope.launch { _orders.value = api.myOrders() }
    }

    fun cancel(id: String) {
        if (_list.value.cancelling != null) return
        _list.value = ListState(cancelling = id)
        viewModelScope.launch {
            when (val result = api.cancel(id)) {
                is ApiResult.Ok -> {
                    _list.value = ListState(notice = ListNotice.CANCELLED)
                    refreshOrders()
                }
                is ApiResult.Failure -> {
                    // 409 `STATUS_UNGUELTIG`: Die Abteilung hat die Bestellung inzwischen bereitgestellt; die Liste zeigt den neuen Stand.
                    val stale = result.code == "STATUS_UNGUELTIG" || result.error == ApiError.NOT_FOUND
                    _list.value = ListState(notice = if (stale) ListNotice.NOT_POSSIBLE else ListNotice.FAILED)
                    if (stale) refreshOrders()
                }
            }
        }
    }

    fun dismissListNotice() {
        if (_list.value.cancelling == null) _list.value = ListState()
    }

    private fun noticeFor(failure: ApiResult.Failure): OrderNotice = when (failure.code) {
        "LADEN_GESCHLOSSEN" -> OrderNotice.CLOSED
        "PREIS_GEAENDERT" -> OrderNotice.PRICE_CHANGED
        "MINDESTBESTELLUNG" -> OrderNotice.MINIMUM
        "ARTIKEL_NICHT_VERFUEGBAR", "GROESSE_NICHT_VERFUEGBAR" -> OrderNotice.UNAVAILABLE
        "BESTELL_LIMIT" -> OrderNotice.LIMIT
        "POSITIONEN_ZU_VIELE" -> OrderNotice.TOO_MANY_LINES
        "MENGE_ZU_GROSS" -> OrderNotice.TOO_MUCH
        "VARIANTE_UNGUELTIG", "UEBERGABE_UNBEKANNT" -> OrderNotice.CHANGED
        else -> when (failure.error) {
            ApiError.NETWORK -> OrderNotice.UNSURE
            ApiError.TOO_MANY_REQUESTS -> OrderNotice.TOO_FAST
            else -> OrderNotice.FAILED
        }
    }
}
