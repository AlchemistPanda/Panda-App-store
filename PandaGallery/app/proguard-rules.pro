# Panda Gallery ProGuard Rules

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room entities
-keep class com.pandagallery.app.data.local.entity.** { *; }

# Keep Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Coil
-dontwarn coil3.**

# Google API
-dontwarn com.google.api.client.**
-keep class com.google.api.services.drive.** { *; }

# TensorFlow Lite (face embeddings)
-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# ---------------------------------------------------------------- navigation + serialization
# Compose Navigation's type-safe routes resolve each argument type by its fully qualified
# name at runtime. R8 renames classes by default, so without this a release build starts and
# immediately dies with "Cannot find class with name ...SmartCollection" the first time a
# route carrying an enum is built. Debug builds never see it because they are not minified.
-keep class com.pandagallery.app.ui.navigation.** { *; }

# Every @Serializable type, and the generated serializer that goes with it.
-keep @kotlinx.serialization.Serializable class com.pandagallery.app.** { *; }
-keepclassmembers class com.pandagallery.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.pandagallery.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Enums are serialized and deserialized by name, including through DataStore preferences
# and navigation arguments.
-keepclassmembers enum com.pandagallery.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}
