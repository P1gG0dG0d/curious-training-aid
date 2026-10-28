plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.haoqi.training"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.haoqi.training"
        minSdk = 26
        targetSdk = 34
        versionCode = 19
        versionName = "0.2.16"
    }

    buildTypes {
        debug {
            // 个人自用：调试版也关掉 debuggable 标记，避免系统弹「可调试应用」兼容性警告
            isDebuggable = false
        }
        release {
            isMinifyEnabled = false
            // 个人自用：正式版也用调试密钥签名，行为与调试版一致、数据互通
            signingConfig = signingConfigs.getByName("debug")
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
}

ksp {
    // 导出 Room schema，方便以后写正式迁移、不丢用户数据
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Room（本地数据库）
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // 读取相机照片的 EXIF 方向，避免发给 AI 的图是歪的
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    // 本地单元测试里 android.jar 的 org.json 是桩（Stub），用真实现替代
    testImplementation("org.json:json:20240303")
}
