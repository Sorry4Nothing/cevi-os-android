package io.github.sorry4nothing.cevios.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.sorry4nothing.cevios.net.ApiClient
import io.github.sorry4nothing.cevios.security.AppLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/** Ein Broadcast-Empfänger hat rund zehn Sekunden. */
private const val LOAD_TIMEOUT_MS = 8_000L

/** Dauert das Laden länger, steht bis dahin der Platzhalter (statische Balken), aber nur, wenn noch nichts zu zeigen ist. */
private const val PLACEHOLDER_AFTER_MS = 800L

private const val DEFAULT_WIDTH_DP = 250
private const val DEFAULT_HEIGHT_DP = 110

/**
 * Basis aller Widgets. Es hält nichts auf dem Gerät: Jedes Update fragt den Server (alle 30 Minuten durch das System, beim Öffnen und
 * Verlassen der App und nach Antworten in der App). Antwortet der Server nicht, zeigt es den letzten Stand aus dem Arbeitsspeicher mit
 * Stand-Zeile, nach dem Prozess-Ende «Ohne Netz» ohne Daten. Das System begrenzt die Häufigkeit; darum zeigt jedes Widget den Stand.
 */
abstract class WidgetProvider(private val kind: WidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext
                val client = ApiClient.create(app)
                val choice = WidgetChoice(app)
                val locked = AppLock(app).enabled
                coroutineScope {
                    ids.map { id ->
                        async { update(app, manager, id, WidgetLoader(client, choice.read(id)), locked) }
                    }.awaitAll()
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun update(context: Context, manager: AppWidgetManager, id: Int, loader: WidgetLoader, locked: Boolean) = coroutineScope {
        val previous = WidgetMemory.read(id)
        val job = async { loader.load(kind) }
        var outcome = withTimeoutOrNull(PLACEHOLDER_AFTER_MS) { job.await() }
        if (outcome == null) {
            if (previous == null) show(context, manager, id, WidgetScreen.Placeholder)
            outcome = withTimeoutOrNull(LOAD_TIMEOUT_MS) { job.await() } ?: LoadOutcome.Offline
        }
        val (screen, snapshot) = resolveScreen(kind, outcome, previous, Instant.now(), locked)
        // Abgemeldet: Stand und Wahl der Widgets verschwinden.
        if (screen == WidgetScreen.SignedOut) {
            WidgetMemory.write(id, null)
            WidgetChoice(context).remove(id)
        } else {
            WidgetMemory.write(id, snapshot)
        }
        show(context, manager, id, screen)
    }

    private fun show(context: Context, manager: AppWidgetManager, id: Int, screen: WidgetScreen) {
        WidgetMemory.writeScreen(id, screen)
        val options = manager.getAppWidgetOptions(id)
        val plan = planFor(context, kind, options)
        manager.updateAppWidget(id, WidgetRenderer(context).render(kind, screen, plan, id))
    }

    /** Zieht der Mensch das Widget grösser oder kleiner, passen sich die Zeilen an, ohne das Netz zu fragen. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        val screen = WidgetMemory.screen(id) ?: return
        show(context.applicationContext, manager, id, screen)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val choice = WidgetChoice(context)
        ids.forEach {
            WidgetMemory.write(it, null)
            WidgetMemory.writeScreen(it, null)
            choice.remove(it)
        }
    }

    companion object {
        private val PROVIDERS = listOf(
            ProgramsWidget::class.java,
            CalendarWidget::class.java,
            CampTodayWidget::class.java,
            TasksWidget::class.java,
            EmergencyWidget::class.java,
            ChatWidget::class.java,
        )

        /** Stösst ein Update aller Widgets an, z. B. wenn die App sich öffnet oder schliesst (Anmelden, Abmelden, neue Antwort). */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            PROVIDERS.forEach { provider ->
                val ids = manager.getAppWidgetIds(ComponentName(context, provider))
                if (ids.isEmpty()) return@forEach
                context.sendBroadcast(
                    Intent(context, provider)
                        .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
                )
            }
        }

        /** Die Grösse in dp aus den Optionen des Launchers (Hochformat: kleinste Breite, grösste Höhe). */
        internal fun planFor(context: Context, kind: WidgetKind, options: Bundle): LayoutPlan {
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: DEFAULT_WIDTH_DP
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 } ?: DEFAULT_HEIGHT_DP
            val keyguard = options.getInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, -1) == AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD
            return layoutPlan(kind, width, height, context.resources.configuration.fontScale, keyguard)
        }
    }
}

class ProgramsWidget : WidgetProvider(WidgetKind.PROGRAM)

class CalendarWidget : WidgetProvider(WidgetKind.CALENDAR)

class CampTodayWidget : WidgetProvider(WidgetKind.CAMP_TODAY)

class TasksWidget : WidgetProvider(WidgetKind.TASKS)

class EmergencyWidget : WidgetProvider(WidgetKind.EMERGENCY)

class ChatWidget : WidgetProvider(WidgetKind.CHAT)
