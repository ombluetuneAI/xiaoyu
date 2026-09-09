plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.xiaoyu.service"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
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
    implementation(project(":core-session"))
    implementation(project(":core-voice"))
    implementation(project(":core-media"))
    implementation(project(":core-mcp"))
    implementation(project(":core-router"))
    implementation(project(":core-registry"))
    implementation(project(":core-link"))
    implementation(project(":core-wake"))
    implementation(libs.okhttp)
    implementation(libs.media3.session)
    implementation(libs.media3.exoplayer)
    implementation("androidx.media:media:1.7.0")
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
}
