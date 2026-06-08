plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val apiHost = (project.findProperty("apiHost") as String?)
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: "10.0.2.2"

val apiPort = (project.findProperty("apiPort") as String?)
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: "5004"

android {
    namespace = "com.tds.binarystars"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tds.binarystars"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Google Auth - Android Client ID
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"61729786326-qo9frvgb3.apps.googleusercontent.com\"")
        
        // Microsoft Auth (Azure AD)
        buildConfigField("String", "MICROSOFT_CLIENT_ID", "\"c727b034-bd56-\"")
        buildConfigField("String", "MICROSOFT_TENANT_ID", "\"beef35aa-e9a2-\"")

        // API endpoints (emulator default; override with -PapiHost and optional -PapiPort)
        buildConfigField("String", "API_BASE_URL", "\"http://$apiHost:$apiPort/api/\"")
        buildConfigField("String", "WS_BASE_URL", "\"ws://$apiHost:$apiPort/ws/messaging\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        getByName("debug") {
            // Usa la ruta absoluta que usaste en la terminal
            storeFile = file("/home/dontloseyourheadsu/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "AndroidDebugKey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.work.runtime.ktx)

    // Jetpack Compose dependencies
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

tasks.register("installEmulatorDebug") {
    group = "installation"
    description = "Installs debug APK using emulator defaults (10.0.2.2:5004)."
    dependsOn(":app:installDebug")
}

tasks.register("installDeviceDebug") {
    group = "installation"
    description = "Installs debug APK for a physical device (requires -PapiHost, optional -PapiPort)."

    doFirst {
        if (!project.hasProperty("apiHost")) {
            throw GradleException("Missing -PapiHost. Example: ./gradlew :app:installDeviceDebug -PapiHost=192.168.1.20 -PapiPort=5004")
        }
    }

    dependsOn(":app:installDebug")
}

tasks.withType<Test> {
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }
}