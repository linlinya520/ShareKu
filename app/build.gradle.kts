plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.linjing.shareku"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.linjing.shareku"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "2.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // 按 ABI 拆分 APK：x86 / x86_64 / arm64-v8a / armeabi-v7a 四包，减小单包体积
    splits {
        abi {
            isEnable = true
            reset()
            include("x86", "x86_64", "arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

        signingConfigs {
        create("release") {
            storeFile = file("/root/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // JDK21 新增的 List.removeFirst/removeLast/getFirst…（SequencedCollection）
        // 在 Android 15 以下并不存在。开启 core library desugaring 后会被转写为兼容实现，
        // 避免旧系统上 NoSuchMethodError 直接闪退（本项目曾真实踩到）。
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

// Force use of ARM64 binaries for AAPT2 in Proot environment
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.android.tools.build" && requested.name == "aapt2") {
            useTarget("com.android.tools.build:aapt2:${'$'}{requested.version}:linux-aarch64")
        }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Material Color Utilities (for dynamic scheme generation)
    implementation("com.google.android.material:material:1.12.0")

    // Ktor Server（仅 CIO 引擎，Netty 已移除——代码中未使用）
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.conditional.headers)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.core)

    // Shizuku (高权限访问 /storage/emulated/0/Android/data 等受限目录)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // Miuix（MIUI 风格 UI 组件库；0.9.3 全模块：ui / preference / blur，本地修复 minCompileSdk 37->36。
    // 注：0.9.4 会拉起 compose 1.12 链（要求 AGP 9.1 + compileSdk 37），当前构建环境暂不满足；
    // 0.9.3 已包含 WindowListPopup / Dropdown / ListPopup 原生弹窗组件与 blur 模块，功能等价。）
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-blur:0.9.3")
    // Liquid Glass（苹果液态玻璃效果，本地修复 minCompileSdk 37->36）
    implementation("io.github.kyant0:backdrop-android:2.0.1")
    // backdrop 的 lens() 折射效果依赖 shapes（同样本地修复 minCompileSdk 37->36）
    implementation("io.github.kyant0:shapes-android:1.2.1")

    // Ktor Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.coil.compose)

    // Media3（本地视频壁纸播放；texture_view 模式保证画面可被液态玻璃背景层采样）
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // Core library desugaring：兼容 JDK21 API（List.removeFirst/removeLast 等），旧系统防崩溃
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
