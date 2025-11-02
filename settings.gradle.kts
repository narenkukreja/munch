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
        // YouTube helper is published to JCenter (read-only) and a JitPack fork exists.
        // Prefer JCenter for the original artifact coordinates used by the library docs.
        jcenter()
        maven("https://jitpack.io")
    }
}

rootProject.name = "Munch For Reddit"
include(":app")
