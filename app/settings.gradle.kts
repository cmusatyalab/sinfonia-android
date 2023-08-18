@file:Suppress("UnstableApiUsage")

include(":sinfonia")


pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "wireguard-android-sinfonia"

include(":tunnel")
include(":ui")
