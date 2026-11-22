package io.github.sorry4nothing.cevios.images

import io.github.sorry4nothing.cevios.programs.AgeGroupList
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class StageLogosTest {
    @Test
    fun onlyStagesWithALogoGetAPath() {
        val json = """
            {"stufen":[
              {"id":"a","name":"Stufe A","kuerzel":"A","logoUrl":"/api/v1/stufen/a/logo?v=1"},
              {"id":"b","name":"Stufe B","kuerzel":"B","logoUrl":null},
              {"id":"c","name":"Stufe C","kuerzel":"C"}
            ]}
        """.trimIndent()
        val list = Json { ignoreUnknownKeys = true }.decodeFromString<AgeGroupList>(json)
        assertEquals(mapOf("a" to "/api/v1/stufen/a/logo?v=1"), stageLogoPaths(list))
    }

    @Test
    fun noStagesOrNoLogoGiveAnEmptyMapSoEveryStageKeepsTheDefaultLogo() {
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(emptyMap<String, String>(), stageLogoPaths(json.decodeFromString("""{"stufen":[]}""")))
        assertEquals(emptyMap<String, String>(), stageLogoPaths(json.decodeFromString("""{"stufen":[{"id":"a","name":"A","logoUrl":null}]}""")))
    }
}
