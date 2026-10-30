package io.github.sorry4nothing.cevios.net

import io.github.sorry4nothing.cevios.programs.SWISS_ZONE
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.InterruptedIOException
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Antwortet im Namen des Servers, ohne Netzwerk, mit JSON in der Form der API (nur Debug-Build). Der Zustand liegt
 * im Speicher und geht mit der App verloren. Die Demo-Person ist eine Elternperson, keine Leitung. Mit `leader` ist sie
 * Stufenleitung der Demo-Stufe: Sie darf die Zuordnung ändern, und «Geländespiel» ist keiner Stufe zugeordnet.
 */
class DemoServer(private val delayMs: Long = DEFAULT_DELAY_MS, private val leader: Boolean = false) : Interceptor {
    private class Reply(val code: Int, val message: String, val body: JsonElement? = null)

    private val lock = Any()
    private val programs = DemoData.programs(LocalDate.now(SWISS_ZONE)).also { list ->
        if (leader) {
            list.single { it.status == "pruefen" }.apply {
                ageGroupIds = emptySet()
                visibility = "intern"
            }
        }
    }
    private val attendance = mutableMapOf<Pair<String, String>, String>()
    private val groupChat = DemoChat(
        DemoData.GROUP_CHAT_ID, direct = false, DemoData.groupMessages(Instant.now(), programs.first()),
    )
    private var directChat: DemoChat? = null
    private var nextMessageNumber = 59

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val reply = synchronized(lock) { route(request) }
        try {
            if (delayMs > 0) Thread.sleep(delayMs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Demo-Antwort abgebrochen")
        }
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(reply.code)
            .message(reply.message)
        reply.body?.let {
            builder.header("Content-Type", "application/json; charset=utf-8")
                .body(it.toString().toResponseBody(JSON_TYPE))
        }
        return builder.build()
    }

    private fun route(request: Request): Reply {
        val segments = request.url.pathSegments
        if (segments.size < 3 || segments[0] != "api" || segments[1] != "v1") return notFound()
        val path = segments.drop(2)
        val method = request.method
        val body = request.body?.let { Buffer().apply { it.writeTo(this) }.readUtf8() }.orEmpty()
        return when {
            method == "GET" && path == listOf("ich") -> ok(profile())
            method == "GET" && path == listOf("stufen") -> ok(ageGroupsJson())
            method == "GET" && path == listOf("hilfe") ->
                ok(buildJsonObject { put("vertrauenspersonen", DemoData.TRUSTED_PERSONS) })
            path[0] == "programme" -> programRoute(method, path, body)
            path[0] == "chats" -> chatRoute(request, path, body)
            path[0] == "nachrichten" -> messageRoute(method, path)
            else -> notFound()
        }
    }

    private fun programRoute(method: String, path: List<String>, body: String): Reply {
        if (method == "GET" && path.size == 1) {
            return ok(buildJsonObject { put("programme", JsonArray(programs.map(::programJson))) })
        }
        val program = programs.find { it.id == path.getOrNull(1) } ?: return notFound()
        return when {
            method == "GET" && path.size == 2 -> ok(programDetail(program))
            method == "PUT" && path.size == 3 && path[2] == "leitung" -> forbidden()
            method == "PUT" && path.size == 3 && path[2] == "zuordnung" -> assign(program, body)
            method == "PUT" && path.size == 4 && path[2] == "teilnahmen" -> {
                val child = DemoData.children.find { it.id == path[3] } ?: return notFound()
                val status = stringField(body, "status")?.takeIf { it == "ja" || it == "nein" }
                    ?: return invalidInput()
                if (!isOpen(program)) return error(409, "Conflict", "PROGRAMM_GESCHLOSSEN", "Das Programm ist geschlossen")
                attendance[program.id to child.id] = status
                Reply(204, "No Content")
            }
            else -> notFound()
        }
    }

    /**
     * Wie `PUT /api/v1/programme/{id}/zuordnung`: voller neuer Stand. Eine Stufenleitung ändert nur ihre eigene Stufe,
     * und die Sichtbarkeit nur, wenn alle bisherigen und neuen Stufen ihr gehören.
     */
    private fun assign(program: DemoProgram, body: String): Reply {
        val fields = (try { Json.parseToJsonElement(body) } catch (e: IllegalArgumentException) { null }) as? JsonObject
        val ids = (fields?.get("stufen") as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        val visibility = stringField(body, "sichtbarkeit")?.takeIf { it in VISIBILITIES }
        if (ids == null || ids.any { it == null } || ids.toSet().size != ids.size || visibility == null) return invalidInput()
        if (!leader) return forbidden()
        val next = ids.filterNotNull().toSet()
        val own = setOf(DemoData.AGE_GROUP_ID)
        val changed = (program.ageGroupIds - next) + (next - program.ageGroupIds)
        if (!own.containsAll(changed)) return forbidden()
        if (visibility != program.visibility && !own.containsAll(program.ageGroupIds + next)) return forbidden()
        if (!DemoData.ageGroupNames.keys.containsAll(next) || (next.isEmpty() && visibility != "intern")) return invalidInput()
        program.ageGroupIds = next
        program.visibility = visibility
        return Reply(204, "No Content")
    }

    private fun chatRoute(request: Request, path: List<String>, body: String): Reply {
        val method = request.method
        if (method == "GET" && path.size == 1) {
            return ok(buildJsonObject { put("chats", JsonArray(allChats().map(::chatJson))) })
        }
        if (method == "POST" && path == listOf("chats", "direkt")) {
            val ageGroupId = stringField(body, "stufeId") ?: return invalidInput()
            if (ageGroupId != DemoData.AGE_GROUP_ID) return notFound()
            val chat = directChat
                ?: DemoChat(DemoData.DIRECT_CHAT_ID, direct = true, DemoData.directMessages(Instant.now()))
                    .also { directChat = it }
            return ok(buildJsonObject { put("id", chat.id) })
        }
        val chat = allChats().find { it.id == path.getOrNull(1) } ?: return notFound()
        if (path.size != 3 || path[2] != "nachrichten") return notFound()
        return when (method) {
            "GET" -> ok(messagePage(chat, request.url.queryParameter("vor")))
            "POST" -> {
                val text = stringField(body, "text")?.takeIf { it.isNotBlank() && it.length <= MAX_TEXT_LENGTH }
                    ?: return invalidInput()
                val message = DemoMessage(
                    DemoData.messageId(nextMessageNumber++), text, Instant.now().truncatedTo(ChronoUnit.SECONDS),
                    DemoData.me, own = true,
                )
                chat.messages.add(message)
                Reply(201, "Created", buildJsonObject { put("id", message.id) })
            }
            else -> notFound()
        }
    }

    private fun messageRoute(method: String, path: List<String>): Reply {
        if (method != "POST" || path.size != 3) return notFound()
        val message = allChats().flatMap { it.messages }.find { it.id == path[1] } ?: return notFound()
        return when (path[2]) {
            "melden" -> if (message.own && !message.hidden) invalidInput() else Reply(204, "No Content")
            "ausblenden" -> forbidden()
            else -> notFound()
        }
    }

    private fun allChats() = listOfNotNull(groupChat, directChat)

    /** Offen für Antworten: aktiv und noch nicht begonnen. */
    private fun isOpen(program: DemoProgram) = program.status == "aktiv" && program.begin.isAfter(Instant.now())

    private fun profile() = buildJsonObject {
        put("id", DemoData.me.id)
        put("name", DemoData.me.name)
        put("benutzername", "demo.eltern")
        put("mussPasswortAendern", false)
        put("datenschutzGelesenAm", "2026-01-01T08:00:00Z")
        put("telefon", null as String?)
        put("pushChat", true)
        put("pushProgramme", true)
        put("admin", false)
        put("zweiterFaktor", false)
        putJsonArray("rollen") {
            if (leader) {
                add(
                    buildJsonObject {
                        put("stufeId", DemoData.AGE_GROUP_ID)
                        put("typ", "stufenleitung")
                    },
                )
            }
        }
        putJsonArray("stufen") { add(ageGroupJson()) }
        putJsonArray("kinder") {
            DemoData.children.forEach {
                add(
                    buildJsonObject {
                        put("id", it.id)
                        put("vorname", it.name)
                        put("nachname", DemoData.CHILD_LAST_NAME)
                        put("stufeId", DemoData.AGE_GROUP_ID)
                    },
                )
            }
        }
    }

    private fun ageGroupJson() = buildJsonObject {
        put("id", DemoData.AGE_GROUP_ID)
        put("name", DemoData.AGE_GROUP_NAME)
        put("kuerzel", DemoData.AGE_GROUP_ABBREVIATION)
    }

    /** `GET /api/v1/stufen`: alle Stufen der Abteilung, mit `reihenfolge`. */
    private fun ageGroupsJson() = buildJsonObject {
        putJsonArray("stufen") {
            add(JsonObject(ageGroupJson() + buildJsonObject { put("reihenfolge", 1) }))
            add(
                buildJsonObject {
                    put("id", DemoData.OTHER_AGE_GROUP_ID)
                    put("name", DemoData.OTHER_AGE_GROUP_NAME)
                    put("kuerzel", DemoData.OTHER_AGE_GROUP_ABBREVIATION)
                    put("reihenfolge", 2)
                },
            )
        }
    }

    private fun programJson(p: DemoProgram) = buildJsonObject {
        put("id", p.id)
        put("titel", p.title)
        put("status", p.status)
        put("sichtbarkeit", p.visibility)
        put("ganztaegig", p.allDay)
        put("beginn", p.start?.toString())
        put("ende", p.end?.toString())
        put("tagVon", p.dayFrom?.toString())
        put("tagBis", p.dayTo?.toString())
        put("googleOrt", p.googleLocation)
        put("treffpunkt", p.meetingPoint)
        put("schlussOrt", p.closingLocation)
        put("schlussZeit", p.closingTime)
        put("mitnehmen", p.bringAlong)
        put("infoEltern", p.parentInfo)
        put("googleBeschreibung", null as String?)
        putJsonArray("stufen") {
            p.ageGroupIds.sorted().forEach { id ->
                add(
                    buildJsonObject {
                        put("id", id)
                        put("name", DemoData.ageGroupNames.getValue(id))
                    },
                )
            }
        }
    }

    private fun programDetail(p: DemoProgram) = JsonObject(
        programJson(p) + buildJsonObject {
            put("teilnahmeOffen", isOpen(p))
            putJsonArray("kinder") {
                DemoData.children.forEach {
                    add(
                        buildJsonObject {
                            put("id", it.id)
                            put("vorname", it.name)
                            put("status", attendance[p.id to it.id])
                        },
                    )
                }
            }
        },
    )

    private fun personJson(person: DemoPerson) = buildJsonObject {
        put("id", person.id)
        put("name", person.name)
    }

    private fun chatJson(chat: DemoChat) = buildJsonObject {
        put("id", chat.id)
        put("typ", if (chat.direct) "direkt" else "stufe")
        put("stufe", ageGroupJson())
        put("eltern", if (chat.direct) personJson(DemoData.me) else JsonNull)
        putJsonArray("stufenleitung") {
            add(personJson(DemoData.leaderA))
            add(personJson(DemoData.leaderB))
        }
    }

    private fun messageJson(m: DemoMessage) = buildJsonObject {
        put("id", m.id)
        put("text", m.text)
        put("erstelltAm", m.createdAt.toString())
        put("autor", personJson(m.author))
        put(
            "programm",
            m.program?.let {
                buildJsonObject {
                    put("id", it.id)
                    put("titel", it.title)
                }
            } ?: JsonNull,
        )
        put("ausgeblendet", m.hidden)
        put("eigene", m.own)
    }

    /** Neueste zuerst; `naechster` ist die ID der ältesten gelieferten Nachricht, wenn es ältere gibt. */
    private fun messagePage(chat: DemoChat, before: String?): JsonObject {
        val candidates = chat.messages.sortedBy { it.id }.filter { before == null || it.id < before }
        val page = candidates.takeLast(PAGE_SIZE).reversed()
        val next = if (candidates.size > PAGE_SIZE) page.last().id else null
        return buildJsonObject {
            put("nachrichten", JsonArray(page.map(::messageJson)))
            put("naechster", next)
        }
    }

    private fun stringField(body: String, name: String): String? = try {
        ((Json.parseToJsonElement(body) as? JsonObject)?.get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content
    } catch (e: IllegalArgumentException) { // auch SerializationException
        null
    }

    private fun ok(body: JsonElement) = Reply(200, "OK", body)
    private fun notFound() = error(404, "Not Found", "NICHT_GEFUNDEN", "Nicht gefunden")
    private fun forbidden() = error(403, "Forbidden", "KEINE_BERECHTIGUNG", "Keine Berechtigung")
    private fun invalidInput() = error(400, "Bad Request", "UNGUELTIGE_EINGABE", "Ungültige Eingabe")

    private fun error(code: Int, message: String, apiCode: String, text: String) =
        Reply(
            code,
            message,
            buildJsonObject {
                put("code", apiCode)
                put("message", text)
            },
        )

    companion object {
        /** Damit Ladezustände sichtbar sind; Tests stellen 0 ein. */
        const val DEFAULT_DELAY_MS = 400L
        private const val PAGE_SIZE = 50
        private const val MAX_TEXT_LENGTH = 2000
        private val VISIBILITIES = setOf("intern", "leitende", "eltern")
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
