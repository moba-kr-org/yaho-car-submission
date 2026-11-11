package ai.yaho.hyundai.core


data class DrivingNotice(val key: String, val priority: Int, val text: String)
data class RoadNoticeEvidence(val kind: String, val grid: String, val status: String, val expiresAt: Long?,
    val users: Int, val simulations: Int)


fun noticeGrid(lat: Double?, lng: Double?): String? {
    if (lat == null || lng == null || !lat.isFinite() || !lng.isFinite() || lat !in -90.0..90.0 || lng !in -180.0..180.0 || (lat==0.0 && lng==0.0)) return null
    val step=300.0/111_320.0
    val row=kotlin.math.floor((lat+90)/step).toLong()
    val center=(-90+(row+0.5)*step).coerceIn(-90.0,90.0)
    val longitudeStep=step/kotlin.math.max(kotlin.math.abs(kotlin.math.cos(center*Math.PI/180)),0.05)
    val col=kotlin.math.floor((lng+180)/longitudeStep).toLong()
    return "$row:$col"
}
fun selectDrivingNotice(warnings: List<ActiveVehicleWarning>, roads: List<RoadNoticeEvidence>, rain: String?,
    now: Long, elapsed: Long, vehicleSimulation: Boolean, roadSimulation: Boolean,
    currentGrid: String?, feedReceivedElapsed: Long?, allowRoad: Boolean, visibility: DrivingNotice? = null): DrivingNotice? {
    val options=mutableListOf<DrivingNotice>()
    val prefix=if(vehicleSimulation) "시험 안내입니다. " else ""
    for(warning in warnings) {
        if(!isSupportedWarningKey(warning.key) || warning.observedAt<=0 || now-warning.observedAt !in 0..30_000) continue
        val pair=when(warning.guidance.id) {
            "power_limited" -> 100 to "차량에서 출력 제한 신호가 전달됐습니다. 계기판 안내를 먼저 확인하세요. 원인은 미확인입니다."
            "seat_belt" -> 90 to "안전벨트 경고 신호가 전달됐습니다. 탑승자의 착용 상태와 계기판 안내를 확인하세요."
            "washer_low" -> 40 to "워셔액 부족 신호가 전달됐습니다. 안전하게 정차한 뒤 해당 차량의 보충 방법을 확인하세요."
            else -> null
        } ?: continue
        options+=DrivingNotice("vehicle:${warning.guidance.id}",pair.first,prefix+pair.second)
    }
    val feedFresh=feedReceivedElapsed!=null && feedReceivedElapsed>=0 && elapsed-feedReceivedElapsed in 0..30_000
    if(allowRoad && currentGrid!=null && feedFresh) for(road in roads) {
        if(road.grid!=currentGrid || road.expiresAt==null || road.expiresAt<=now || road.status !in setOf("unverified","corroborated","conflicted")) continue

        if(if(roadSimulation) road.simulations<=0 || road.users!=0 else road.users<=0 || road.simulations!=0) continue
        val title=when(road.kind) {"accident_report"->"사고";"slippery_report"->"미끄러운 노면";"road_hazard"->"도로 위험";else->null}?:continue
        val evidence=if(road.status=="conflicted") "상충하는 제보가 있습니다. 실제 상황은 미확인입니다." else "제보가 있습니다. 실제 상황과 진행 방향은 미확인입니다."
        options+=DrivingNotice("road:${road.kind}",80,(if(roadSimulation) "시험 안내입니다. " else "")+"같은 위치 격자에 $title $evidence 주변을 확인하세요.")
    }
    if(visibility?.key=="visibility:check" && visibility.priority==60)options+=visibility
    if(rain!=null)options+=DrivingNotice("rain",20,rain)
    return options.sortedWith(compareByDescending<DrivingNotice>{it.priority}.thenBy{it.key}).firstOrNull()
}

data class GuidanceStamp(val at: Long, val key: String, val priority: Int)
class PriorityGuidanceController(private val read:()->GuidanceStamp?,private val persist:(GuidanceStamp)->Boolean,
    private val stopSpeech:()->Unit) {
    var banner:String?=null;private set
    private var candidate:DrivingNotice?=null
    private var foreground=false
    private var openedElapsed=0L
    private var serial=0L
    private var spokenSerial=-1L
    fun update(notice:DrivingNotice?,active:Boolean,now:Long,elapsed:Long):String? {
        if(candidate!=notice || foreground!=active) {
            stopSpeech();banner=null;candidate=notice;foreground=active
            val last=read()
            val higher=last!=null && now>=last.at && last.at>0 && notice!=null && notice.priority>last.priority && notice.key!=last.key
            val permitted=mayPresentRainNotice(notice!=null,last?.at,now) || higher
            if(active && notice!=null && elapsed>=0 && permitted && persist(GuidanceStamp(now,notice.key,notice.priority))) {
                serial++;openedElapsed=elapsed;banner=notice.text
            }
        }
        if(banner!=null && (!active || notice==null || elapsed<openedElapsed || elapsed-openedElapsed>=12_000)) {banner=null;stopSpeech()}
        return banner
    }
    val canRead:Boolean get()=foreground && banner!=null && spokenSerial!=serial
    fun consumeSpeech(ready:Boolean):String? {
        if(!ready || !canRead)return null
        spokenSerial=serial;return banner
    }
    fun dismiss(){banner=null;stopSpeech()}
}
