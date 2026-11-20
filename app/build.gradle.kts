plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mohdrozaimi.cinepro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mohdrozaimi.cinepro"
        minSdk = 29
        targetSdk = 35
        val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 0
        versionCode = buildNumber + 1
        versionName = "1.0.$buildNumber"
        buildConfigField("int", "BUILD_NUMBER", buildNumber.toString())
    }

    signingConfigs {
        create("cinepro") {
            storeFile = rootProject.file("keystore/cinepro.jks")
            storePassword = "cinepro123"
            keyAlias = "cinepro"
            keyPassword = "cinepro123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("cinepro")
        }
        debug {
            signingConfig = signingConfigs.getByName("cinepro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
}
