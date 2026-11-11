-keepattributes *Annotation*,InnerClasses,EnclosingMethod
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class **$Companion { kotlinx.serialization.KSerializer serializer(...); }
-keep enum ai.pleos.playground.vehicle.constant.ErrorCode { *; }
-keepclassmembers class ai.pleos.playground.vehicle.callback.exception.** {
    public ai.pleos.playground.vehicle.constant.ErrorCode *();
}
