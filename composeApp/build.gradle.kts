import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(project(":shared"))
            implementation(libs.coroutines.core)
            implementation(libs.androidx.lifecycle.runtime.compose)
            // Needed to build an HttpClient around the shared expect/actual engine.
            implementation(libs.ktor.client.core)
            // The shared API clients' default `Json` arg resolves at the call site.
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.work.runtime.ktx)
            // Fused location for the SOS alert (CAP-001 / REQ-SOS-003).
            implementation(libs.play.services.location)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }
    }
}

android {
    namespace = "com.diabecarekids.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.diabecarekids.app"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // CI injects these via `-PversionCode`/`-PversionName` (see Makefile
        // `assemble-release`). Safe defaults keep local/debug builds unchanged.
        versionCode = (project.findProperty("versionCode") as? String)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as? String) ?: "1.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Opt-in release signing. No `-PreleaseStorePath` → no "releaseCi" config,
    // so local/keystore-less builds stay unsigned. CI opts in via `SIGNING_ARGS`.
    signingConfigs {
        if (project.findProperty("releaseStorePath") != null) {
            create("releaseCi") {
                storeFile = file(project.findProperty("releaseStorePath") as String)
                storePassword = project.findProperty("releaseStorePassword") as? String
                keyAlias = project.findProperty("releaseKeyAlias") as? String
                keyPassword = project.findProperty("releaseKeyPassword") as? String
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // null (no releaseCi config) → release APK is unsigned.
            signingConfig = signingConfigs.findByName("releaseCi")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
