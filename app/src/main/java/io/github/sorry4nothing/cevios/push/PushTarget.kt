package io.github.sorry4nothing.cevios.push

/** Wohin ein Push führt: in einen Chat, in ein Programm oder zu einem Objekt im Lager. */
sealed interface PushTarget {
    val id: String

    data class Chat(override val id: String) : PushTarget

    data class Program(override val id: String) : PushTarget

    /** Der Entscheid zu einem eigenen Terminvorschlag (termin_entscheid); die ID ist die des Vorschlags. */
    data class Proposal(override val id: String) : PushTarget

    /** Der Entscheid zu einer eigenen Raumanfrage (raum_entscheid); die ID ist die der Anfrage. */
    data class RoomDecision(override val id: String) : PushTarget

    /** Ein Objekt im Lager (`id`); in welchem Lager es liegt, löst `GET /api/v1/lager/aufloesen/{typ}/{id}` auf. */
    data class Camp(val type: String, override val id: String) : PushTarget

    /** Eine Seite, die ein Widget öffnet (Liste der Programme, Chatliste, Lager-Seiten); `campId` nur, wenn das Widget das Lager kennt. */
    data class Area(val area: WidgetArea, val campId: String?) : PushTarget {
        override val id: String get() = campId.orEmpty()
    }

    companion object {
        /** Die `typ`-Werte der Lager-Pushes (`LagerPushTyp` im Vertrag). */
        val CAMP_TYPES = setOf("lager_aufgabe", "lager_erwaehnung", "lager_pinnwand", "lager_eingang", "lager_einteilung", "lager_anwesenheit")

        private val ID = Regex("^[0-9a-fA-F-]{36}$")

        /**
         * Aus den Daten des Pushes (`typ`, `id`). Der Inhalt kommt von aussen: Eine Kennung, die keine ist, führt nirgends
         * hin (sie würde sonst im Pfad einer Anfrage landen).
         */
        fun from(type: String?, id: String?): PushTarget? {
            WidgetArea.entries.firstOrNull { it.wire == type }?.let { return Area(it, id?.takeIf(ID::matches)) }
            if (id == null || !ID.matches(id)) return null
            return when (type) {
                "chat" -> Chat(id)
                "programm" -> Program(id)
                "termin_entscheid" -> Proposal(id)
                "raum_entscheid" -> RoomDecision(id)
                in CAMP_TYPES -> Camp(type!!, id)
                else -> null
            }
        }
    }
}

/** Die Seiten, die nur Widgets öffnen (`typ` im Intent); sie tragen höchstens die Kennung des Lagers. */
enum class WidgetArea(val wire: String) {
    PROGRAMS("widget_programme"),
    CHAT("widget_chat"),
    CAMP_TODAY("widget_lager_heute"),
    CAMP_TASKS("widget_lager_aufgaben"),
    CAMP_EMERGENCY("widget_lager_notfall"),
}
