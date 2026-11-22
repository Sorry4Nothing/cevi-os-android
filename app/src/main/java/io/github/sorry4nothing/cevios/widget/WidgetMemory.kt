package io.github.sorry4nothing.cevios.widget

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Der zuletzt gesehene Stand je Widget, **nur im Arbeitsspeicher**: Nach dem Prozess-Ende ist er weg, ein Widget ohne Netz zeigt dann
 * «Ohne Netz» ohne Daten. Es gibt keinen Zwischenspeicher auf dem Gerät (Entscheid des Nutzers steht aus). Er wird bei Abmeldung und
 * Sitzungsende gelöscht (`forgetWidgets`).
 */
object WidgetMemory {
    private val snapshots = ConcurrentHashMap<Int, Snapshot>()

    fun read(widgetId: Int): Snapshot? = snapshots[widgetId]

    fun write(widgetId: Int, snapshot: Snapshot?) {
        if (snapshot == null) snapshots.remove(widgetId) else snapshots[widgetId] = snapshot
    }

    private val screens = ConcurrentHashMap<Int, WidgetScreen>()

    /** Der zuletzt gezeigte Zustand: Beim Ändern der Grösse zeichnet das Widget ihn neu, ohne das Netz zu fragen. */
    fun screen(widgetId: Int): WidgetScreen? = screens[widgetId]

    fun writeScreen(widgetId: Int, screen: WidgetScreen?) {
        if (screen == null) screens.remove(widgetId) else screens[widgetId] = screen
    }

    fun clear() {
        snapshots.clear()
        screens.clear()
    }
}

/**
 * Die Wahl, die die Konfiguration trifft: eine Stufe für W1, ein Lager für W3 (je nur eine Kennung, keine Namen und keine Inhalte).
 * Sie bleibt über das Prozess-Ende hinaus, sonst vergässe das Widget sie; sie fällt mit dem Widget und bei der Abmeldung weg.
 */
class WidgetChoice(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(widgetId: Int): String? = prefs.getString(key(widgetId), null)

    fun write(widgetId: Int, id: String) = prefs.edit().putString(key(widgetId), id).apply()

    fun remove(widgetId: Int) = prefs.edit().remove(key(widgetId)).apply()

    fun clear() = prefs.edit().clear().apply()

    private fun key(widgetId: Int) = "w$widgetId"

    private companion object {
        const val FILE = "widget_choice"
    }
}

/** Abmelden oder Sitzungsende: Stand und Wahl der Widgets verschwinden, die Widgets zeigen «Zum Anzeigen in der App anmelden». */
fun forgetWidgets(context: Context) {
    WidgetMemory.clear()
    WidgetChoice(context).clear()
    WidgetProvider.refreshAll(context)
}
