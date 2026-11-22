package io.github.sorry4nothing.cevios.programs

import io.github.sorry4nothing.cevios.chat.Profile
import io.github.sorry4nothing.cevios.contacts.ContactList
import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiResult
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import java.time.LocalDate

@Serializable
private data class RsvpRequest(val status: String)

private const val PAGE_SIZE = 100

/** Schutz vor einer Endlosschleife: Mit 100 Terminen je Seite sind 20 Seiten weit mehr als ein halbes Jahr. */
private const val MAX_PAGES = 20

class ProgramsApi(private val api: ApiClient) {
    /**
     * «Mein Kalender»: die Termine, die das Fenster `from` bis `until` (Schweizer Kalendertage, inklusive) berühren und die die
     * Person sieht. Der Server liefert sie seitenweise; die App holt alle Seiten (höchstens [MAX_PAGES]) und gibt eine Liste zurück.
     */
    suspend fun list(from: LocalDate, until: LocalDate): ApiResult<ProgramList> {
        val all = mutableListOf<Program>()
        var after = ""
        repeat(MAX_PAGES) {
            when (val page = api.get<ProgramPage>("/api/v1/termine/mein?von=$from&bis=$until&limit=$PAGE_SIZE$after")) {
                is ApiResult.Failure -> return page
                is ApiResult.Ok -> {
                    all += page.value.programs
                    val next = page.value.next
                    if (!page.value.more || next == null) return ApiResult.Ok(ProgramList(all))
                    after = "&nach=" + URLEncoder.encode(next, "UTF-8")
                }
            }
        }
        return ApiResult.Ok(ProgramList(all))
    }

    /** Ein Termin aus «Mein Kalender» mit Treffpunkt, Mitnehmen und den eigenen Kindern. */
    suspend fun read(id: String): ApiResult<Program> = api.get("/api/v1/termine/$id")

    /** Meldet ein eigenes Kind an («ja») oder ab («nein»); eine frühere Antwort wird überschrieben. */
    suspend fun setAttendance(id: String, childId: String, attending: Boolean): ApiResult<Unit> =
        api.put("/api/v1/programme/$id/teilnahmen/$childId", rsvpBody(attending))

    /** Dasselbe für die eigene Anmeldung als Leitung. */
    suspend fun setLeaderRsvp(id: String, attending: Boolean): ApiResult<Unit> =
        api.put("/api/v1/programme/$id/leitung", rsvpBody(attending))

    suspend fun ageGroups(): ApiResult<AgeGroupList> = api.get("/api/v1/stufen")

    /** Die Stufenleitungen der Stufen des Programms, ohne die, die abgesagt haben («nein»); der Hauptkontakt steht zuerst. Leer nur, wenn die Stufe keine SL hat. */
    suspend fun contacts(id: String): ApiResult<ContactList> = api.get("/api/v1/programme/$id/kontakte")

    /** Zahlen und Listen der Anmeldungen (nur ansehen): Admins sowie Leitende und Stufenleitungen der Stufen des Programms. */
    suspend fun registrations(id: String): ApiResult<Registrations> = api.get("/api/v1/programme/$id/teilnahmen")

    suspend fun profile(): ApiResult<Profile> = api.get("/api/v1/ich")

    private fun rsvpBody(attending: Boolean) = RsvpRequest(if (attending) "ja" else "nein")
}
