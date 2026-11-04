import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val firstStepSettings = Properties().apply {
    val settingsFile = rootProject.file("firststep.local.properties")
    if (settingsFile.isFile) settingsFile.inputStream().use { load(it) }
}
fun configString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "") + "\""

android {
    namespace = "com.example.firststep"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.firststep"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", configString(firstStepSettings.getProperty("apiBaseUrl", "http://10.0.2.2:8787")))
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", configString(firstStepSettings.getProperty("googleWebClientId", "")))
        }
        release {
            buildConfigField("String", "API_BASE_URL", configString(firstStepSettings.getProperty("releaseApiBaseUrl", "")))
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", configString(firstStepSettings.getProperty("googleWebClientId", "")))
            optimization {
                enable = false
            }
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.room:room-runtime:2.7.2")
    annotationProcessor("androidx.room:room-compiler:2.7.2")
    implementation("com.google.ar:core:1.48.0")
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
