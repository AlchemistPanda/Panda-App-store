# R8 (minify + resource shrinking) is on for release builds (see app/build.gradle.kts).
#
# Nothing app-specific needs keeping today; the reflection-dependent parts are covered by
# consumer rules the libraries ship, or by AAPT:
# - kotlinx.serialization: the catalog is parsed via the explicit Catalog.serializer(), and
#   kotlinx-serialization-core ships rules keeping Companion/serializer() of @Serializable classes.
# - WorkManager (UpdateCheckWorker): work-runtime keeps ListenableWorker subclasses and their
#   (Context, WorkerParameters) constructors.
# - DataStore, OkHttp, Coil: ship their own consumer rules.
# - Activity, Service, BroadcastReceiver, FileProvider: kept by AAPT from the manifest.
# Add a rule here only for new reflection (e.g. Class.forName, enum names persisted to disk).

# OkHttp platform probes
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
