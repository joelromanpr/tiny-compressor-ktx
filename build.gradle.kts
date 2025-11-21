// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    id("com.diffplug.spotless") version "8.0.0"
}

// Apply Spotless to the root project itself for root build.gradle.kts and settings.gradle.kts
apply(plugin = "com.diffplug.spotless")
configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlinGradle {
        target("*.kts")
        targetExclude("build/**/*.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

subprojects {
    // Apply Spotless to every subproject
    apply(plugin = "com.diffplug.spotless")
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        // Kotlin source
        kotlin {
            target("**/*.kt")
            targetExclude("${layout.buildDirectory}/**/*.kt", "**/build/**/*.kt")
            ktlint()
            licenseHeaderFile(rootProject.file("spotless/spotless.license.kt"))
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            target("**/*.kts")
            targetExclude("build/**/*.kts")
            ktlint()
            trimTrailingWhitespace()
            endWithNewline()
        }
        // XML resources
        format("xml") {
            target("**/*.xml")
            targetExclude("**/build/**/*.xml")
            licenseHeaderFile(rootProject.file("spotless/spotless.license.xml"), "(<[^!?])")
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}
