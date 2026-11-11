import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val debugApiUrl = providers.gradleProperty("yahoApiBaseUrl")
    .orElse("http://10.0.2.2:4180")
    .get()
val releaseApiUrl = providers.gradleProperty("yahoReleaseApiBaseUrl")
    .orElse(providers.environmentVariable("YAHO_RELEASE_API_BASE_URL"))
    .getOrElse("")

fun String.asBuildConfigLiteral(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val signingPropertiesFile = rootProject.file(".env.signing")
val localSigningProperties = Properties().apply {
    if (signingPropertiesFile.isFile) signingPropertiesFile.inputStream().use(::load)
}
fun signingValue(envName: String): String? =
    providers.environmentVariable(envName).orNull ?: localSigningProperties.getProperty(envName)

val releaseStoreFile = signingValue("YAHO_ANDROID_RELEASE_KEYSTORE")
val releaseStorePassword = signingValue("YAHO_ANDROID_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("YAHO_ANDROID_RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("YAHO_ANDROID_RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "ai.yaho.hyundai"
    compileSdk = 35
    useLibrary("android.car", false)

    defaultConfig {
        applicationId = "ai.yaho.hyundai"
        minSdk = 28
        targetSdk = 35
        versionCode = 3
        versionName = "0.1.2-preview"

        testInstrumentationRunner = "ai.yaho.hyundai.ObservationProbeInstrumentation"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
                storeType = "PKCS12"
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", debugApiUrl.asBuildConfigLiteral())
        }
        release {
            isMinifyEnabled = true
            buildConfigField("String", "API_BASE_URL", releaseApiUrl.asBuildConfigLiteral())
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    doFirst {
        check(hasReleaseSigning) {
            "Release artifacts must be signed. Configure YAHO_ANDROID_RELEASE_* via environment or apps/android/.env.signing."
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    debugImplementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.0.21")

    implementation("com.google.zxing:core:3.5.3")

    debugImplementation("org.maplibre.gl:android-sdk:11.11.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
