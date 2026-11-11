package ai.yaho.hyundai.core

import java.util.ArrayDeque
import java.util.UUID


internal class GleoDebugDiagnostics(
    private val enabled: Boolean,
    private val traceIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val activeTraceIds = mutableSetOf<String>()
    private val completedRecords = ArrayDeque<GleoDebugTraceRecord>()

    init {
        require(capacity > 0)
    }

    @Synchronized
    fun begin(action: String, startedElapsedRealtimeMs: Long): GleoDebugTraceToken? {
        if (!enabled || activeTraceIds.size >= capacity || action !in ALLOWED_ACTIONS || startedElapsedRealtimeMs < 0L) return null
        val traceId = traceIdFactory()
        if (!traceId.matches(TRACE_ID_PATTERN) || !activeTraceIds.add(traceId)) return null
        return GleoDebugTraceToken(traceId, action, startedElapsedRealtimeMs)
    }


    @Synchronized
    fun complete(
        token: GleoDebugTraceToken,
        status: String,
        completedElapsedRealtimeMs: Long,
    ): GleoDebugTraceRecord? {
        if (!enabled || !activeTraceIds.remove(token.traceId) || token.action !in ALLOWED_ACTIONS) return null
        val record = GleoDebugTraceRecord(
            traceId = token.traceId,
            action = token.action,
            status = status.takeIf { it in ALLOWED_STATUSES } ?: "unknown",
            elapsedMs = (completedElapsedRealtimeMs - token.startedElapsedRealtimeMs).coerceAtLeast(0L),
        )
        completedRecords.addLast(record)
        while (completedRecords.size > capacity) completedRecords.removeFirst()
        return record
    }

    @Synchronized
    fun discard(token: GleoDebugTraceToken?) {
        if (enabled && token != null) activeTraceIds.remove(token.traceId)
    }

    @Synchronized
    fun recentRecords(): List<GleoDebugTraceRecord> =
        if (enabled) completedRecords.toList() else emptyList()

    companion object {
        private const val DEFAULT_CAPACITY = 32
        private val TRACE_ID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
        private val ALLOWED_ACTIONS = setOf(
            "GET_DRIVING_CONTEXT",
            "REPORT_ROAD_EVENT",
            "REQUEST_SERVICE_BOOKING",
        )
        private val ALLOWED_STATUSES = setOf("success", "asking", "fail")
    }
}

internal data class GleoDebugTraceToken(
    val traceId: String,
    val action: String,
    val startedElapsedRealtimeMs: Long,
)

internal data class GleoDebugTraceRecord(
    val traceId: String,
    val action: String,
    val status: String,
    val elapsedMs: Long,
) {
    fun toLogLine(): String =
        "traceId=$traceId action=$action status=$status elapsedMs=$elapsedMs origin=unknown"
}
