plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tds.binarystars"
        minSdk = 29
        targetSdk = 34
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
    implementation(libs.material)
    
    // Configured libraries
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.location)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.strikethrough)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.ext.tasklist)
    implementation(libs.markwon.html)
    implementation(libs.microsoft.msal) {
        exclude(group = "io.opentelemetry")
        exclude(group = "com.microsoft.device.display", module = "display-mask")
    }
    implementation(libs.opentelemetry.api)
    implementation(libs.maplibre)
    implementation(libs.androidx.work.runtime.ktx)

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