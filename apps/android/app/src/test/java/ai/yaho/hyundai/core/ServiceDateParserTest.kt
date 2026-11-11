package ai.yaho.hyundai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceDateParserTest {
    @Test
    fun dateOnlyRequestsUseKoreanServiceTime() {
        assertEquals("2026-11-05T00:00:00Z", ServiceDateParser.normalize("2026-11-05"))
    }

    @Test
    fun preservesValidInstantAndRejectsUnboundedText() {
        assertEquals("2026-11-04T12:30:00Z", ServiceDateParser.normalize("2026-11-04T12:30:00Z"))
        assertNull(ServiceDateParser.normalize("appointment someday"))
        assertNull(ServiceDateParser.normalize("2".repeat(81)))
    }
}
