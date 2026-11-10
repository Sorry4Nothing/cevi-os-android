package io.github.sorry4nothing.cevios.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.StringRes
import io.github.sorry4nothing.cevios.MainActivity
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.programs.Program
import io.github.sorry4nothing.cevios.programs.ProgramsApi
import io.github.sorry4nothing.cevios.programs.SWISS_ZONE
import io.github.sorry4nothing.cevios.security.AppLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZonedDateTime

/** Was das Widget zeigt: ein Satz anstelle der Programme oder die Programme mit dem Stand der eigenen Kinder. */
private sealed interface WidgetState {
    data class Message(@StringRes val text: Int) : WidgetState

    data class Programs(val programs: List<Program>) : WidgetState
}

/**
 * Homescreen-Widget «Nächste Programme»: die nächsten zwei Programme mit der eigenen Zusage (Leitung) und dem Stand der eigenen Kinder. Es hält nichts
 * zwischen: Jedes Update fragt den Server (alle 30 Minuten, beim Öffnen und Verlassen der App und nach jeder Antwort).
 * Antwortet der Server nicht, bleibt der letzte Stand stehen. Ist das Entsperren der App an, zeigt es keine Personendaten.
 */
class ProgramsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Ein Broadcast-Empfänger hat rund zehn Sekunden.
                val state = withTimeoutOrNull(LOAD_TIMEOUT_MS) { load(context) }
                // Ohne Antwort bleibt der letzte Stand stehen (z. B. im Energiesparmodus, der Hintergrundprozessen das Netz sperrt).
                if (state != null) manager.updateAppWidget(ids, render(context, state))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val LOAD_TIMEOUT_MS = 8_000L
        private const val MAX_CHIPS = 4

        /** Ab drei Kindern steht nur der Anfangsbuchstabe im Chip, damit alle Platz haben. */
        private const val COMPACT_FROM = 3

        /** Stösst ein Update an, z. B. wenn die App sich schliesst (Anmelden, Abmelden, neue Antwort). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ProgramsWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, ProgramsWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }

        /** `null`, wenn der Server nicht antwortet: Dann bleibt der letzte Stand. */
        private suspend fun load(context: Context): WidgetState? {
            if (AppLock(context).enabled) return WidgetState.Message(R.string.widget_locked)
            val client = ApiClient.create(context)
            if (!client.hasSession()) return WidgetState.Message(R.string.widget_signed_out)
            val api = ProgramsApi(client)
            val now = ZonedDateTime.now(SWISS_ZONE)
            val list = when (val result = api.list(now.toLocalDate(), now.toLocalDate().plusMonths(2))) {
                is ApiResult.Ok -> result.value.programs
                is ApiResult.Failure ->
                    return if (result.error == ApiError.NOT_SIGNED_IN) WidgetState.Message(R.string.widget_signed_out) else null
            }
            val next = upcoming(list, now)
            if (next.isEmpty()) return WidgetState.Message(R.string.widget_empty)
            // Die Kinder stehen nur im Detail; scheitert es, bleibt das Programm ohne Chips stehen.
            val details = coroutineScope { next.map { async { api.read(it.id) } }.awaitAll() }
            return WidgetState.Programs(next.mapIndexed { i, program -> (details[i] as? ApiResult.Ok)?.value ?: program })
        }

        private fun render(context: Context, state: WidgetState): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_programs)
            views.setOnClickPendingIntent(R.id.widget_root, open(context, null))
            val programs = (state as? WidgetState.Programs)?.programs.orEmpty()
            views.setViewVisibility(R.id.widget_rows, if (programs.isEmpty()) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.widget_message, if (programs.isEmpty()) View.VISIBLE else View.GONE)
            if (state is WidgetState.Message) views.setTextViewText(R.id.widget_message, context.getString(state.text))
            // Launcher recyceln das Layout: Ohne Leeren hängt jedes Update die Zeilen erneut an.
            views.removeAllViews(R.id.widget_rows)
            programs.forEachIndexed { i, program -> views.addView(R.id.widget_rows, row(context, program, divider = i > 0)) }
            return views
        }

        private fun row(context: Context, program: Program, divider: Boolean): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            val stage = if (program.allAgeGroups) context.getString(R.string.stage_all) else program.ageGroups.joinToString(", ") { it.name }
            row.setViewVisibility(R.id.row_divider, if (divider) View.VISIBLE else View.GONE)
            row.setTextViewText(R.id.row_title, program.title)
            row.setTextViewText(R.id.row_time, dateLine(program, context.getString(R.string.time_list_all_day)))
            row.setTextViewText(R.id.row_stage, stage)
            row.setOnClickPendingIntent(R.id.row_root, open(context, program.id))
            // Als Leitung steht die eigene Zusage («Du») vor den Kindern.
            val own = program.ownRegistration?.let { Answer(context.getString(R.string.widget_own_name), it, own = true) }
            val answers = listOfNotNull(own) + program.children.map { Answer(it.firstName, it.status) }
            val compact = answers.size >= COMPACT_FROM
            answers.take(MAX_CHIPS).forEach { row.addView(R.id.row_chips, chip(context, it, compact)) }
            return row
        }

        /** Eine Zusage: `status` «ja», «nein», sonst offen. */
        private class Answer(val name: String, val status: String?, val own: Boolean = false)

        /** Chip mit Symbol und Name, ab `COMPACT_FROM` Zusagen ein Kreis mit Anfangsbuchstabe und Badge; beide mit Beschreibung («Lena kommt»), der Stand nie nur über die Farbe. */
        private fun chip(context: Context, answer: Answer, compact: Boolean): RemoteViews {
            val (chipLayout, dotLayout, description) = when (answer.status) {
                "ja" -> Triple(R.layout.widget_child_yes, R.layout.widget_dot_yes, if (answer.own) R.string.widget_own_yes else R.string.child_status_yes)
                "nein" -> Triple(R.layout.widget_child_no, R.layout.widget_dot_no, if (answer.own) R.string.widget_own_no else R.string.child_status_no)
                else -> Triple(R.layout.widget_child_open, R.layout.widget_dot_open, if (answer.own) R.string.widget_own_open else R.string.child_status_open)
            }
            val chip = RemoteViews(context.packageName, if (compact) dotLayout else chipLayout)
            chip.setTextViewText(R.id.chip_text, if (compact) answer.name.take(1).uppercase() else answer.name)
            chip.setContentDescription(R.id.chip_root, context.getString(description, answer.name))
            return chip
        }

        /** Öffnet die App; mit `programId` gleich beim Programm (derselbe Weg wie das Tippen auf einen Push). */
        private fun open(context: Context, programId: String?): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (programId != null) intent.putExtra("typ", "programm").putExtra("id", programId)
            // Eigener Anfragecode je Programm: Sonst überschriebe ein PendingIntent den anderen (Extras zählen nicht).
            return PendingIntent.getActivity(
                context,
                programId?.hashCode() ?: 0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
