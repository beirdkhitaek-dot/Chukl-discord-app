plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val appVersionName = "1.0.2"
val appVersionCode = 3

android {
    namespace = "com.chukl.discord"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chukl.discord"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        ndk { abiFilters += listOf("arm64-v8a") }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        jniLibs { pickFirsts += listOf("**/libc++_shared.so") }
    }
}

dependencies {
    // Discord Social SDK (the workflow copies the .aar into app/libs).
    implementation(mapOf("name" to "discord_partner_sdk", "ext" to "aar"))
    // Needed by the SDK's built-in login flow.
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.core:core-ktx:1.13.1")
}
