package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.VersionConflict
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Was die Person bei Sitzungen und Kommunikation darf, nach den Rechten aus dem Lagerdetail. Der Server prüft bei jeder Anfrage.
 * Ist das Lager geschlossen, bleibt nichts zum Ändern.
 */
data class MeetingRights(
    /** Alle Höcks, Protokolle und Beschlüsse lesen. Das Team sieht nur Höcks der Art `team` mit Datum, Zeit, Titel und Ort. */
    val readAll: Boolean,
    val editMeetings: Boolean,
    val editMinutes: Boolean,
    val readPinboard: Boolean,
    val editPinboard: Boolean,
    /** Reaktionen und Stimmen (`interaktion.schreiben`). */
    val interact: Boolean,
    val readInbox: Boolean,
    val answerInbox: Boolean,
    val sendInbox: Boolean,
    /** Eigene Meldungen und Antworten lesen: auch im geschlossenen Lager (nur lesend). */
    val readOwnInbox: Boolean,
    val mentionToTask: Boolean,
    /** Eine eigene Erwähnung als erledigt markieren (jede Rolle, solange das Lager offen ist). */
    val markMention: Boolean,
    /** Beiträge als «gesehen» melden: Im abgeschlossenen Lager antwortet der Server mit 409, die App meldet gar nicht erst. */
    val markSeen: Boolean,
) {
    companion object {
        fun of(p: CampPermissions) = MeetingRights(
            readAll = p.may("hoecks.lesen_alle"),
            editMeetings = p.mayWrite("hoecks.bearbeiten"),
            editMinutes = p.mayWrite("protokolle.bearbeiten"),
            readPinboard = p.may("pinnwand.lesen"),
            editPinboard = p.mayWrite("pinnwand.bearbeiten"),
            interact = p.mayWrite("interaktion.schreiben"),
            readInbox = p.may("eingang.lesen"),
            answerInbox = p.mayWrite("eingang.antworten"),
            sendInbox = p.mayWrite("eingang.senden"),
            readOwnInbox = p.may("eingang.senden"),
            mentionToTask = p.mayWrite("aufgaben.bearbeiten", "aufgaben.eigene"),
            markMention = !p.readOnly,
            markSeen = p.mayWrite("pinnwand.lesen"),
        )
    }
}

object MeetingRules {
    /** Anstehende Höcks, der nächste zuerst: heute und später, nach Datum und Zeit. */
    fun upcoming(meetings: List<Meeting>, today: LocalDate): List<Meeting> =
        meetings.filter { (parseDay(it.date) ?: LocalDate.MAX) >= today }.sortedWith(byDateTime())

    /** Vergangene Höcks, der jüngste zuerst. */
    fun past(meetings: List<Meeting>, today: LocalDate): List<Meeting> =
        meetings.filter { (parseDay(it.date) ?: LocalDate.MAX) < today }.sortedWith(byDateTime().reversed())

    private fun byDateTime(): Comparator<Meeting> = compareBy({ parseDay(it.date) ?: LocalDate.MAX }, { it.time.orEmpty() })

    /** Der Höck mit dem spätesten Datum (bei gleichem Tag der späteste), Vorlage für «Wie der letzte». */
    fun last(meetings: List<Meeting>): Meeting? = meetings.maxWithOrNull(byDateTime())

    /** Die Traktanden in der Reihenfolge, die der Server meldet (`reihenfolge`, dann ID als Gleichstand). */
    fun sorted(items: List<AgendaItem>): List<AgendaItem> = items.sortedWith(compareBy({ it.order }, { it.id }))

    /**
     * Verschiebt das Traktandum an `index` um `delta` Stellen; `null`, wenn es am Rand schon steht. Das Ergebnis ist die neue
     * Reihenfolge der ganzen Liste (die Stelle in der Liste ist die neue `reihenfolge`).
     */
    fun move(items: List<AgendaItem>, index: Int, delta: Int): List<AgendaItem>? {
        val target = index + delta
        if (index !in items.indices || target !in items.indices) return null
        val copy = items.toMutableList()
        copy.add(target, copy.removeAt(index))
        return copy
    }

    /** Die Dauer aus dem Eingabefeld: `null` für leer oder ungültig. Gültig sind ganze Zahlen von 1 bis 600 (Minuten). */
    fun minutesOf(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..600 }

    /** Leer ist erlaubt (keine Angabe), sonst muss die Zahl gültig sein. */
    fun isValidMinutes(text: String): Boolean = text.isBlank() || minutesOf(text) != null

    /** Wie viele Anwesende von allen Mitgliedern («3 von 4»). */
    fun presentCount(members: List<MemberPresence>): Int = members.count { it.present }

    /** Aus der Zahl der Traktanden und der Stelle: «Traktandum 2 von 5»; `index` zählt ab 0. */
    fun position(index: Int, total: Int): Pair<Int, Int> = (index + 1).coerceIn(1, maxOf(total, 1)) to total

    /** Das erste offene Traktandum; wer es nicht findet, startet beim ersten. */
    fun firstOpen(items: List<AgendaItem>): Int = items.indexOfFirst { it.status == AgendaStatus.OPEN }.takeIf { it >= 0 } ?: 0

    /** Ob das Höck-Datum heute oder in der Vergangenheit liegt (dann läuft oder lief er; «Hoeck läuft» braucht nur den Tag). */
    fun isRunningDay(meeting: Meeting, today: LocalDate): Boolean = parseDay(meeting.date) == today
}

private val MENTION_MARK = Regex("@\\{person:([0-9a-fA-F-]{36})\\}")

/**
 * Ersetzt die Marken `@{person:<id>}` im Text eines Inhalts durch «@Name». Ist die Person nicht bekannt, steht «@» und `unknown`:
 * Der Server liefert Marken im Titel einer Erwähnung unverändert.
 */
fun renderMentions(text: String, names: Map<String, String>, unknown: String): String =
    MENTION_MARK.replace(text) { match -> "@" + (names[match.groupValues[1]] ?: unknown) }

/** Die Marke für eine Person im Text eines Inhalts. */
fun mentionMark(personId: String): String = "@{person:$personId}"

/** Ob ein Text Marken enthält (dann gleicht die App nach dem Speichern die Erwähnungen ab). */
fun hasMentionMarks(vararg texts: String?): Boolean = texts.any { it != null && MENTION_MARK.containsMatchIn(it) }

/** Der Text zum Kopieren in einen Chat ausserhalb der App: Titel, Leerzeile, Text. Mehr gibt die App nicht weiter. */
fun copyText(post: Post): String = if (post.text.isBlank()) post.title else post.title + "\n\n" + post.text

/** Anteil einer Antwort an allen Stimmen in Prozent (0, wenn noch niemand abgestimmt hat). */
fun pollPercent(option: PollOption, poll: Poll): Int {
    val total = poll.options.sumOf { it.votes }
    return if (total == 0) 0 else Math.round(option.votes * 100f / total)
}

/** Die Antworten, die nach einem Tipp auf `option` gelten sollen: bei «mehrfach» an- oder abgewählt, sonst nur diese. */
fun votesAfterTap(poll: Poll, option: PollOption): List<String> {
    val chosen = poll.options.filter { it.own }.map { it.id }
    return when {
        !poll.multiple -> listOf(option.id)
        option.id in chosen -> chosen - option.id
        else -> chosen + option.id
    }
}

private val ZURICH = ZoneId.of("Europe/Zurich")

/** Anfang des Tages (Zeit des Lagers) als Zeitpunkt, wie ihn die API für `sichtbarAb` erwartet. */
fun dayStartInstant(date: String): String? = parseDay(date)?.atStartOfDay(ZURICH)?.toInstant()?.toString()

/** Ende des Tages (23:59 Zeit des Lagers): «Bis So 18.7.» gilt einschliesslich dieses Tages. */
fun dayEndInstant(date: String): String? = parseDay(date)?.atTime(LocalTime.of(23, 59))?.atZone(ZURICH)?.toInstant()?.toString()

/** Das Datum (Zeit des Lagers) eines Zeitpunkts der API; `null`, wenn er fehlt oder unlesbar ist. */
fun instantToDay(iso: String?): String? = iso?.let { runCatching { Instant.parse(it).atZone(ZURICH).toLocalDate().toString() }.getOrNull() }

/** Ob ein Beitrag mit Zeitfenster gerade sichtbar ist; wer die Pinnwand bearbeitet, sieht auch Vergangenes und Künftiges. */
fun postState(post: Post, now: Instant): PostWindow {
    val from = post.visibleFrom?.let { runCatching { Instant.parse(it) }.getOrNull() }
    val until = post.visibleUntil?.let { runCatching { Instant.parse(it) }.getOrNull() }
    return when {
        from != null && now.isBefore(from) -> PostWindow.UPCOMING
        until != null && !now.isBefore(until) -> PostWindow.EXPIRED
        else -> PostWindow.VISIBLE
    }
}

enum class PostWindow { UPCOMING, VISIBLE, EXPIRED }

/** Ein Rückfragecode in Vierergruppen («ABCD-EFGH-…»), wie ihn der Server schickt; Eingabe ohne Bindestriche und Leerzeichen ist erlaubt. */
fun normalizeCode(input: String): String = input.filter { it.isLetterOrDigit() }.uppercase()

/**
 * Baut aus einer Änderung, die der Server wegen `VERSION_KONFLIKT` abgelehnt hat, dieselbe Darstellung wie die Warteschlange
 * (`ConflictScreen`): «Deine Fassung» aus den gesendeten Feldern, «Aktueller Stand» aus der Antwort des Servers. Die Änderung ist nie
 * in der Warteschlange; die Person löst den Konflikt, dann sendet der Aufrufer sie neu mit der aktuellen Version.
 */
fun directConflict(
    summary: String,
    sent: Map<String, JsonElement>,
    conflict: VersionConflict,
    labels: Map<String, String>,
    id: String = UUID.randomUUID().toString(),
    now: Long = System.currentTimeMillis(),
): QueuedChange = QueuedChange(
    clientOpId = id,
    type = "direkt",
    payload = JsonObject(sent.filterKeys { it != "version" }),
    summary = summary,
    createdAt = now,
    fieldLabels = labels,
    state = ChangeState.CONFLICT,
    code = "VERSION_KONFLIKT",
    current = conflict.current,
    differing = conflict.differing.filter { it in sent.keys },
)

/** Die neue Version aus dem Stand, den der Server beim Konflikt mitgeliefert hat. */
fun conflictVersion(conflict: VersionConflict): Long? = (conflict.current["version"] as? JsonPrimitive)?.content?.toLongOrNull()

private val TIME_PATTERN = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")

/** Eine Uhrzeit als `HH:MM` (24 Stunden), wie sie der Server für einen Höck verlangt; leer ist erlaubt (der Höck ist dann ein Tag). */
fun isValidTime(text: String): Boolean = text.isBlank() || TIME_PATTERN.matches(text.trim())

/** Ob ein Link zu einer Datei erlaubt ist: https, ohne Leerzeichen, mit Punkt im Host. Der Server prüft ihn streng noch einmal. */
fun isValidFileLink(link: String): Boolean {
    val text = link.trim()
    if (!text.startsWith("https://") || text.any { it.isWhitespace() } || text.length > 2048) return false
    val host = text.removePrefix("https://").substringBefore('/').substringBefore('?')
    return host.contains('.') && !host.contains('@') && !host.contains(':')
}
