plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("com.vanniktech.maven.publish") version "0.35.0"
}

android {
    namespace = "com.joelromanpr.tinycompressor"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 30
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += "-Xexplicit-api=strict"
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.8.0")
    // Needed for EXIF preservation
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // Flow is part of the public API, so consumers need this at compile time.
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Unit test deps
    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.exifinterface:exifinterface:1.3.7")
}

version = providers.gradleProperty("VERSION_NAME").get()
mavenPublishing {
    publishToMavenCentral()

    coordinates(
        groupId = providers.gradleProperty("GROUP").get(),
        artifactId = "tiny-compressor-ktx",
        version = version.toString(),
    )

    pom {
        name.set("Tiny Compressor KTX")
        description.set(
            "Kotlin image compression for Android with suspend and Flow APIs, JPEG, PNG, and WebP output, configurable dimensions and size limits, and optional EXIF preservation.",
        )
        url.set("https://github.com/joelromanpr/tiny-compressor-ktx")

        licenses {
            license {
                name.set("The MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("joelromanpr")
                name.set("Joel Roman")
                email.set("contact@joelromanpr.com")
            }
        }

        scm {
            url.set("https://github.com/joelromanpr/tiny-compressor-ktx")
            connection.set("scm:git:https://github.com/joelromanpr/tiny-compressor-ktx.git")
            developerConnection.set("scm:git:ssh://git@github.com/joelromanpr/tiny-compressor-ktx.git")
        }
    }

    // Configure signing for all publications
    signAllPublications()
}
