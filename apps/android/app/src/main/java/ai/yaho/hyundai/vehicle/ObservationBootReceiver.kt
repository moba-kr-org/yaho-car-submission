package ai.yaho.hyundai.vehicle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import ai.yaho.hyundai.MainActivity
import ai.yaho.hyundai.R
import ai.yaho.hyundai.core.ObservationStartup
import ai.yaho.hyundai.core.observationStartup
import ai.yaho.hyundai.data.AppConsentStore


class ObservationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val choice = AppConsentStore(context).read()
        when (observationStartup(choice.reviewed, choice.vehicleRead, choice.resumeAfterBoot, Build.VERSION.SDK_INT)) {
            ObservationStartup.DISABLED -> return
            ObservationStartup.START_SERVICE -> try {
                context.startForegroundService(Intent(context, VehicleObservationService::class.java))
            } catch (_: Exception) { remind(context) }
            ObservationStartup.USER_OPEN_REQUIRED -> remind(context)
        }
    }
    private fun remind(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("observation_restart", "차량 관측 다시 시작", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(2102, Notification.Builder(context, "observation_restart").setSmallIcon(R.drawable.ic_settings)
            .setContentTitle("야호 차량 관측을 다시 시작해 주세요")
            .setContentText("시스템 실행 제한으로 앱을 한 번 열어야 합니다")
            .setContentIntent(open).setAutoCancel(true).build())
    }
}
