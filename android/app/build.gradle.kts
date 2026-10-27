import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun prop(name: String): String? = (findProperty(name) as String?)?.takeIf { it.isNotBlank() }

val abis = (prop("yerel.abis") ?: "arm64-v8a").split(',').map { it.trim() }

android {
    namespace = "app.yerel"
    compileSdk = 35
    prop("yerel.ndkVersion")?.let { ndkVersion = it }

    defaultConfig {
        applicationId = "app.yerel"
        minSdk = 28
        targetSdk = 35
        versionCode = prop("yerel.versionCode")?.toInt() ?: 1
        versionName = prop("yerel.versionName") ?: "1.0.0"
        ndk { abiFilters += abis }
        externalNativeBuild {
            cmake {
                // llama.cpp is always built optimized, also for debug APKs
                // 16 KB-page-compatible libraries for newer Android releases
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_shared", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
                // GPU (OpenCL) and NPU (Hexagon) backends, when their SDKs are available (see CI)
                for (name in listOf("YEREL_OPENCL_ROOT", "YEREL_HEXAGON_SDK_ROOT", "YEREL_HEXAGON_TOOLS_ROOT")) {
                    System.getenv(name)?.takeIf { it.isNotBlank() }?.let { arguments += "-D$name=$it" }
                }
            }
        }
    }

    signingConfigs {
        create("release") {
            // A fixed key so every CI build can be installed over the previous one.
            // Override with YEREL_KEYSTORE / YEREL_KEYSTORE_PASSWORD / YEREL_KEY_ALIAS / YEREL_KEY_PASSWORD.
            val local = Properties().apply {
                rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
            }
            fun v(env: String, key: String) = System.getenv(env)?.takeIf { it.isNotBlank() } ?: local.getProperty(key)
            storeFile = file(v("YEREL_KEYSTORE", "storeFile") ?: "yerel-release.jks")
            storePassword = v("YEREL_KEYSTORE_PASSWORD", "storePassword")
            keyAlias = v("YEREL_KEY_ALIAS", "keyAlias")
            keyPassword = v("YEREL_KEY_PASSWORD", "keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        // libraries stay uncompressed in the APK and load from there (no extracted copy on disk);
        // the engine picks the CPU backend variant by library name
        jniLibs.useLegacyPackaging = false
        resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
