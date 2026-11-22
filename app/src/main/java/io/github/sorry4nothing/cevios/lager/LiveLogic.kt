package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiResult
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

private val SWISS_LOCALE = Locale.forLanguageTag("de-CH")
private val ZURICH_ZONE = ZoneId.of("Europe/Zurich")

/**
 * Was die Person in «Im Lager» darf, nach den Rechten aus dem Lagerdetail. Der Server prüft bei jeder Anfrage. Im geschlossenen
 * Lager bleibt nichts zum Ändern.
 */
data class LiveRights(
    /** `anwesenheit.eigene`: die eigene Anwesenheit eintragen und Anfragen stellen. Standard: Lagerleitung, Leitung, Team. */
    val ownAttendance: Boolean,
    /** `anwesenheit.lesen_alle`: die Zusammenfassung lesen. Standard: Lagerleitung, Leitung, Lesen. */
    val readAttendance: Boolean,
    /** `anwesenheit.bearbeiten`: Anfragen entscheiden und die Frist setzen. Standard: nur die Lagerleitung. */
    val decideAttendance: Boolean,
    /** `tagesseiten.lesen`: Tagesseiten lesen. Standard: Lagerleitung, Leitung, Lesen (das Team nicht). */
    val readDayPages: Boolean,
    /** `tagesseiten.bearbeiten`: Tageschef, Notiz und Blöcke ändern. Standard: Lagerleitung und Leitung. */
    val editDayPages: Boolean,
    /** `einkauf.lesen`: die Einkaufsliste lesen. Standard: Lagerleitung, Leitung, Lesen (das Team nicht). */
    val readShopping: Boolean,
    /** `einkauf.bearbeiten`: Artikel anlegen, ändern, abhaken, löschen. Standard: Lagerleitung und Leitung. */
    val editShopping: Boolean,
    /** `packliste.abhaken`: abhaken, entfernen, eigene Punkte. Standard: Lagerleitung, Leitung, Team. */
    val tickPacking: Boolean,
    /** `infos.bearbeiten`: Abschnitte ändern, anlegen, löschen, ordnen. Standard: nur die Lagerleitung. */
    val editInfos: Boolean,
    /** Eigene To-dos führt jede Rolle (auch «lesen»), solange das Lager offen ist. */
    val todos: Boolean,
    /** Die Anwesenheit öffnen: die eigene oder die aller; auch im geschlossenen Lager (nur lesend). */
    val seesAttendance: Boolean,
) {
    companion object {
        fun of(p: CampPermissions) = LiveRights(
            ownAttendance = p.mayWrite("anwesenheit.eigene"),
            readAttendance = p.may("anwesenheit.lesen_alle"),
            decideAttendance = p.mayWrite("anwesenheit.bearbeiten"),
            readDayPages = p.may("tagesseiten.lesen"),
            editDayPages = p.mayWrite("tagesseiten.bearbeiten"),
            readShopping = p.may("einkauf.lesen"),
            editShopping = p.mayWrite("einkauf.bearbeiten"),
            tickPacking = p.mayWrite("packliste.abhaken"),
            editInfos = p.mayWrite("infos.bearbeiten"),
            todos = !p.readOnly,
            seesAttendance = p.may("anwesenheit.eigene", "anwesenheit.lesen_alle"),
        )
    }
}

/** Ein Feld der Anwesenheit: Lagertag und Abschnitt. */
data class AttendanceKey(val dayId: String, val sectionId: String)

/** Was beim Speichern geschieht: Felder, die der Server direkt einträgt, und Felder, für die eine Anfrage nötig ist. */
data class AttendancePlan(val direct: List<AttendanceField>, val requests: List<AttendanceField>)

/** Eine Zeile der Zusammenfassung: ein Tag mit den Zahlen je Abschnitt. */
data class AttendanceSummaryDay(val day: CampDay, val sections: List<AttendanceSummarySection>)

data class AttendanceSummarySection(val section: DaySection, val present: Int, val absent: Int, val open: Int)

/** Eine Zeile der Einkaufsliste für die Anzeige: ein Artikel (oder mehrere zusammengezählte) mit Menge und Angabe. */
data class ShopLine(val key: String, val name: String, val amount: String?, val buyer: String?, val buyerId: String?, val count: Int, val done: Boolean, val entries: List<ShopEntry>)

/** Eine Abteilung der Einkaufsliste. */
data class ShopSection(val group: ShopGroup, val lines: List<ShopLine>)

/** Ein Abschnitt der Packliste mit seinen Zeilen. */
data class PackSection(val title: String, val rows: List<PackRow>)

/** Eine Zeile der Packliste: ein Punkt der Vorlage oder ein eigener. `own` heisst: nur die Person sieht ihn. */
data class PackRow(val id: String, val text: String, val checked: Boolean, val own: Boolean, val leadershipOnly: Boolean)

object LiveRules {
    // ---- Anwesenheit ----

    fun valueOf(own: AttendanceOwn?, day: String, section: String): Attendance =
        own?.entries?.firstOrNull { it.lagertagId == day && it.tagesabschnittId == section }?.value ?: Attendance.OPEN

    /** Die Frist ist abgelaufen, wenn es eine gibt und sie vor `now` liegt. Eine unlesbare Frist sperrt nichts (der Server entscheidet). */
    fun deadlinePassed(deadline: String?, now: Instant): Boolean = deadline?.let { runCatching { Instant.parse(it) }.getOrNull() }?.isBefore(now) == true

    /**
     * Teilt die Änderungen der Person in Felder, die der Server direkt einträgt, und Felder, die eine Anfrage brauchen: Ein offenes
     * Feld (keine Zeile oder `O`) trägt der Server bis zur Frist direkt ein; ein eingetragenes (`A` oder `F`) oder jedes nach der
     * Frist braucht eine Anfrage. Gleiche Werte und Felder mit offener Anfrage fallen weg (je Feld wartet höchstens eine).
     */
    fun plan(own: AttendanceOwn, draft: Map<AttendanceKey, Attendance>, passed: Boolean): AttendancePlan {
        val waiting = own.requests.filter { it.status == RequestStatus.OPEN }.map { AttendanceKey(it.lagertagId, it.tagesabschnittId) }.toSet()
        val direct = mutableListOf<AttendanceField>()
        val requests = mutableListOf<AttendanceField>()
        for ((key, wanted) in draft) {
            val current = valueOf(own, key.dayId, key.sectionId)
            if (wanted == current || key in waiting) continue
            val field = AttendanceField(key.dayId, key.sectionId, wanted)
            if (current == Attendance.OPEN && !passed) direct += field else requests += field
        }
        return AttendancePlan(direct, requests)
    }

    /** «Ganzer Tag»: Sind alle Abschnitte gleich, geht es einen Wert weiter (Da, Frei, Offen); sonst werden alle «Da». */
    fun wholeDay(current: List<Attendance>): Attendance {
        val first = current.firstOrNull() ?: return Attendance.PRESENT
        return if (current.all { it == first }) first.next() else Attendance.PRESENT
    }

    /** Ein Feld mit offener Anfrage: Es lässt sich nicht noch einmal anfragen. */
    fun waitingFor(own: AttendanceOwn?, key: AttendanceKey): AttendanceRequest? =
        own?.requests?.firstOrNull { it.status == RequestStatus.OPEN && it.lagertagId == key.dayId && it.tagesabschnittId == key.sectionId }

    fun summary(summary: AttendanceSummary, schedule: Schedule): List<AttendanceSummaryDay> {
        val sections = schedule.sections.sortedBy { it.order }
        return schedule.days.sortedBy { it.date }.map { day ->
            AttendanceSummaryDay(
                day,
                sections.map { section ->
                    fun count(value: Attendance) = summary.rows.filter { it.lagertagId == day.id && it.tagesabschnittId == section.id && it.value == value }.sumOf { it.total }
                    AttendanceSummarySection(section, count(Attendance.PRESENT), count(Attendance.ABSENT), count(Attendance.OPEN))
                },
            )
        }
    }

    /** Die Frist als Zeitpunkt: Ende des gewählten Tages in Zürich (23:59). */
    fun deadlineAt(date: LocalDate): String = date.atTime(23, 59).atZone(ZURICH_ZONE).toInstant().toString()

    fun deadlineDate(deadline: String?): LocalDate? = deadline?.let { runCatching { Instant.parse(it).atZone(ZURICH_ZONE).toLocalDate() }.getOrNull() }

    // ---- Tagesseiten und Heute ----

    /** «Tag 3 von 8»: der Lagertag von heute; `null` vor dem Start und nach dem Ende. */
    fun dayOfCamp(from: String, until: String, today: LocalDate): Pair<Int, Int>? {
        val start = parseDay(from) ?: return null
        val end = parseDay(until) ?: return null
        if (today.isBefore(start) || today.isAfter(end)) return null
        return (ChronoUnit.DAYS.between(start, today) + 1).toInt() to (ChronoUnit.DAYS.between(start, end) + 1).toInt()
    }

    /** Blöcke einer Art in der Reihenfolge des Servers. */
    fun blocks(blocks: List<DayBlock>, kind: BlockKind): List<DayBlock> = blocks.filter { it.kind == kind }.sortedBy { it.order }

    /** Die eigenen Schichten von heute mit dem Namen des Dienstes: (Dienst, Abschnitt). */
    fun myShiftsToday(shifts: TodayShiftPlan?, selfId: String, sections: List<DaySection>): List<Pair<Duty?, DaySection?>> {
        val plan = shifts ?: return emptyList()
        val duties = plan.duties.associateBy { it.id }
        return plan.shifts.filter { it.personId == selfId }.map { shift ->
            duties[shift.dienstId] to sections.firstOrNull { it.id == shift.tagesabschnittId }
        }.sortedBy { (_, section) -> section?.order ?: Int.MAX_VALUE }
    }

    /** Fällig heute oder früher, noch offen, die Person ist verantwortlich oder unterstützt; zuerst die ältesten Fristen. */
    fun dueForMe(tasks: List<CampTask>, selfId: String, today: LocalDate): List<CampTask> =
        tasks.filter { task ->
            !task.done && task.involves(selfId) &&
                TaskRules.dueDate(task)?.let { !it.isAfter(today) } == true
        }.sortedWith(compareBy({ TaskRules.dueDate(it) ?: LocalDate.MAX }, { it.order }))

    /** Das nächste Datum für «In den nächsten Tag schieben». */
    fun nextDay(date: String?): String? = parseDay(date)?.plusDays(1)?.toString()

    // ---- Einkauf ----

    /** Eine Menge ohne überflüssige Nullen: «6», «1.5». */
    fun formatAmount(amount: Double?, unit: String?): String? {
        if (amount == null) return null
        val number = if (amount % 1.0 == 0.0) amount.toLong().toString() else String.format(SWISS_LOCALE, "%.2f", amount).trimEnd('0').trimEnd('.', ',')
        return listOfNotNull(number, unit?.takeIf { it.isNotBlank() }).joinToString(" ")
    }

    /** Liest eine Menge aus der Eingabe: «6», «1,5», «1.5»; leer oder keine Zahl ist `null`, eine negative oder zu grosse Menge auch. */
    fun parseAmount(text: String): Double? =
        text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it > 0 && it <= MAX_AMOUNT && it.isFinite() }

    /** Die Abteilungen der zusammengezählten Liste in der Reihenfolge des Ladens; leere Abteilungen fehlen. */
    fun shopSections(items: List<ShopTotal>): List<ShopSection> =
        items.groupBy { it.group }.entries.sortedBy { it.key.ordinal }.map { (group, list) ->
            ShopSection(
                group,
                list.sortedWith(compareBy({ it.done }, { it.name.lowercase(SWISS_LOCALE) })).map { total ->
                    ShopLine(
                        key = total.entries.firstOrNull()?.id ?: total.name,
                        name = total.name,
                        amount = formatAmount(total.amount, total.unit),
                        buyer = total.buyer?.display,
                        buyerId = total.buyerId,
                        count = total.count,
                        done = total.done,
                        entries = total.entries,
                    )
                },
            )
        }

    /** Die Liste als Text zum Teilen oder Kopieren: je Abteilung eine Zeile mit dem Titel, darunter die offenen Artikel. */
    fun shopText(sections: List<ShopSection>, title: (ShopGroup) -> String): String =
        sections.mapNotNull { section ->
            val open = section.lines.filterNot { it.done }
            if (open.isEmpty()) null else (listOf(title(section.group)) + open.map { "- " + listOfNotNull(it.name, it.amount).joinToString(" ") }).joinToString("\n")
        }.joinToString("\n\n")

    /** Die Vorschläge, die so beginnen, ohne den schon getippten Namen selbst. */
    fun matching(suggestions: List<ShopSuggestion>, typed: String): List<ShopSuggestion> =
        suggestions.filterNot { it.name.equals(typed.trim(), ignoreCase = true) }

    // ---- Packliste ----

    /** Die Abschnitte der Packliste: erst die der Vorlage in ihrer Reihenfolge, die eigenen Punkte dazu; entfernte Punkte fehlen. */
    fun packSections(list: PackList): List<PackSection> {
        val rows = linkedMapOf<String, MutableList<PackRow>>()
        list.items.filter { it.state != PackState.REMOVED }.sortedBy { it.order }.forEach {
            rows.getOrPut(it.section) { mutableListOf() } += PackRow(it.id, it.text, it.state == PackState.CHECKED, own = false, leadershipOnly = it.leadershipOnly)
        }
        list.own.sortedBy { it.order }.forEach {
            rows.getOrPut(it.section) { mutableListOf() } += PackRow(it.id, it.text, it.checked, own = true, leadershipOnly = false)
        }
        return rows.map { (title, list) -> PackSection(title, list) }
    }

    /** «5 von 9 gepackt»: (gepackt, alle) ohne entfernte Punkte. */
    fun packCount(list: PackList): Pair<Int, Int> {
        val items = list.items.filter { it.state != PackState.REMOVED }
        return (items.count { it.state == PackState.CHECKED } + list.own.count { it.checked }) to (items.size + list.own.size)
    }

    // ---- Notfall ----

    /**
     * Wohin «Route öffnen» führt: der gespeicherte Kartenlink (nur https), sonst die Adresse als `geo:`-Suche für die Karten-App des
     * Geräts. `null`, wenn es weder Link noch Adresse gibt.
     */
    fun routeUri(address: EmergencyAddress): String? {
        address.kartenLink?.takeIf { it.startsWith("https://") }?.let { return it }
        val text = listOfNotNull(address.address, address.place).filter { it.isNotBlank() }.joinToString(", ")
        return text.takeIf { it.isNotBlank() }?.let { "geo:0,0?q=" + URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
    }

    /**
     * Was das Notfall-Fenster nach einer Anfrage zeigt und ob es veraltet ist: Ein neuer Stand ersetzt den alten; scheitert die Anfrage
     * (kein Netz), bleibt der zuletzt geladene Stand stehen, solange es einen gibt. Nur ohne alten Stand erscheint der Fehler.
     */
    fun emergencyAfter(previous: ApiResult<EmergencyState>?, result: ApiResult<Emergency>, now: Instant): Pair<ApiResult<EmergencyState>, Boolean> = when {
        result is ApiResult.Ok -> ApiResult.Ok(EmergencyState(result.value, now)) to false
        previous is ApiResult.Ok -> previous to true
        else -> (result as ApiResult.Failure) to false
    }

    /** Die Kontakte, die man anrufen kann (mit Nummer), in der Reihenfolge des Servers. */
    fun callable(contacts: List<EmergencyContact>): List<EmergencyContact> = contacts.filter { !it.phone.isNullOrBlank() }

    // ---- Infos ----

    /** Verschiebt den Abschnitt `id` um eine Stelle; ausserhalb der Liste bleibt sie unverändert. */
    fun moved(list: List<InfoSection>, id: String, delta: Int): List<InfoSection> {
        val from = list.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in list.indices) return list
        return list.toMutableList().also { it.add(to, it.removeAt(from)) }
    }

    private const val MAX_AMOUNT = 100000.0
}

/** Die Notrufnummern der Schweiz, die der Entwurf fest zeigt: Sanität und Rega. Sie stehen nicht in der API. */
object SwissEmergency {
    const val AMBULANCE = "144"
    const val RESCUE_FLIGHT = "1414"
}
