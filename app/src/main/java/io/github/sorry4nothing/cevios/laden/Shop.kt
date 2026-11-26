package io.github.sorry4nothing.cevios.laden

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Ein Artikel des Lädelis. Preise sind Rappen (ein `Long`: 20 Positionen mal 99 Stück mal bis 10 000 Franken passen sonst nicht in einen `Int`). */
@Serializable
data class ShopItem(
    val id: String,
    val name: String,
    @SerialName("preisRappen") val priceCents: Long,
    /** `einheit` (keine Wahl) oder `liste` (die Person wählt eine der [variants]). */
    @SerialName("groessenart") val sizeKind: String = SIZE_UNIT,
    @SerialName("varianten") val variants: List<String> = emptyList(),
    /** Grössen aus [variants], die nicht zu haben sind: sichtbar, aber nicht bestellbar. */
    @SerialName("nichtVerfuegbar") val unavailableVariants: List<String> = emptyList(),
    @SerialName("hinweis") val note: String? = null,
    /** Schon unter Berücksichtigung der Grössen gerechnet (alle Grössen weg heisst `false`). */
    @SerialName("verfuegbar") val available: Boolean = true,
)

const val SIZE_UNIT = "einheit"
const val SIZE_LIST = "liste"

@Serializable
data class ShopLimits(
    @SerialName("positionen") val positions: Int = 20,
    @SerialName("menge") val quantity: Int = 10,
    @SerialName("offeneBestellungen") val openOrders: Int = 5,
)

/** Regeln und Texte der Abteilung (`einstellungen`); die App zeigt die Texte, die Regeln prüft der Server. */
@Serializable
data class ShopSettings(
    @SerialName("bestellenOffen") val open: Boolean = true,
    @SerialName("mindestRappen") val minimumCents: Long = 0,
    @SerialName("bezahlhinweis") val paymentNote: String = "",
    @SerialName("zahlungsart") val paymentMethod: String = "",
    /** Meldung nach dem Bestellen; `{uebergabe}` steht für die gewählte Übergabeart. */
    @SerialName("bestaetigung") val confirmation: String = "",
    @SerialName("uebergabehinweis") val handoverNote: String = "",
    /** Ein Absatz je Zeile. */
    @SerialName("bedingungen") val terms: String = "",
    @SerialName("geschlossenText") val closedText: String = "",
)

/** `GET /api/v1/laden`. */
@Serializable
data class ShopView(
    @SerialName("artikel") val items: List<ShopItem> = emptyList(),
    @SerialName("uebergabearten") val handovers: List<String> = emptyList(),
    @SerialName("grenzen") val limits: ShopLimits = ShopLimits(),
    @SerialName("einstellungen") val settings: ShopSettings = ShopSettings(),
)

@Serializable
data class ShopOrderLine(
    @SerialName("artikelId") val itemId: String? = null,
    val name: String,
    @SerialName("variante") val variant: String? = null,
    @SerialName("menge") val quantity: Int,
    @SerialName("einzelpreisRappen") val unitCents: Long,
)

/** Eine Bestellung; `status`: `offen`, `bereit`, `uebergeben` oder `storniert` (die zwei letzten sind endgültig). */
@Serializable
data class ShopOrder(
    val id: String,
    val status: String,
    @SerialName("uebergabe") val handover: String,
    @SerialName("summeRappen") val totalCents: Long,
    @SerialName("erstelltAm") val createdAt: String,
    @SerialName("geaendertAm") val changedAt: String? = null,
    @SerialName("positionen") val lines: List<ShopOrderLine> = emptyList(),
)

@Serializable
data class ShopOrderPage(
    @SerialName("bestellungen") val orders: List<ShopOrder> = emptyList(),
    @SerialName("weitere") val more: Boolean = false,
    @SerialName("naechste") val next: String? = null,
)

/** Die Anfrage nennt nur Artikel, Grösse und Menge; Name, Preis und Summe liest der Server selbst. */
@Serializable
data class ShopOrderRequest(
    @SerialName("positionen") val lines: List<ShopOrderLineRequest>,
    @SerialName("uebergabe") val handover: String,
    /** Die Summe, die die Person gesehen hat: Weicht der Server ab, entsteht keine Bestellung (409 `PREIS_GEAENDERT`). */
    @SerialName("erwarteteSummeRappen") val expectedCents: Long,
)

@Serializable
data class ShopOrderLineRequest(
    @SerialName("artikelId") val itemId: String,
    @SerialName("variante") val variant: String? = null,
    @SerialName("menge") val quantity: Int,
)
