import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

val hasCustomReleaseKeystore = keystorePropsFile.exists() &&
    !keystoreProps.getProperty("storeFile").isNullOrBlank() &&
    rootProject.file(keystoreProps.getProperty("storeFile")).exists()

android {
    namespace = "app.stateside.env"
    // 编译针对 Android 16 (API 36)，向下兼容 Android 14/15 (API 34+)，向上兼容 Android 17 (API 37)
    compileSdk = 36

    defaultConfig {
        applicationId = "app.stateside.env"
        minSdk = 34
        targetSdk = 36
        versionCode = 120
        versionName = "1.2.0"
    }

    signingConfigs {
        if (hasCustomReleaseKeystore) {
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
            applicationIdSuffix = ""
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 若未配置自定义签名证书，自动回退使用 debug 签名以便直接编译出可上真机安装的精简版 APK
            signingConfig = if (hasCustomReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "META-INF/BCKEY.DSA"
            excludes += "META-INF/BCKEY.SF"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
        }
        jniLibs {
            // Android 15 / 16 / 17 真机强制要求 16 KB 页面对齐无压缩直映
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.kotlinx.coroutines.android)

    // 本机 127.0.0.1 无线调试 ADB 客户端与 Android 14~17 系统原生 Conscrypt TLS 1.3 + SPAKE2 配对支持
    implementation(libs.libadb.android)
    implementation(libs.bouncycastle.prov)

    // Android 14 ~ 17 系统隐藏 API 豁免（用于反射调用 CarrierConfigManager.overrideConfig 与 ILocaleManager）
    implementation(libs.hiddenapibypass)

    testImplementation(libs.junit)
}
