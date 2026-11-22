package io.github.sorry4nothing.cevios.widget

import io.github.sorry4nothing.cevios.chat.Chat
import io.github.sorry4nothing.cevios.chat.ChatAgeGroup
import io.github.sorry4nothing.cevios.chat.ChatPerson
import io.github.sorry4nothing.cevios.chat.LastMessage
import io.github.sorry4nothing.cevios.lager.CampTask
import io.github.sorry4nothing.cevios.programs.Program
import io.github.sorry4nothing.cevios.programs.ProgramChild
import io.github.sorry4nothing.cevios.programs.ProgramTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Datenschutz der Widgets: Das Datenmodell hat keine Felder für Kindernamen, Chat-Inhalte, Telefonnummern und Namen anderer Personen,
 * und aus API-Antworten voller Namen und Texte entsteht in jedem Zustand und jeder Grösse nichts davon.
 */
class WidgetRedactionTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    // Erfundene Marker; steht einer in einem Widget, ist ein Name oder Text durchgerutscht.
    private val childName = "KINDNAME-ZACHARIAS"
    private val otherChild = "KINDNAME-ZORA"
    private val chatText = "CHATTEXT-GEHEIM"
    private val chatAuthor = "AUTORNAME-FREMD"
    private val phone = "+41 00 000 00 00"
    private val personName = "PERSONNAME-ANDERE"

    private val modelClasses = listOf(
        ProgramRow::class.java, ProgramStatus::class.java, CalendarRow::class.java, DutyLine::class.java, TaskRow::class.java,
        WidgetContent.Programs::class.java, WidgetContent.Calendar::class.java, WidgetContent.CampToday::class.java,
        WidgetContent.Tasks::class.java, WidgetContent.Emergency::class.java, WidgetContent.Chat::class.java, Redacted::class.java,
        Snapshot::class.java, WidgetScreen.Showing::class.java, WidgetScreen.Locked::class.java, WidgetScreen.NeverOpened::class.java,
        Stand::class.java,
    )

    @Test
    fun theModelHasNoFieldForForbiddenData() {
        val forbidden = setOf(
            "name", "firstName", "vorname", "ceviName", "childName", "children", "kinder", "phone", "telefon", "message", "messages",
            "nachricht", "lastMessage", "sender", "author", "autor", "address", "adresse", "email", "person", "persons", "assignee",
            "supporters", "participants", "contacts",
        )
        for (type in modelClasses) {
            val names = type.declaredFields.map { it.name }.filter { it != "Companion" && !it.startsWith("$") }
            assertTrue("$type: ${names.filter { it in forbidden }}", names.none { it in forbidden })
        }
    }

    @Test
    fun onlyTheMeetingPointIsAnAddressAndOnlyInTheProgramRow() {
        for (type in modelClasses) {
            val places = type.declaredFields.map { it.name }.filter { it in setOf("place", "location", "address", "adresse", "ort", "meetingPoint") }
            if (type == ProgramRow::class.java) assertEquals(listOf("place"), places) else assertTrue("$type: $places", places.isEmpty())
        }
    }

    private val now = Instant.parse("2026-11-21T17:02:00Z")
    private val programWithNames = Program(
        id = "p", title = "Waldspiel", status = "aktiv", allDay = false, start = "2026-11-28T13:00:00Z", end = "2026-11-28T15:00:00Z",
        place = "Pfadiheim Beispielwald", meetingPoint = "Pfadiheim Beispielwald, Eingang Nord\nKontakt $phone",
        tags = listOf(ProgramTag("s", "Wölfe", "stufe")),
        children = listOf(ProgramChild("1", childName, null), ProgramChild("2", otherChild, "ja")),
        ownRegistration = "ja",
    )
    private val chats = listOf(
        Chat(
            id = "c", type = "stufe", ageGroup = ChatAgeGroup("s", "Wölfe", "W"), unread = 3,
            lastMessage = LastMessage(chatText, "2026-11-21T10:00:00Z", own = false, author = ChatPerson("x", chatAuthor)),
        ),
        Chat(id = "d", type = "direkt", ageGroup = ChatAgeGroup("s", "Wölfe", "W"), unread = 0, lastMessage = LastMessage(chatText, "2026-11-21T10:00:00Z", own = false, author = ChatPerson("x", chatAuthor))),
    )
    private val task = CampTask(id = "t", version = 1, title = "Offerte Lagerhaus einholen", due = "2027-03-15", assigneeId = "me", assignee = io.github.sorry4nothing.cevios.lager.TaskPerson("me", personName, personName))

    private val contents: List<Pair<WidgetKind, WidgetContent>> = listOf(
        WidgetKind.PROGRAM to WidgetContent.Programs(listOf(programRow(programWithNames))),
        WidgetKind.CALENDAR to calendarContent(listOf(programWithNames), 5),
        WidgetKind.CAMP_TODAY to WidgetContent.CampToday("c", 3, 8, DutyLine("Küche", "Frühstück", "07:00–10:00"), listOf("Abfahrt Ausflug 13:30")),
        WidgetKind.TASKS to tasksContent("c", listOf(task), "me", LocalDate.of(2027, 3, 20), 3),
        WidgetKind.EMERGENCY to WidgetContent.Emergency("c"),
        WidgetKind.CHAT to chatContent(chats),
    )

    private val markers = listOf(childName, otherChild, chatText, chatAuthor, phone, personName)

    @Test
    fun mappedContentCarriesNoNamesTextsOrNumbers() {
        for ((_, content) in contents) {
            for (marker in markers) assertFalse("$content enthält $marker", content.toString().contains(marker))
        }
        val program = (contents[0].second as WidgetContent.Programs).rows.single()
        assertEquals(ProgramStatus(ProgramState.CHILDREN_OPEN, 1), program.status)
        // Der Treffpunkt ist der Ort ohne die Zeile mit der Nummer.
        assertTrue(program.place!!.startsWith("Pfadiheim Beispielwald"))
        assertEquals(3, (contents[5].second as WidgetContent.Chat).unread)
        assertEquals(1, (contents[5].second as WidgetContent.Chat).chats)
    }

    private val outcomes = listOf<(WidgetContent?) -> LoadOutcome>(
        { LoadOutcome.Loaded(it!!) }, { LoadOutcome.Offline }, { LoadOutcome.Failed }, { LoadOutcome.NotSignedIn }, { LoadOutcome.NoCamp },
    )

    private val sizes = listOf(172 to 196, 360 to 196, 360 to 412)

    @Test
    fun noKindStateOrSizeLetsAForbiddenFieldThrough() {
        for ((kind, content) in contents) {
            for (previous in listOf<Snapshot?>(null, Snapshot(content, now.minusSeconds(3 * 3600)), Snapshot(content, now.minusSeconds(600)))) {
                for (locked in listOf(false, true)) {
                    for (outcome in outcomes) {
                        val (screen, kept) = resolveScreen(kind, outcome(content), previous, now, locked)
                        for ((w, h) in sizes) {
                            for (keyguard in listOf(false, true)) {
                                val plan = layoutPlan(kind, w, h, keyguard = keyguard)
                                val text = screen.toString() + kept.toString()
                                for (marker in markers) assertFalse("$kind $screen $w×$h: $marker", text.contains(marker))
                                // Gesperrt: weder Titel noch Treffpunkt, nur Art, Zeit und Zähler.
                                if (screen is WidgetScreen.Locked) {
                                    for (secret in listOf("Waldspiel", "Pfadiheim", "Wölfe", "Küche", "Abfahrt", "Offerte")) {
                                        assertFalse("$kind gesperrt zeigt $secret", screen.toString().contains(secret))
                                    }
                                }
                                // Auf dem Sperrbildschirm gibt es nie einen Treffpunkt.
                                if (keyguard) assertFalse(plan.showPlace)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun everyStateIsReachableAtEverySize() {
        for ((kind, content) in contents) {
            val screens = buildSet {
                add(resolveScreen(kind, LoadOutcome.Loaded(content), null, now, false).first::class)
                add(resolveScreen(kind, LoadOutcome.Loaded(content), null, now, true).first::class)
                add(resolveScreen(kind, LoadOutcome.Offline, Snapshot(content, now.minusSeconds(60)), now, false).first::class)
                add(resolveScreen(kind, LoadOutcome.NotSignedIn, null, now, false).first::class)
                add(resolveScreen(kind, LoadOutcome.Failed, null, now, false).first::class)
                add(resolveScreen(kind, LoadOutcome.NoCamp, null, now, false).first::class)
                add(resolveScreen(kind, LoadOutcome.Offline, null, now, false).first::class)
            }
            // Normal, Gesperrt (nur der Notfall bleibt bedienbar), Abgemeldet, Kein Lager, Fehler, Ohne Netz ohne Daten.
            assertTrue("$kind: $screens", screens.size >= if (kind == WidgetKind.EMERGENCY) 3 else 5)
            for ((w, h) in sizes) assertTrue(layoutPlan(kind, w, h).rows >= 0)
        }
    }
}
