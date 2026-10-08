import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

/**
 * Release signing, loaded from `keystore.properties` at the repo root.
 *
 * Kept out of this file (and out of version control) so the keystore password is not
 * committed. When the file is absent the release build still assembles - unsigned - rather
 * than failing, so a clean clone can be built without the private key.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "com.pandagallery.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pandagallery.app"
        minSdk = 31
        targetSdk = 35
        versionCode = 23
        versionName = "1.3.6"
        // Surfaced in Settings › About via BuildConfig instead of a hardcoded string.

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // ML Kit, TFLite and Media3 each ship native libraries for every ABI, which
            // together accounted for well over half the APK. x86/x86_64 only exist for
            // emulators; every shipping Android phone is one of these two. Add the x86
            // variants back if you need to run a release build on an emulator.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            val storeFileName = keystoreProperties.getProperty("storeFile")
            if (storeFileName != null) {
                storeFile = rootProject.file(storeFileName)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release").takeIf {
                keystoreProperties.getProperty("storeFile") != null
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        // TFLite models are memory-mapped straight out of the APK, which only works if
        // aapt leaves them uncompressed.
        noCompress += "tflite"
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Haze (backdrop blur / glassmorphism)
    implementation(libs.haze)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    // Paging
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // DataStore
    implementation(libs.androidx.datastore.preferences)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Security & Biometric
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)

    // Media
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.heifwriter)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation("androidx.media3:media3-transformer:1.5.1")
    implementation("androidx.media3:media3-effect:1.5.1")

    // Kotlin
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Coil (Image Loading)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.coil.svg)
    implementation(libs.coil.gif)

    // Material
    implementation(libs.google.material)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.image.labeling)
    implementation(libs.mlkit.face.detection)
    implementation(libs.mlkit.segmentation.selfie)

    // Face embeddings for people grouping. The model itself is an opt-in asset --
    // see scripts/download_face_model.sh. Without it, people grouping stays disabled.
    implementation(libs.tensorflow.lite)

    // Google Drive (Phase 3)
    implementation(libs.play.services.auth)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

abstract class CopyApksTask : DefaultTask() {
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val apkFile: RegularFileProperty

    @get:Internal
    abstract val rootDir: DirectoryProperty

    @get:Internal
    abstract val buildsDir: DirectoryProperty

    @get:Input
    abstract val releaseMode: Property<Boolean>

    /**
     * The build's own `versionName`, so the versioned copy is named after the build it actually
     * contains. This used to be hardcoded to "1.0.0", which meant every release overwrote a file
     * called `PandaGallery-1.0.0.apk` with whatever the current version was — a file that lies
     * about its version is worse than no file when the APK is about to be shared with someone.
     */
    @get:Input
    abstract val versionName: Property<String>

    @TaskAction
    fun copyApks() {
        val src = apkFile.asFile.orNull
        if (src == null || !src.exists()) return

        val root = rootDir.asFile.get()
        val builds = buildsDir.asFile.get()
        builds.mkdirs()

        if (releaseMode.get()) {
            val versioned = "PandaGallery-${versionName.get()}.apk"
            src.copyTo(File(root, versioned), overwrite = true)
            src.copyTo(File(root, "PandaGallery-release.apk"), overwrite = true)
            src.copyTo(File(root, "app-release.apk"), overwrite = true)
            src.copyTo(File(builds, versioned), overwrite = true)
            src.copyTo(File(builds, "PandaGallery-release.apk"), overwrite = true)
            src.copyTo(File(builds, "app-release.apk"), overwrite = true)
            logger.lifecycle("📦 [Auto-Copy] Copied release APK to project root and builds/ as $versioned")
        } else {
            src.copyTo(File(root, "PandaGallery-debug.apk"), overwrite = true)
            src.copyTo(File(root, "app-debug.apk"), overwrite = true)
            src.copyTo(File(builds, "PandaGallery-debug.apk"), overwrite = true)
            src.copyTo(File(builds, "app-debug.apk"), overwrite = true)
            logger.lifecycle("📦 [Auto-Copy] Copied updated debug APK to project root and builds/ directory")
        }
    }
}

val copyReleaseApks = tasks.register<CopyApksTask>("copyReleaseApks") {
    apkFile.set(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    rootDir.set(layout.projectDirectory.dir(".."))
    buildsDir.set(layout.projectDirectory.dir("../builds"))
    releaseMode.set(true)
    versionName.set(android.defaultConfig.versionName ?: "unversioned")
}

val copyDebugApks = tasks.register<CopyApksTask>("copyDebugApks") {
    apkFile.set(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    rootDir.set(layout.projectDirectory.dir(".."))
    buildsDir.set(layout.projectDirectory.dir("../builds"))
    releaseMode.set(false)
    versionName.set(android.defaultConfig.versionName ?: "unversioned")
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(copyReleaseApks)
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(copyDebugApks)
}



