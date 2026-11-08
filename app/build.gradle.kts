import java.util.Properties
import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
}

val firstStepSettings = Properties().apply {
    val settingsFile = rootProject.file("firststep.local.properties")
    if (settingsFile.isFile) settingsFile.inputStream().use { load(it) }
}
fun configString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "") + "\""

// Named H07 Firebase app only. No Google Services plugin/generated XML or default auto-init.
// Client configuration is public app metadata; never infer OAuth IDs or use legacy backend settings.
val identityConfig = mutableMapOf("appId" to "", "apiKey" to "", "projectId" to "", "webClientId" to "")
// Explicit build-only input override for isolated config-removal checks; never modifies user config.
val identityFile = file(providers.gradleProperty("h07ConfigPath").orElse("google-services.json").get())
if (identityFile.isFile) {
    try {
        val json = JsonSlurper().parseText(providers.fileContents(layout.projectDirectory.file(identityFile.absolutePath)).asText.get()) as Map<*, *>
        val project = json["project_info"] as? Map<*, *>
        val clients = json["client"] as? List<*>
        val client = clients?.mapNotNull { it as? Map<*, *> }?.singleOrNull {
            val info = it["client_info"] as? Map<*, *>
            val androidInfo = info?.get("android_client_info") as? Map<*, *>
            androidInfo?.get("package_name") == "com.example.firststep"
        }
        val info = client?.get("client_info") as? Map<*, *>
        identityConfig["appId"] = info?.get("mobilesdk_app_id") as? String ?: ""
        identityConfig["projectId"] = project?.get("project_id") as? String ?: ""
        identityConfig["apiKey"] = (client?.get("api_key") as? List<*>)?.mapNotNull { it as? Map<*, *> }
            ?.firstOrNull()?.get("current_key") as? String ?: ""
        identityConfig["webClientId"] = (client?.get("oauth_client") as? List<*>)?.mapNotNull { it as? Map<*, *> }
            ?.firstOrNull { (it["client_type"] as? Number)?.toInt() == 3 }?.get("client_id") as? String ?: ""
    } catch (_: Exception) {
        identityConfig.keys.toList().forEach { identityConfig[it] = "" }
    }
}

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
        buildConfigField("boolean", "H07_CONFIGURED", identityConfig.values.all { it.isNotBlank() }.toString())
        buildConfigField("String", "H07_APP_ID", configString(identityConfig.getValue("appId")))
        buildConfigField("String", "H07_API_KEY", configString(identityConfig.getValue("apiKey")))
        buildConfigField("String", "H07_PROJECT_ID", configString(identityConfig.getValue("projectId")))
        buildConfigField("String", "H07_WEB_CLIENT_ID", configString(identityConfig.getValue("webClientId")))
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
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
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
