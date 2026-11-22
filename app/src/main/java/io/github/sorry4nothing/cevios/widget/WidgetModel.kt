package io.github.sorry4nothing.cevios.widget

import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Das Datenmodell der Widgets. Es enthält nur, was ein Widget zeigen darf: **keine Kindernamen** (der Stand der Zusagen ist eine Zahl),
 * **keine Chat-Inhalte oder Namen** (nur Zähler), **keine Telefonnummern**, **keine Namen anderer Personen**, Adressen nur als
 * Treffpunkt in [ProgramRow.place]. Die verbotenen Felder gibt es hier gar nicht; `WidgetRedactionTest` hält das fest.
 */
enum class WidgetKind(val wire: String, val needsCamp: Boolean) {
    PROGRAM("programm", false),
    CALENDAR("kalender", false),
    CAMP_TODAY("lager_heute", true),
    TASKS("aufgaben", true),
    EMERGENCY("notfall", true),
    CHAT("chat", false),
}

/** Wie die eigenen Zusagen zu einem Programm stehen; die dringendste zuerst (offen, dann abgesagt, dann zugesagt). */
enum class ProgramState { CHILDREN_OPEN, YOU_OPEN, CHILDREN_NO, YOU_NO, ALL_YES, YOU_YES }

/** `count` ist die Zahl der Kinder (nie ihre Namen); bei «Du» ohne Bedeutung. */
data class ProgramStatus(val state: ProgramState, val count: Int = 0)

/** `time` ist `null` bei ganztägigen Terminen. */
data class ProgramRow(
    val id: String,
    val title: String,
    val date: LocalDate?,
    val time: String?,
    /** Der Treffpunkt; nur auf dem Homescreen gezeigt, nie in der Redaktion. */
    val place: String?,
    val groups: List<String>,
    val status: ProgramStatus?,
)

data class CalendarRow(val id: String, val title: String, val date: LocalDate?, val time: String?, val groups: List<String>)

/** Der eigene Dienst heute: Dienstname, Abschnitt und Zeit (nie, wer sonst noch eingeteilt ist). */
data class DutyLine(val title: String, val section: String?, val time: String?)

enum class Urgency { OVERDUE, SOON, LATER, NONE }

data class TaskRow(val id: String, val title: String, val due: LocalDate?, val urgency: Urgency)

sealed interface WidgetContent {
    val isEmpty: Boolean

    data class Programs(val rows: List<ProgramRow>) : WidgetContent {
        override val isEmpty get() = rows.isEmpty()
    }

    data class Calendar(val total: Int, val rows: List<CalendarRow>) : WidgetContent {
        override val isEmpty get() = rows.isEmpty()
    }

    /** `day` ist `null` ausserhalb der Lagertage. `important` sind die Titel von «Wichtig heute». */
    data class CampToday(val campId: String, val day: Int?, val days: Int, val duty: DutyLine?, val important: List<String>) : WidgetContent {
        override val isEmpty get() = duty == null && important.isEmpty()
    }

    data class Tasks(val campId: String, val overdue: Int, val open: Int, val rows: List<TaskRow>) : WidgetContent {
        override val isEmpty get() = open == 0
    }

    /** `campId` ist `null`, solange kein Lager bekannt ist: Der Knopf funktioniert trotzdem. */
    data class Emergency(val campId: String?) : WidgetContent {
        override val isEmpty get() = false
    }

    /** `chats` ist die Zahl der Chats mit ungelesenen Nachrichten. */
    data class Chat(val unread: Int, val chats: Int) : WidgetContent {
        override val isEmpty get() = unread == 0
    }
}

/** Was von einem Widget bleibt, wenn die App gesperrt ist oder es auf dem Sperrbildschirm steht: Art, Zeit und Zähler. */
data class Redacted(
    val kind: WidgetKind,
    /** Datum und Zeit der Zeilen; ohne Titel, Ort und Gruppe. */
    val whens: List<Pair<LocalDate?, String?>> = emptyList(),
    val counter: Int? = null,
    val day: Int? = null,
    val days: Int? = null,
    val dutyTime: String? = null,
)

/** Wie aktuell der gezeigte Stand ist. */
enum class Freshness { FRESH, OFFLINE, STALE }

data class Stand(val freshness: Freshness, val at: ZonedDateTime, val hours: Int = 0)

/** Der Zustand des Widgets; die Buchstaben sind die Codes des Entwurfs (Teil 4.5). */
sealed interface WidgetScreen {
    /** N, T, E (leer), V (veraltet), O (ohne Netz): Inhalt mit Stand. */
    data class Showing(val content: WidgetContent, val stand: Stand) : WidgetScreen

    /** P: statische Balken. */
    data object Placeholder : WidgetScreen

    /** I: nichts geladen und nichts da; `offline`, wenn das Netz fehlt. */
    data class NeverOpened(val offline: Boolean = false) : WidgetScreen

    /** U */
    data object SignedOut : WidgetScreen

    /** S: App gesperrt oder Sperrbildschirm. `redacted` ist `null`, solange nichts geladen ist. */
    data class Locked(val redacted: Redacted?) : WidgetScreen

    /** R: kein Lager oder keine Rolle. */
    data object NoCamp : WidgetScreen

    /** F */
    data object Failed : WidgetScreen
}

/** Die Grössenklasse eines Widgets: 2×2, 4×2 oder 4×4. */
enum class SizeClass { SMALL, MEDIUM, LARGE }

/** Wohin ein Tippen führt. Die App baut daraus dasselbe Ziel wie bei einem Push (`PushTarget`). */
sealed interface WidgetTarget {
    data object Programs : WidgetTarget
    data class Program(val id: String) : WidgetTarget
    data object Chat : WidgetTarget
    data class CampToday(val campId: String?) : WidgetTarget
    data class CampTasks(val campId: String?) : WidgetTarget

    /** Die Aufgabe liegt im Lager; die App löst das Lager über den Server auf. */
    data class CampTask(val id: String) : WidgetTarget
    data class CampEmergency(val campId: String?) : WidgetTarget
}
