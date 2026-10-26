import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 高德 Key 从根目录 amap.properties 读取（该文件不入库），拿到 Key 后直接填入即可
val amapProperties = Properties().apply {
    val amapFile = rootProject.file("amap.properties")
    if (amapFile.exists()) {
        amapFile.inputStream().use { load(it) }
    }
}
val amapAndroidKey: String = amapProperties.getProperty("AMAP_ANDROID_KEY", "")
val amapWebKey: String = amapProperties.getProperty("AMAP_WEB_KEY", "")

android {
    namespace = "com.example.citygo"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.citygo"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 高德地图 SDK 10.x 仅提供 arm 架构 so（arm64-v8a / armeabi-v7a），不支持 x86 / x86_64。
        // 若 APK 中混入 x86_64 目录（如 androidx.graphics.path 等带多架构的依赖），
        // 部分 ARM 真机安装时会错误选中 x86_64 ABI，导致高德 native 库无法加载并弹出
        // 「当前设备不支持高德地图渲染」降级面板。这里在打包阶段只保留 arm64-v8a，
        // 从根源避免 x86_64 so 被打入 APK。
        ndk {
            abiFilters += "arm64-v8a"
        }

        // 高德 Android Key 注入 Manifest meta-data；Web 服务 Key 注入 BuildConfig 供天气接口使用
        manifestPlaceholders["AMAP_ANDROID_KEY"] = amapAndroidKey
        buildConfigField("String", "AMAP_WEB_KEY", "\"$amapWebKey\"")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // 高德导航合包（3D地图 + 定位 + 路径规划搜索 + 导航）
    implementation(libs.amap.navi)
    // 天气 Web API 网络栈
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)
    // 餐厅列表结构化数据（Vo/Api/RetrofitClient）后续由攻略结果页直接拉取展示，
    // 不建独立餐厅 Activity / Adapter / 布局
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    // 社区游记模块图片加载
    implementation(libs.coil.compose)

    // Room 本地数据库（全国城市景点 POI 缓存）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore 简单配置持久化（深色模式开关 / 当前选中城市）
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}