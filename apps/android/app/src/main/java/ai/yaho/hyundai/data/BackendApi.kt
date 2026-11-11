package ai.yaho.hyundai.data

import ai.yaho.hyundai.BuildConfig
import ai.yaho.hyundai.core.ExperimentVariant
import ai.yaho.hyundai.core.GleoCommand
import ai.yaho.hyundai.vehicle.VehicleContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL

data class RoadFeedItem(
    val eventKey: String,
    val kind: String,
    val direction: String?,
    val gridCellId: String,
    val timeWindowStart: String,
    val precisionMeters: Int,
    val reportCount: Int,
    val participantCount: Int,
    val status: String,
    val userSourceCount: Int,
    val simulatorSourceCount: Int,
    val expiresAt: String,
)

data class KnowledgeResult(
    val documentId: String,
    val title: String,
    val sourceTitle: String,
    val publisher: String,
    val url: String,
    val excerpt: String,
    val contentDigest: String,
    val approvedAt: String,
)

data class ApiMessage(val text: String, val success: Boolean, val recordId: String? = null)

class BackendApi(
    private val credentialProvider: InstallationCredentialProvider,
    private val idempotencyKeyStore: IdempotencyKeyStore,
    private val baseUrl: String = BuildConfig.API_BASE_URL,
) {
    private val NETWORK_SHARED_KEYS = setOf(
        "Brake.getCurrentBrakePedalStatus",
        "Display.getCurrentTemperatureDisplayUnit",
        "Door.getCurrentGloveBoxLockState",
        "DrivingMode.getCruiseControlTargetSpeed",
        "DrivingMode.getCruiseControlState",
        "DrivingMode.getCurrentDrivingModeState",
        "EvBattery.getInstantaneousEvEfficiency",
        "EvBattery.getSinceChargeEvEfficiency",
        "EvBattery.getCumulativeEvEfficiency",
        "EvBattery.getCurrentEvEfficiency",
        "EvBattery.getEvBatteryStateOfCharge",
        "EvBattery.getEvBatteryLevel",
        "EvBattery.getEvChargeCurrentDrawLimit",
        "EvBattery.isEvChargePortOpen",
        "EvBattery.getEvChargeSwitchState",
        "EvBattery.getEvBatteryCapacity",
        "Fuel.getInstantaneousFuelEconomy",
        "Fuel.getSinceChargeFuelEconomy",
        "Fuel.getCumulativeFuelEconomy",
        "Fuel.getCurrentFuelEconomy",
        "Fuel.getFuelPercent",
        "Gear.getCurrentGearSelectionState",
        "Light.getCabinLightSwitch",
        "Light.getCurrentCabinLightState",
        "Light.getCurrentHighBeamLightState",
        "Light.getHighBeamLightSwitch",
        "Light.getLowBeamLightSwitch",
        "Light.getCurrentLowBeamLightState",
        "Light.getCurrentTailLampLightState",
        "Light.getTailLampLightSwitch",
        "Light.getCurrentFrontFogLightState",
        "Light.getFrontFogLightSwitch",
        "Light.getCurrentRearFogLightState",
        "Light.getRearFogLightSwitch",
        "Light.getAmbientColorAutoByDriveModeEnabled",
        "Odometer.getCurrentDistanceDriven",
        "Odometer.getTripCurrentDrivingTime",
        "Odometer.getTripCumulativeDrivingTime",
        "Odometer.getTripSinceRefillDrivingTime",
        "Odometer.getTripCurrentDrivingDistance",
        "Odometer.getTripInstantaneousDrivingDistance",
        "Odometer.getTripCumulativeDrivingDistance",
        "Odometer.getTripSinceRefillDrivingDistance",
        "Odometer.getRangeRemainingDistance",
        "Safety.getEngineOilLevelState",
        "Safety.getBrakeFluidLevelState",
        "Safety.getTransmissionOilLevelState",
        "Safety.hasLowWasherFluidWarning",
        "Safety.hasPowerDownWarningIndicator",
        "SteeringWheel.getHvacSteeringWheelHeatState",
        "SteeringWheel.getCurrentSteeringWheelAngle",
        "Toll.getCurrentEtcsVoiceGuidanceVolume",
        "TurnSignal.getCurrentTurnSignalState",
        "Window.getCurrentSmartGlassState",
        "wiper.front.active",
        "brake.pedal.pressed",
        "gear.selection",
        "vehicle.speed.kph",
        "engine.speed.rpm",
        "engine.coolant.celsius",
        "energy.fuel.percent",
        "energy.battery.soc.percent",
        "odometer.total.km",
        "weather.ambient.celsius",
        "diagnostic.dtc.codes"
    )

    val isConfigured: Boolean get() = validateBaseUrl(baseUrl) == null
    val configurationMessage: String get() = validateBaseUrl(baseUrl).orEmpty()
    val canProvisionLocalInstallation: Boolean
        get() = BuildConfig.DEBUG && isLoopbackHttp(baseUrl) && isConfigured


    suspend fun provisionLocalInstallation(variant: ExperimentVariant): ApiMessage = withContext(Dispatchers.IO) {
        if (!canProvisionLocalInstallation) {
            return@withContext ApiMessage("로컬 개발 서버에 연결할 수 없습니다.", false)
        }
        val response = request(
            method = "POST",
            path = "/v1/dev/installations",
            body = JSONObject(),
            allowUnauthenticated = true,
            timeoutMillis = 5_000,
        )
        if (response.code !in 200..299) return@withContext ApiMessage(response.message, false)
        runCatching {
            val json = JSONObject(response.body)
            val environment = json.optString("environment")
            require(environment == "development") { "Expected development installation" }
            val id = json.optString("installationId").takeIf(String::isNotBlank)
                ?: error("Missing installationId")
            val token = json.optString("accessToken").takeIf(String::isNotBlank)
                ?: error("Missing accessToken")
            (credentialProvider as? DebugInstallationCredentialStore)
                ?.save(variant, InstallationIdentity(id, token))
                ?: error("Debug credential store is unavailable")
            ApiMessage("${variant.label} 로컬 설치가 연결되었습니다.", true)
        }.getOrElse {
            ApiMessage("로컬 설치 응답을 확인하지 못했습니다.", false)
        }
    }

    suspend fun networkState(variant: ExperimentVariant): Result<Boolean> = withContext(Dispatchers.IO) {
        val response = request("GET", "/v1/network/state", variant = variant)
        if (response.code !in 200..299) Result.failure(IllegalStateException(response.message))
        else runCatching { JSONObject(response.body).getBoolean("consent") }
    }

    suspend fun setNetworkSharing(variant: ExperimentVariant, consent: Boolean): ApiMessage = withContext(Dispatchers.IO) {
        val response = request("PUT", "/v1/network/consent", variant = variant, body = JSONObject().put("consent", consent))
        ApiMessage(response.message, response.code in 200..299)
    }

    suspend fun pushNetworkSnapshot(
        variant: ExperimentVariant,
        snapshot: ai.yaho.hyundai.vehicle.VehicleSnapshot,
        location: VehicleContext,
        canSend: () -> Boolean = { true },
    ): ApiMessage = withContext(Dispatchers.IO) {
        if (!canSend()) return@withContext ApiMessage("공유가 꺼져 있어 전송하지 않았습니다.", false)
        val lat = location.latitude ?: return@withContext ApiMessage("위치가 없어 전송하지 않았습니다.", false)
        val lng = location.longitude ?: return@withContext ApiMessage("위치가 없어 전송하지 않았습니다.", false)
        val now = System.currentTimeMillis()
        val readings = snapshot.readings.filter { reading ->
            reading.key in NETWORK_SHARED_KEYS && reading.state == "수신" && reading.observedAt != null && now - reading.observedAt in 0..30_000 &&
                (location.locationEvidence == null || kotlin.math.abs(reading.observedAt-location.locationEvidence.observedAt) <= 15_000)
        }
        if (readings.isEmpty()) return@withContext ApiMessage("새 관측을 기다리고 있습니다.", false)
        val signals = JSONObject()
        readings.forEach { reading ->
            reading.value?.let { value ->
                val scalar: Any = value.toBooleanStrictOrNull() ?: value.toDoubleOrNull() ?: value
                signals.put(reading.key, scalar)
            }
        }
        readings.find { it.key == "Brake.getCurrentBrakePedalStatus" }?.value?.toBooleanStrictOrNull()?.let { signals.put("brake.pedal.pressed", it) }
        readings.find { it.key == "Gear.getCurrentGearSelectionState" }?.value?.let { signals.put("gear.selection", it) }
        val observed = readings.find { it.key == "wiper.front.active" }?.observedAt ?: readings.maxOf { it.observedAt!! }
        location.locationEvidence?.let { evidence ->
            if (!ai.yaho.hyundai.core.locationMatchesObservation(evidence,observed,now,android.os.SystemClock.elapsedRealtime()))
                return@withContext ApiMessage("위치와 차량 관측의 시각을 맞추고 있습니다. 전송하지 않았습니다.",false)
        }
        if (!location.isSample && location.locationEvidence == null) return@withContext ApiMessage("위치 출처·시각 근거가 없어 전송하지 않았습니다.",false)
        val body = JSONObject().put("producer", "pleos")
            .put("source", if (location.isSample) "simulator" else "device_reported")
            .put("location", JSONObject().put("lat", lat).put("lng", lng))
            .put("observedAt", java.time.Instant.ofEpochMilli(observed).toString()).put("signals", signals)
        location.locationEvidence?.let { evidence ->
            body.put("locationEvidence",JSONObject().put("provider","pleos_fused_location")
                .put("observedAt",java.time.Instant.ofEpochMilli(evidence.observedAt).toString())
                .put("confidence",evidence.confidence?.toDouble() ?: JSONObject.NULL).put("accuracyMeters",JSONObject.NULL))
        }
        location.headingDegrees?.let { body.put("headingDegrees", it) }
        if (!canSend()) return@withContext ApiMessage("공유가 꺼져 있어 전송하지 않았습니다.", false)
        val response = request("POST", "/v1/network/observations", variant = variant, body = body,
            idempotencyKey = java.util.UUID.randomUUID().toString())
        ApiMessage(if (response.code in 200..299) "중앙 서버에 ${signals.length()}개 관측을 전달했습니다." else response.message, response.code in 200..299)
    }

    suspend fun readSindangWeather(): JSONObject? = try {
        val reply = request("GET", "/v1/weather/sindang")
        if (reply.code in 200..299) runCatching { JSONObject(reply.body) }.getOrNull() else null
    } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
        null
    }

    suspend fun networkContext(variant: ExperimentVariant, location: VehicleContext, canQuery: () -> Boolean = { true }): Result<JSONObject> = withContext(Dispatchers.IO) {
        if (!canQuery()) return@withContext Result.failure(IllegalStateException("공유가 꺼져 있어 위치를 전송하지 않았습니다."))
        val lat = location.latitude ?: return@withContext Result.failure(IllegalStateException("위치 미제공"))
        val lng = location.longitude ?: return@withContext Result.failure(IllegalStateException("위치 미제공"))
        if (location.locationEvidence != null && !ai.yaho.hyundai.core.validLocationEvidence(location.locationEvidence,System.currentTimeMillis(),android.os.SystemClock.elapsedRealtime()))
            return@withContext Result.failure(IllegalStateException("위치가 만료되어 조회하지 않았습니다."))
        if (!location.isSample && location.locationEvidence == null) return@withContext Result.failure(IllegalStateException("위치 출처 미확인"))
        val channel = if (location.isSample) "simulator" else "device_reported"
        if (!canQuery()) return@withContext Result.failure(IllegalStateException("공유가 꺼져 있어 위치를 전송하지 않았습니다."))
        val response = request("GET", "/v1/network/context?lat=$lat&lng=$lng&source=$channel", variant = variant)
        if (response.code !in 200..299) Result.failure(IllegalStateException(response.message)) else runCatching { JSONObject(response.body) }
    }

    suspend fun updateConsent(variant: ExperimentVariant, consent: Boolean): ApiMessage = withContext(Dispatchers.IO) {
        val response = request(
            method = "PUT",
            path = "/v1/installations/me/consent",
            body = JSONObject().put("consent", consent),
            variant = variant,
        )
        ApiMessage(response.message, response.code in 200..299)
    }

    suspend fun submitReport(
        variant: ExperimentVariant,
        command: GleoCommand.ReportRoadEvent,
        context: VehicleContext,
    ): ApiMessage = withContext(Dispatchers.IO) {
        val lat = context.latitude ?: return@withContext ApiMessage("현재 위치를 확인할 수 없습니다.", false)
        val lng = context.longitude ?: return@withContext ApiMessage("현재 위치를 확인할 수 없습니다.", false)
        val source = if (context.isSample) "simulator" else "user"
        val location = JSONObject().put("lat", lat).put("lng", lng)
        val semanticPayload = JSONObject()
            .put("kind", command.kind)
            .put("location", location)
            .put("source", source)
            .put("headingDegrees", context.headingDegrees)
            .toString()
        val reservation = runCatching { idempotencyKeyStore.reserve(variant, "report", semanticPayload) }
            .getOrElse { return@withContext ApiMessage(it.message ?: "이전 제보 결과를 먼저 확인해 주세요.", false) }
        val body = JSONObject()
            .put("kind", command.kind)
            .put("location", JSONObject().put("lat", lat).put("lng", lng))
            .put("occurredAt", reservation.createdAt)
            .put("source", source)
        context.headingDegrees?.let { body.put("headingDegrees", it) }
        val response = request(
            method = "POST",
            path = "/v1/reports",
            body = body,
            variant = variant,
            idempotencyKey = reservation.key,
        )
        resolveOperationIfDefinitive(variant, "report", response)
        ApiMessage(response.message, response.isAccepted(), response.recordId("reportId", "id"))
    }

    suspend fun requestService(
        variant: ExperimentVariant,
        command: GleoCommand.RequestServiceBooking,
        context: VehicleContext,
        approved: Boolean,
        approvalStillCurrent: () -> Boolean = { true },
        workshopName: String? = null,
    ): ApiMessage = withContext(Dispatchers.IO) {
        if (!approved || !approvalStillCurrent()) return@withContext ApiMessage("사용자 확인이 필요합니다.", false)
        val installation = credentialProvider.credentialsFor(variant)
            ?: return@withContext ApiMessage("설치 연결이 필요합니다.",false)
        val operation = "booking_${installation.installationId}"
        val pending = idempotencyKeyStore.pending(variant,operation) ?: idempotencyKeyStore.pending(variant,"booking")
        if (pending != null) return@withContext ApiMessage("이전 정비 요청의 결과 확인이 필요합니다. 아래 최근 요청에서 접수 결과를 확인해 주세요. 새 요청을 전송하지 않았습니다.",false)
        val body = JSONObject()
            .put("approved", true)
            .put("serviceType", command.serviceType)
            .put("requestedAt", command.requestedDate)
        if (workshopName != null) {
            body.put("workshopName", workshopName)
        } else {
            val lat = context.latitude ?: return@withContext ApiMessage("현재 위치를 확인할 수 없습니다.", false)
            val lng = context.longitude ?: return@withContext ApiMessage("현재 위치를 확인할 수 없습니다.", false)
            body.put("location", JSONObject().put("lat", lat).put("lng", lng))
        }
        command.notes?.takeIf(String::isNotBlank)?.let { body.put("notes", it) }

        val prepared = try {
            request("POST", "/v1/booking-reviews", body, variant)
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            return@withContext ApiMessage("요청 내용 확인 시간이 초과됐습니다. 점검 요청은 전송하지 않았습니다.", false)
        }
        if (prepared.code !in 200..299) return@withContext ApiMessage("요청 내용 확인 단계에서 중단됐습니다. 점검 요청은 전송하지 않았습니다. ${prepared.message}", false)
        val review = runCatching { JSONObject(prepared.body) }.getOrNull()
        val reviewToken = review?.optString("reviewToken").orEmpty()
        val reviewValid = Regex("^[0-9a-f]{64}$").matches(reviewToken) && runCatching {
            java.time.Instant.parse(review?.optString("expiresAt")).isAfter(java.time.Instant.now())
        }.getOrDefault(false)
        if (!reviewValid) return@withContext ApiMessage("요청 내용 확인에 실패했습니다. 점검 요청은 전송하지 않았습니다.", false)
        if (!approvalStillCurrent() || credentialProvider.credentialsFor(variant)?.installationId != installation.installationId) {
            return@withContext ApiMessage("요청 내용이나 차량 상태가 바뀌었습니다. 최신 내용을 다시 확인해 주세요. 점검 요청은 전송하지 않았습니다.", false)
        }
        val semanticPayload = body.toString()
        val reservation = runCatching { idempotencyKeyStore.reserve(variant, operation, semanticPayload) }
            .getOrElse { return@withContext ApiMessage(it.message ?: "이전 정비 요청 결과를 먼저 확인해 주세요.", false) }
        body.put("reviewToken", reviewToken)
        val response = request(
            method = "POST",
            path = "/v1/bookings",
            body = body,
            variant = variant,
            idempotencyKey = reservation.key,
        )
        resolveOperationIfDefinitive(variant, operation, response, reservation.key)
        ApiMessage(response.message, response.isAccepted(), response.recordId("bookingId"))
    }

    suspend fun fetchBookings(variant: ExperimentVariant): Result<List<BookingStatus>> = bookingResult {
        val response = request("GET", "/v1/bookings", variant = variant)
        check(response.code in 200..299) { response.message }
        val items = JSONObject(response.body).getJSONArray("items")
        require(items.length() <= 20)
        List(items.length()) { parseBookingStatus(items.getJSONObject(it)) }
    }

    fun hasPendingBooking(variant: ExperimentVariant): Boolean {
        val id = credentialProvider.credentialsFor(variant)?.installationId ?: return false
        return idempotencyKeyStore.pending(variant,"booking_$id") != null || idempotencyKeyStore.pending(variant,"booking") != null
    }

    suspend fun recoverPendingBooking(variant: ExperimentVariant): Result<BookingStatus?> = bookingResult {
        val id = credentialProvider.credentialsFor(variant)?.installationId ?: error("설치 연결이 필요합니다.")
        val scoped = "booking_$id"
        val operation = if (idempotencyKeyStore.pending(variant,scoped) != null) scoped else "booking"
        val pending = idempotencyKeyStore.pending(variant,operation) ?: return@bookingResult null
        val response = request("GET", "/v1/booking-operations/${encodePathSegment(pending.key)}", variant = variant)
        check(response.code in 200..299) { "이전 요청 접수 결과를 확인하지 못했습니다. 새 요청을 보내지 마세요. 최근 요청 목록도 확인해 주세요." }
        val booking = parseBookingStatus(JSONObject(response.body).getJSONObject("booking"))
        if (booking.status in setOf("needs_configuration","queued","cancelled","failed")) idempotencyKeyStore.resolveIfMatches(variant,operation,pending.key)
        booking
    }

    suspend fun cancelBooking(variant: ExperimentVariant, bookingId: String, approved: Boolean): Result<BookingStatus> = bookingResult {
        check(approved) { "취소 확인이 필요합니다." }
        require(Regex("^[0-9a-fA-F-]{36}$").matches(bookingId))
        val id = credentialProvider.credentialsFor(variant)?.installationId ?: error("설치 연결이 필요합니다.")
        val operation = "cancel_${id}_$bookingId"
        val reservation = idempotencyKeyStore.reserve(variant,operation,bookingId)
        val response = request("POST", "/v1/bookings/$bookingId/cancel",JSONObject(),variant,reservation.key)
        check(response.code in 200..299) { response.message }
        val booking = parseBookingStatus(JSONObject(response.body).getJSONObject("booking"))
        if (booking.status == "cancelled" || booking.cancellationRequested || booking.status == "failed")
            idempotencyKeyStore.resolveIfMatches(variant,operation,reservation.key)
        booking
    }

    suspend fun sendEventFeedback(
        variant: ExperimentVariant,
        eventKey: String,
        state: String,
        source: String,
    ): ApiMessage = withContext(Dispatchers.IO) {
        require(state == "confirm" || state == "clear")
        val semanticPayload = JSONObject()
            .put("eventKey", eventKey)
            .put("state", state)
            .put("source", source)
            .toString()
        val operation = "feedback_${digest(eventKey)}"
        val reservation = runCatching { idempotencyKeyStore.reserve(variant, operation, semanticPayload) }
            .getOrElse { return@withContext ApiMessage(it.message ?: "이전 의견 결과를 먼저 확인해 주세요.", false) }
        val response = request(
            method = "POST",
            path = "/v1/events/${encodePathSegment(eventKey)}/feedback",
            body = JSONObject().put("state", state).put("source", source),
            variant = variant,
            idempotencyKey = reservation.key,
        )
        resolveOperationIfDefinitive(variant, operation, response)
        ApiMessage(response.message, response.isAccepted())
    }

    suspend fun updateOwnedReport(variant: ExperimentVariant, reportId: String, state: String): ApiMessage =
        withContext(Dispatchers.IO) {
            require(state == "confirm" || state == "clear")
            val operation = "owner_${digest(reportId)}_$state"
            val semanticPayload = JSONObject().put("reportId", reportId).put("state", state).toString()
            val reservation = runCatching { idempotencyKeyStore.reserve(variant, operation, semanticPayload) }
                .getOrElse { return@withContext ApiMessage(it.message ?: "이전 제보 처리 결과를 먼저 확인해 주세요.", false) }
            val response = request(
                method = "POST",
                path = "/v1/reports/${encodePathSegment(reportId)}/$state",
                body = JSONObject(),
                variant = variant,
                idempotencyKey = reservation.key,
            )
            resolveOperationIfDefinitive(variant, operation, response)
            ApiMessage(response.message, response.isAccepted())
        }

    suspend fun fetchFeed(variant: ExperimentVariant): Result<List<RoadFeedItem>> = withContext(Dispatchers.IO) {
        val response = request("GET", "/v1/feed", variant = variant)
        if (response.code !in 200..299) return@withContext Result.failure(IllegalStateException(response.message))
        runCatching { parseFeed(response.body) }
    }

    suspend fun searchKnowledge(variant: ExperimentVariant, query: String): Result<List<KnowledgeResult>> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
            val response = request("GET", "/v1/knowledge/search?q=$encoded", variant = variant)
            if (response.code !in 200..299) {
                return@withContext Result.failure(IllegalStateException(response.message))
            }
            runCatching { parseKnowledge(response.body) }
        }

    private suspend fun <T> bookingResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
        Result.failure(IllegalStateException("서버 응답이 지연되고 있습니다. 기존 요청의 상태를 다시 확인해 주세요."))
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (error: Exception) { Result.failure(error) }

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        variant: ExperimentVariant? = null,
        idempotencyKey: String? = null,
        allowUnauthenticated: Boolean = false,
        timeoutMillis: Long = 4_800,
    ): HttpReply = withTimeout(timeoutMillis) {
        withContext(Dispatchers.IO) {
            validateBaseUrl(baseUrl)?.let { return@withContext HttpReply(0, it) }
            val identity = variant?.let(credentialProvider::credentialsFor)
            if (variant != null && identity == null && !allowUnauthenticated) {
                return@withContext HttpReply(401, "이 구성의 로컬 설치 인증이 필요합니다.")
            }
            var connection: HttpURLConnection? = null
            try {
                connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 2_400
                connection.readTimeout = 2_700
                connection.setRequestProperty("Accept", "application/json")
                identity?.let { connection.setRequestProperty("Authorization", "Bearer ${it.accessToken}") }
                idempotencyKey?.let { connection.setRequestProperty("Idempotency-Key", it) }
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")

                    val bytes = body.toString().toByteArray(Charsets.UTF_8)
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                val code = connection.responseCode
                val stream = if (code in 200..399) connection.inputStream else connection.errorStream
                val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                HttpReply(code, responseMessage(code, responseText), responseText)
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                throw timeout
            } catch (_: Exception) {
                HttpReply(0, "서버에 연결하지 못했습니다. 요청 결과를 확인할 수 없으면 같은 요청만 다시 확인해 주세요.")
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun responseMessage(code: Int, body: String): String {
        val json = runCatching { JSONObject(body) }.getOrNull()
        val bookingStatus = json?.optJSONObject("booking")?.optString("status")
        if (bookingStatus == "needs_configuration") {
            return "점검 요청을 서버에 보관했습니다. 업체 연동 전이라 전달·예약 확정은 되지 않았습니다."
        }
        if (bookingStatus == "unknown" || json?.optString("status") == "unknown") {
            return "서버가 요청 결과를 확정하지 못했습니다. 같은 요청의 결과를 다시 확인해 주세요."
        }
        when (json?.optJSONObject("error")?.optString("code")) {
            "booking_review_required", "invalid_booking_review", "booking_review_expired", "booking_review_mismatch" ->
                return "요청 확인이 만료됐거나 내용이 바뀌었습니다. 최신 내용을 다시 검토해 주세요."
            "booking_review_consumed" -> return "이미 사용한 요청 확인입니다. 최근 요청에서 기존 접수 상태를 확인해 주세요."
        }
        val serverMessage = json?.optJSONObject("error")?.optString("message")
            ?.takeIf(String::isNotBlank)
            ?: json?.optString("message")?.takeIf(String::isNotBlank)
        return serverMessage ?: when (code) {
            in 200..299 -> "요청을 접수했습니다."
            401 -> "설치 인증을 확인할 수 없습니다. 로컬 연결을 다시 확인해 주세요."
            403 -> "이 구성의 동의를 확인할 수 없습니다. 동의 설정을 확인해 주세요."
            404 -> "요청한 기능을 서버에서 찾지 못했습니다."
            0 -> "서버에 연결하지 못했습니다. 요청 결과를 확인할 수 없습니다."
            else -> "요청을 처리하지 못했습니다. (HTTP $code)"
        }
    }

    private fun resolveOperationIfDefinitive(variant: ExperimentVariant, operation: String, response: HttpReply, expectedKey: String? = null) {
        val json = runCatching { JSONObject(response.body) }.getOrNull()
        val status = json?.optString("status")
        val bookingStatus = json?.optJSONObject("booking")?.optString("status")

        val reviewRejected = json?.optJSONObject("error")?.optString("code") in setOf(
            "booking_review_required", "invalid_booking_review", "booking_review_expired", "booking_review_mismatch", "booking_review_consumed")
        val definiteFailure = reviewRejected || bookingStatus == "needs_configuration" ||
            (response.code in 400..499 && response.code !in setOf(408, 409, 425, 429) &&
                status !in setOf("unknown", "pending"))
        if (response.isAccepted() || definiteFailure) {
            if (expectedKey != null) idempotencyKeyStore.resolveIfMatches(variant,operation,expectedKey)
            else idempotencyKeyStore.resolve(variant,operation)
        }
    }

    private fun parseFeed(body: String): List<RoadFeedItem> {
        val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val eventKey = item.optString("eventKey").takeIf(String::isNotBlank) ?: continue
                val sources = item.optJSONObject("sources") ?: JSONObject()
                val location = item.optJSONObject("location") ?: JSONObject()
                add(
                    RoadFeedItem(
                        eventKey = eventKey,
                        kind = item.optString("kind", "other"),
                        direction = item.optString("direction").takeIf(String::isNotBlank),
                        gridCellId = item.optString("gridCellId", ""),
                        timeWindowStart = item.optString("timeWindowStart", ""),
                        precisionMeters = location.optInt("precisionMeters", 300).coerceAtLeast(300),
                        reportCount = item.optInt("reportCount"),
                        participantCount = item.optInt("participantCount"),
                        status = item.optString("status", "unverified"),
                        userSourceCount = sources.optInt("user"),
                        simulatorSourceCount = sources.optInt("simulator"),
                        expiresAt = item.optString("expiresAt", ""),
                    ),
                )
            }
        }
    }

    private fun parseKnowledge(body: String): List<KnowledgeResult> {
        val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val source = item.optJSONObject("source") ?: JSONObject()
                add(
                    KnowledgeResult(
                        documentId = item.optString("documentId", ""),
                        title = item.optString("title", "차량 관리 안내"),
                        sourceTitle = source.optString("title", "차량 관리 지식"),
                        publisher = source.optString("publisher", ""),
                        url = source.optString("url", ""),
                        excerpt = item.optString("excerpt", ""),
                        contentDigest = item.optString("contentDigest", ""),
                        approvedAt = item.optString("approvedAt", ""),
                    ),
                )
            }
        }
    }

    private fun validateBaseUrl(value: String): String? {
        if (value.isBlank()) return "운영 HTTPS 서버 주소가 설정되지 않았습니다."
        val uri = runCatching { URI(value) }.getOrNull() ?: return "서버 주소 형식을 확인해 주세요."
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase().orEmpty()
        if (BuildConfig.DEBUG) {
            if (scheme == "https") return null
            if (scheme == "http" && host in loopbackHosts) return null
            return "디버그 HTTP는 에뮬레이터 로컬 주소에서만 허용됩니다."
        }
        return if (scheme == "https") null else "릴리스 서버 주소는 HTTPS여야 합니다."
    }

    private fun isLoopbackHttp(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme.equals("http", ignoreCase = true) && uri.host?.lowercase() in loopbackHosts
    }

    private fun encodePathSegment(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun digest(value: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .take(8)
        .joinToString("") { byte -> "%02x".format(byte) }

    private data class HttpReply(val code: Int, val message: String, val body: String = message) {
        fun isAccepted(): Boolean {
            val json = runCatching { JSONObject(body) }.getOrNull()
            val status = json?.optString("status")
            val bookingStatus = json?.optJSONObject("booking")?.optString("status")
            return code in 200..299 && status !in setOf("unknown", "needs_configuration", "failed") &&
                bookingStatus !in setOf("unknown", "needs_configuration", "failed")
        }

        fun recordId(vararg keys: String): String? = runCatching {
            val json = JSONObject(body)
            val nestedReport = json.optJSONObject("report")
            val nestedBooking = json.optJSONObject("booking")
            keys.firstNotNullOfOrNull { key ->
                json.optString(key).takeIf(String::isNotBlank)
                    ?: nestedReport?.optString(key)?.takeIf(String::isNotBlank)
                    ?: nestedBooking?.optString(key)?.takeIf(String::isNotBlank)
            }
        }.getOrNull()
    }

    companion object {
        private val loopbackHosts = setOf("10.0.2.2", "127.0.0.1", "localhost")
    }
}
