package io.github.sorry4nothing.cevios.laden

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.SessionStorage
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

class ShopApiTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var server: MockWebServer
    private lateinit var api: ShopApi

    @Before
    fun start() {
        server = MockWebServer().apply { start() }
        val storage = object : SessionStorage {
            override fun read(): String? = null
            override fun write(value: String?) = Unit
        }
        api = ShopApi(ApiClient(server.url("/").toString(), ApiClient.httpClient(storage, "1.0.0")))
    }

    @After
    fun stop() = server.close()

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()

    private val shirt = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
    private val mug = "9b2f1c3e-5a41-4d6c-8e1f-0a1b2c3d4e5f"
    private val orderId = "11111111-1111-4111-8111-111111111111"

    private val shopBody = """{
        "angemeldetAls": null,
        "artikel": [
          {"id":"$shirt","name":"Beispiel-Shirt","preisRappen":2500,"mediumId":null,"groessenart":"liste","varianten":["S","M","L"],
           "nichtVerfuegbar":["L"],"hinweis":"Fällt klein aus","verfuegbar":true,"verfuegbarGesetzt":true,"reihenfolge":0},
          {"id":"$mug","name":"Beispiel-Tasse","preisRappen":0,"mediumId":"22222222-2222-4222-8222-222222222222","groessenart":"einheit","varianten":[],
           "nichtVerfuegbar":[],"hinweis":null,"verfuegbar":false,"verfuegbarGesetzt":false,"reihenfolge":1}
        ],
        "uebergabearten": ["Abholung im Lädeli", "Übergabe am Anlass"],
        "grenzen": {"positionen":20,"menge":10,"offeneBestellungen":5},
        "einstellungen": {"bestellenOffen":true,"schnellDazu":false,"preiseOeffentlich":true,"maxMenge":10,"maxPositionen":20,"maxOffene":5,
          "mindestRappen":1000,"bezahlhinweis":"Bezahlt wird bei der Übergabe.","zahlungsart":"Bar","bestaetigung":"Danke! Übergabe: {uebergabe}.",
          "uebergabehinweis":"","bedingungen":"Erste Zeile\nZweite Zeile","geschlossenText":"Zurzeit zu."}
    }"""

    @Test
    fun theShopListsItemsHandoversLimitsAndRules() {
        server.enqueue(json(200, shopBody))
        val shop = (runBlocking { api.shop() } as ApiResult.Ok).value
        assertEquals("GET /api/v1/laden HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(2, shop.items.size)
        val shirtItem = shop.items[0]
        assertEquals(2500L, shirtItem.priceCents)
        assertEquals(SIZE_LIST, shirtItem.sizeKind)
        assertEquals(listOf("S", "M"), shirtItem.orderableVariants())
        assertEquals("Fällt klein aus", shirtItem.note)
        assertFalse(shop.items[1].available)
        assertNull(shirtItem.mediumId)
        assertEquals("22222222-2222-4222-8222-222222222222", shop.items[1].mediumId)
        assertEquals(listOf("Abholung im Lädeli", "Übergabe am Anlass"), shop.handovers)
        assertEquals(5, shop.limits.openOrders)
        assertEquals(1000L, shop.settings.minimumCents)
        assertTrue(shop.settings.open)
        assertEquals("Zurzeit zu.", shop.settings.closedText)
    }

    @Test
    fun anOrderSendsOnlyItemsSizesQuantitiesHandoverAndTheSeenTotal() {
        server.enqueue(
            json(
                201,
                """{"id":"$orderId","status":"offen","uebergabe":"Abholung im Lädeli","summeRappen":5000,"erstelltAm":"2026-12-12T10:00:00Z",
                    "geaendertAm":"2026-12-12T10:00:00Z","positionen":[{"artikelId":"$shirt","name":"Beispiel-Shirt","variante":"M","menge":2,"einzelpreisRappen":2500}]}""",
            ),
        )
        val cart = Cart(listOf(CartLine(shirt, "M", 2)))
        val order = (runBlocking { api.order(orderRequest(cart, "Abholung im Lädeli", 5000)) } as ApiResult.Ok).value
        val request = server.takeRequest()
        assertEquals("POST /api/v1/laden/bestellungen HTTP/1.1", request.requestLine)
        val body = request.body!!.utf8()
        assertTrue(body, body.contains("\"artikelId\":\"$shirt\""))
        assertTrue(body, body.contains("\"variante\":\"M\""))
        assertTrue(body, body.contains("\"menge\":2"))
        assertTrue(body, body.contains("\"erwarteteSummeRappen\":5000"))
        // Der Server rechnet Name und Preis selbst: Die Anfrage nennt keinen.
        assertFalse(body, body.contains("preis"))
        assertFalse(body, body.contains("summeRappen\":5000,\"uebergabe"))
        assertEquals(OrderStatus.OPEN, orderStatus(order.status))
        assertEquals(5000L, order.totalCents)
        assertEquals(2, order.lines[0].quantity)
    }

    @Test
    fun aUnitItemSendsNoSize() {
        server.enqueue(json(400, """{"code":"VARIANTE_UNGUELTIG","message":"x"}"""))
        runBlocking { api.order(orderRequest(Cart(listOf(CartLine(mug, null, 1))), "Abholung im Lädeli", 0)) }
        val body = server.takeRequest().body!!.utf8()
        assertFalse(body, body.contains("variante"))
    }

    @Test
    fun theServerCodesOfAFailedOrderReachTheApp() {
        server.enqueue(json(409, """{"code":"PREIS_GEAENDERT","message":"x","summeRappen":5500}"""))
        server.enqueue(json(409, """{"code":"LADEN_GESCHLOSSEN","message":"x"}"""))
        server.enqueue(json(409, """{"code":"MINDESTBESTELLUNG","message":"x","mindestRappen":1000}"""))
        server.enqueue(json(422, """{"code":"GROESSE_NICHT_VERFUEGBAR","feld":"positionen","message":"x","positionen":[]}"""))
        val request = orderRequest(Cart(listOf(CartLine(shirt, "M", 1))), "Abholung im Lädeli", 2500)
        val codes = List(4) { (runBlocking { api.order(request) } as ApiResult.Failure).code }
        assertEquals(listOf("PREIS_GEAENDERT", "LADEN_GESCHLOSSEN", "MINDESTBESTELLUNG", "GROESSE_NICHT_VERFUEGBAR"), codes)
    }

    @Test
    fun myOrdersComeNewestFirstWithTheirLines() {
        server.enqueue(
            json(
                200,
                """{"bestellungen":[
                    {"id":"$orderId","status":"bereit","uebergabe":"Abholung im Lädeli","summeRappen":2500,"erstelltAm":"2026-12-12T10:00:00Z",
                     "geaendertAm":"2026-12-13T08:00:00Z","positionen":[{"artikelId":null,"name":"Altes Shirt","variante":null,"menge":1,"einzelpreisRappen":2500}]}
                ],"weitere":false,"naechste":null}""",
            ),
        )
        val page = (runBlocking { api.myOrders() } as ApiResult.Ok).value
        assertEquals("GET /api/v1/laden/bestellungen/meine?limit=100 HTTP/1.1", server.takeRequest().requestLine)
        assertEquals(OrderStatus.READY, orderStatus(page.orders[0].status))
        // Ein gelöschter Artikel behält Name und Preis.
        assertNull(page.orders[0].lines[0].itemId)
        assertEquals("Altes Shirt", page.orders[0].lines[0].name)
        assertFalse(canCancelOrder(page.orders[0]))
    }

    @Test
    fun cancellingUsesTheOrderRouteAndAnAlreadyHandedOverOrderIsAConflict() {
        server.enqueue(MockResponse.Builder().code(204).build())
        assertTrue(runBlocking { api.cancel(orderId) } is ApiResult.Ok)
        assertEquals("DELETE /api/v1/laden/bestellungen/$orderId HTTP/1.1", server.takeRequest().requestLine)

        server.enqueue(json(409, """{"code":"STATUS_UNGUELTIG","message":"x"}"""))
        val failure = runBlocking { api.cancel(orderId) } as ApiResult.Failure
        assertEquals("STATUS_UNGUELTIG", failure.code)
        assertEquals(ApiError.REGISTRATION_CLOSED, failure.error) // 409 ohne bekannten Code; die App liest `code`, nicht den Fehlertyp
    }

    @Test
    fun noRouteUnderAdminIsEverUsed() {
        server.enqueue(json(200, shopBody))
        server.enqueue(json(200, """{"bestellungen":[],"weitere":false,"naechste":null}"""))
        server.enqueue(json(201, """{"id":"$orderId","status":"offen","uebergabe":"x","summeRappen":0,"erstelltAm":"2026-12-12T10:00:00Z","geaendertAm":"2026-12-12T10:00:00Z","positionen":[]}"""))
        server.enqueue(MockResponse.Builder().code(204).build())
        runBlocking {
            api.shop()
            api.myOrders()
            api.order(orderRequest(Cart(listOf(CartLine(shirt, "M", 1))), "x", 0))
            api.cancel(orderId)
        }
        repeat(4) { assertFalse(server.takeRequest().requestLine.contains("/admin")) }
    }
}
