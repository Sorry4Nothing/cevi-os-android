package io.github.sorry4nothing.cevios.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import io.github.sorry4nothing.cevios.programs.SWISS_ZONE
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Die Karten der Widget-Auswahl (Entwurf WK1 bis WK6): ab Android 15 zeichnet die App die Vorschau selbst, mit denselben Bausteinen
 * wie das Widget und erfundenen Beispieldaten (nie echte Daten). Vorher zeigt die Auswahl den Namen und den Beschreibungssatz.
 * Das System begrenzt, wie oft eine App Vorschauen setzen darf; ein abgelehnter Aufruf schadet nicht.
 */
object WidgetPreviews {
    private val standAt = ZonedDateTime.of(2026, 11, 21, 18, 2, 0, 0, SWISS_ZONE)
    private val demoStand = Stand(Freshness.FRESH, standAt)

    private val demo: Map<Class<out AppWidgetProvider>, Pair<WidgetKind, WidgetContent>> = mapOf(
        ProgramsWidget::class.java to (
            WidgetKind.PROGRAM to WidgetContent.Programs(
                listOf(
                    ProgramRow(
                        "demo", "Waldspiel", LocalDate.of(2026, 11, 28), "14:00", "Pfadiheim Beispielwald, Eingang Nord", listOf("Wölfe"),
                        ProgramStatus(ProgramState.CHILDREN_OPEN, 1),
                    ),
                ),
            )
            ),
        CalendarWidget::class.java to (
            WidgetKind.CALENDAR to WidgetContent.Calendar(
                3,
                listOf(
                    CalendarRow("1", "Waldspiel", LocalDate.of(2026, 11, 28), "14:00", listOf("Wölfe")),
                    CalendarRow("2", "Materialhöck", LocalDate.of(2026, 12, 4), "19:30", listOf("Team Mat")),
                    CalendarRow("3", "Spielnachmittag", LocalDate.of(2026, 12, 5), "14:00", listOf("Wölfe", "Adler")),
                ),
            )
            ),
        CampTodayWidget::class.java to (
            WidgetKind.CAMP_TODAY to WidgetContent.CampToday("demo", 3, 8, DutyLine("Küche", "Frühstück", "07:00–10:00"), listOf("Abfahrt Ausflug 13:30"))
            ),
        TasksWidget::class.java to (
            WidgetKind.TASKS to WidgetContent.Tasks(
                "demo", 2, 7,
                listOf(TaskRow("1", "Offerte Lagerhaus einholen", LocalDate.of(2027, 3, 15), Urgency.OVERDUE)),
            )
            ),
        EmergencyWidget::class.java to (WidgetKind.EMERGENCY to WidgetContent.Emergency(null)),
        ChatWidget::class.java to (WidgetKind.CHAT to WidgetContent.Chat(3, 2)),
    )

    fun publish(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val manager = AppWidgetManager.getInstance(context)
        val renderer = WidgetRenderer(context)
        demo.forEach { (provider, entry) ->
            val (kind, content) = entry
            // Vorschau im Format 4×2 (Entwurf); der Notfall und der Chat sind höchstens 4×2 breit.
            val plan = layoutPlan(kind, 360, 196)
            runCatching {
                manager.setWidgetPreview(
                    ComponentName(context, provider),
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                    renderer.render(kind, WidgetScreen.Showing(content, demoStand), plan, widgetId = 0),
                )
            }
        }
    }
}
