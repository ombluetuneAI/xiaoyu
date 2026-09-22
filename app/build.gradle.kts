import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Release 签名配置（可选）：从工程根的 keystore.properties 读取，缺失则 release 回退为未签名。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystoreProps.getProperty("storeFile")?.let {
    rootProject.file(it).exists()
} ?: false

android {
    namespace = "com.xiaoyu.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.xiaoyu.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
        ndk {
            // 真机/平板仅 arm64，去掉 x86/armeabi 可大幅缩小 APK
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 与 release 使用不同 applicationId，便于在同一台设备上共存安装
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                // 未提供 keystore 时保持未签名，需手动签名后才能安装
                signingConfig
            }
        }
    }

    buildFeatures {
        viewBinding = true
    }

    androidResources {
        noCompress += listOf("onnx", "txt")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":service"))
    implementation(project(":core-link"))
    implementation(project(":core-registry"))
    implementation(project(":core-wake"))
    implementation(project(":core-voice"))
    implementation(project(":core-media"))
    implementation(project(":core-router"))
    implementation(project(":core-mcp"))
    implementation(project(":core-session"))
    implementation(libs.material)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.media3.ui)
    implementation(libs.coil)
}
