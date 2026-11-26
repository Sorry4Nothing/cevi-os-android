package io.github.sorry4nothing.cevios.formulare

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Ein offenes Formular der Website in der Liste (`GET /api/v1/formulare`). */
@Serializable
data class FormSummary(
    val id: String,
    @SerialName("titel") val title: String? = null,
    @SerialName("seitenTitel") val pageTitle: String,
    /** Letzter Tag für Antworten (`JJJJ-MM-TT`), `null` ohne Frist. */
    @SerialName("schliesstAm") val closesOn: String? = null,
    @SerialName("beantwortet") val answered: Boolean = false,
)

@Serializable
data class FormList(@SerialName("formulare") val forms: List<FormSummary> = emptyList())

/**
 * Ein Feld der Definition. Der Typ bleibt ein Text: Kennt die App ihn nicht, zeigt sie das Feld als «geht in der App nicht» und bietet
 * das Senden nicht an (nichts erfinden). Grenzen gelten nur beim passenden Typ.
 */
@Serializable
data class FormField(
    val id: String,
    @SerialName("typ") val type: String,
    @SerialName("beschriftung") val label: String,
    @SerialName("hilfe") val help: String? = null,
    @SerialName("pflicht") val required: Boolean = false,
    @SerialName("optionen") val options: List<String> = emptyList(),
    @SerialName("minLaenge") val minLength: Int? = null,
    @SerialName("maxLaenge") val maxLength: Int? = null,
    val min: Double? = null,
    val max: Double? = null,
    @SerialName("ganzzahl") val wholeNumber: Boolean = false,
    @SerialName("von") val from: String? = null,
    @SerialName("bis") val until: String? = null,
    @SerialName("minAuswahl") val minChoices: Int? = null,
    @SerialName("maxAuswahl") val maxChoices: Int? = null,
)

/** Die veröffentlichte Definition (`GET /api/v1/formulare/{id}`). */
@Serializable
data class FormDefinition(
    val id: String,
    @SerialName("titel") val title: String? = null,
    @SerialName("einleitung") val intro: String? = null,
    @SerialName("felder") val fields: List<FormField> = emptyList(),
    /** Wozu die Angaben dienen und wer die Antworten sieht; steht vor dem Senden. */
    @SerialName("datenschutz") val privacy: String = "",
    @SerialName("absenden") val submitLabel: String = "",
    /** Bestätigung nach dem Senden. */
    @SerialName("danke") val thanks: String = "",
    @SerialName("schliesstAm") val closesOn: String? = null,
    @SerialName("geschlossen") val closed: Boolean = false,
    @SerialName("eineProPerson") val onePerPerson: Boolean = false,
    @SerialName("beantwortet") val answered: Boolean = false,
)

/** Der Body beim Senden. `homepage` ist die Falle gegen Skripte und bleibt leer; `dauerMs` ist die gemessene Zeit seit dem Laden. */
@Serializable
data class FormAnswerRequest(
    @SerialName("antworten") val answers: Map<String, JsonElement>,
    // Ohne Standardwert, damit das Feld immer mitgeschickt wird (der Server verlangt es, leer).
    val homepage: String,
    @SerialName("dauerMs") val durationMs: Long,
) {
    // Die Antworten sind Angaben von Personen (auch von Kindern): Sie stehen in keinem Log.
    override fun toString() = "FormAnswerRequest(${answers.size} Felder)"
}

@Serializable
data class FormSent(val ok: Boolean = true)
