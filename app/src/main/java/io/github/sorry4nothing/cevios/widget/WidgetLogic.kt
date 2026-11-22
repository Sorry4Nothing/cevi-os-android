package io.github.sorry4nothing.cevios.widget

import io.github.sorry4nothing.cevios.chat.Chat
import io.github.sorry4nothing.cevios.lager.Camp
import io.github.sorry4nothing.cevios.lager.CampTask
import io.github.sorry4nothing.cevios.lager.MineSort
import io.github.sorry4nothing.cevios.lager.TaskRules
import io.github.sorry4nothing.cevios.programs.Program
import io.github.sorry4nothing.cevios.programs.SWISS_ZONE
import io.github.sorry4nothing.cevios.programs.Timing
import io.github.sorry4nothing.cevios.programs.counts
import io.github.sorry4nothing.cevios.programs.groupChips
import io.github.sorry4nothing.cevios.programs.startDay
import io.github.sorry4nothing.cevios.push.WidgetArea
import io.github.sorry4nothing.cevios.programs.time
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Was das Laden einer Widget-Quelle ergeben hat. */
sealed interface LoadOutcome {
    data class Loaded(val content: WidgetContent) : LoadOutcome
    data object NotSignedIn : LoadOutcome

    /** Kein aktives Lager oder keine Rolle dafür. */
    data object NoCamp : LoadOutcome
    data object Offline : LoadOutcome
    data object Failed : LoadOutcome
}

/** Der letzte gesehene Stand; er liegt nur im Arbeitsspeicher (`WidgetMemory`). */
data class Snapshot(val content: WidgetContent, val loadedAt: Instant)

/** Ab so vielen Stunden gilt ein Stand als veraltet (Zustand V). */
const val STALE_AFTER_HOURS = 2L

/**
 * Welcher Zustand bei welchen Daten: das Herz der Widgets. Gibt auch den Stand zurück, der im Arbeitsspeicher bleibt.
 * Ein gesperrtes Widget zeigt nur Art, Zeit und Zähler (und merkt sich nichts). Der Notfall-Knopf bleibt ohne Daten bedienbar.
 */
fun resolveScreen(
    kind: WidgetKind,
    outcome: LoadOutcome,
    previous: Snapshot?,
    now: Instant,
    appLocked: Boolean,
): Pair<WidgetScreen, Snapshot?> {
    val zoned = now.atZone(SWISS_ZONE)
    return when (outcome) {
        LoadOutcome.NotSignedIn -> WidgetScreen.SignedOut to null
        LoadOutcome.NoCamp -> WidgetScreen.NoCamp to null
        is LoadOutcome.Loaded -> when {
            kind == WidgetKind.EMERGENCY -> WidgetScreen.Showing(outcome.content, Stand(Freshness.FRESH, zoned)) to Snapshot(outcome.content, now)
            appLocked -> WidgetScreen.Locked(redact(outcome.content)) to previous
            else -> WidgetScreen.Showing(outcome.content, Stand(Freshness.FRESH, zoned)) to Snapshot(outcome.content, now)
        }
        LoadOutcome.Offline, LoadOutcome.Failed -> {
            val offline = outcome == LoadOutcome.Offline
            when {
                kind == WidgetKind.EMERGENCY ->
                    if (offline) WidgetScreen.Showing(WidgetContent.Emergency((previous?.content as? WidgetContent.Emergency)?.campId), Stand(Freshness.OFFLINE, zoned)) to previous
                    else WidgetScreen.Failed to previous
                appLocked -> WidgetScreen.Locked(null) to previous
                previous != null -> {
                    val hours = Duration.between(previous.loadedAt, now).toHours().toInt()
                    val freshness = if (hours >= STALE_AFTER_HOURS) Freshness.STALE else Freshness.OFFLINE
                    WidgetScreen.Showing(previous.content, Stand(freshness, previous.loadedAt.atZone(SWISS_ZONE), hours)) to previous
                }
                offline -> WidgetScreen.NeverOpened(offline = true) to null
                else -> WidgetScreen.Failed to null
            }
        }
    }
}

/** Streicht alles ausser Art, Zeit und Zähler. */
fun redact(content: WidgetContent): Redacted = when (content) {
    is WidgetContent.Programs -> Redacted(WidgetKind.PROGRAM, whens = content.rows.map { it.date to it.time })
    is WidgetContent.Calendar -> Redacted(WidgetKind.CALENDAR, whens = content.rows.map { it.date to it.time }, counter = content.total)
    is WidgetContent.CampToday -> Redacted(WidgetKind.CAMP_TODAY, day = content.day, days = content.days, dutyTime = content.duty?.time)
    is WidgetContent.Tasks -> Redacted(WidgetKind.TASKS, counter = content.overdue)
    is WidgetContent.Emergency -> Redacted(WidgetKind.EMERGENCY)
    is WidgetContent.Chat -> Redacted(WidgetKind.CHAT, counter = content.unread)
}

/** Wie viel ein Widget in der gerade gemeldeten Grösse zeigt. */
data class LayoutPlan(val size: SizeClass, val rows: Int, val showPlace: Boolean, val showGroups: Boolean)

private const val NARROW_DP = 210
private const val TALL_DP = 260
private const val CHROME_DP = 56
private const val COUNTER_DP = 44
private const val TOLERANCE_DP = 4

/** Zwei Zellen breit ist klein, vier breit und niedrig mittel, vier breit und hoch gross. */
fun sizeClassOf(widthDp: Int, heightDp: Int): SizeClass = when {
    widthDp < NARROW_DP -> SizeClass.SMALL
    heightDp < TALL_DP -> SizeClass.MEDIUM
    else -> SizeClass.LARGE
}

/**
 * Zwischengrössen: Es gibt so viele Zeilen, wie in die gemeldete Höhe passen (höchstens so viele wie der Entwurf in 4×4); passt nicht
 * einmal eine volle Zeile, fällt zuerst der Ort weg. Die Schrift macht die Zeilen höher (`fontScale`), also gibt es dann weniger.
 */
fun layoutPlan(kind: WidgetKind, widthDp: Int, heightDp: Int, fontScale: Float = 1f, keyguard: Boolean = false): LayoutPlan {
    val size = sizeClassOf(widthDp, heightDp)
    val scale = fontScale.coerceIn(1f, 2f)
    val avail = heightDp - CHROME_DP * (1 + (scale - 1) / 2) + TOLERANCE_DP
    fun fit(rowDp: Int, cap: Int, space: Float = avail) = (space / (rowDp * scale)).toInt().coerceIn(0, cap)
    return when (kind) {
        WidgetKind.PROGRAM -> when {
            size == SizeClass.SMALL -> LayoutPlan(size, 1, showPlace = false, showGroups = heightDp >= 150 * scale)
            fit(92, 2) >= 1 -> LayoutPlan(size, fit(92, 2), showPlace = !keyguard, showGroups = true)
            else -> LayoutPlan(size, 1, showPlace = false, showGroups = fit(74, 1) >= 1)
        }
        WidgetKind.CALENDAR -> when (size) {
            SizeClass.SMALL -> LayoutPlan(size, 1, showPlace = false, showGroups = heightDp >= 130 * scale)
            SizeClass.MEDIUM -> LayoutPlan(size, fit(48, 3).coerceAtLeast(1), showPlace = false, showGroups = false)
            SizeClass.LARGE -> LayoutPlan(size, fit(64, 5).coerceAtLeast(1), showPlace = false, showGroups = true)
        }
        WidgetKind.TASKS -> when (size) {
            SizeClass.SMALL -> LayoutPlan(size, 0, showPlace = false, showGroups = false)
            SizeClass.MEDIUM -> LayoutPlan(size, fit(56, 1, avail - COUNTER_DP).coerceAtLeast(1), showPlace = false, showGroups = false)
            SizeClass.LARGE -> LayoutPlan(size, fit(56, 3, avail - COUNTER_DP).coerceAtLeast(1), showPlace = false, showGroups = false)
        }
        WidgetKind.CAMP_TODAY -> when (size) {
            SizeClass.SMALL -> LayoutPlan(size, 0, showPlace = false, showGroups = false)
            SizeClass.MEDIUM -> LayoutPlan(size, 1, showPlace = false, showGroups = false)
            SizeClass.LARGE -> LayoutPlan(size, 2, showPlace = false, showGroups = false)
        }
        WidgetKind.CHAT, WidgetKind.EMERGENCY -> LayoutPlan(size, if (size == SizeClass.SMALL) 0 else 1, showPlace = false, showGroups = false)
    }
}

// ---- Daten zu Inhalt ----

/** Der Stand der Zusagen ohne Namen; `null`, wenn es nichts zu sagen gibt. Offen vor abgesagt vor zugesagt. */
fun programStatus(program: Program): ProgramStatus? {
    val children = program.children.counts()
    val own = program.ownRegistration
    return when {
        children.open > 0 -> ProgramStatus(ProgramState.CHILDREN_OPEN, children.open)
        own != null && own != "ja" && own != "nein" -> ProgramStatus(ProgramState.YOU_OPEN)
        children.no > 0 -> ProgramStatus(ProgramState.CHILDREN_NO, children.no)
        own == "nein" -> ProgramStatus(ProgramState.YOU_NO)
        children.yes > 0 -> ProgramStatus(ProgramState.ALL_YES, children.yes)
        own == "ja" -> ProgramStatus(ProgramState.YOU_YES)
        else -> null
    }
}

private val HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm")

/** `null` bei ganztägigen Terminen und ohne lesbare Zeit. */
fun timeOf(program: Program): String? = (program.time() as? Timing.Timed)?.let { HOUR_MINUTE.format(it.start) }

private val PHONE_LIKE = Regex("""\+?\d[\d\s()/.-]{6,}\d""")

/** Der Treffpunkt für das Widget: die erste Zeile ohne Nummern; was die Leitung sonst ins Feld schreibt (Kontakt, Hinweise), bleibt weg. */
fun meetingPlace(text: String?, max: Int = 80): String? {
    val line = text.orEmpty().lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !PHONE_LIKE.containsMatchIn(it) } ?: return null
    return if (line.length <= max) line else line.take(max - 1).trimEnd() + "…"
}

fun programRow(program: Program): ProgramRow = ProgramRow(
    id = program.id,
    title = program.title,
    date = program.startDay(),
    time = timeOf(program),
    place = meetingPlace(program.location),
    groups = groupChips(program).map { it.name },
    status = programStatus(program),
)

fun calendarRow(program: Program): CalendarRow =
    CalendarRow(program.id, program.title, program.startDay(), timeOf(program), groupChips(program).map { it.name })

/** «Mein Kalender»: jeder Termin einmal, auch bei mehreren Gruppen. */
fun calendarContent(upcoming: List<Program>, limit: Int): WidgetContent.Calendar {
    val unique = upcoming.distinctBy { it.id }
    return WidgetContent.Calendar(unique.size, unique.take(limit).map(::calendarRow))
}

/** Nur die Zähler der Chatliste: Vorschau, Absender und Namen bleiben aussen vor. */
fun chatContent(chats: List<Chat>): WidgetContent.Chat {
    val unread = chats.map { it.unread }.filter { it > 0 }
    return WidgetContent.Chat(unread.sum(), unread.size)
}

/** Das Lager, das ein Lager-Widget zeigt: das gewählte, sonst das einzige oder erste aktive; Rollen mit nur «lesen» zählen nicht. */
fun pickCamp(camps: List<Camp>, chosenId: String?): Camp? {
    val active = camps.filter { it.active }
    return active.firstOrNull { it.id == chosenId } ?: active.firstOrNull()
}

/** «Tag 3 von 8»: `null` ausserhalb der Lagertage. */
fun campDay(camp: Camp, today: LocalDate): Pair<Int?, Int> {
    val from = LocalDate.parse(camp.from.take(10))
    val until = LocalDate.parse(camp.until.take(10))
    val days = (ChronoUnit.DAYS.between(from, until) + 1).toInt()
    val day = if (today.isBefore(from) || today.isAfter(until)) null else (ChronoUnit.DAYS.between(from, today) + 1).toInt()
    return day to days
}

fun urgencyOf(task: CampTask, today: LocalDate): Urgency = when {
    TaskRules.isOverdue(task, today) -> Urgency.OVERDUE
    TaskRules.isSoon(task, today) -> Urgency.SOON
    task.due != null -> Urgency.LATER
    else -> Urgency.NONE
}

/** Meine offenen Aufgaben: die Zähler und die nächsten nach Frist. Nur Titel und Frist, nie, wer sonst dran ist. */
fun tasksContent(campId: String, tasks: List<CampTask>, meId: String, today: LocalDate, limit: Int): WidgetContent.Tasks {
    val open = TaskRules.mine(tasks, meId, MineSort.DUE, today).filter { !it.done }
    return WidgetContent.Tasks(
        campId = campId,
        overdue = open.count { TaskRules.isOverdue(it, today) },
        open = open.size,
        rows = open.take(limit).map { TaskRow(it.id, it.title, TaskRules.dueDate(it), urgencyOf(it, today)) },
    )
}

/** Der erste Satz eines Hinweises, gekürzt: Widgets zeigen Titel, nie lange Texte. */
fun importantTitle(text: String, max: Int = 60): String? {
    val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
    return if (line.length <= max) line else line.take(max - 1).trimEnd() + "…"
}

// ---- Texte der Daten (rein, ohne Ressourcen) ----

private val MONTHS_SHORT = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")

private fun weekday(date: LocalDate) = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMAN).take(2)

/** «Sa 28. Nov, 14:00» (W1). */
fun whenLong(date: LocalDate?, time: String?, allDayLabel: String): String {
    date ?: return ""
    return "${weekday(date)} ${date.dayOfMonth}. ${MONTHS_SHORT[date.monthValue - 1]}, ${time ?: allDayLabel}"
}

/** «Sa 28.11.» oder mit Zeit «Sa 28.11., 14:00» (W2). */
fun whenShort(date: LocalDate?, time: String?, withTime: Boolean): String {
    date ?: return ""
    val day = "${weekday(date)} ${date.dayOfMonth}.${date.monthValue}."
    return if (withTime && time != null) "$day, $time" else day
}

/** «17. bis 24. Juli 2027» (WC2); leer, wenn ein Datum fehlt. */
fun campDates(from: String, until: String): String {
    val a = runCatching { LocalDate.parse(from.take(10)) }.getOrNull() ?: return ""
    val b = runCatching { LocalDate.parse(until.take(10)) }.getOrNull() ?: return ""
    fun month(d: LocalDate) = d.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)
    return when {
        a.year == b.year && a.month == b.month -> "${a.dayOfMonth}. bis ${b.dayOfMonth}. ${month(b)} ${b.year}"
        a.year == b.year -> "${a.dayOfMonth}. ${month(a)} bis ${b.dayOfMonth}. ${month(b)} ${b.year}"
        else -> "${a.dayOfMonth}. ${month(a)} ${a.year} bis ${b.dayOfMonth}. ${month(b)} ${b.year}"
    }
}

/** «15. März» (W4). */
fun dueLong(date: LocalDate): String = "${date.dayOfMonth}. ${Month.of(date.monthValue).getDisplayName(TextStyle.FULL, Locale.GERMAN)}"

// ---- Ziele ----

/** Die Extras des Intents (`typ`, `id`), mit denen die App das Ziel baut, auch beim Kaltstart. */
fun intentExtras(target: WidgetTarget): Pair<String, String?> = when (target) {
    WidgetTarget.Programs -> WidgetArea.PROGRAMS.wire to null
    is WidgetTarget.Program -> "programm" to target.id
    WidgetTarget.Chat -> WidgetArea.CHAT.wire to null
    is WidgetTarget.CampToday -> WidgetArea.CAMP_TODAY.wire to target.campId
    is WidgetTarget.CampTasks -> WidgetArea.CAMP_TASKS.wire to target.campId
    is WidgetTarget.CampTask -> "lager_aufgabe" to target.id
    is WidgetTarget.CampEmergency -> WidgetArea.CAMP_EMERGENCY.wire to target.campId
}

/** Wohin ein Tippen auf die Fläche eines Widgets führt (kleine Widgets und Kopfzeile). */
fun headerTarget(content: WidgetContent?, kind: WidgetKind): WidgetTarget = when (kind) {
    WidgetKind.PROGRAM -> (content as? WidgetContent.Programs)?.rows?.firstOrNull()?.let { WidgetTarget.Program(it.id) } ?: WidgetTarget.Programs
    WidgetKind.CALENDAR -> WidgetTarget.Programs
    WidgetKind.CAMP_TODAY -> WidgetTarget.CampToday((content as? WidgetContent.CampToday)?.campId)
    WidgetKind.TASKS -> WidgetTarget.CampTasks((content as? WidgetContent.Tasks)?.campId)
    WidgetKind.EMERGENCY -> WidgetTarget.CampEmergency((content as? WidgetContent.Emergency)?.campId)
    WidgetKind.CHAT -> WidgetTarget.Chat
}

/** Die Ziele der Zeilen: ab «mittel» öffnet jede Zeile ihr eigenes Ziel. */
fun rowTargets(content: WidgetContent): List<WidgetTarget> = when (content) {
    is WidgetContent.Programs -> content.rows.map { WidgetTarget.Program(it.id) }
    is WidgetContent.Calendar -> content.rows.map { WidgetTarget.Program(it.id) }
    is WidgetContent.Tasks -> content.rows.map { WidgetTarget.CampTask(it.id) }
    else -> emptyList()
}
