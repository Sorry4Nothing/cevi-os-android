package io.github.sorry4nothing.cevios.lager

import io.github.sorry4nothing.cevios.net.ResponseCache

/** Wen die Abzählliste zeigt: alle Teilnehmenden, die Leitenden und das Team, eine Stufe oder eine Gruppe. */
sealed interface CountSource {
    data object Participants : CountSource
    data object Team : CountSource
    data class Stage(val name: String) : CountSource
    data class Group(val id: String) : CountSource
}

/** Die gewählten Quellen; mehrere zugleich. Mindestens eine bleibt gewählt (eine leere Liste hätte nichts zu zählen). */
data class CountSelection(val sources: Set<CountSource> = setOf(CountSource.Participants)) {
    fun has(source: CountSource) = source in sources

    /** Wählt die Quelle ab oder an; die letzte gewählte lässt sich nicht abwählen. */
    fun toggled(source: CountSource): CountSelection =
        if (source in sources) (if (sources.size > 1) CountSelection(sources - source) else this) else CountSelection(sources + source)
}

/** Eine Abfrage der Liste beim Server (`art`, `stufe`, `gruppe`). */
data class CountQuery(val team: Boolean, val stage: String? = null, val groupId: String? = null)

/**
 * Die Abzählliste lebt auf dem eigenen Gerät: Wer in der Liste steht, sagt der Server (nur Lesen), wer abgehakt ist, steht nur hier.
 * Gespeichert werden ausschliesslich IDs der Personen, nie Namen; sie liegen verschlüsselt im Ordner des Lagers ([CountStore]).
 */
object CountRules {
    /** Welche Listen der Server liefern muss; was eine breitere Quelle schon abdeckt, fragt die App nicht noch einmal. */
    fun queries(selection: CountSelection): List<CountQuery> {
        val all = CountSource.Participants in selection.sources
        val team = CountSource.Team in selection.sources
        return buildList {
            if (all) add(CountQuery(team = false))
            if (team) add(CountQuery(team = true))
            selection.sources.forEach { source ->
                when (source) {
                    is CountSource.Stage -> if (!all) add(CountQuery(team = false, stage = source.name))
                    // Eine Gruppe sind ihre Teilnehmenden und ihre Leitenden.
                    is CountSource.Group -> {
                        if (!all) add(CountQuery(team = false, groupId = source.id))
                        if (!team) add(CountQuery(team = true, groupId = source.id))
                    }
                    else -> Unit
                }
            }
        }.distinct()
    }

    /** Die Einträge mehrerer Abfragen als eine Liste: Wer in zwei Quellen steht, steht nur einmal da. */
    fun merge(pages: List<CountPage>): List<CountEntry> = pages.flatMap { it.entries }.distinctBy { it.id }

    /** Die Liste mit den eigenen Haken; «x von y» zählt über die ganze Liste. Der Haken des Servers gilt hier nie. */
    fun page(entries: List<CountEntry>, checked: Set<String>): CountPage {
        val marked = entries.map { it.copy(checked = it.id in checked, checkedAt = null) }
        return CountPage(marked, null, marked.count { it.checked }, marked.size)
    }

    fun toggle(checked: Set<String>, id: String): Set<String> = if (id in checked) checked - id else checked + id

    /** Nur IDs, eine je Zeile. */
    fun encode(checked: Set<String>): String = checked.sorted().joinToString("\n")

    /** Liest nur, was eine ID ist; alles andere (kaputt, fremd) fällt weg. */
    fun decode(text: String): Set<String> = text.lineSequence().map { it.trim() }.filter { ID.matches(it) }.toSet()

    private val ID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
}

private const val COUNT_KEY = "abzaehlen"

/**
 * Die eigenen Haken der Abzählliste je Lager. Ohne Zwischenspeicher (`null`: Widgets, Tests) bleibt es beim Arbeitsspeicher. Die Löschwege
 * sind die des Zwischenspeichers: Das Lager ist nicht mehr in der Liste, abgeschlossen, 404 oder 403, Abmelden, Sitzungsende, nach 30 Tagen.
 */
class CountStore(private val cache: ResponseCache?) {
    fun load(campId: String): Set<String> = cache?.loadLocal(campId, COUNT_KEY)?.let(CountRules::decode).orEmpty()

    fun save(campId: String, checked: Set<String>) {
        cache?.storeLocal(campId, COUNT_KEY, CountRules.encode(checked))
    }
}
