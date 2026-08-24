pluginManagement {
    repositories {
        google()
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

rootProject.name = "MediaVault"

include(":app")
include(":core:common")
include(":core:model")
include(":core:database")
include(":core:domain")
include(":core:extractor-ytdlp")
