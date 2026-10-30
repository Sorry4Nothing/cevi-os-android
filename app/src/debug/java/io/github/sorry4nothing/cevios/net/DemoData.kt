package io.github.sorry4nothing.cevios.net

import io.github.sorry4nothing.cevios.programs.SWISS_ZONE
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Alle Namen, Orte und Texte sind erfunden. Echte Personen, Orte und Abteilungen kommen hier nie vor.

internal data class DemoPerson(val id: String, val name: String)

internal class DemoProgram(
    val id: String,
    val title: String,
    val status: String,
    val start: Instant? = null,
    val end: Instant? = null,
    val dayFrom: LocalDate? = null,
    val dayTo: LocalDate? = null,
    val googleLocation: String? = null,
    val meetingPoint: String? = null,
    val closingLocation: String? = null,
    val closingTime: String? = null,
    val bringAlong: String? = null,
    val parentInfo: String? = null,
) {
    /** Zugeordnete Stufen und Sichtbarkeit; die Leitung ändert sie über `PUT .../zuordnung`. */
    var ageGroupIds: Set<String> = setOf(DemoData.AGE_GROUP_ID)
    var visibility = "eltern"

    val allDay get() = dayFrom != null

    /** Beginn als Zeitpunkt; bei ganztägigen Programmen der Beginn des ersten Tages. */
    val begin: Instant get() = start ?: dayFrom!!.atStartOfDay(SWISS_ZONE).toInstant()
}

internal class DemoMessage(
    val id: String,
    val text: String?,
    val createdAt: Instant,
    val author: DemoPerson,
    val program: DemoProgram? = null,
    val hidden: Boolean = false,
    val own: Boolean = false,
)

/** `readAt` ist der Gelesen-Stand der Demo-Person; ohne Stand zählen alle Nachrichten anderer. */
internal class DemoChat(val id: String, val direct: Boolean, val messages: MutableList<DemoMessage>, var readAt: Instant? = null) {
    /** Sichtbare Nachrichten anderer, die nach dem Gelesen-Stand eingingen. */
    fun unread(): Int {
        val read = readAt
        return messages.count { !it.own && !it.hidden && (read == null || it.createdAt.isAfter(read)) }
    }
}

internal object DemoData {
    const val AGE_GROUP_ID = "00000000-0000-4000-8000-000000000001"
    const val AGE_GROUP_NAME = "Jungschar (Demo)"
    const val AGE_GROUP_ABBREVIATION = "JS"

    /** Eine zweite Stufe, in der die Demo-Leitung nichts ändern darf. */
    const val OTHER_AGE_GROUP_ID = "00000000-0000-4000-8000-000000000002"
    const val OTHER_AGE_GROUP_NAME = "Wölfe (Demo)"
    const val OTHER_AGE_GROUP_ABBREVIATION = "WO"
    val ageGroupNames = mapOf(AGE_GROUP_ID to AGE_GROUP_NAME, OTHER_AGE_GROUP_ID to OTHER_AGE_GROUP_NAME)
    const val GROUP_CHAT_ID = "00000000-0000-4000-8000-0000000000c1"
    const val DIRECT_CHAT_ID = "00000000-0000-4000-8000-0000000000c2"

    val me = DemoPerson("00000000-0000-4000-8000-000000000010", "Demo Eltern")
    val children = listOf(
        DemoPerson("00000000-0000-4000-8000-000000000021", "Mia"),
        DemoPerson("00000000-0000-4000-8000-000000000022", "Noah"),
    )
    const val CHILD_LAST_NAME = "Muster"
    val leaderA = DemoPerson("00000000-0000-4000-8000-000000000031", "Beispiel Leitung A")
    val leaderB = DemoPerson("00000000-0000-4000-8000-000000000032", "Beispiel Leitung B")
    val otherParent = DemoPerson("00000000-0000-4000-8000-000000000033", "Beispiel Eltern")

    const val TRUSTED_PERSONS =
        "Bei Fragen oder Sorgen kannst du dich an zwei Vertrauenspersonen wenden. " +
            "Beispiel Vertrauensperson 1 ist Abteilungsleitung und per E-Mail an vertrauen1@example.org erreichbar. " +
            "Beispiel Vertrauensperson 2 ist Coach der Leitenden und meldet sich meist innerhalb von zwei Tagen. " +
            "Alle Angaben in der Demo sind erfunden."

    /** Eine Nachrichten-ID, die sich als Text nach der Reihenfolge ordnet (wie UUIDv7). */
    fun messageId(number: Int) = "0198a000-0000-7000-8000-%012x".format(number)

    private fun at(day: LocalDate, hour: Int, minute: Int) = day.atTime(hour, minute).atZone(SWISS_ZONE).toInstant()

    /** Sechs Programme relativ zu `today` (Schweizer Datum), nach Beginn sortiert. */
    fun programs(today: LocalDate): List<DemoProgram> {
        fun id(n: Int) = "00000000-0000-4000-8000-0000000001%02x".format(n)
        val tomorrow = today.plusDays(1)
        return listOf(
            DemoProgram(
                id(1), "Waldnachmittag (Demo)", "aktiv",
                start = at(tomorrow, 14, 0), end = at(tomorrow, 17, 0),
                googleLocation = "Waldrand, Beispielort",
                meetingPoint = "Beim Spielplatz am Waldrand",
                closingLocation = "Beim Spielplatz am Waldrand", closingTime = "16:30:00",
                bringAlong = "Znüni, Trinkflasche, wetterfeste Kleidung",
                parentInfo = "Bitte bis morgen Mittag zusagen oder absagen.",
            ),
            DemoProgram(
                id(2), "Spieleabend (Demo)", "aktiv",
                start = at(tomorrow, 18, 30), end = at(tomorrow, 20, 0),
                googleLocation = "Pfarrsaal, Beispielort",
            ),
            DemoProgram(
                id(3), "Basteln mit Holz (Demo)", "abgesagt",
                start = at(today.plusDays(5), 14, 0), end = at(today.plusDays(5), 16, 0),
                meetingPoint = "Werkraum",
            ),
            DemoProgram(
                id(4), "Geländespiel (Demo)", "pruefen",
                start = at(today.plusDays(8), 13, 30), end = at(today.plusDays(8), 17, 0),
                meetingPoint = "Bahnhofplatz Beispielort",
            ),
            DemoProgram(
                id(5), "Sommerlager (Demo)", "aktiv",
                dayFrom = today.plusDays(12), dayTo = today.plusDays(14),
                meetingPoint = "Besammlung beim Schulhaus", closingLocation = "Beim Schulhaus", closingTime = "12:00:00",
                bringAlong = "Schlafsack, Taschenlampe, Hausschuhe",
                parentInfo = "Die Abgabe der Kinder ist am dritten Tag um 12 Uhr.",
            ),
            DemoProgram(
                id(6), "Bastelmorgen (Demo)", "aktiv",
                start = at(today.plusDays(20), 10, 0), end = at(today.plusDays(20), 12, 0),
            ),
        ).sortedBy { it.begin }
    }

    private val texts = listOf(
        "Hallo zusammen, schön, dass ihr dabei seid.",
        "Wir freuen uns auf das nächste Programm.",
        "Danke für die Info.",
        "Vergesst die Trinkflasche nicht.",
        "Mein Kind kommt gern mit.",
        "Das Wetter soll gut werden.",
        "Treffpunkt ist wie immer beim Spielplatz.",
        "Kann ich dir noch etwas helfen?",
        "Super, bis bald.",
        "Wir bringen etwas Kleines zum Essen mit.",
        "Alles klar, danke.",
        "Bei Regen findet das Programm im Pfarrsaal statt.",
    )

    private val authors = listOf(leaderA, otherParent, me, leaderB, otherParent, leaderA)

    /** 55 Nachrichten (Nummern 1 bis 55) von gestern 9 Uhr bis kurz vor `now`; so entsteht eine zweite Seite. */
    fun groupMessages(now: Instant, program: DemoProgram): MutableList<DemoMessage> {
        val start = at(now.atZone(SWISS_ZONE).toLocalDate().minusDays(1), 9, 0)
        val step = Duration.between(start, now.minusSeconds(300)).dividedBy(54)
        return MutableList(55) { index ->
            val number = index + 1
            val time = start.plus(step.multipliedBy(index.toLong())).truncatedTo(ChronoUnit.SECONDS)
            val id = messageId(number)
            when (number) {
                30 -> DemoMessage(id, null, time, me, hidden = true, own = true)
                45 -> DemoMessage(id, "Hier die Infos zum nächsten Programm.", time, leaderA, program = program)
                else -> {
                    val author = authors[number % authors.size]
                    DemoMessage(id, texts[index % texts.size], time, author, own = author == me)
                }
            }
        }
    }

    /** Drei Nachrichten (Nummern 56 bis 58) im Einzelchat. */
    fun directMessages(now: Instant): MutableList<DemoMessage> {
        fun time(minutesAgo: Long) = now.minusSeconds(minutesAgo * 60).truncatedTo(ChronoUnit.SECONDS)
        return mutableListOf(
            DemoMessage(messageId(56), "Hallo, du kannst mir hier direkt schreiben.", time(120), leaderA),
            DemoMessage(messageId(57), "Danke, das ist praktisch.", time(90), me, own = true),
            DemoMessage(messageId(58), "Gern, melde dich bei Fragen.", time(60), leaderA),
        )
    }
}
