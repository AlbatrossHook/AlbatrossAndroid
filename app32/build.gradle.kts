plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "qing.albatross"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "qing.albatross.demo32"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = libs.versions.versionCode.get().toInt()
        versionName = libs.versions.versionName.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(arrayOf("armeabi-v7a", "x86"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
//                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":annotation"))
    implementation(project(":core"))
    implementation(project(":demo"))
}