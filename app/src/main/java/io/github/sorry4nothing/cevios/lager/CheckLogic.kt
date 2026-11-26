package io.github.sorry4nothing.cevios.lager

/**
 * Was die Person im Lager-Check darf. Lesen: `check.lesen` (Standard: Lagerleitung, Leitung, Lesen; das Team nur, wenn die
 * Lagerleitung es einstellt). Eigene Punkte schreiben: `zeitplan.bearbeiten` und `check.lesen`; im geschlossenen Lager bleibt
 * nichts zum Ändern. Die App zeigt nur, was erlaubt ist; der Server prüft trotzdem.
 */
data class CheckRights(val read: Boolean, val edit: Boolean) {
    companion object {
        fun of(p: CampPermissions) = CheckRights(
            read = p.may("check.lesen"),
            edit = p.may("check.lesen") && p.mayWrite("zeitplan.bearbeiten"),
        )
    }
}

/** Reine Funktionen des Checks, ohne Gerät testbar. */
object CheckRules {
    /** Höchstens so viele eigene Punkte je Lager (der Server antwortet sonst 409 `LIMIT_ERREICHT`). */
    const val MAX_POINTS = 100

    /** Höchste Länge eines Titels (Vertrag). */
    const val TITLE_MAX = 200

    /** Prüfer mit offenen Punkten zuerst, dann Hinweise, dann die in Ordnung; innerhalb davon die Reihenfolge des Servers. */
    fun sortedCheckers(check: CampCheck): List<CheckChecker> = check.checkers.sortedByDescending { it.level.ordinal }

    /** Zahl der Hinweise mit Schwere «offen» (das Lager ist deshalb nicht bereit). */
    fun blockingCount(check: CampCheck): Int = check.checkers.sumOf { c -> c.notes.count { it.blocking } }

    /** Die eigenen Punkte: offene zuerst, dann erledigte; innerhalb nach `reihenfolge`. */
    fun sortedPoints(points: List<CheckItem>): List<CheckItem> = points.sortedWith(compareBy({ it.done }, { it.order }, { it.title }))

    /** Der Titel, der hinausgeht: ohne Rand, nicht leer, nicht zu lang; sonst `null` (der Knopf bleibt aus). */
    fun validTitle(text: String): String? = text.trim().takeIf { it.isNotEmpty() && it.length <= TITLE_MAX }

    /** Ein neuer Punkt ist nur unter der Grenze möglich. */
    fun canAdd(points: List<CheckItem>): Boolean = points.size < MAX_POINTS

    /** Ersetzt einen Punkt durch den Stand des Servers. */
    fun replace(points: List<CheckItem>, changed: CheckItem): List<CheckItem> = points.map { if (it.id == changed.id) changed else it }
}
