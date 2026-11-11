package ai.yaho.hyundai.core

const val VISIBILITY_WIPER = "wiper.front.state"
const val VISIBILITY_DEFROSTER = "HVAC.getHvacDefrosterState.FRONT_WINDSHIELD"
data class VisibilitySignal(val key:String,val state:String,val value:String?,val at:Long?,val elapsed:Long?)
data class VisibilityAssessment(val status:String,val wiperSamples:Int=0,val defrosterSamples:Int=0,
    val overlapMs:Long=0,val lightContext:String="하향등 상태 미확인",val climateContext:String="공조 상태 미확인",
    val notice:DrivingNotice?=null)


class VisibilityMonitor {
    private data class Sample(val elapsed:Long,val active:Boolean)
    private val series=mutableMapOf<String,MutableList<Long>>()
    private val last=mutableMapOf<String,Sample>()
    private val conflicts=mutableMapOf<String,Long>()
    private var lastClock:Long?=null
    fun clear(){series.clear();last.clear();conflicts.clear();lastClock=null}
    private fun fresh(row:VisibilitySignal?,now:Long,elapsed:Long)=row!=null &&
        observationFreshness(row.state,row.value!=null,true,row.at,row.elapsed,now,elapsed)==ObservationFreshness.FRESH
    fun update(rows:List<VisibilitySignal>,collecting:Boolean,now:Long,elapsed:Long,simulation:Boolean):VisibilityAssessment {
        if(!collecting){clear();return VisibilityAssessment("stopped")}
        if(lastClock?.let{elapsed<it}==true)clear()
        lastClock=elapsed
        series.values.forEach{samples->samples.removeAll{elapsed-it>120_000}}
        last.entries.removeAll{elapsed-it.value.elapsed>120_000}
        conflicts.entries.removeAll{elapsed-it.value>120_000}
        val groups=rows.groupBy{it.key}
        val wiper=groups[VISIBILITY_WIPER]?.singleOrNull()
        val defroster=groups[VISIBILITY_DEFROSTER]?.singleOrNull()
        val low=groups["Light.getCurrentLowBeamLightState"]?.singleOrNull()
        val light=if(fresh(low,now,elapsed)) when(low?.value){"ON"->"하향등 ON · 밝기·날씨 판정 아님";"OFF"->"하향등 OFF · 밝기·날씨 판정 아님";"DAYTIME_RUNNING"->"주간 주행 상태 응답 · 밝기·날씨 판정 아님";else->"하향등 상태 미확인"} else "하향등 상태 미확인"
        val setting=groups["HVAC.getHvacTemperature.ROW_1_LEFT"]?.singleOrNull()
        val measured=groups["HVAC.getHvacTemperatureCurrent.ROW_1_LEFT"]?.singleOrNull()
        val climate=if(fresh(setting,now,elapsed)||fresh(measured,now,elapsed)) "공조 온도 응답 있음 · 설정/측정 구분, 김서림 계산에 미사용" else "공조 상태 미확인"
        fun assessment(status:String,overlap:Long=0,notice:DrivingNotice?=null)=VisibilityAssessment(status,
            series[VISIBILITY_WIPER]?.size?:0,series[VISIBILITY_DEFROSTER]?.size?:0,overlap,light,climate,notice)
        val activeWiper=when(wiper?.value){"ON"->true;"OFF"->false;else->null}
        val activeDefrost=when(defroster?.value){"true"->true;"false"->false;else->null}
        if(!fresh(wiper,now,elapsed)||!fresh(defroster,now,elapsed)||activeWiper==null||activeDefrost==null||
            kotlin.math.abs(wiper!!.elapsed!!-defroster!!.elapsed!!)>25_000){series.clear();return assessment("unknown")}
        for((row,active) in listOf(wiper!! to activeWiper,defroster!! to activeDefrost)) {
            val at=row.elapsed!!;val previous=last[row.key]
            if(previous!=null && (at<previous.elapsed || (at==previous.elapsed&&active!=previous.active))) {
                conflicts[row.key]=previous.elapsed;series.clear();return assessment("unknown")
            }
            if(conflicts[row.key]?.let{at<=it}==true){series.clear();return assessment("unknown")}
            if(previous==null||at>previous.elapsed) {
                val samples=series.getOrPut(row.key){mutableListOf()}
                if(!active || previous?.active==false || (previous!=null&&at-previous.elapsed>25_000))samples.clear()
                if(active)samples.add(at)
                samples.removeAll{elapsed-it>120_000}
                while(samples.size>32)samples.removeAt(0)
                last[row.key]=Sample(at,active)
            }
        }
        if(!activeWiper||!activeDefrost){series.clear();return assessment("not_combined")}
        val a=series[VISIBILITY_WIPER].orEmpty();val b=series[VISIBILITY_DEFROSTER].orEmpty()
        val overlap=if(a.isNotEmpty()&&b.isNotEmpty())(minOf(a.last(),b.last())-maxOf(a.first(),b.first())).coerceAtLeast(0) else 0
        val qualified=a.size>=3&&b.size>=3&&overlap>=30_000
        val notice=if(qualified)DrivingNotice("visibility:check",60,(if(simulation)"시험 안내입니다. " else "")+
            "와이퍼와 앞유리 성에 제거의 동시 작동 신호가 이어집니다. 시야를 확인하고, 잘 보이지 않으면 안전하게 정차하세요.") else null
        return assessment(if(qualified)"check_recommended" else "observing",overlap,notice)
    }
}
