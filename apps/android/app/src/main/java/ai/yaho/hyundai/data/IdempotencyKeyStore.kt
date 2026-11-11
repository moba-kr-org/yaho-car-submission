package ai.yaho.hyundai.data

import android.content.Context
import ai.yaho.hyundai.core.ExperimentVariant
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class OperationReservation(val key: String, val createdAt: String)

class IdempotencyKeyStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun reserve(variant: ExperimentVariant, operation: String, semanticPayload: String): OperationReservation = synchronized(operationLock) {
        val prefix = "${variant.wireKey}_$operation"
        val payloadDigest = digest(semanticPayload)
        val existingKey = preferences.getString("${prefix}_key", null)
        if (existingKey != null) {
            val existingDigest = preferences.getString("${prefix}_digest", null)
            check(existingDigest == payloadDigest) {
                "A previous $operation outcome is unresolved. Retry the original request before changing it."
            }
            return OperationReservation(
                key = existingKey,
                createdAt = preferences.getString("${prefix}_created_at", null) ?: Instant.now().toString(),
            )
        }

        val reservation = OperationReservation(UUID.randomUUID().toString(), Instant.now().toString())
        val saved = preferences.edit()
            .putString("${prefix}_key", reservation.key)
            .putString("${prefix}_digest", payloadDigest)
            .putString("${prefix}_created_at", reservation.createdAt)
            .commit()
        check(saved) { "요청 식별자를 기기에 저장하지 못했습니다. 전송하지 않습니다." }
        return reservation
    }

    fun pending(variant: ExperimentVariant, operation: String): OperationReservation? = synchronized(operationLock) {
        val prefix = "${variant.wireKey}_$operation"
        val key = preferences.getString("${prefix}_key", null) ?: return null
        return OperationReservation(key, preferences.getString("${prefix}_created_at", null).orEmpty())
    }

    fun resolveIfMatches(variant: ExperimentVariant, operation: String, expectedKey: String): Boolean = synchronized(operationLock) {
        val prefix = "${variant.wireKey}_$operation"
        if (preferences.getString("${prefix}_key",null) != expectedKey) return false
        return preferences.edit().remove("${prefix}_key").remove("${prefix}_digest").remove("${prefix}_created_at").commit()
    }

    fun resolve(variant: ExperimentVariant, operation: String) = synchronized(operationLock) {
        val prefix = "${variant.wireKey}_$operation"
        preferences.edit()
            .remove("${prefix}_key")
            .remove("${prefix}_digest")
            .remove("${prefix}_created_at")
            .apply()
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    companion object {
        private val operationLock = Any()
        private const val PREFS_NAME = "yaho_hyundai_pending_operations"
    }
}
