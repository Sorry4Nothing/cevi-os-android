package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.net.ResponseCache
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Die Routen der Einteilung: Teilnehmende, Zimmer, Fahrten, Gruppen, Schichtplan, Diensttausch und Abzählliste. Der Server kennt
 * für sie keine Operationen der Offline-Warteschlange: Änderungen gehen direkt hinaus und brauchen Netz. Zeilen mit Version
 * (Teilnehmende, Zimmer, Fahrten, Gruppen, Dienste, Tausch) nennen die zuletzt gesehene `version`; stimmt sie nicht, antwortet der
 * Server mit `VERSION_KONFLIKT` (`ApiResult.Failure.conflict`). Zustände ohne Version (Zuordnungen, Besetzung, Haken) setzt der Server
 * auf genau den genannten Wert; eine Wiederholung ändert nichts.
 */
class PlanningApi(private val api: ApiClient) {
    /** Der verschlüsselte Speicher des Lagers auf dem Gerät (für die eigenen Haken der Abzählliste); `null` ohne Zwischenspeicher. */
    val cache: ResponseCache? get() = api.cache

    /** Liest alle Seiten einer Liste (Cursor `nach`); `page` baut den Pfad zu einer Seite, `next` und `items` lesen die Antwort. */
    private suspend fun <P, T> all(page: suspend (String?) -> ApiResult<P>, next: (P) -> String?, items: (P) -> List<T>): ApiResult<List<T>> {
        val all = mutableListOf<T>()
        var after: String? = null
        repeat(MAX_PAGES) {
            when (val result = page(after)) {
                is ApiResult.Failure -> return result
                is ApiResult.Ok -> {
                    all += items(result.value)
                    after = next(result.value) ?: return ApiResult.Ok(all)
                }
            }
        }
        return ApiResult.Ok(all)
    }

    private fun cursor(after: String?) = after?.let { "&nach=$it" }.orEmpty()

    // ---- Teilnehmende ----

    suspend fun participants(campId: String): ApiResult<List<Participant>> =
        all({ api.get<ParticipantPage>("/api/v1/lager/$campId/teilnehmer?limit=$PAGE_SIZE${cursor(it)}") }, { it.next }, { it.participants })

    suspend fun createParticipant(campId: String, body: JsonObject): ApiResult<Participant> = api.post("/api/v1/lager/$campId/teilnehmer", body)

    suspend fun changeParticipant(campId: String, id: String, body: JsonObject): ApiResult<Participant> = api.patchJson("/api/v1/lager/$campId/teilnehmer/$id", body)

    suspend fun deleteParticipant(campId: String, participant: Participant): ApiResult<Unit> =
        api.delete("/api/v1/lager/$campId/teilnehmer/${participant.id}?version=${participant.version}")

    /** Liest eingefügten Text (eine Person je Zeile); mit `apply` false nur als Vorschau, es wird nichts geschrieben. */
    suspend fun importParticipants(campId: String, text: String, apply: Boolean): ApiResult<ImportResult> =
        api.post("/api/v1/lager/$campId/teilnehmer/import", buildJsonObject { put("text", text); put("uebernehmen", apply) })

    /** Wählt Teilnehmende ab («nicht dabei») oder wieder zu; Abgewählte stehen weder in Gruppen noch in der Abzählliste. */
    suspend fun setExcluded(campId: String, ids: List<String>, excluded: Boolean): ApiResult<ChangedCount> =
        api.putJson("/api/v1/lager/$campId/gruppen/abgewaehlt", buildJsonObject { put("abgewaehlt", excluded); put("teilnehmerIds", strings(ids)) })

    // ---- Zimmer und Zelte ----

    suspend fun rooms(campId: String): ApiResult<RoomList> = api.get("/api/v1/lager/$campId/zimmer")

    /** Die Mitglieder für Auswahllisten; braucht `einteilung.lesen`. Das Team ohne `einteilung.bearbeiten` sieht nur die Sichtbaren. */
    suspend fun members(campId: String): ApiResult<CampMemberList> = api.get("/api/v1/lager/$campId/mitglieder")

    suspend fun openPeople(campId: String, team: Boolean): ApiResult<List<OpenPerson>> =
        all({ api.get<OpenPage>("/api/v1/lager/$campId/zimmer/offen?art=${if (team) "team" else "teilnehmer"}&limit=$PAGE_SIZE${cursor(it)}") }, { it.next }, { it.entries })

    suspend fun createRoom(campId: String, body: JsonObject): ApiResult<Room> = api.post("/api/v1/lager/$campId/zimmer", body)

    suspend fun changeRoom(campId: String, id: String, body: JsonObject): ApiResult<Room> = api.patchJson("/api/v1/lager/$campId/zimmer/$id", body)

    suspend fun deleteRoom(campId: String, room: Room): ApiResult<Unit> = api.delete("/api/v1/lager/$campId/zimmer/${room.id}?version=${room.version}")

    suspend fun assignToRoom(campId: String, roomId: String, participantIds: List<String>, personIds: List<String>): ApiResult<RoomFill> =
        api.post("/api/v1/lager/$campId/zimmer/$roomId/zuordnung", people(participantIds, personIds))

    suspend fun removeFromRoom(campId: String, roomId: String, participantIds: List<String>, personIds: List<String>): ApiResult<RoomFill> =
        api.post("/api/v1/lager/$campId/zimmer/$roomId/zuordnung/entfernen", people(participantIds, personIds))

    // ---- Fahrten ----

    suspend fun trips(campId: String): ApiResult<List<Trip>> =
        all({ api.get<TripPage>("/api/v1/lager/$campId/fahrten?limit=$PAGE_SIZE${cursor(it)}") }, { it.next }, { it.trips })

    /** Personen ohne Fahrt in einer Richtung (nicht für das Team: 403). Die Gesamtzahl steht in der ersten Seite. */
    suspend fun withoutTrip(campId: String, direction: TripDirection): ApiResult<List<TripCandidate>> =
        all({ api.get<TripCandidatePage>("/api/v1/lager/$campId/fahrten/ohne?richtung=${direction.wire}&limit=$PAGE_SIZE${cursor(it)}") }, { it.next }, { it.people })

    suspend fun createTrip(campId: String, body: JsonObject): ApiResult<Trip> = api.post("/api/v1/lager/$campId/fahrten", body)

    suspend fun changeTrip(campId: String, id: String, body: JsonObject): ApiResult<Trip> = api.patchJson("/api/v1/lager/$campId/fahrten/$id", body)

    suspend fun deleteTrip(campId: String, trip: Trip): ApiResult<Unit> = api.delete("/api/v1/lager/$campId/fahrten/${trip.id}?version=${trip.version}")

    suspend fun addPassenger(campId: String, tripId: String, candidate: TripCandidate): ApiResult<TripPassengerAdded> =
        api.post(
            "/api/v1/lager/$campId/fahrten/$tripId/mitfahrer",
            buildJsonObject { candidate.personId?.let { put("personId", it) } ?: candidate.teilnehmerId?.let { put("teilnehmerId", it) } },
        )

    suspend fun removePassenger(campId: String, tripId: String, passenger: TripPerson): ApiResult<Unit> =
        api.delete("/api/v1/lager/$campId/fahrten/$tripId/mitfahrer/${passenger.id}?version=${passenger.version}")

    // ---- Gruppen ----

    suspend fun groups(campId: String): ApiResult<GroupList> = api.get("/api/v1/lager/$campId/gruppen")

    suspend fun createGroup(campId: String, name: String): ApiResult<GroupRow> = api.post("/api/v1/lager/$campId/gruppen", buildJsonObject { put("name", name) })

    suspend fun changeGroup(campId: String, id: String, body: JsonObject): ApiResult<GroupRow> = api.patchJson("/api/v1/lager/$campId/gruppen/$id", body)

    suspend fun deleteGroup(campId: String, group: Group): ApiResult<Unit> = api.delete("/api/v1/lager/$campId/gruppen/${group.id}?version=${group.version}")

    /** Setzt Teilnehmende und Mitglieder in eine Gruppe (wer in einer anderen steht, wird verschoben); `groupId` null nimmt sie heraus. */
    suspend fun assignToGroup(campId: String, groupId: String?, participantIds: List<String>, personIds: List<String>, leader: Boolean): ApiResult<ChangedCount> =
        api.putJson(
            "/api/v1/lager/$campId/gruppen/zuordnung",
            buildJsonObject {
                put("gruppeId", groupId?.let(::JsonPrimitive) ?: JsonNull)
                if (participantIds.isNotEmpty()) put("teilnehmerIds", strings(participantIds))
                if (personIds.isNotEmpty()) {
                    put("personIds", strings(personIds))
                    put("rolle", if (leader) "leitung" else "mitglied")
                }
            },
        )

    /** Mischt die Teilnehmenden nach Stufe auf `count` Gruppen; ohne `apply` nur die Vorschau. Mit `seed` ist das Ergebnis wiederholbar. */
    suspend fun mixGroups(campId: String, count: Int, apply: Boolean, seed: Long?): ApiResult<MixResult> =
        api.post("/api/v1/lager/$campId/gruppen/mischen", buildJsonObject { put("anzahl", count); put("uebernehmen", apply); seed?.let { put("seed", it) } })

    // ---- Schichtplan ----

    suspend fun shiftPlan(campId: String): ApiResult<ShiftPlan> = api.get("/api/v1/lager/$campId/schichtplan")

    /** Lagertage und Tagesabschnitte; sie gehören zum Zeitplan, der Schichtplan braucht nur diese beiden Listen. */
    suspend fun schedule(campId: String): ApiResult<Schedule> = api.get("/api/v1/lager/$campId/zeitplan")

    suspend fun createDuty(campId: String, body: JsonObject): ApiResult<Duty> = api.post("/api/v1/lager/$campId/schichtplan/dienste", body)

    suspend fun changeDuty(campId: String, id: String, body: JsonObject): ApiResult<Duty> = api.patchJson("/api/v1/lager/$campId/schichtplan/dienste/$id", body)

    suspend fun deleteDuty(campId: String, duty: Duty): ApiResult<Unit> = api.delete("/api/v1/lager/$campId/schichtplan/dienste/${duty.id}?version=${duty.version}")

    /** Setzt die ganze Besetzung eines Dienstes an einem Tag auf genau diese Personen; leere Listen leeren den Dienst. */
    suspend fun staffDuty(campId: String, dayId: String, dutyId: String, personIds: List<String>, participantIds: List<String>): ApiResult<ChangedCount> =
        api.putJson(
            "/api/v1/lager/$campId/schichtplan/schichten",
            buildJsonObject { put("lagertagId", dayId); put("dienstId", dutyId); put("personIds", strings(personIds)); put("teilnehmerIds", strings(participantIds)) },
        )

    suspend fun suggestShifts(campId: String, apply: Boolean, seed: Long?, withParticipants: Boolean, onlyPresent: Boolean): ApiResult<ShiftSuggestion> =
        api.post(
            "/api/v1/lager/$campId/schichtplan/vorschlag",
            buildJsonObject {
                put("uebernehmen", apply)
                seed?.let { put("seed", it) }
                put("mitTeilnehmenden", withParticipants)
                put("nurAnwesende", onlyPresent)
            },
        )

    // ---- Diensttausch ----

    /** Alle Anfragen des Lagers, älteste zuerst (nur mit dem Recht zu entscheiden). */
    suspend fun swaps(campId: String): ApiResult<List<Swap>> =
        all({ api.get<SwapPage>("/api/v1/lager/$campId/diensttausch?limit=$PAGE_SIZE${cursor(it)}") }, { it.next }, { it.swaps })

    /** Die letzten Anfragen der eigenen Person, neueste zuerst. */
    suspend fun ownSwaps(campId: String): ApiResult<List<Swap>> =
        when (val result = api.get<SwapList>("/api/v1/lager/$campId/diensttausch/eigene")) {
            is ApiResult.Ok -> ApiResult.Ok(result.value.swaps)
            is ApiResult.Failure -> result
        }

    suspend fun requestSwap(campId: String, shiftId: String, withShiftId: String?, withPersonId: String?, note: String?): ApiResult<Swap> =
        api.post(
            "/api/v1/lager/$campId/diensttausch",
            buildJsonObject {
                put("schichtId", shiftId)
                withShiftId?.let { put("mitSchichtId", it) }
                withPersonId?.let { put("mitPersonId", it) }
                note?.takeIf { it.isNotBlank() }?.let { put("notiz", it.trim()) }
            },
        )

    suspend fun decideSwap(campId: String, swap: Swap, version: Long, accept: Boolean, takeOverBy: String?): ApiResult<Swap> =
        api.post(
            "/api/v1/lager/$campId/diensttausch/${swap.id}/entscheid",
            buildJsonObject {
                put("entscheid", if (accept) "angenommen" else "abgelehnt")
                put("version", version)
                takeOverBy?.let { put("mitPersonId", it) }
            },
        )

    suspend fun withdrawSwap(campId: String, swap: Swap, version: Long): ApiResult<Swap> =
        api.post("/api/v1/lager/$campId/diensttausch/${swap.id}/zurueckziehen", buildJsonObject { put("version", version) })

    // ---- Abzählliste ----

    /**
     * Wer in der Abzählliste steht, mit Filtern (`stage`, `groupId`); `team` zählt die Mitglieder statt der Teilnehmenden. Die Haken gehören
     * der Person allein und gehen nie an den Server: Die Schreib-Routen `PUT …/abzaehlen` und `POST …/abzaehlen/zuruecksetzen` ruft die App nicht auf.
     */
    suspend fun count(campId: String, team: Boolean, stage: String?, groupId: String?): ApiResult<CountPage> {
        val all = mutableListOf<CountEntry>()
        var after: String? = null
        var last: CountPage? = null
        repeat(MAX_PAGES) {
            val query = buildString {
                append("art=").append(if (team) "team" else "teilnehmer").append("&limit=").append(PAGE_SIZE)
                stage?.let { append("&stufe=").append(java.net.URLEncoder.encode(it, "UTF-8")) }
                groupId?.let { append("&gruppe=").append(it) }
                after?.let { append("&nach=").append(it) }
            }
            when (val page = api.get<CountPage>("/api/v1/lager/$campId/abzaehlen?$query")) {
                is ApiResult.Failure -> return page
                is ApiResult.Ok -> {
                    all += page.value.entries
                    last = page.value
                    after = page.value.next ?: return ApiResult.Ok(page.value.copy(entries = all, next = null))
                }
            }
        }
        return ApiResult.Ok(last!!.copy(entries = all))
    }

    private fun people(participantIds: List<String>, personIds: List<String>) = buildJsonObject {
        if (participantIds.isNotEmpty()) put("teilnehmerIds", strings(participantIds))
        if (personIds.isNotEmpty()) put("personIds", strings(personIds))
    }

    private fun strings(values: List<String>) = JsonArray(values.map(::JsonPrimitive))

    private companion object {
        const val PAGE_SIZE = 200
        const val MAX_PAGES = 10
    }
}

/** Antwort von «Mitfahrt anlegen» (nur das, was die App braucht). */
@kotlinx.serialization.Serializable
data class TripPassengerAdded(val id: String, val version: Long)

/** Eine Gruppe ohne ihre Leute (Antwort von Anlegen und Ändern). */
@kotlinx.serialization.Serializable
data class GroupRow(val id: String, val version: Long, val name: String, @kotlinx.serialization.SerialName("reihenfolge") val order: Int = 0)
