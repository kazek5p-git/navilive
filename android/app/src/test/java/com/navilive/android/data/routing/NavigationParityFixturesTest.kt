package com.navilive.android.data.routing

import org.json.JSONArray
import org.json.JSONObject
import com.navilive.android.model.RouteStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class NavigationParityFixturesTest {

    @Test
    fun addressFormattingMatchesSharedFixtures() {
        val fixtures = loadFixtures().getJSONArray("addressCases")
        for (index in 0 until fixtures.length()) {
            val entry = fixtures.getJSONObject(index)
            val actual = AddressFormattingCore.formatAddress(
                address = entry.optJSONObject("address")?.toStringMap(),
                fallback = entry.getString("fallback"),
            )
            assertEquals(entry.getString("name"), entry.getString("expected"), actual)
        }
    }

    @Test
    fun instructionDescriptorsMatchSharedFixtures() {
        val fixtures = loadFixtures().getJSONArray("instructionCases")
        for (index in 0 until fixtures.length()) {
            val entry = fixtures.getJSONObject(index)
            val descriptor = NavigationInstructionCore.describe(
                maneuverType = entry.getString("maneuverType"),
                modifier = entry.optStringOrNull("modifier"),
                roadName = entry.optStringOrNull("roadName"),
            )
            assertEquals(entry.getString("name"), entry.getString("expectedParity"), descriptor.paritySignature())
        }
    }


    @Test
    fun routeStepSimplificationKeepsSameRoadTurnManeuvers() {
        val previous = RouteStep(
            instruction = "Skręć w lewo w Warmińską",
            distanceMeters = 90,
            maneuverType = "turn",
            maneuverModifier = "left",
            roadName = "Warmińska",
        )
        val nextTurn = RouteStep(
            instruction = "Skręć w prawo w Warmińską",
            distanceMeters = 120,
            maneuverType = "turn",
            maneuverModifier = "right",
            roadName = "Warmińska",
        )

        assertFalse(
            RouteStepSimplificationCore.shouldSuppressRouteStep(
                step = nextTurn,
                previous = previous,
                index = 2,
                lastIndex = 4,
            ),
        )
    }

    @Test
    fun routeStepSimplificationStillMergesShortSameRoadContinuations() {
        val previous = RouteStep(
            instruction = "Idź Warmińską",
            distanceMeters = 90,
            maneuverType = "turn",
            maneuverModifier = "left",
            roadName = "Warmińska",
        )
        val shortContinuation = RouteStep(
            instruction = "Kontynuuj Warmińską",
            distanceMeters = 20,
            maneuverType = "continue",
            maneuverModifier = "straight",
            roadName = "Warmińska",
        )

        assertTrue(
            RouteStepSimplificationCore.shouldSuppressRouteStep(
                step = shortContinuation,
                previous = previous,
                index = 2,
                lastIndex = 4,
            ),
        )
    }
    private fun loadFixtures(): JSONObject {
        val fixturePath = locateFixtureFile()
        return JSONObject(String(Files.readAllBytes(fixturePath)))
    }

    private fun locateFixtureFile(): Path {
        var current = Path.of("").toAbsolutePath()
        while (current != null) {
            val candidate = current.resolve("shared").resolve("test-fixtures").resolve("navigation-parity-fixtures.json")
            if (Files.exists(candidate)) {
                return candidate
            }
            current = current.parent
        }
        error("Could not locate shared fixture file from ${Path.of("").toAbsolutePath().absolutePathString()}")
    }

    private fun JSONObject.toStringMap(): Map<String, String> {
        val mapped = linkedMapOf<String, String>()
        val iterator = keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            mapped[key] = optString(key)
        }
        return mapped
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        return when {
            isNull(key) -> null
            else -> optString(key).takeIf { it.isNotBlank() }
        }
    }
}
