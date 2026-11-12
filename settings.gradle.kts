pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "demo"
include(":demo")
include(":lib")

if (providers.gradleProperty("includeBenchmarks").orNull == "true") {
    include(":benchmark")
}

if (providers.gradleProperty("includeFootprint").orNull == "true") {
    include(":footprint")
}
