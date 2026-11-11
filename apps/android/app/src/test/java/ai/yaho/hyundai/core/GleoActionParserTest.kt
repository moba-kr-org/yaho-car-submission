package ai.yaho.hyundai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GleoActionParserTest {
    @Test
    fun parsesReportFieldsAndIgnoresUnrecognizedInput() {
        val result = GleoActionParser.parse(
            "REPORT_ROAD_EVENT",
            """{"kind":"road_hazard","accessToken":"must-not-be-used"}""",
        )

        assertTrue(result is GleoParseResult.Valid)
        val command = (result as GleoParseResult.Valid).command as GleoCommand.ReportRoadEvent
        assertEquals("road_hazard", command.kind)
    }

    @Test
    fun acceptsJsonStringWrappedParameters() {
        val result = GleoActionParser.parse(
            "REQUEST_SERVICE_BOOKING",
            "\"{\\\"serviceType\\\":\\\"타이어 점검\\\",\\\"requestedDate\\\":\\\"2026-11-05T09:00:00+09:00\\\"}\"",
        )
        assertTrue(result is GleoParseResult.Valid)
    }

    @Test
    fun reportsMissingRequiredFieldsForGleoAskingCallback() {
        val result = GleoActionParser.parse("REPORT_ROAD_EVENT", "{}")
        assertEquals(
            GleoParseResult.MissingFields("REPORT_ROAD_EVENT", listOf("kind")),
            result,
        )
    }

    @Test
    fun rejectsUnknownActionAndInvalidRoadEventCategory() {
        assertTrue(GleoActionParser.parse("UNLISTED_FUNCTION", "{}") is GleoParseResult.Invalid)
        assertTrue(
            GleoActionParser.parse(
                "REPORT_ROAD_EVENT",
                """{"kind":"remote_unlock"}""",
            ) is GleoParseResult.Invalid,
        )
    }

    @Test
    fun rejectsOversizedActionParameterAndUnapprovedScope() {
        assertTrue(
            GleoActionParser.parse("GET_DRIVING_CONTEXT", "x".repeat(4_097)) is GleoParseResult.Invalid,
        )
        assertTrue(
            GleoActionParser.parse(
                "GET_DRIVING_CONTEXT",
                """{"scope":"account"}""",
            ) is GleoParseResult.Invalid,
        )
    }
    @Test fun malformedOptionalScopeCannotSilentlyExpandToAll() {
        for(raw in listOf("{\"scope\":true}","{\"scope\":null}","{\"scope\":\"\"}","{\"scope\":{}}")) {
            assertTrue(GleoActionParser.parse("GET_DRIVING_CONTEXT",raw) is GleoParseResult.Invalid)
        }
    }
    @Test fun invalidOptionalNotesAreNotSilentlyDiscarded() {
        assertTrue(GleoActionParser.parse("REQUEST_SERVICE_BOOKING","""{"serviceType":"점검","requestedDate":"2026-11-06","notes":123}""") is GleoParseResult.Invalid)
    }

    @Test
    fun vehicleContextFreshnessUsesConsentServiceStateAndMonotonicReceiptTime() {
        val observations = listOf(
            GleoObservationClock(received = true, receivedElapsedMillis = 99_900L),
            GleoObservationClock(received = true, receivedElapsedMillis = 69_999L),
            GleoObservationClock(received = true, receivedElapsedMillis = 100_001L),
            GleoObservationClock(received = false, receivedElapsedMillis = 99_900L),
            GleoObservationClock(received = true, receivedElapsedMillis = null),
        )

        assertEquals(
            GleoFreshObservationSummary(count = 1, freshestAgeMillis = 100L),
            GleoObservationFreshnessPolicy.summarize(
                consented = true,
                serviceActive = true,
                nowElapsedMillis = 100_000L,
                observations = observations,
            ),
        )
        assertEquals(
            GleoFreshObservationSummary(count = 0, freshestAgeMillis = null),
            GleoObservationFreshnessPolicy.summarize(
                consented = false,
                serviceActive = true,
                nowElapsedMillis = 100_000L,
                observations = observations,
            ),
        )
        assertEquals(
            GleoFreshObservationSummary(count = 0, freshestAgeMillis = null),
            GleoObservationFreshnessPolicy.summarize(
                consented = true,
                serviceActive = false,
                nowElapsedMillis = 100_000L,
                observations = observations,
            ),
        )
    }

    @Test
    fun aPendingReviewCannotBeReplacedByAnotherGleoRequest() {
        val pending = GleoCommand.ReportRoadEvent("rain_report")
        val replacement = GleoCommand.ReportRoadEvent("accident_report")
        val secondFunction = GleoCommand.RequestServiceBooking("점검", "2026-11-06", null)

        assertEquals(
            GleoPendingReviewDecision.Start(replacement),
            GleoPendingReviewPolicy.decide(current = null, requested = replacement),
        )
        assertEquals(
            GleoPendingReviewDecision.KeepCurrent(pending),
            GleoPendingReviewPolicy.decide(current = pending, requested = replacement),
        )
        assertEquals(
            GleoPendingReviewDecision.KeepCurrent(pending),
            GleoPendingReviewPolicy.decide(current = pending, requested = secondFunction),
        )
    }

    @Test
    fun booleanDirectScopeIsRejectedInsteadOfDefaultingToAll() {
        assertEquals(
            GleoIntentParameterNormalization.Invalid("scope"),
            GleoIntentParameterPolicy.normalize(
                actionParamPresent = false,
                actionParam = null,
                directParameterExtras = mapOf("scope" to true),
            ),
        )
    }

    @Test
    fun presentActionParamMustBeANonblankString() {
        for (value in listOf(null, true, 42, " ")) {
            assertEquals(
                GleoIntentParameterNormalization.Invalid("ACTION_PARAM"),
                GleoIntentParameterPolicy.normalize(
                    actionParamPresent = true,
                    actionParam = value,
                    directParameterExtras = emptyMap(),
                ),
            )
        }
    }

    @Test
    fun validDirectScopeWithoutActionParamIsPreserved() {
        val normalized = GleoIntentParameterPolicy.normalize(
            actionParamPresent = false,
            actionParam = null,
            directParameterExtras = mapOf("scope" to "vehicle"),
        )
        assertEquals(
            GleoIntentParameterNormalization.Valid(null, mapOf("scope" to "vehicle")),
            normalized,
        )
        val parameters = normalized as GleoIntentParameterNormalization.Valid
        assertTrue(GleoActionParser.parse("GET_DRIVING_CONTEXT", parameters.actionParam, parameters.directParameters) is GleoParseResult.Valid)
    }
}
