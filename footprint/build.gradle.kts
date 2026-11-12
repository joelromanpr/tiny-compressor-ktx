plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.joelromanpr.tinycompressor.footprint"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.joelromanpr.tinycompressor.footprint"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    flavorDimensions += "dependencies"
    productFlavors {
        create("baseline") { dimension = "dependencies" }
        create("withCompressor") { dimension = "dependencies" }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    "withCompressorImplementation"(project(":lib"))
}
