plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.phoneagent.remote"
    compileSdk = 34  // 编译SDK（满足AndroidX依赖要求，不影响运行时）

    defaultConfig {
        applicationId = "com.phoneagent.remote"
        minSdk = 21  // Android 5.0+ (与 Termux 一致)
        targetSdk = 28  // Android 9.0 (与 Termux 一致，避免 Android 10+ 的 W^X 限制)
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // 指定支持的 ABI，确保 lib 目录被创建
        // 注意：.so 文件通过 GitHub Actions 编译，不需要本地 CMake
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    // 签名配置（用于测试，生产环境应使用独立的密钥库）
    signingConfigs {
        create("release") {
            // 使用 debug 密钥进行签名（简化测试流程）
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 确保 release 版本不是 debuggable
            isDebuggable = false
            // 使用签名配置
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    
    // 使用预编译的 libtermux.so，不需要 CMake
    // .so 文件位于 app/src/main/jniLibs/{arch}/libtermux.so
    
    // 禁用 NDK 符号剥离（因为我们使用预编译的 .so 文件）
    packaging {
        jniLibs {
            useLegacyPackaging = false
            // 保留调试符号，避免 strip 警告
            keepDebugSymbols += listOf("**/*.so")
        }
        // 确保不排除任何文件
        resources {
            excludes += listOf()  // 不排除任何资源
        }
    }
    
    // 确保 jniLibs 目录被正确识别
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
    
    // Lint 配置：禁用 targetSdk 检查（不上架 Google Play）
    lint {
        disable += "ExpiredTargetSdkVersion"
        abortOnError = false
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // WebSocket (OkHttp)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON
    implementation("com.google.code.gson:gson:2.10.1")
    
    // DataStore (替代 SharedPreferences)
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // WebView
    implementation("androidx.webkit:webkit:1.10.0")
    
    // Timber (日志)
    implementation("com.jakewharton.timber:timber:5.0.1")
    
    // WorkManager (后台任务调度)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

