plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.tds.binarystars"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.tds.binarystars"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Google Auth - Android Client ID
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"61729786326-qo9frvgb3.apps.googleusercontent.com\"")
        
        // Microsoft Auth (Azure AD)
        buildConfigField("String", "MICROSOFT_CLIENT_ID", "\"c727b034-bd56-49a1c73\"")
        buildConfigField("String", "MICROSOFT_TENANT_ID", "\"beef35aa-e9a2-7bf1\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}