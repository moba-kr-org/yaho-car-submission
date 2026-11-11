package ai.yaho.hyundai.core

data class GleoObservationClock(
    val received: Boolean,
    val receivedElapsedMillis: Long?,
)

data class GleoFreshObservationSummary(
    val count: Int,
    val freshestAgeMillis: Long?,
)

object GleoObservationFreshnessPolicy {
    private const val MAX_FRESH_AGE_MILLIS = 30_000L

    fun summarize(
        consented: Boolean,
        serviceActive: Boolean,
        nowElapsedMillis: Long,
        observations: List<GleoObservationClock>,
    ): GleoFreshObservationSummary {
        if (!consented || !serviceActive || nowElapsedMillis < 0L) {
            return GleoFreshObservationSummary(count = 0, freshestAgeMillis = null)
        }

        val ages = observations.mapNotNull { observation ->
            val receivedAt = observation.receivedElapsedMillis ?: return@mapNotNull null
            if (!observation.received) return@mapNotNull null
            if (receivedAt !in 0..nowElapsedMillis) return@mapNotNull null
            (nowElapsedMillis - receivedAt).takeIf { it in 0..MAX_FRESH_AGE_MILLIS }
        }
        return GleoFreshObservationSummary(
            count = ages.size,
            freshestAgeMillis = ages.minOrNull(),
        )
    }
}

sealed interface GleoIntentParameterNormalization {
    data class Valid(
        val actionParam: String?,
        val directParameters: Map<String, String?>,
    ) : GleoIntentParameterNormalization

    data class Invalid(val key: String) : GleoIntentParameterNormalization
}

object GleoIntentParameterPolicy {
    private val directParameterKeys = setOf("scope", "kind", "serviceType", "requestedDate", "notes")

    fun normalize(
        actionParamPresent: Boolean,
        actionParam: Any?,
        directParameterExtras: Map<String, Any?>,
    ): GleoIntentParameterNormalization {
        if (actionParamPresent && (actionParam !is String || actionParam.isBlank())) {
            return GleoIntentParameterNormalization.Invalid("ACTION_PARAM")
        }
        if (!actionParamPresent && actionParam != null) {
            return GleoIntentParameterNormalization.Invalid("ACTION_PARAM")
        }

        val directParameters = mutableMapOf<String, String?>()
        for ((key, value) in directParameterExtras) {
            if (key !in directParameterKeys) continue
            val stringValue = value as? String
                ?: return GleoIntentParameterNormalization.Invalid(key)
            directParameters[key] = stringValue
        }
        return GleoIntentParameterNormalization.Valid(
            actionParam = actionParam as? String,
            directParameters = directParameters,
        )
    }
}

sealed interface GleoPendingReviewDecision {
    data class Start(val command: GleoCommand) : GleoPendingReviewDecision
    data class KeepCurrent(val command: GleoCommand) : GleoPendingReviewDecision
}

object GleoPendingReviewPolicy {
    fun decide(current: GleoCommand?, requested: GleoCommand): GleoPendingReviewDecision =
        if (current == null) GleoPendingReviewDecision.Start(requested)
        else GleoPendingReviewDecision.KeepCurrent(current)
}
