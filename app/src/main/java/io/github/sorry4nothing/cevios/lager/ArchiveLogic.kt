package io.github.sorry4nothing.cevios.lager

import java.time.LocalDate
import java.time.YearMonth

/**
 * Was die Person in «Ablage und Querschnitt» darf, nach den Rechten aus dem Lagerdetail. Der Server prüft bei jeder Anfrage.
 * Im geschlossenen Lager bleibt nichts zum Ändern.
 */
data class ArchiveRights(
    /** `dokumente.bearbeiten`: Dokumente anlegen und löschen. Standard: Lagerleitung und Leitung. */
    val editDocuments: Boolean,
    /** `adressbuch.lesen`: Das Adressbuch enthält Personendaten Dritter. Standard: nur die Lagerleitung. */
    val readAddressBook: Boolean,
    /** `adressbuch.bearbeiten`: Kontakte anlegen, ändern, löschen. Standard: nur die Lagerleitung. */
    val editAddressBook: Boolean,
    /** `zeitplan.bearbeiten`: Lagertage und Abschnitte übernehmen. Standard: nur die Lagerleitung. */
    val editSchedule: Boolean,
) {
    companion object {
        fun of(p: CampPermissions) = ArchiveRights(
            editDocuments = p.mayWrite("dokumente.bearbeiten"),
            readAddressBook = p.may("adressbuch.lesen"),
            editAddressBook = p.mayWrite("adressbuch.bearbeiten"),
            editSchedule = p.mayWrite("zeitplan.bearbeiten"),
        )
    }
}

enum class TimelineKind { DAY, MILESTONE, MEETING, DUE }

/**
 * Eine Zeile im Zeitplan: ein Lagertag, ein Meilenstein, ein Höck oder die Frist einer Aufgabe. `detail` ist die Art des
 * Meilensteins, die Zeit des Höcks oder das Ressort der Aufgabe; `number` zählt die Lagertage ab 1.
 */
data class TimelineEntry(
    val kind: TimelineKind,
    val date: LocalDate,
    val title: String?,
    val detail: String? = null,
    val id: String? = null,
    val number: Int? = null,
)

/** Die Logik des Zeitplans und der Monatsansicht. Reine Funktionen, damit sie ohne Gerät getestet sind. */
object TimelineRules {
    /** Alle Zeilen nach Datum; am selben Tag zuerst der Lagertag, dann Meilensteine, Höcks und Fristen. Erledigte Aufgaben fehlen. */
    fun entries(data: TimelineData): List<TimelineEntry> {
        val days = data.schedule.days.mapNotNull { day -> parseDay(day.date)?.let { day to it } }.sortedBy { it.second }
        val list = buildList {
            days.forEachIndexed { index, (day, date) -> add(TimelineEntry(TimelineKind.DAY, date, day.label, id = day.id, number = index + 1)) }
            data.schedule.milestones.forEach { m -> parseDay(m.date)?.let { add(TimelineEntry(TimelineKind.MILESTONE, it, m.title, m.kind, m.id)) } }
            data.meetings.forEach { m -> parseDay(m.date)?.let { add(TimelineEntry(TimelineKind.MEETING, it, m.title, m.time?.takeIf(String::isNotBlank), m.id)) } }
            data.tasks.filterNot { it.done }.forEach { t ->
                TaskRules.dueDate(t)?.let { add(TimelineEntry(TimelineKind.DUE, it, t.title, t.ressort?.name, t.id)) }
            }
        }
        return list.sortedWith(compareBy<TimelineEntry>({ it.date }, { it.kind.ordinal }, { it.title.orEmpty() }))
    }

    /** Die Zeilen gruppiert nach Monat, in der Reihenfolge des Kalenders. */
    fun byMonth(entries: List<TimelineEntry>): List<Pair<YearMonth, List<TimelineEntry>>> =
        entries.groupBy { YearMonth.from(it.date) }.toSortedMap().map { it.key to it.value }

    /** Der Monat, den die Monatsansicht zuerst zeigt: der heutige, wenn es dort Einträge gibt, sonst der nächste mit Einträgen, sonst der letzte. */
    fun startMonth(entries: List<TimelineEntry>, today: LocalDate): YearMonth {
        val current = YearMonth.from(today)
        val months = entries.map { YearMonth.from(it.date) }.distinct().sorted()
        return months.firstOrNull { it >= current } ?: months.lastOrNull() ?: current
    }

    /** Die Felder des Monats, Montag zuerst; vor dem ersten Tag und nach dem letzten stehen leere Felder bis zur vollen Woche. */
    fun cells(month: YearMonth): List<LocalDate?> {
        val lead = month.atDay(1).dayOfWeek.value - 1
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val padded = List(lead) { null } + days
        return padded + List((WEEK - padded.size % WEEK) % WEEK) { null }
    }

    /** Die Arten, die an einem Tag etwas haben (für die Punkte in der Monatsansicht). */
    fun kindsOn(entries: List<TimelineEntry>, date: LocalDate): Set<TimelineKind> = entries.filter { it.date == date }.map { it.kind }.toSet()

    private const val WEEK = 7
}

/** Wohin ein Treffer der Suche führt; das Modul des Objekts öffnet es (`typ` und `id` nennt der Server). */
sealed interface SearchDestination {
    data class Task(val id: String) : SearchDestination
    data class Live(val area: LiveArea) : SearchDestination
    data class Plan(val area: PlanArea) : SearchDestination
    data object Meetings : SearchDestination
    data object Pinboard : SearchDestination
    data object Inbox : SearchDestination
    data object Ressorts : SearchDestination
}

object SearchRules {
    /** Das Ziel zu einem Typ der Suche; ein Typ, den die App nicht kennt, führt nirgendwohin (der Treffer bleibt lesbar). */
    fun destination(hit: SearchHit): SearchDestination? = when (hit.type) {
        "aufgabe" -> SearchDestination.Task(hit.id)
        "meilenstein" -> SearchDestination.Live(LiveArea.TIMELINE)
        "dokument" -> SearchDestination.Live(LiveArea.DOCUMENTS)
        "adressbuch" -> SearchDestination.Live(LiveArea.ADDRESSBOOK)
        "info" -> SearchDestination.Live(LiveArea.INFOS)
        "notfall" -> SearchDestination.Live(LiveArea.EMERGENCY)
        "einkauf" -> SearchDestination.Live(LiveArea.SHOPPING)
        "packliste" -> SearchDestination.Live(LiveArea.PACKING)
        "tagesseite" -> SearchDestination.Live(LiveArea.TODAY)
        "hoeck", "traktandum", "protokoll", "beschluss" -> SearchDestination.Meetings
        "pinnwand" -> SearchDestination.Pinboard
        "eingang" -> SearchDestination.Inbox
        "ressort" -> SearchDestination.Ressorts
        "zimmer" -> SearchDestination.Plan(PlanArea.ROOMS)
        "fahrt" -> SearchDestination.Plan(PlanArea.TRIPS)
        "gruppe" -> SearchDestination.Plan(PlanArea.GROUPS)
        "dienst" -> SearchDestination.Plan(PlanArea.SHIFTS)
        "teilnehmer" -> SearchDestination.Plan(PlanArea.PARTICIPANTS)
        else -> null
    }

    /** Die Typen in der Reihenfolge der Abschnitte; die Abschnitte heissen so wie im Entwurf. Unbekannte Typen kommen am Ende in «Weiteres». */
    val ORDER = listOf(
        "aufgabe", "hoeck", "traktandum", "protokoll", "beschluss", "pinnwand", "eingang", "ressort", "tagesseite", "meilenstein",
        "dokument", "info", "teilnehmer", "zimmer", "fahrt", "gruppe", "dienst", "einkauf", "packliste", "notfall", "adressbuch",
    )

    /** Die Treffer nach Typ gruppiert, in der festen Reihenfolge; innerhalb eines Typs bleibt die Reihenfolge des Servers (beste zuerst). */
    fun grouped(hits: List<SearchHit>): List<Pair<String, List<SearchHit>>> {
        val byType = hits.groupBy { it.type }
        val known = ORDER.filter { it in byType }.map { it to byType.getValue(it) }
        val other = byType.filterKeys { it !in ORDER }.values.flatten()
        return if (other.isEmpty()) known else known + (OTHER_TYPE to other)
    }

    const val OTHER_TYPE = "weiteres"

    /** Eine Suche beginnt ab zwei Zeichen (so verlangt es der Server). */
    fun valid(query: String): Boolean = query.trim().length >= MIN_CHARS

    const val MIN_CHARS = 2
}
