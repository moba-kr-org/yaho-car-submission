package ai.yaho.hyundai.vehicle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import ai.yaho.hyundai.MainActivity
import ai.yaho.hyundai.R
import ai.yaho.hyundai.data.AppConsentStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


class VehicleObservationService : Service() {
    private var collector: VehicleSnapshotCollector? = null
    private lateinit var consent: AppConsentStore
    private var listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    override fun onCreate() {
        super.onCreate()
        consent = AppConsentStore(this)
        listener = consent.listen { if (!consent.read().vehicleRead) stopSelf() }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!consent.read().vehicleRead) { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "차량 자동 관측", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, VehicleObservationService::class.java).setAction("STOP_OBSERVATION"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (intent?.action == "STOP_OBSERVATION") {
            consent.write(consent.read().copy(vehicleRead = false).normalized())
            stopSelf(); return START_NOT_STICKY
        }
        val notification = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_settings)
            .setContentTitle("야호 차량 상태 관측 중")
            .setContentText("앱 전환 중에도 관측합니다 · 설정에서 중지할 수 있습니다")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "관측 중지", stop).build()).build()
        if (android.os.Build.VERSION.SDK_INT >= 29) startForeground(2101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(2101, notification)
        if (collector == null) collector = VehicleSnapshotCollector(this) { snapshot ->
            if (consent.read().vehicleRead) current.value = snapshot
        }.also { it.start() }
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }
    override fun onDestroy() {
        collector?.release(); collector = null
        current.value = VehicleSnapshot()
        listener?.let(consent::unlisten)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        private const val CHANNEL = "vehicle_observation"
        private val current = MutableStateFlow(VehicleSnapshot())
        val snapshots = current.asStateFlow()
    }
}
