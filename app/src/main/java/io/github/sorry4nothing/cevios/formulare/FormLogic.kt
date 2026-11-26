package io.github.sorry4nothing.cevios.formulare

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Was eine Person in ein Feld eingegeben hat. Nur im Arbeitsspeicher, nie in einem Log, nie auf dem Gerät. */
sealed interface FormValue {
    /** Text, Zahl (als Eingabe), Datum (`JJJJ-MM-TT`), E-Mail, Telefon und die gewählte Option bei Auswahl und Optionsfeldern. */
    data class Text(val text: String) : FormValue {
        override fun toString() = "Text"
    }

    /** Ja oder Nein und Zustimmung. */
    data class Flag(val on: Boolean) : FormValue {
        override fun toString() = "Flag"
    }

    /** Mehrfachwahl. */
    data class Multi(val chosen: Set<String>) : FormValue {
        override fun toString() = "Multi"
    }
}

/** Was an einem Feld nicht stimmt; die Codes des Servers (`fehler[].code`) und die eigene Vorprüfung sprechen dieselbe Sprache. */
enum class FormProblem {
    REQUIRED, FORMAT, TOO_LONG, TOO_SHORT, RANGE, OPTION, COUNT, CONTROL, UNKNOWN;

    companion object {
        fun fromCode(code: String): FormProblem = when (code) {
            "PFLICHT" -> REQUIRED
            "TYP", "FORMAT" -> FORMAT
            "ZU_LANG" -> TOO_LONG
            "ZU_KURZ" -> TOO_SHORT
            "BEREICH" -> RANGE
            "OPTION" -> OPTION
            "ANZAHL" -> COUNT
            "STEUERZEICHEN" -> CONTROL
            else -> UNKNOWN
        }
    }
}

/** Die Feldtypen, die die App ausfüllen lässt. Alle elf des Vertrags; ein anderer Typ wird nie erfunden. */
val FORM_TYPES = setOf("text", "textbereich", "zahl", "datum", "email", "telefon", "auswahl", "optionsfelder", "mehrfachwahl", "janein", "zustimmung")

/** Der Server lehnt Antworten unter 2000 ms seit dem Laden ab (`ZU_SCHNELL`). */
const val FORM_MIN_DURATION_MS = 2000L

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

object FormRules {
    /** Felder, die die App nicht kennt: Das Formular lässt sich dann hier nicht senden. */
    fun unsupported(form: FormDefinition): List<FormField> = form.fields.filter { it.type !in FORM_TYPES }

    /** Das Senden gibt es nur, wenn das Formular offen ist, noch zu beantworten ist und die App alle Felder kennt. */
    fun canSend(form: FormDefinition): Boolean = !form.closed && !(form.onePerPerson && form.answered) && unsupported(form).isEmpty()

    /** Zahl aus der Eingabe; Komma und Punkt gelten, Leerraum nicht. `null`, wenn es keine Zahl ist. */
    fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    /** Wie lange die App vor dem Senden noch wartet, damit die gemessene Zeit seit dem Laden nicht unter dem Minimum liegt. */
    fun waitBeforeSend(elapsedMs: Long): Long = (FORM_MIN_DURATION_MS - elapsedMs).coerceAtLeast(0)

    /** Die Vorprüfung jedes Feldes; der Server prüft dasselbe noch einmal. Leer heisst: nichts zu beanstanden. */
    fun problems(form: FormDefinition, values: Map<String, FormValue>): Map<String, FormProblem> =
        form.fields.mapNotNull { field -> problem(field, values[field.id])?.let { field.id to it } }.toMap()

    private fun problem(field: FormField, value: FormValue?): FormProblem? = when (field.type) {
        "text", "textbereich" -> {
            val text = (value as? FormValue.Text)?.text?.trim().orEmpty()
            when {
                text.isEmpty() -> if (field.required) FormProblem.REQUIRED else null
                text.any { it.isISOControl() && !(field.type == "textbereich" && (it == '\n' || it == '\r' || it == '\t')) } -> FormProblem.CONTROL
                field.minLength != null && text.length < field.minLength -> FormProblem.TOO_SHORT
                field.maxLength != null && text.length > field.maxLength -> FormProblem.TOO_LONG
                else -> null
            }
        }
        "zahl" -> {
            val text = (value as? FormValue.Text)?.text?.trim().orEmpty()
            val number = parseNumber(text)
            when {
                text.isEmpty() -> if (field.required) FormProblem.REQUIRED else null
                number == null -> FormProblem.FORMAT
                field.wholeNumber && number != Math.floor(number) -> FormProblem.FORMAT
                field.min != null && number < field.min -> FormProblem.RANGE
                field.max != null && number > field.max -> FormProblem.RANGE
                else -> null
            }
        }
        "datum" -> {
            val text = (value as? FormValue.Text)?.text.orEmpty()
            when {
                text.isEmpty() -> if (field.required) FormProblem.REQUIRED else null
                field.from != null && text < field.from -> FormProblem.RANGE
                field.until != null && text > field.until -> FormProblem.RANGE
                else -> null
            }
        }
        "email" -> {
            val text = (value as? FormValue.Text)?.text?.trim().orEmpty()
            when {
                text.isEmpty() -> if (field.required) FormProblem.REQUIRED else null
                !EMAIL.matches(text) -> FormProblem.FORMAT
                else -> null
            }
        }
        // Das Format einer Nummer prüft der Server (Länder, Leerzeichen); hier zählt nur, ob etwas da ist.
        "telefon" -> if ((value as? FormValue.Text)?.text?.isBlank() != false && field.required) FormProblem.REQUIRED else null
        "auswahl", "optionsfelder" -> {
            val text = (value as? FormValue.Text)?.text.orEmpty()
            when {
                text.isEmpty() -> if (field.required) FormProblem.REQUIRED else null
                text !in field.options -> FormProblem.OPTION
                else -> null
            }
        }
        "mehrfachwahl" -> {
            val chosen = (value as? FormValue.Multi)?.chosen.orEmpty()
            val least = maxOf(field.minChoices ?: 0, if (field.required) 1 else 0)
            when {
                chosen.isEmpty() && least == 0 -> null
                chosen.isEmpty() -> FormProblem.REQUIRED
                !field.options.containsAll(chosen) -> FormProblem.OPTION
                chosen.size < least || (field.maxChoices != null && chosen.size > field.maxChoices) -> FormProblem.COUNT
                else -> null
            }
        }
        "janein" -> if (field.required && value !is FormValue.Flag) FormProblem.REQUIRED else null
        // Bei einer Zustimmung ist `pflicht` immer wahr: ohne Haken geht es nicht weiter.
        "zustimmung" -> if ((value as? FormValue.Flag)?.on != true) FormProblem.REQUIRED else null
        else -> null
    }

    /** Die Antworten für den Versand: Leere freiwillige Felder fehlen, Texte sind getrimmt, Zahlen sind Zahlen. Nur gültige Eingaben (nach [problems]). */
    fun body(form: FormDefinition, values: Map<String, FormValue>): Map<String, JsonElement> {
        val out = LinkedHashMap<String, JsonElement>()
        for (field in form.fields) {
            val value = values[field.id] ?: continue
            when (field.type) {
                "text", "textbereich", "email" -> (value as? FormValue.Text)?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { out[field.id] = JsonPrimitive(it) }
                "telefon" -> (value as? FormValue.Text)?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { out[field.id] = JsonPrimitive(it) }
                "datum", "auswahl", "optionsfelder" -> (value as? FormValue.Text)?.text?.takeIf { it.isNotEmpty() }?.let { out[field.id] = JsonPrimitive(it) }
                "zahl" -> (value as? FormValue.Text)?.text?.let { parseNumber(it) }?.let { number ->
                    out[field.id] = if (field.wholeNumber) JsonPrimitive(number.toLong()) else JsonPrimitive(number)
                }
                "mehrfachwahl" -> (value as? FormValue.Multi)?.chosen?.takeIf { it.isNotEmpty() }?.let { chosen ->
                    out[field.id] = JsonArray(field.options.filter { it in chosen }.map { JsonPrimitive(it) })
                }
                "janein", "zustimmung" -> (value as? FormValue.Flag)?.let { out[field.id] = JsonPrimitive(it.on) }
            }
        }
        return out
    }

    /** Wie viele offene Formulare es gibt (noch nicht beantwortet); für die Zeile im Profil. */
    fun unanswered(forms: List<FormSummary>): Int = forms.count { !it.answered }
}
