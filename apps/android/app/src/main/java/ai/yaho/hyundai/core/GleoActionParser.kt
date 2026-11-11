package ai.yaho.hyundai.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

sealed interface GleoCommand {
    val functionName: String

    data class GetDrivingContext(val scope: String?) : GleoCommand {
        override val functionName = "GET_DRIVING_CONTEXT"
    }

    data class ReportRoadEvent(
        val kind: String,
    ) : GleoCommand {
        override val functionName = "REPORT_ROAD_EVENT"
    }

    data class RequestServiceBooking(
        val serviceType: String,
        val requestedDate: String,
        val notes: String?,
    ) : GleoCommand {
        override val functionName = "REQUEST_SERVICE_BOOKING"
    }
}

sealed interface GleoParseResult {
    data class Valid(val command: GleoCommand) : GleoParseResult
    data class MissingFields(val functionName: String, val fields: List<String>) : GleoParseResult
    data class Invalid(val functionName: String, val reason: String) : GleoParseResult
}

object GleoActionParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        action: String?,
        actionParam: String?,
        directParameters: Map<String, String?> = emptyMap(),
    ): GleoParseResult {
        val functionName = action?.takeIf { it in supportedFunctions }
            ?: return GleoParseResult.Invalid(action.orEmpty(), "Unsupported action")
        if ((actionParam?.length ?: 0) > MAX_ACTION_PARAM_LENGTH) {
            return GleoParseResult.Invalid(functionName, "ACTION_PARAM is too large")
        }
        val parameters = parseObject(actionParam, directParameters)
            ?: return GleoParseResult.Invalid(functionName, "ACTION_PARAM must be a JSON object")

        fun value(key: String, maxLength: Int): String? {
            val primitive = parameters[key] as? JsonPrimitive ?: return null
            if (!primitive.isString) return null
            return primitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty)?.takeIf { it.length <= maxLength }
        }

        val optionalLimits = when(functionName) {
            "GET_DRIVING_CONTEXT" -> mapOf("scope" to 20)
            "REQUEST_SERVICE_BOOKING" -> mapOf("notes" to 300)
            else -> emptyMap()
        }
        for((key,limit) in optionalLimits) {
            if(key in parameters && value(key,limit)==null)
                return GleoParseResult.Invalid(functionName,"Invalid optional parameter: $key")
        }

        return when (functionName) {
            "GET_DRIVING_CONTEXT" -> {
                val scope = value("scope", 20)
                if (scope != null && scope !in setOf("vehicle", "navigation", "all")) {
                    GleoParseResult.Invalid(functionName, "Unsupported context scope")
                } else {
                    GleoParseResult.Valid(GleoCommand.GetDrivingContext(scope))
                }
            }

            "REPORT_ROAD_EVENT" -> {
                val kind = value("kind", 32)
                val missing = if (kind == null) listOf("kind") else emptyList()
                when {
                    missing.isNotEmpty() -> GleoParseResult.MissingFields(functionName, missing)
                    kind !in reportKinds -> GleoParseResult.Invalid(functionName, "Unsupported road event kind")
                    else -> GleoParseResult.Valid(GleoCommand.ReportRoadEvent(kind!!))
                }
            }

            "REQUEST_SERVICE_BOOKING" -> {
                val serviceType = value("serviceType", 80)
                val requestedDate = value("requestedDate", 80)
                val normalizedDate = requestedDate?.let(ServiceDateParser::normalize)
                val notes = value("notes", 300)
                val missing = buildList {
                    if (serviceType == null) add("serviceType")
                    if (normalizedDate == null) add("requestedDate in ISO 8601 format")
                }
                if (missing.isNotEmpty()) {
                    GleoParseResult.MissingFields(functionName, missing)
                } else {
                    GleoParseResult.Valid(
                        GleoCommand.RequestServiceBooking(serviceType!!, normalizedDate!!, notes),
                    )
                }
            }

            else -> GleoParseResult.Invalid(functionName, "Unsupported action")
        }
    }

    private fun parseObject(raw: String?, directParameters: Map<String, String?>): JsonObject? {
        val source = raw?.trim()?.takeIf(String::isNotEmpty)
        val baseObject = if (source == null) {
            JsonObject(emptyMap())
        } else {
            runCatching {
                var element: JsonElement = json.parseToJsonElement(source)
                if (element is JsonPrimitive && element.isString) {
                    if (element.content.length > MAX_ACTION_PARAM_LENGTH) return null
                    element = json.parseToJsonElement(element.content)
                }
                element.jsonObject
            }.getOrNull() ?: return null
        }
        val allowedDirectKeys = setOf("scope", "kind", "serviceType", "requestedDate", "notes")
        if (directParameters.none { (key, value) -> key in allowedDirectKeys && value != null }) return baseObject
        return JsonObject(baseObject.toMutableMap().apply {
            directParameters.forEach { (key, value) ->
                if (key in allowedDirectKeys && value != null && key !in this) {
                    put(key, JsonPrimitive(value.take(MAX_DIRECT_VALUE_LENGTH)))
                }
            }
        })
    }

    private val supportedFunctions = setOf(
        "GET_DRIVING_CONTEXT",
        "REPORT_ROAD_EVENT",
        "REQUEST_SERVICE_BOOKING",
    )

    private val reportKinds = setOf("stalled_vehicle", "road_hazard", "traffic", "other", "slippery_report", "rain_report", "accident_report")

    private const val MAX_ACTION_PARAM_LENGTH = 4_096
    private const val MAX_DIRECT_VALUE_LENGTH = 500
}
