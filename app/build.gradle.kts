plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.selfmod.agent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.selfmod.agent"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // MediaPipe GenAI 原生库体积较大，只保留 arm64-v8a。
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        // android.util.Log 等框架方法在 JVM 单测里返回默认值而不是抛 "not mocked"。
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation("com.google.android.material:material:1.12.0")
    implementation(libs.rhino)
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)
    // 本地离线推理（MediaPipe LLM Inference）。缺省只打包 arm64-v8a。
    implementation(libs.mediapipe.genai)

    testImplementation("junit:junit:4.13.2")
    // 单元测试里用真实 org.json，避免 Android stub 返回 null。
    testImplementation("org.json:json:20231013")

    debugImplementation(libs.compose.ui.tooling)
}
