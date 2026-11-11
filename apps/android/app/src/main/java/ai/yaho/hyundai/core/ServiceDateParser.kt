package ai.yaho.hyundai.core

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

object ServiceDateParser {
    private val serviceZone = ZoneId.of("Asia/Seoul")

    fun normalize(value: String): String? {
        val input = value.trim()
        if (input.isEmpty() || input.length > 80) return null
        runCatching { OffsetDateTime.parse(input).toInstant().toString() }.getOrNull()?.let { return it }
        runCatching { Instant.parse(input).toString() }.getOrNull()?.let { return it }
        return runCatching {
            LocalDate.parse(input)
                .atTime(9, 0)
                .atZone(serviceZone)
                .toInstant()
                .toString()
        }.getOrNull()
    }
}
