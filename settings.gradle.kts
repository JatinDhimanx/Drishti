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
        // Mapsforge & OSM repositories
        maven { url = java.net.URI("https://jitpack.io") }
    }
}

rootProject.name = "Drishti"

// Application entry
include(":app")

// Core modules
include(":core:model")
include(":core:vision")
include(":core:inference")
include(":core:tracking")
include(":core:risk")
include(":core:feedback")
include(":core:location")
include(":core:database")

// Feature modules
include(":feature:onboarding")
include(":feature:navigation")
include(":feature:destination")
include(":feature:settings")
