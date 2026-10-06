# Minification is disabled for release builds (see app/build.gradle.kts).
# These rules keep kotlinx.serialization working if it is ever turned on.

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.pandaapps.appstore.**$$serializer { *; }
-keepclassmembers class com.pandaapps.appstore.** {
    *** Companion;
}
-keepclasseswithmembers class com.pandaapps.appstore.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp platform probes
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
