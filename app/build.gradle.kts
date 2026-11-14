plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.chahu.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chahu.app"
        minSdk = 28            // 공모전 요구사항: Android API 28 이상
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Android Studio > Build > Generate Signed Bundle / APK 로 서명
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // TODO(Pleos): Pleos Connect SDK 의존성 추가 (Vehicle, NaviHelper, TextToSpeech, SpeechToText)

    testImplementation("junit:junit:4.13.2")
}
