package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiResult
import java.time.LocalDate
import java.util.Locale

private val SWISS_LOCALE = Locale.forLanguageTag("de-CH")

/**
 * Was die Person bei der Einteilung darf, nach den Rechten aus dem Lagerdetail. Der Server prüft bei jeder Anfrage. Im
 * geschlossenen Lager bleibt nichts zum Ändern.
 */
data class PlanRights(
    /** `teilnehmer.bearbeiten`: Teilnehmende erfassen, ändern, löschen, importieren; sieht den Cevi-Namen. Standard: nur die Lagerleitung. */
    val editParticipants: Boolean,
    /** `einteilung.bearbeiten`: Zimmer, Fahrten, Gruppen und Schichtplan ändern. Standard: Lagerleitung und Leitung. */
    val edit: Boolean,
    /** Das Team sieht nur das eigene Zimmer und die eigene Fahrt und keine Liste «ohne Fahrt». */
    val seeAll: Boolean,
    /** `diensttausch.anfragen`: für eine eigene Schicht anfragen. Standard: Lagerleitung, Leitung und Team. */
    val requestSwap: Boolean,
    /** `diensttausch.entscheiden`: Anfragen annehmen oder ablehnen. Standard: Lagerleitung und Leitung. */
    val decideSwap: Boolean,
) {
    companion object {
        fun of(p: CampPermissions) = PlanRights(
            editParticipants = p.mayWrite("teilnehmer.bearbeiten"),
            edit = p.mayWrite("einteilung.bearbeiten"),
            // Der Server zeigt dem Team nur das Eigene (Zeilenfilter nach Rolle, kein Recht dafür im Katalog).
            seeAll = p.role != CampRole.TEAM || p.may("einteilung.bearbeiten"),
            requestSwap = p.mayWrite("diensttausch.anfragen"),
            decideSwap = p.mayWrite("diensttausch.entscheiden"),
        )
    }
}

/** Die Zelle des Schichtplans: ein Dienst an einem Tag mit den Eingeteilten. */
data class ShiftCell(val day: CampDay, val duty: Duty, val section: DaySection?, val shifts: List<Shift>) {
    /** Wie viele Plätze noch fehlen bis zur Mindestbesetzung (nie unter 0). */
    val missing: Int get() = (duty.minimum - shifts.size).coerceAtLeast(0)
    val isFull: Boolean get() = missing == 0
}

object PlanRules {
    /** Die Dienste in der Reihenfolge des Plans: nach Abschnitt, dann Reihenfolge und Name. */
    fun orderedDuties(plan: ShiftPlan, sections: List<DaySection>): List<Duty> {
        val order = sections.associate { it.id to it.order }
        return plan.duties.sortedWith(compareBy({ order[it.tagesabschnittId] ?: Int.MAX_VALUE }, { it.order }, { it.name.lowercase(SWISS_LOCALE) }))
    }

    /** Eine Zelle je Tag und Dienst (Tage nach Datum, Dienste in der Reihenfolge des Plans). */
    fun cells(plan: ShiftPlan, schedule: Schedule): List<List<ShiftCell>> {
        val duties = orderedDuties(plan, schedule.sections)
        val sections = schedule.sections.associateBy { it.id }
        val byCell = plan.shifts.groupBy { it.lagertagId to it.dienstId }
        return schedule.days.sortedBy { it.date }.map { day ->
            duties.map { duty ->
                ShiftCell(day, duty, sections[duty.tagesabschnittId], (byCell[day.id to duty.id] ?: emptyList()).sortedBy { it.name.lowercase(SWISS_LOCALE) })
            }
        }
    }

    /** Wie viele Dienste an einem Tag noch nicht voll besetzt sind. */
    fun gaps(row: List<ShiftCell>): Int = row.count { !it.isFull }

    /** Die Schichten der Person im ganzen Plan (für «Deine Dienste hervorgehoben» und die Wahl bei einer Tauschanfrage). */
    fun shiftsOf(plan: ShiftPlan, personId: String): List<Shift> = plan.shifts.filter { it.personId == personId }

    /** Das Kürzel zu einem Namen: die ersten zwei Buchstaben in Grossbuchstaben (Avatare). */
    fun initials(name: String): String = name.trim().take(2).uppercase(SWISS_LOCALE)

    /** Alle Stufen der Teilnehmenden, alphabetisch (Filter der Abzählliste und der Teilnehmenden). */
    fun stages(participants: List<Participant>): List<String> =
        participants.mapNotNull { it.stage?.takeIf { s -> s.isNotBlank() } }.distinct().sortedBy { it.lowercase(SWISS_LOCALE) }

    /** Teilnehmende nach Nachname und Vorname (Namen ohne Beachtung der Schreibweise). */
    fun sortedParticipants(participants: List<Participant>): List<Participant> =
        participants.sortedWith(compareBy({ it.lastName.lowercase(SWISS_LOCALE) }, { it.firstName.lowercase(SWISS_LOCALE) }))

    /**
     * Die bekannten Personen des Lagers (Konten) aus allem, was geladen ist: der Plan, die Gruppen, die Zimmer und die Fahrten, dazu
     * `extra` (Aufgaben, Höck-Modus). Es gibt keine Route, die alle Mitglieder liefert; wer nirgends vorkommt, fehlt in der Auswahl.
     */
    fun knownPeople(
        plan: ShiftPlan?,
        groups: GroupList?,
        rooms: RoomList?,
        trips: List<Trip>?,
        extra: List<RessortPerson>,
    ): List<RessortPerson> {
        val all = LinkedHashMap<String, RessortPerson>()
        extra.forEach { all.putIfAbsent(it.id, it) }
        fun add(id: String?, name: String?, cevi: String? = null) {
            if (id != null && !name.isNullOrBlank()) all.putIfAbsent(id, RessortPerson(id, name, cevi))
        }
        plan?.shifts?.forEach { add(it.personId, it.name, it.ceviName) }
        plan?.stats?.forEach { add(it.personId, it.name) }
        groups?.groups?.forEach { g -> g.people.forEach { add(it.personId, it.name, it.ceviName) } }
        rooms?.rooms?.forEach { r -> r.members.filter { !it.isParticipant }.forEach { add(it.id, it.name) } }
        trips?.forEach { t ->
            add(t.driver?.personId, t.driver?.name)
            t.passengers.forEach { add(it.personId, it.name) }
        }
        return all.values.sortedBy { it.display.lowercase(SWISS_LOCALE) }
    }

    /** Wer in einer Fahrt oder in keiner: Mitfahrende einer Richtung ohne den Fahrer. */
    fun seatedKeys(trips: List<Trip>, direction: TripDirection): Set<String> = buildSet {
        trips.filter { it.direction == direction }.forEach { t ->
            t.fahrerPersonId?.let { add("p:$it") }
            t.passengers.forEach { add(it.personId?.let { id -> "p:$id" } ?: "t:${it.teilnehmerId}") }
        }
    }

    /** Fahrten einer Richtung nach Datum und Zeit; Fahrten ohne Datum stehen zuletzt. */
    fun tripsOf(trips: List<Trip>, direction: TripDirection): List<Trip> =
        trips.filter { it.direction == direction }.sortedWith(compareBy({ parseDay(it.date) ?: LocalDate.MAX }, { it.time.orEmpty() }, { it.id }))

    /** Der Name einer Person oder eines Teilnehmenden in einer Schicht: wer nicht bekannt ist, bekommt `fallback`. */
    fun swapActor(swap: Swap, plan: ShiftPlan?, people: Map<String, String>, fallback: String): String =
        swap.vonName?.takeIf { it.isNotBlank() } ?: people[swap.vonPersonId] ?: plan?.shifts?.firstOrNull { it.id == swap.schichtId && it.personId == swap.vonPersonId }?.name ?: fallback

    /** Offene Anfragen zuerst (die älteste oben), danach die entschiedenen, die jüngste zuerst. */
    fun sortedSwaps(swaps: List<Swap>): List<Swap> =
        swaps.filter { it.status == SwapStatus.OPEN }.sortedBy { it.createdAt } + swaps.filter { it.status != SwapStatus.OPEN }.sortedByDescending { it.createdAt }

    /** Wie viele Personen von `total` den Haken haben, als Anteil für die Anzeige. */
    fun percent(present: Int, total: Int): Int = if (total <= 0) 0 else (present * 100) / total
}

/** Wählt in einer Liste von Schlüsseln aus oder ab (Mehrfachauswahl in den Blättern). */
fun <T> Set<T>.toggled(item: T): Set<T> = if (item in this) this - item else this + item

/**
 * Verschiebt eine Person in zwei Schritten: erst heraus, dann hinein (der Server lässt niemanden an zwei Orten sitzen, Hinzufügen
 * zuerst geht darum nicht). Scheitert das Hinein, ist die Person schon heraus: `onRemoved` meldet es, und ein neuer Versuch
 * (`removed = true`) macht nur noch den zweiten Schritt. So bleibt der Zustand nach einem Fehler wiederholbar.
 */
suspend fun <T> moveInTwoSteps(
    removed: Boolean,
    remove: suspend () -> ApiResult<*>,
    add: suspend () -> ApiResult<T>,
    onRemoved: () -> Unit,
): ApiResult<T> {
    if (!removed) {
        val out = remove()
        if (out is ApiResult.Failure) return out
        onRemoved()
    }
    return add()
}

/** Wie «Anna Beispiel» in Kurzform für Avatare: Vorname, sonst Name. */
fun shortName(display: String): String = display.substringBefore(" (").trim()

/** Ein Wochentag und Tag zum Vorlesen: «Montag, 17. Juli». */
fun spokenDay(date: String): String = parseDay(date)?.let { weekdayName(it) + ", " + formatDay(date) } ?: date
