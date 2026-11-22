package io.github.sorry4nothing.cevios.widget

import io.github.sorry4nothing.cevios.chat.ChatApi
import io.github.sorry4nothing.cevios.lager.BlockKind
import io.github.sorry4nothing.cevios.lager.Camp
import io.github.sorry4nothing.cevios.lager.CampApi
import io.github.sorry4nothing.cevios.lager.CampRole
import io.github.sorry4nothing.cevios.lager.LiveApi
import io.github.sorry4nothing.cevios.lager.TasksApi
import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.programs.Program
import io.github.sorry4nothing.cevios.programs.ProgramsApi
import io.github.sorry4nothing.cevios.programs.SWISS_ZONE
import io.github.sorry4nothing.cevios.programs.groupChips
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.ZonedDateTime

/** So viele Zeilen holt ein Widget höchstens (4×4 im Entwurf); die Grösse bestimmt später, wie viele davon stehen. */
private const val PROGRAM_ROWS = 2
private const val CALENDAR_ROWS = 5
private const val TASK_ROWS = 3
private const val IMPORTANT_ROWS = 2

/**
 * Holt für ein Widget nur die Felder, die es zeigt (siehe `WidgetModel`). Jede Aktualisierung fragt den Server mit der Sitzung; es gibt
 * keinen Zwischenspeicher auf dem Gerät. Die Antworten der API (mit Namen und Texten) verlassen diese Klasse nicht.
 */
class WidgetLoader(private val client: ApiClient, private val choice: String?) {
    private val programs = ProgramsApi(client)

    suspend fun load(kind: WidgetKind, now: ZonedDateTime = ZonedDateTime.now(SWISS_ZONE)): LoadOutcome {
        if (!client.hasSession()) return LoadOutcome.NotSignedIn
        return when (kind) {
            WidgetKind.PROGRAM -> programsContent(now)
            WidgetKind.CALENDAR -> calendarContent(now)
            WidgetKind.CAMP_TODAY -> campToday(now)
            WidgetKind.TASKS -> tasks(now)
            WidgetKind.EMERGENCY -> emergency()
            WidgetKind.CHAT -> chat()
        }
    }

    private fun failure(result: ApiResult.Failure, campWidget: Boolean = false): LoadOutcome = when (result.error) {
        ApiError.NOT_SIGNED_IN -> LoadOutcome.NotSignedIn
        ApiError.NETWORK -> LoadOutcome.Offline
        ApiError.FORBIDDEN, ApiError.NOT_FOUND -> if (campWidget) LoadOutcome.NoCamp else LoadOutcome.Failed
        else -> LoadOutcome.Failed
    }

    private suspend fun upcomingPrograms(now: ZonedDateTime): ApiResult<List<Program>> =
        when (val result = programs.list(now.toLocalDate(), now.toLocalDate().plusMonths(2))) {
            is ApiResult.Ok -> ApiResult.Ok(upcoming(result.value.programs, now, limit = Int.MAX_VALUE))
            is ApiResult.Failure -> result
        }

    /** Die Stufe der Konfiguration lässt nur Programme dieser Stufe (oder ohne Stufe) übrig. */
    private fun inStage(program: Program): Boolean {
        val stage = choice ?: return true
        val stages = groupChips(program).mapNotNull { it.stageId }
        return stages.isEmpty() || stage in stages
    }

    private suspend fun programsContent(now: ZonedDateTime): LoadOutcome {
        val next = when (val result = upcomingPrograms(now)) {
            is ApiResult.Ok -> result.value.filter(::inStage).take(PROGRAM_ROWS)
            is ApiResult.Failure -> return failure(result)
        }
        // Die eigenen Kinder stehen nur im Detail; scheitert es, bleibt das Programm ohne Stand stehen.
        val details = coroutineScope { next.map { async { programs.read(it.id) } }.awaitAll() }
        val rows = next.mapIndexed { i, program -> programRow((details[i] as? ApiResult.Ok)?.value ?: program) }
        return LoadOutcome.Loaded(WidgetContent.Programs(rows))
    }

    private suspend fun calendarContent(now: ZonedDateTime): LoadOutcome =
        when (val result = upcomingPrograms(now)) {
            is ApiResult.Ok -> LoadOutcome.Loaded(calendarContent(result.value, CALENDAR_ROWS))
            is ApiResult.Failure -> failure(result)
        }

    private suspend fun chat(): LoadOutcome =
        when (val result = ChatApi(client).chats()) {
            is ApiResult.Ok -> LoadOutcome.Loaded(chatContent(result.value.chats))
            is ApiResult.Failure -> failure(result)
        }

    /** Das Lager des Widgets: das gewählte, sonst das einzige aktive; Rolle «lesen» zählt nicht als Rolle für Aufgaben. */
    private suspend fun camp(): Pair<Camp?, LoadOutcome?> =
        when (val result = CampApi(client).list()) {
            is ApiResult.Ok -> pickCamp(result.value.camps, choice) to null
            is ApiResult.Failure -> null to failure(result, campWidget = true)
        }

    private suspend fun emergency(): LoadOutcome {
        val (camp, failed) = camp()
        return when {
            failed != null -> failed
            camp == null -> LoadOutcome.NoCamp
            else -> LoadOutcome.Loaded(WidgetContent.Emergency(camp.id))
        }
    }

    private suspend fun campToday(now: ZonedDateTime): LoadOutcome {
        val (camp, failed) = camp()
        if (failed != null) return failed
        camp ?: return LoadOutcome.NoCamp
        val live = LiveApi(client)
        val (day, days) = campDay(camp, now.toLocalDate())
        // «Heute» und «Meine Woche» gehen nebeneinander; fehlt eines (Rolle, Netz), bleibt nur dieser Teil weg.
        val (today, week, schedule) = coroutineScope {
            Triple(async { live.today(camp.id) }, async { live.myWeek(camp.id) }, async { live.schedule(camp.id) })
        }
        val todayView = today.await()
        if (todayView is ApiResult.Failure && todayView.error == ApiError.NETWORK) return LoadOutcome.Offline
        val important = ((todayView as? ApiResult.Ok)?.value?.day?.blocks.orEmpty())
            .filter { it.kind == BlockKind.IMPORTANT }
            .mapNotNull { importantTitle(it.text) }
            .take(IMPORTANT_ROWS)
        val sections = ((schedule.await() as? ApiResult.Ok)?.value?.sections.orEmpty())
        val shift = ((week.await() as? ApiResult.Ok)?.value?.shifts.orEmpty()).firstOrNull { it.date == now.toLocalDate().toString() }
        val duty = shift?.duty?.let { d ->
            val section = sections.firstOrNull { it.id == shift.tagesabschnittId }
            DutyLine(d.name, section?.name, section?.time)
        }
        return LoadOutcome.Loaded(WidgetContent.CampToday(camp.id, day, days, duty, important))
    }

    private suspend fun tasks(now: ZonedDateTime): LoadOutcome {
        val (camp, failed) = camp()
        if (failed != null) return failed
        // Aufgaben gibt es für Lagerleitung und Leitung; für alle anderen Rollen ist das Widget ohne Rolle.
        if (camp == null || (camp.role != CampRole.LEADERSHIP && camp.role != CampRole.LEADER)) return LoadOutcome.NoCamp
        val me = when (val profile = programs.profile()) {
            is ApiResult.Ok -> profile.value.id
            is ApiResult.Failure -> return failure(profile)
        }
        return when (val list = TasksApi(client).listAll(camp.id)) {
            is ApiResult.Ok -> LoadOutcome.Loaded(tasksContent(camp.id, list.value, me, now.toLocalDate(), TASK_ROWS))
            is ApiResult.Failure -> failure(list, campWidget = true)
        }
    }
}
