plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.malaxg.milo.core.domain"
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
    // api：让上层模块无需重复声明即可拿到 Flow 与模型
    api(project(":core:common"))
    api(libs.kotlinx.coroutines.core)
}
