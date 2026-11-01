// Imported here: inside the android {} block the word "java" means the
// project's Java settings, not the java.* package, so java.time.… doesn't resolve.
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    kotlin("multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(21)

    androidTarget()

    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.runtime)
                implementation("androidx.activity:activity-compose:1.9.0")
                implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
                implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
                implementation("androidx.core:core-ktx:1.13.1")
                // SqlDriver / QueryResult types, used directly by the backup (BackupManager).
                implementation("app.cash.sqldelight:runtime:2.0.2")
                // Google's ready-made barcode/QR scanner screen (runs in Play services, no camera permission).
                implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }
    }
}

// ---- Version: single source of truth is gradle.properties (appVersionName /
// appVersionCode). The GitHub release workflow checks that the release tag
// (v0.6.0) matches appVersionName.
val appVersionName = providers.gradleProperty("appVersionName").get()
val appVersionCode = providers.gradleProperty("appVersionCode").get().toInt()
// Debug builds get their own app id (".debug") so they live next to the real
// app on the phone with their own data. Empty value = same id as the real app.
val debugAppIdSuffix = providers.gradleProperty("debugAppIdSuffix").orNull.orEmpty()

// ---- Release signing. Never committed to git. Values come from either
//  - environment variables (GitHub Actions: from the repository secrets), or
//  - keystore.properties in the project root (optional, for local release builds):
//      storeFile=/Users/<you>/FoodDiary-release.jks
//      storePassword=...
//      keyAlias=fooddiary
//      keyPassword=...
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(property: String, env: String): String? =
    keystoreProperties.getProperty(property)?.takeIf { it.isNotBlank() } ?: System.getenv(env)?.takeIf { it.isNotBlank() }
val releaseStoreFile = signingValue("storeFile", "FOODDIARY_KEYSTORE_PATH")

android {
    namespace = "com.fooddiary.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fooddiary.android"
        minSdk = 26
        targetSdk = 34
        // versionCode must grow with every release: 0.6.0 -> 600, 0.6.1 -> 601, 1.0.0 -> 10000.
        versionCode = appVersionCode
        versionName = appVersionName
        manifestPlaceholders["appLabel"] = "Дневник питания"

        // Shown in «Настройки → О приложении». Set at build time, local time of the build machine.
        val buildTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "FOODDIARY_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "FOODDIARY_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "FOODDIARY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (debugAppIdSuffix.isNotEmpty()) {
                applicationIdSuffix = debugAppIdSuffix
                manifestPlaceholders["appLabel"] = "Дневник (debug)"
            }
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            // Code shrinking off for now: one less thing that can break the first releases.
            isMinifyEnabled = false
            // Without a key (e.g. a local build with no keystore.properties) the
            // release APK is simply unsigned; the GitHub workflow refuses that.
            signingConfig = if (releaseStoreFile != null) signingConfigs.getByName("release") else null
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
