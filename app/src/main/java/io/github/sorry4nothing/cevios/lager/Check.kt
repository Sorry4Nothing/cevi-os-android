package io.github.sorry4nothing.cevios.lager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wie weit das Lager ist (`stand` des Checks und jedes Prüfers): bereit, nur Hinweise oder offen (nicht bereit). */
enum class CheckLevel(val wire: String) {
    READY("bereit"),
    NOTES("hinweise"),
    OPEN("offen"),
    ;

    companion object {
        /** Ein Stand, den die App nicht kennt, gilt als offen: Lieber einmal zu viel nachsehen als ein «bereit» zu behaupten. */
        fun from(wire: String?): CheckLevel = entries.firstOrNull { it.wire == wire } ?: OPEN
    }
}

/** Ein Beispiel zu einem Hinweis: eine Aufgabe (Frist), ein Meilenstein (Datum) oder ein eigener Punkt (ohne Datum). */
@Serializable
data class CheckExample(
    val id: String,
    @SerialName("titel") val title: String,
    @SerialName("datum") val date: String? = null,
    /** Nur bei Meilensteinen und nur mit dem Recht auf alle Aufgaben. */
    @SerialName("offeneAufgaben") val openTasks: Int? = null,
)

/** Ein Hinweis eines Prüfers; `severity` ist `offen` (das Lager ist nicht bereit) oder `hinweis` (gut zu wissen). */
@Serializable
data class CheckNote(
    val code: String,
    @SerialName("schwere") val severity: String,
    @SerialName("text") val text: String,
    @SerialName("anzahl") val count: Int? = null,
    @SerialName("beispiele") val examples: List<CheckExample> = emptyList(),
) {
    val blocking: Boolean get() = severity == "offen"
}

/** Das Ergebnis eines Prüfers (`aufgaben`, `meilensteine`, `packliste`, `notfall`, `adressbuch`, `eigene`). */
@Serializable
data class CheckChecker(
    val id: String,
    @SerialName("titel") val title: String,
    @SerialName("stand") val levelName: String,
    @SerialName("hinweise") val notes: List<CheckNote> = emptyList(),
) {
    val level: CheckLevel get() = CheckLevel.from(levelName)
}

/** `GET /api/v1/lager/{id}/check`: nur Prüfer, deren Quelle die Rolle lesen darf. */
@Serializable
data class CampCheck(
    @SerialName("stand") val levelName: String,
    @SerialName("datum") val date: String,
    @SerialName("pruefer") val checkers: List<CheckChecker> = emptyList(),
) {
    val level: CheckLevel get() = CheckLevel.from(levelName)
}

/** Ein eigener Punkt der Lagerleitung. `version` braucht jede Änderung (sonst 409 `VERSION_KONFLIKT`). */
@Serializable
data class CheckItem(
    val id: String,
    val version: Long,
    @SerialName("titel") val title: String,
    @SerialName("erledigt") val done: Boolean,
    @SerialName("reihenfolge") val order: Int,
)

@Serializable
data class CheckItems(@SerialName("punkte") val points: List<CheckItem> = emptyList())

/** Beides, was der Bildschirm braucht: der Check und die eigenen Punkte (fehlt eines, fehlt nur dieser Teil). */
data class CheckData(val check: CampCheck?, val points: List<CheckItem>?)
