# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlinx Serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.zai.agent.**$$serializer { *; }
-keepclassmembers class com.zai.agent.** {
    *** Companion;
}
-keepclasseswithmembers class com.zai.agent.** {
    kotlinx.serialization.KSerializer serializer(...);
}
