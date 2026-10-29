plugins {
    // AGP 9 已内置 Kotlin 支持，无需再应用 org.jetbrains.kotlin.android
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.malaxg.milo.core.common"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // 纯 Kotlin 模块，无第三方依赖
}
