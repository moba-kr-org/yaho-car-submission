package ai.yaho.hyundai.core


data class WarningServiceDraft(val serviceType:String,val notes:String,val origin:WarningServiceOrigin)
fun prepareWarningServiceDraft(warning:ActiveVehicleWarning,current:VehicleWarningAssessment,
    collecting:Boolean,canPrepare:Boolean,simulation:Boolean):WarningServiceDraft? {
    if(!collecting||!canPrepare||!isSupportedWarningKey(warning.key))return null
    val matching=current.active.singleOrNull{it.key==warning.key&&it.observedAt==warning.observedAt}?:return null
    val service=matching.guidance.serviceType?.takeIf{it.isNotBlank()}?:return null
    val origin=WarningServiceOrigin(matching.key,matching.observedAt)
    val notes="${if(simulation)"에뮬레이터·합성 시험 관측" else "SDK 관측"} · ${matching.guidance.title} · SDK ${matching.key} · 관측 ${java.time.Instant.ofEpochMilli(matching.observedAt)}. 원인 미확인·현재 상태는 바뀔 수 있음. 차종에 맞는 설명서와 점검으로 추가 확인이 필요합니다."
    return WarningServiceDraft(service,notes,origin)
}
