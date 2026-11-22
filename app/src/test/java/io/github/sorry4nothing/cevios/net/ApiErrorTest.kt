package io.github.sorry4nothing.cevios.net

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiErrorTest {
    @Test
    fun everyErrorHasItsOwnText() {
        assertEquals(ApiError.entries.size, ApiError.entries.map { it.text }.toSet().size)
    }

    @Test
    fun unknownStatusIsUnknownError() {
        assertEquals(ApiError.UNKNOWN, ApiError.fromStatus(418))
    }

    @Test
    fun conflictCodeDecidesTheText() {
        assertEquals(ApiError.PROGRAM_STARTED, ApiError.from(409, "PROGRAMM_BEGONNEN"))
        assertEquals(ApiError.REGISTRATION_CLOSED, ApiError.from(409, "PROGRAMM_GESCHLOSSEN"))
        assertEquals(ApiError.REGISTRATION_CLOSED, ApiError.from(409, null))
    }

    @Test
    fun campCodes() {
        assertEquals(ApiError.LAGER_CLOSED, ApiError.from(409, "LAGER_GESCHLOSSEN"))
        assertEquals(ApiError.FEED_RELOAD, ApiError.from(410, "FEED_NEU_LADEN"))
        assertEquals(ApiError.SECOND_FACTOR_MISSING, ApiError.from(403, "ZWEITER_FAKTOR_FEHLT"))
        assertEquals(ApiError.FORBIDDEN, ApiError.from(403, "KEINE_BERECHTIGUNG"))
        assertEquals(ApiError.MAINTENANCE, ApiError.from(503, "IN_WARTUNG"))
        assertEquals(ApiError.UNKNOWN, ApiError.from(503, null))
    }
}
