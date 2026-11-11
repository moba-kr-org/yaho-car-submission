package ai.yaho.hyundai.core


class RainGuidanceController(lastPresentedAt:()->Long?,persist:(Long)->Boolean,stopSpeech:()->Unit) {
    private val delegate=PriorityGuidanceController({lastPresentedAt()?.let{GuidanceStamp(it,"rain",20)}},{persist(it.at)},stopSpeech)
    val banner:String? get()=delegate.banner
    fun update(notice:String?,active:Boolean,now:Long,elapsed:Long)=delegate.update(notice?.let{DrivingNotice("rain",20,it)},active,now,elapsed)
    val canRead:Boolean get()=delegate.canRead
    fun consumeSpeech(ready:Boolean)=delegate.consumeSpeech(ready)
    fun dismiss()=delegate.dismiss()
}
