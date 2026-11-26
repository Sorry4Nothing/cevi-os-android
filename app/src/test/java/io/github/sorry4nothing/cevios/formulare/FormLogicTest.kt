package io.github.sorry4nothing.cevios.formulare

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

class FormLogicTest {
    @get:Rule val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    private fun field(id: String, type: String, required: Boolean = false, options: List<String> = emptyList(), build: (FormField) -> FormField = { it }) =
        build(FormField(id, type, "Feld $id", required = required, options = options))

    private fun form(vararg fields: FormField, closed: Boolean = false, onePerPerson: Boolean = false, answered: Boolean = false) =
        FormDefinition("f", "Titel", null, fields.toList(), "Datenschutz", "Senden", "Danke", null, closed, onePerPerson, answered)

    private fun problems(form: FormDefinition, vararg values: Pair<String, FormValue>) = FormRules.problems(form, values.toMap())

    @Test
    fun allElevenTypesOfTheContractAreSupported() {
        assertEquals(11, FORM_TYPES.size)
        val all = form(*FORM_TYPES.map { field(it, it) }.toTypedArray())
        assertTrue(FormRules.unsupported(all).isEmpty())
        assertEquals(listOf("neu"), FormRules.unsupported(form(field("a", "text"), field("b", "neu"))).map { it.type })
    }

    @Test
    fun requiredTextAndOptionalTextBehaveDifferently() {
        val f = form(field("a", "text", required = true), field("b", "text"))
        assertEquals(mapOf("a" to FormProblem.REQUIRED), problems(f, "a" to FormValue.Text("  "), "b" to FormValue.Text("")))
        assertTrue(problems(f, "a" to FormValue.Text("Hallo")).isEmpty())
    }

    @Test
    fun textLengthAndControlCharactersAreChecked() {
        val f = form(field("a", "text") { it.copy(minLength = 3, maxLength = 5) }, field("b", "textbereich"))
        assertEquals(FormProblem.TOO_SHORT, problems(f, "a" to FormValue.Text("ab"))["a"])
        assertEquals(FormProblem.TOO_LONG, problems(f, "a" to FormValue.Text("abcdef"))["a"])
        assertNull(problems(f, "a" to FormValue.Text("abcd"))["a"])
        assertEquals(FormProblem.CONTROL, problems(f, "a" to FormValue.Text("ab\u0007cd"))["a"])
        // Eine Textfläche darf Zeilen umbrechen, ein einzeiliges Feld nicht.
        assertNull(problems(f, "b" to FormValue.Text("eins\nzwei"))["b"])
        assertEquals(FormProblem.CONTROL, problems(f, "a" to FormValue.Text("eins\nzwei"))["a"])
    }

    @Test
    fun numbersAcceptACommaAndKeepTheirBounds() {
        val f = form(field("n", "zahl") { it.copy(min = 1.0, max = 10.0, wholeNumber = true) })
        assertEquals(FormProblem.FORMAT, problems(f, "n" to FormValue.Text("abc"))["n"])
        assertEquals(FormProblem.FORMAT, problems(f, "n" to FormValue.Text("2,5"))["n"])
        assertEquals(FormProblem.RANGE, problems(f, "n" to FormValue.Text("0"))["n"])
        assertEquals(FormProblem.RANGE, problems(f, "n" to FormValue.Text("11"))["n"])
        assertTrue(problems(f, "n" to FormValue.Text("7")).isEmpty())
        assertEquals(2.5, FormRules.parseNumber(" 2,5 ")!!, 0.0)
        assertNull(FormRules.parseNumber("NaN"))
    }

    @Test
    fun datesRespectTheirRange() {
        val f = form(field("d", "datum", required = true) { it.copy(from = "2027-01-01", until = "2027-12-31") })
        assertEquals(FormProblem.REQUIRED, problems(f)["d"])
        assertEquals(FormProblem.RANGE, problems(f, "d" to FormValue.Text("2026-12-31"))["d"])
        assertEquals(FormProblem.RANGE, problems(f, "d" to FormValue.Text("2028-01-01"))["d"])
        assertTrue(problems(f, "d" to FormValue.Text("2027-06-15")).isEmpty())
    }

    @Test
    fun emailNeedsTheShapeOfAnAddressAndPhoneOnlyNeedsContentWhenRequired() {
        val f = form(field("e", "email"), field("t", "telefon", required = true))
        assertEquals(FormProblem.FORMAT, problems(f, "e" to FormValue.Text("keine adresse"), "t" to FormValue.Text("079"))["e"])
        assertTrue(problems(f, "e" to FormValue.Text("name@beispiel.invalid"), "t" to FormValue.Text("079 000 00 00")).isEmpty())
        assertEquals(FormProblem.REQUIRED, problems(f)["t"])
    }

    @Test
    fun choicesMustBeKnownOptions() {
        val f = form(field("a", "auswahl", required = true, options = listOf("rot", "blau")), field("o", "optionsfelder", options = listOf("x")))
        assertEquals(FormProblem.REQUIRED, problems(f)["a"])
        assertEquals(FormProblem.OPTION, problems(f, "a" to FormValue.Text("grün"))["a"])
        assertTrue(problems(f, "a" to FormValue.Text("rot")).isEmpty())
    }

    @Test
    fun multipleChoiceCountsTheAnswers() {
        val f = form(field("m", "mehrfachwahl", required = true, options = listOf("a", "b", "c")) { it.copy(minChoices = 1, maxChoices = 2) })
        assertEquals(FormProblem.REQUIRED, problems(f)["m"])
        assertEquals(FormProblem.COUNT, problems(f, "m" to FormValue.Multi(setOf("a", "b", "c")))["m"])
        assertEquals(FormProblem.OPTION, problems(f, "m" to FormValue.Multi(setOf("z")))["m"])
        assertTrue(problems(f, "m" to FormValue.Multi(setOf("a", "c"))).isEmpty())
    }

    @Test
    fun consentIsAlwaysRequiredAndYesNoOnlyWhenMarked() {
        val f = form(field("z", "zustimmung"), field("j", "janein", required = true), field("k", "janein"))
        assertEquals(setOf("z", "j"), problems(f).keys)
        assertEquals(FormProblem.REQUIRED, problems(f, "z" to FormValue.Flag(false), "j" to FormValue.Flag(false))["z"])
        assertTrue(problems(f, "z" to FormValue.Flag(true), "j" to FormValue.Flag(false)).isEmpty())
    }

    @Test
    fun theBodyHoldsTypedAnswersAndOmitsEmptyOptionalFields() {
        val f = form(
            field("a", "text"), field("b", "text"), field("n", "zahl") { it.copy(wholeNumber = true) }, field("x", "zahl"), field("m", "mehrfachwahl", options = listOf("a", "b", "c")),
            field("j", "janein"), field("z", "zustimmung"), field("d", "datum"),
        )
        val body = FormRules.body(
            f,
            mapOf(
                "a" to FormValue.Text("  Hallo "), "b" to FormValue.Text(""), "n" to FormValue.Text("7"), "x" to FormValue.Text("2,5"),
                "m" to FormValue.Multi(setOf("c", "a")), "j" to FormValue.Flag(false), "z" to FormValue.Flag(true), "d" to FormValue.Text("2027-06-15"),
            ),
        )
        assertEquals(setOf("a", "n", "x", "m", "j", "z", "d"), body.keys)
        assertEquals(JsonPrimitive("Hallo"), body["a"])
        assertEquals(JsonPrimitive(7L), body["n"])
        assertEquals(JsonPrimitive(2.5), body["x"])
        assertEquals(JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("c"))), body["m"])
        assertEquals(JsonPrimitive(false), body["j"])
        assertEquals(JsonPrimitive("2027-06-15"), body["d"])
    }

    @Test
    fun sendingIsOfferedOnlyForOpenUnansweredFormsTheAppFullyKnows() {
        assertTrue(FormRules.canSend(form(field("a", "text"))))
        assertFalse(FormRules.canSend(form(field("a", "text"), closed = true)))
        assertFalse(FormRules.canSend(form(field("a", "text"), onePerPerson = true, answered = true)))
        assertTrue(FormRules.canSend(form(field("a", "text"), onePerPerson = false, answered = true)))
        assertFalse(FormRules.canSend(form(field("a", "unbekannt"))))
    }

    @Test
    fun aQuickFormWaitsOutTheMinimumTimeInsteadOfInventingIt() {
        assertEquals(2000L, FormRules.waitBeforeSend(0))
        assertEquals(500L, FormRules.waitBeforeSend(1500))
        assertEquals(0L, FormRules.waitBeforeSend(2000))
        assertEquals(0L, FormRules.waitBeforeSend(9000))
    }

    @Test
    fun serverCodesMapToProblems() {
        assertEquals(FormProblem.REQUIRED, FormProblem.fromCode("PFLICHT"))
        assertEquals(FormProblem.FORMAT, FormProblem.fromCode("TYP"))
        assertEquals(FormProblem.FORMAT, FormProblem.fromCode("FORMAT"))
        assertEquals(FormProblem.TOO_LONG, FormProblem.fromCode("ZU_LANG"))
        assertEquals(FormProblem.TOO_SHORT, FormProblem.fromCode("ZU_KURZ"))
        assertEquals(FormProblem.RANGE, FormProblem.fromCode("BEREICH"))
        assertEquals(FormProblem.OPTION, FormProblem.fromCode("OPTION"))
        assertEquals(FormProblem.COUNT, FormProblem.fromCode("ANZAHL"))
        assertEquals(FormProblem.CONTROL, FormProblem.fromCode("STEUERZEICHEN"))
        assertEquals(FormProblem.UNKNOWN, FormProblem.fromCode("UNBEKANNT"))
        assertEquals(FormProblem.UNKNOWN, FormProblem.fromCode("etwas Neues"))
    }

    @Test
    fun theValuesNeverPrintTheirContent() {
        assertFalse(FormValue.Text("Geheim").toString().contains("Geheim"))
        assertFalse(FormValue.Multi(setOf("Geheim")).toString().contains("Geheim"))
    }
}
