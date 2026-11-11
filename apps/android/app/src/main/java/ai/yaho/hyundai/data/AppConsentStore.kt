package ai.yaho.hyundai.data

import android.content.Context
import android.content.SharedPreferences

data class AppConsents(
    val reviewed: Boolean = false,
    val vehicleRead: Boolean = false,
    val location: Boolean = false,
    val manualControl: Boolean = false,
    val network: Boolean = false,
    val resumeAfterBoot: Boolean = false,
    val liveLocationNetwork: Boolean = false,
) {
    fun normalized() = copy(manualControl = vehicleRead && manualControl, network = vehicleRead && network, resumeAfterBoot = vehicleRead && resumeAfterBoot,
        liveLocationNetwork = vehicleRead && location && network && liveLocationNetwork)
}


class AppConsentStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_consent_v1", Context.MODE_PRIVATE)
    init {

        if (prefs.getBoolean("reviewed", false) && prefs.getInt("notice_version", 0) < 2) {
            prefs.edit().putBoolean("reviewed", false).putBoolean("vehicle", false)
                .putBoolean("location", false).putBoolean("control", false).putBoolean("network", false)
                .putBoolean("withdraw_a", true).putBoolean("withdraw_b", true)
                .putLong("revision", prefs.getLong("revision", 0L) + 1).commit()
        }
    }
    fun read(): AppConsents {
        if (!prefs.getBoolean("reviewed", false)) return AppConsents()
        return AppConsents(true, prefs.getBoolean("vehicle", false), prefs.getBoolean("location", false),
            prefs.getBoolean("control", false), prefs.getBoolean("network", false), prefs.getBoolean("resume_after_boot", false), prefs.getBoolean("live_location_network", false)).normalized()
    }
    fun write(value: AppConsents) {
        val previous = read()
        val choice = value.normalized()
        val withdraw = choice.reviewed && (!choice.network || (previous.liveLocationNetwork && !choice.liveLocationNetwork))
        prefs.edit().putBoolean("reviewed", choice.reviewed).putBoolean("vehicle", choice.vehicleRead)
            .putInt("notice_version", 2)
            .putBoolean("location", choice.location).putBoolean("control", choice.manualControl)
            .putBoolean("network", choice.network).putBoolean("resume_after_boot", choice.resumeAfterBoot).putBoolean("live_location_network", choice.liveLocationNetwork).putLong("changed_at", System.currentTimeMillis())
            .putBoolean("withdraw_a", withdraw || prefs.getBoolean("withdraw_a", false))
            .putBoolean("withdraw_b", withdraw || prefs.getBoolean("withdraw_b", false))
            .putLong("revision", prefs.getLong("revision", 0L) + 1L).commit()
    }
    fun withdrawalPending(variant: ai.yaho.hyundai.core.ExperimentVariant) = prefs.getBoolean("withdraw_${variant.wireKey}", false)
    fun revision() = prefs.getLong("revision", 0L)
    fun acknowledgeWithdrawal(variant: ai.yaho.hyundai.core.ExperimentVariant, revision: Long) {
        if (revision() == revision && withdrawalPending(variant)) prefs.edit().putBoolean("withdraw_${variant.wireKey}", false).commit()
    }
    fun listen(callback: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "revision") callback() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }
    fun unlisten(listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(listener)
}
