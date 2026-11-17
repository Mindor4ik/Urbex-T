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

rootProject.name = "UrbexT"

include(":app")
include(":core")
include(":data")
include(":domain")
include(":feature-map")
include(":feature-objects")
include(":feature-mesh")
include(":feature-base")
include(":feature-archive")
include(":feature-profile")
include(":feature-settings")
