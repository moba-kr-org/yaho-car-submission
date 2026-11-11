package ai.yaho.hyundai.data

import ai.yaho.hyundai.core.*
import android.content.Context
import java.time.Year


class VehicleManualProfileStore(context: Context) {
    private val prefs=context.applicationContext.getSharedPreferences("yaho_manual_profile",Context.MODE_PRIVATE)
    fun read(): VehicleManualProfile? = parseManualProfile(prefs.getString("model","").orEmpty(),prefs.getString("year","").orEmpty(),prefs.getString("market","").orEmpty(),Year.now().value)
    fun write(profile: VehicleManualProfile): Boolean {
        require(parseManualProfile(profile.model,profile.year.toString(),profile.market,Year.now().value)==profile)
        return prefs.edit().putString("model",profile.model).putString("year",profile.year.toString()).putString("market",profile.market).commit()
    }
    fun clear(): Boolean = prefs.edit().clear().commit()
}
