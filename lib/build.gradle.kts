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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Unit test deps
    testImplementation("junit:junit:4.13.2")
}

version = "1.0.0"
mavenPublishing {
    coordinates(
        groupId = "io.github.joelromanpr",
        artifactId = "tiny-compressor-ktx",
        version = version.toString(),
    )

    pom {
        name.set("tiny-compressor-ktx")
        description.set(
            "A tiny, modern image compression library for Android. Kotlin-first, coroutine/Flow-friendly, and Compose-ready with a small but powerful API. Sensible defaults, EXIF preservation, and efficient decoding via ImageDecoder on modern devices.",
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
            connection.set("scm:git:git://github.com/joelromanpr/tiny-compressor-ktx.git")
            developerConnection.set("scm:git:ssh://git@github.com/joelromanpr/tiny-compressor-ktx.git")
        }
    }

    // Configure signing for all publications
    signAllPublications()
}
