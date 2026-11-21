# Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.karmakameleon.**$$serializer { *; }
-keepclassmembers class com.karmakameleon.** {
    *** Companion;
}
-keepclasseswithmembers class com.karmakameleon.** {
    kotlinx.serialization.KSerializer serializer(...);
}
