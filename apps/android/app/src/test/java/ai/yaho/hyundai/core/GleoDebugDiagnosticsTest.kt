package ai.yaho.hyundai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GleoDebugDiagnosticsTest {
    @Test
    fun recordsOnlyAllowlistedCallbackMetadataWithUnknownOrigin() {
        val diagnostics = GleoDebugDiagnostics(
            enabled = true,
            traceIdFactory = { "00000000-0000-4000-8000-000000000001" },
        )

        val token = diagnostics.begin("GET_DRIVING_CONTEXT", 100L)
        val record = diagnostics.complete(token!!, "success", 157L)!!

        assertEquals("00000000-0000-4000-8000-000000000001", record.traceId)
        assertEquals("GET_DRIVING_CONTEXT", record.action)
        assertEquals("success", record.status)
        assertEquals(57L, record.elapsedMs)
        assertEquals(
            "traceId=00000000-0000-4000-8000-000000000001 action=GET_DRIVING_CONTEXT " +
                "status=success elapsedMs=57 origin=unknown",
            record.toLogLine(),
        )
        assertEquals(listOf(record), diagnostics.recentRecords())
    }

    @Test
    fun releaseModeDoesNotGenerateIdsOrKeepRecords() {
        var generatedIds = 0
        val diagnostics = GleoDebugDiagnostics(enabled = false, traceIdFactory = { generatedIds++; "unused" })

        assertNull(diagnostics.begin("GET_DRIVING_CONTEXT", 100L))
        assertTrue(diagnostics.recentRecords().isEmpty())
        assertEquals(0, generatedIds)
    }

    @Test
    fun rejectsUnknownActionsAndInvalidStartTimes() {
        val diagnostics = GleoDebugDiagnostics(enabled = true)

        assertNull(diagnostics.begin("UNKNOWN_ACTION", 100L))
        assertNull(diagnostics.begin("GET_DRIVING_CONTEXT", -1L))
        assertTrue(diagnostics.recentRecords().isEmpty())
    }

    @Test
    fun statusIsAllowlistedAndDuplicateCompletionIsIgnored() {
        val diagnostics = GleoDebugDiagnostics(
            enabled = true,
            traceIdFactory = { "00000000-0000-4000-8000-000000000002" },
        )
        val token = diagnostics.begin("REPORT_ROAD_EVENT", 50L)!!

        val record = diagnostics.complete(token, "asking/raw-payload-secret", 45L)!!

        assertEquals("unknown", record.status)
        assertEquals(0L, record.elapsedMs)
        assertFalse(record.toLogLine().contains("raw-payload-secret"))
        assertNull(diagnostics.complete(token, "success", 60L))
        assertEquals(listOf(record), diagnostics.recentRecords())
    }

    @Test
    fun inMemoryHistoryRemainsBounded() {
        val ids = listOf(
            "00000000-0000-4000-8000-000000000003",
            "00000000-0000-4000-8000-000000000004",
            "00000000-0000-4000-8000-000000000005",
        ).iterator()
        val diagnostics = GleoDebugDiagnostics(enabled = true, traceIdFactory = { ids.next() }, capacity = 2)

        repeat(3) { index ->
            val token = diagnostics.begin("REQUEST_SERVICE_BOOKING", index * 10L)!!
            diagnostics.complete(token, "asking", index * 10L + 5L)
        }

        assertEquals(2, diagnostics.recentRecords().size)
        assertEquals("00000000-0000-4000-8000-000000000004", diagnostics.recentRecords().first().traceId)
        assertEquals("00000000-0000-4000-8000-000000000005", diagnostics.recentRecords().last().traceId)
    }

    @Test
    fun activeTraceTokensAreAlsoBounded() {
        val ids = listOf(
            "00000000-0000-4000-8000-000000000006",
            "00000000-0000-4000-8000-000000000007",
            "00000000-0000-4000-8000-000000000008",
        ).iterator()
        val diagnostics = GleoDebugDiagnostics(enabled = true, traceIdFactory = { ids.next() }, capacity = 2)
        val first = diagnostics.begin("GET_DRIVING_CONTEXT", 0L)!!
        val second = diagnostics.begin("REPORT_ROAD_EVENT", 1L)!!

        assertNull(diagnostics.begin("REQUEST_SERVICE_BOOKING", 2L))
        diagnostics.discard(first)
        assertEquals("00000000-0000-4000-8000-000000000008", diagnostics.begin("REQUEST_SERVICE_BOOKING", 2L)?.traceId)
        diagnostics.discard(second)
    }
}
