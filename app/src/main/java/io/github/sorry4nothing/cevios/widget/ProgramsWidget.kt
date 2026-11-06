package io.github.sorry4nothing.cevios.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.StringRes
import io.github.sorry4nothing.cevios.MainActivity
import io.github.sorry4nothing.cevios.R
import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.net.ApiError
import io.github.sorry4nothing.cevios.net.ApiResult
import io.github.sorry4nothing.cevios.programs.Program
import io.github.sorry4nothing.cevios.programs.ProgramChild
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
 * Homescreen-Widget «Nächste Programme»: die nächsten zwei Programme mit dem Stand der eigenen Kinder. Es hält nichts
 * zwischen: Jedes Update fragt den Server (alle 30 Minuten und beim Verlassen der App). Ist das Entsperren der App an,
 * zeigt es keine Personendaten.
 */
class ProgramsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Ein Broadcast-Empfänger hat rund zehn Sekunden.
                val state = withTimeoutOrNull(LOAD_TIMEOUT_MS) { load(context) } ?: WidgetState.Message(offline(context))
                manager.updateAppWidget(ids, render(context, state))
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

        /** Im Energiesparmodus sperrt Android das Netz für Hintergrundprozesse: Das Widget sagt es, statt «keine Verbindung» zu melden. */
        private fun offline(context: Context): Int =
            if (context.getSystemService(PowerManager::class.java).isPowerSaveMode) R.string.widget_power_save else R.string.widget_offline

        private suspend fun load(context: Context): WidgetState {
            if (AppLock(context).enabled) return WidgetState.Message(R.string.widget_locked)
            val client = ApiClient.create(context)
            if (!client.hasSession()) return WidgetState.Message(R.string.widget_signed_out)
            val api = ProgramsApi(client)
            val now = ZonedDateTime.now(SWISS_ZONE)
            val list = when (val result = api.list(now.toLocalDate(), now.toLocalDate().plusMonths(2))) {
                is ApiResult.Ok -> result.value.programs
                is ApiResult.Failure ->
                    return WidgetState.Message(if (result.error == ApiError.NOT_SIGNED_IN) R.string.widget_signed_out else offline(context))
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
            programs.forEachIndexed { i, program -> views.addView(R.id.widget_rows, row(context, program, divider = i > 0)) }
            return views
        }

        private fun row(context: Context, program: Program, divider: Boolean): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.widget_row)
            val stage = program.ageGroups.joinToString(", ") { it.name }
            row.setViewVisibility(R.id.row_divider, if (divider) View.VISIBLE else View.GONE)
            row.setTextViewText(R.id.row_title, program.title)
            row.setTextViewText(R.id.row_time, dateLine(program, context.getString(R.string.time_list_all_day)))
            row.setTextViewText(R.id.row_stage, stage)
            row.setViewVisibility(R.id.row_stage, if (stage.isEmpty()) View.GONE else View.VISIBLE)
            row.setOnClickPendingIntent(R.id.row_root, open(context, program.id))
            val compact = program.children.size >= COMPACT_FROM
            program.children.take(MAX_CHIPS).forEach { row.addView(R.id.row_chips, chip(context, it, compact)) }
            return row
        }

        /** Chip mit Symbol und Name, ab `COMPACT_FROM` Kinder ein Kreis mit Anfangsbuchstabe und Badge; beide mit Beschreibung («Lena kommt»), der Stand nie nur über die Farbe. */
        private fun chip(context: Context, child: ProgramChild, compact: Boolean): RemoteViews {
            val (chipLayout, dotLayout, description) = when (child.status) {
                "ja" -> Triple(R.layout.widget_child_yes, R.layout.widget_dot_yes, R.string.child_status_yes)
                "nein" -> Triple(R.layout.widget_child_no, R.layout.widget_dot_no, R.string.child_status_no)
                else -> Triple(R.layout.widget_child_open, R.layout.widget_dot_open, R.string.child_status_open)
            }
            val chip = RemoteViews(context.packageName, if (compact) dotLayout else chipLayout)
            chip.setTextViewText(R.id.chip_text, if (compact) child.firstName.take(1).uppercase() else child.firstName)
            chip.setContentDescription(R.id.chip_root, context.getString(description, child.firstName))
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
