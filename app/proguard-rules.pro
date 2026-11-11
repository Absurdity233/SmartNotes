# 保留 kotlinx.serialization 生成的序列化器
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.nexo.smartnotes.**$$serializer { *; }
-keepclassmembers class com.nexo.smartnotes.** {
    *** Companion;
}
-keepclasseswithmembers class com.nexo.smartnotes.** {
    kotlinx.serialization.KSerializer serializer(...);
}
