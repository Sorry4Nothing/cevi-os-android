package io.github.sorry4nothing.cevios.lager

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Wie die Aufgabenliste die Aufgaben zeigt (Entwurf L5, T6). */
enum class TaskView { LIST, MINE, PEOPLE, RESSORT, KANBAN }

/** Sortierung von «Meine»: nach Frist, in der Reihenfolge der Lagerleitung oder nach Ressort. */
enum class MineSort { DUE, ORDER, RESSORT }

/** Abschnitte der Liste (Entwurf L5): überfällig, in den nächsten zwei Wochen, später, ohne Frist, erledigt. */
enum class TaskSection { OVERDUE, SOON, LATER, NO_DUE, DONE }

/** Was die Person mit Aufgaben und Ressorts darf, nach den Rechten aus dem Lagerdetail. Der Server prüft bei jeder Anfrage. */
data class TaskRights(
    val edit: Boolean,
    val setStatus: Boolean,
    val readAll: Boolean,
    val readRessorts: Boolean,
    val editRessorts: Boolean,
    val readHistory: Boolean,
    val seeLeadershipStart: Boolean,
) {
    companion object {
        /**
         * `aufgaben.bearbeiten` ändert alle Aufgaben und liest auch alle; `aufgaben.eigene` setzt nur den Status eigener Aufgaben
         * (das Team sieht nur seine eigenen). Ist das Lager geschlossen, bleibt nichts zum Ändern.
         */
        fun of(p: CampPermissions) = TaskRights(
            edit = p.mayWrite("aufgaben.bearbeiten"),
            setStatus = p.mayWrite("aufgaben.bearbeiten", "aufgaben.eigene"),
            readAll = p.may("aufgaben.lesen_alle", "aufgaben.bearbeiten"),
            readRessorts = p.may("ressorts.lesen"),
            editRessorts = p.mayWrite("ressorts.bearbeiten"),
            readHistory = p.may("verlauf.lesen"),
            seeLeadershipStart = p.seesLeadershipStart,
        )
    }
}

private val SWISS = Locale.forLanguageTag("de-CH")
private val DAY_MONTH_FORMAT = DateTimeFormatter.ofPattern("d. MMMM", SWISS)
private val DAY_MONTH_YEAR_FORMAT = DateTimeFormatter.ofPattern("d. MMMM yyyy", SWISS)
private val ZURICH = ZoneId.of("Europe/Zurich")

/** «15. März»; ein unlesbares Datum steht unverändert da. */
fun formatDay(iso: String?): String = parseDay(iso)?.let { DAY_MONTH_FORMAT.format(it) } ?: iso.orEmpty()

/** «15. März 2027». */
fun formatDayLong(iso: String?): String = parseDay(iso)?.let { DAY_MONTH_YEAR_FORMAT.format(it) } ?: iso.orEmpty()

fun parseDay(iso: String?): LocalDate? = iso?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

fun weekdayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, SWISS)

/** Heute in der Zeit des Lagers (Zürich), wie es der Server rechnet. */
fun todayInCamp(): LocalDate = LocalDate.now(ZURICH)

/** Die Logik der Aufgabenliste: Abschnitte, Sortierung, Ansichten. Reine Funktionen, damit sie ohne Gerät getestet sind. */
object TaskRules {
    /** Bis wann eine Frist als «bald» gilt. */
    const val SOON_DAYS = 14L

    fun dueDate(task: CampTask): LocalDate? = parseDay(task.due)

    /** Wie viele Tage die Frist her ist; `null`, wenn die Aufgabe nicht überfällig ist. Erledigte Aufgaben sind nie überfällig. */
    fun overdueDays(task: CampTask, today: LocalDate): Int? {
        val due = dueDate(task) ?: return null
        if (task.done || !due.isBefore(today)) return null
        return ChronoUnit.DAYS.between(due, today).toInt()
    }

    fun isOverdue(task: CampTask, today: LocalDate) = overdueDays(task, today) != null

    /** «Später erinnern»: bis zu diesem Datum bleibt die Aufgabe ruhig im Abschnitt «Später». */
    fun isSnoozed(task: CampTask, today: LocalDate): Boolean {
        val until = parseDay(task.snoozedUntil) ?: return false
        return !task.done && until.isAfter(today)
    }

    fun isSoon(task: CampTask, today: LocalDate): Boolean {
        val due = dueDate(task) ?: return false
        return !task.done && !due.isBefore(today) && !due.isAfter(today.plusDays(SOON_DAYS))
    }

    fun section(task: CampTask, today: LocalDate): TaskSection = when {
        task.done -> TaskSection.DONE
        isSnoozed(task, today) -> TaskSection.LATER
        isOverdue(task, today) -> TaskSection.OVERDUE
        isSoon(task, today) -> TaskSection.SOON
        task.due == null -> TaskSection.NO_DUE
        else -> TaskSection.LATER
    }

    /** Erst die Frist (ohne Frist zuletzt), dann die Reihenfolge der Lagerleitung, dann der Titel. */
    private val byDue = Comparator<CampTask> { a, b ->
        val da = dueDate(a) ?: LocalDate.MAX
        val db = dueDate(b) ?: LocalDate.MAX
        da.compareTo(db).takeIf { it != 0 } ?: a.order.compareTo(b.order).takeIf { it != 0 } ?: a.title.compareTo(b.title, ignoreCase = true)
    }

    private val byOrder = compareBy<CampTask>({ it.order }, { it.title.lowercase(SWISS) })

    fun sortByDue(tasks: List<CampTask>) = tasks.sortedWith(byDue)

    /** Die Abschnitte der Liste in fester Reihenfolge; leere fehlen. Erledigte stehen zuletzt (die Liste klappt sie ein). */
    fun sections(tasks: List<CampTask>, today: LocalDate): List<Pair<TaskSection, List<CampTask>>> =
        tasks.groupBy { section(it, today) }
            .let { groups -> TaskSection.entries.mapNotNull { s -> groups[s]?.let { s to sortByDue(it) } } }

    /** Meine Aufgaben: offene, bei denen ich Verantwortliche bin oder unterstütze; erledigte kommen am Ende. */
    fun mine(tasks: List<CampTask>, meId: String, sort: MineSort, today: LocalDate): List<CampTask> {
        val own = tasks.filter { it.involves(meId) }
        val comparator: Comparator<CampTask> = when (sort) {
            MineSort.DUE -> byDue
            MineSort.ORDER -> byOrder
            MineSort.RESSORT -> compareBy<CampTask>({ it.ressort?.name?.lowercase(SWISS) ?: "￿" }).then(byDue)
        }
        val (done, open) = own.partition { it.done }
        return open.sortedWith(comparator) + done.sortedWith(comparator)
    }

    /** Nach Personen: die wirksame Verantwortliche (auch die einspringende Leitung des Ressorts); ohne Person steht der Schlüssel `null` zuletzt. */
    fun byPerson(tasks: List<CampTask>): List<Pair<TaskPerson?, List<CampTask>>> {
        val groups = tasks.groupBy { it.assignee?.id }
        val named = groups.filterKeys { it != null }.map { (_, list) -> list.first().assignee to sortByDue(list) }
            .sortedBy { (person, _) -> person?.display?.lowercase(SWISS).orEmpty() }
        return named + listOfNotNull(groups[null]?.let { null to sortByDue(it) })
    }

    /** Nach Ressorts, alphabetisch; Aufgaben ohne Ressort zuletzt. */
    fun byRessort(tasks: List<CampTask>): List<Pair<TaskRessortRef?, List<CampTask>>> {
        val groups = tasks.groupBy { it.ressort?.id }
        val named = groups.filterKeys { it != null }.map { (_, list) -> list.first().ressort to sortByDue(list) }
            .sortedBy { (ressort, _) -> ressort?.name?.lowercase(SWISS).orEmpty() }
        return named + listOfNotNull(groups[null]?.let { null to sortByDue(it) })
    }

    /** Die Spalten des Kanban: Offen, Läuft, Wartet, Erledigt; «Nächster Höck» nur, wenn es solche Aufgaben gibt. */
    fun columns(tasks: List<CampTask>): List<Pair<TaskStatus, List<CampTask>>> {
        val groups = tasks.groupBy { it.status }
        val statuses = TaskStatus.CHOOSABLE + if (groups.containsKey(TaskStatus.NEXT_MEETING)) listOf(TaskStatus.NEXT_MEETING) else emptyList()
        return statuses.map { status -> status to (groups[status].orEmpty().sortedWith(byOrder)) }
    }

    /** Wer zur Auswahl steht: die Mitglieder vom Server, solange sie fehlen (kein Recht, Fehler) alle, die in den Aufgaben stehen. */
    fun pickable(tasks: List<CampTask>, me: TaskPerson?, members: List<TaskPerson>?): List<TaskPerson> = members ?: people(tasks, me)

    /** Wer in «Neues für dich» und bei «Ich übernehme» vorkommt: alle Personen, die in den Aufgaben stehen. */
    fun people(tasks: List<CampTask>, me: TaskPerson?): List<TaskPerson> {
        val all = LinkedHashMap<String, TaskPerson>()
        me?.let { all[it.id] = it }
        tasks.forEach { task ->
            task.assignee?.let { all.putIfAbsent(it.id, it) }
            task.supporters.forEach { all.putIfAbsent(it.id, it) }
        }
        return all.values.sortedBy { it.display.lowercase(SWISS) }
    }

    /**
     * Die neue Reihenfolge, wenn die Aufgabe `taskId` in `list` um `delta` Stellen wandert (−1: nach oben). Gibt die Einträge für
     * `PUT …/aufgaben/reihenfolge` zurück (ID und zuletzt gesehene Version, die Stelle in der Liste ist die neue `reihenfolge`);
     * `null`, wenn sich nichts bewegt.
     */
    fun moved(list: List<CampTask>, taskId: String, delta: Int): List<Pair<String, Long>>? {
        val from = list.indexOfFirst { it.id == taskId }
        val to = from + delta
        if (from < 0 || to !in list.indices) return null
        val reordered = list.toMutableList().also { it.add(to, it.removeAt(from)) }
        return reordered.map { it.id to it.version }
    }
}

/** Die Ressorts als Baum in der Reihenfolge der Lagerleitung: Jedes Ressort mit seiner Tiefe (0 = oberste Ebene). Ein Ressort ohne Elternteil in der Liste gilt als oberste Ebene. */
fun ressortTree(ressorts: List<Ressort>): List<Pair<Ressort, Int>> {
    val ids = ressorts.map { it.id }.toSet()
    val byParent = ressorts.groupBy { it.parentId?.takeIf { parent -> parent in ids } }
    val order = compareBy<Ressort>({ it.order }, { it.name.lowercase(SWISS) })
    val out = mutableListOf<Pair<Ressort, Int>>()
    fun walk(parent: String?, depth: Int, seen: Set<String>) {
        byParent[parent].orEmpty().sortedWith(order).forEach { ressort ->
            if (ressort.id in seen) return@forEach
            out += ressort to depth
            walk(ressort.id, depth + 1, seen + ressort.id)
        }
    }
    walk(null, 0, emptySet())
    return out
}

/** Der Satz über dem Fortschritt des Starts: Countdown vor dem Lager, «Tag X von Y» im Lager, danach «vorbei». */
sealed interface CountdownLabel {
    data class DaysToGo(val days: Int) : CountdownLabel
    data object StartsToday : CountdownLabel
    data class DayOf(val day: Int, val total: Int) : CountdownLabel
    data object Over : CountdownLabel
}

fun countdownLabel(countdown: StartCountdown): CountdownLabel {
    val from = parseDay(countdown.from)
    val until = parseDay(countdown.until)
    val total = if (from != null && until != null) (ChronoUnit.DAYS.between(from, until) + 1).toInt() else 0
    return when {
        countdown.daysToStart > 0 -> CountdownLabel.DaysToGo(countdown.daysToStart)
        countdown.daysToStart == 0 -> if (total == 1) CountdownLabel.DayOf(1, 1) else CountdownLabel.StartsToday
        countdown.daysToEnd >= 0 -> CountdownLabel.DayOf(-countdown.daysToStart + 1, total)
        else -> CountdownLabel.Over
    }
}

/** Anteil der erledigten Aufgaben in ganzen Prozent; 0 ohne Aufgaben. */
fun percentDone(progress: TaskProgress?): Int {
    if (progress == null || progress.total <= 0) return 0
    return (progress.done * 100 / progress.total).coerceIn(0, 100)
}

/** Ein Tag in Worten: heute, morgen, der Wochentag (innerhalb einer Woche) oder das Datum («15. März»). */
sealed interface DayLabel {
    data object Today : DayLabel
    data object Tomorrow : DayLabel
    data class Name(val text: String) : DayLabel
}

fun dayLabel(date: String, today: LocalDate): DayLabel {
    val day = parseDay(date) ?: return DayLabel.Name(date)
    return when (ChronoUnit.DAYS.between(today, day)) {
        0L -> DayLabel.Today
        1L -> DayLabel.Tomorrow
        in 2L..6L -> DayLabel.Name(weekdayName(day))
        else -> DayLabel.Name(formatDay(date))
    }
}

/** Der erste Höck ab heute, nach Datum und Zeit; `null`, wenn keiner ansteht. */
fun nextMeeting(meetings: List<MeetingEntry>, today: LocalDate): MeetingEntry? =
    meetings.filter { parseDay(it.date)?.let { d -> !d.isBefore(today) } == true }
        .minWithOrNull(compareBy({ it.date }, { it.time ?: "" }))

/** Die erste Warnung der nächsten drei Tage (heute eingeschlossen): Datum und Art der Warnung; `null` ohne Warnung. */
fun nextWeatherWarning(weather: Weather?, today: LocalDate): Pair<LocalDate, List<String>>? =
    weather?.days
        ?.mapNotNull { day -> parseDay(day.date)?.takeIf { it in today..today.plusDays(2) && day.warnings.isNotEmpty() }?.let { it to day.warnings } }
        ?.minByOrNull { it.first }
