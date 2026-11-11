package ai.yaho.hyundai.ui

import androidx.compose.runtime.*
import android.os.SystemClock
import ai.yaho.hyundai.core.*
import ai.yaho.hyundai.data.RoadFeedItem
import ai.yaho.hyundai.vehicle.VehicleContext
import kotlinx.coroutines.delay

@Composable
internal fun rememberDrivingPriority(warnings:VehicleWarningAssessment, roads:List<RoadFeedItem>,
    feedReceivedElapsed:Long?, feedError:String?, location:VehicleContext?, allowRoad:Boolean, rain:String?, visibility:DrivingNotice?=null):DrivingNotice? {
    val clock by produceState(System.currentTimeMillis() to SystemClock.elapsedRealtime()) {
        while(true) {value=System.currentTimeMillis() to SystemClock.elapsedRealtime();delay(1000)}
    }
    val locationReady=location!=null && (location.locationEvidence?.let{validLocationEvidence(it,clock.first,clock.second)}?:location.isSample)
    val grid=if(locationReady)noticeGrid(location?.latitude,location?.longitude) else null
    val evidence=roads.map { RoadNoticeEvidence(it.kind,it.gridCellId,it.status,
        runCatching{java.time.Instant.parse(it.expiresAt).toEpochMilli()}.getOrNull(),it.userSourceCount,it.simulatorSourceCount) }
    return selectDrivingNotice(warnings.active,evidence,rain,clock.first,clock.second,
        android.os.Build.HARDWARE in setOf("ranchu","goldfish"),location?.isSample!=false,
        grid,feedReceivedElapsed,allowRoad&&feedError==null,visibility)
}
