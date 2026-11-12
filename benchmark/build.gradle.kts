plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.joelromanpr.tinycompressor.benchmark"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.joelromanpr.tinycompressor.benchmark"
        minSdk = 30
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val revision = providers.gradleProperty("benchmarkRevision").orElse("working-tree").get()
        require(revision.matches(Regex("[a-zA-Z0-9-]+")))
        buildConfigField("String", "REVISION", "\"$revision\"")
        buildConfigField("String", "LIBRARY_VERSION", "\"${providers.gradleProperty("VERSION_NAME").get()}\"")
    }
    buildFeatures {
        buildConfig = true
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
    implementation(project(":lib"))
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.exifinterface:exifinterface:1.3.7")
}
