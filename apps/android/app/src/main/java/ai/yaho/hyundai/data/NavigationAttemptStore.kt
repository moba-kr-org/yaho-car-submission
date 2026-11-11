package ai.yaho.hyundai.data

import android.content.Context


class NavigationAttemptStore(context: Context) {
    private val prefs=context.applicationContext.getSharedPreferences("yaho_navigation_attempt",Context.MODE_PRIVATE)
    fun unresolved()=prefs.getBoolean("unresolved",false)
    fun begin(): Boolean = synchronized(lock) {
        if(unresolved()) return false
        prefs.edit().putBoolean("unresolved",true).commit()
    }
    fun acknowledgeManually()=synchronized(lock) {prefs.edit().remove("unresolved").commit()}
    companion object {private val lock=Any()}
}
