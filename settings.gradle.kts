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

rootProject.name = "Island"

include(":app")
include(":core:model")
include(":engine")
include(":data")
include(":service:listener")
include(":service:overlay")
include(":ui:island")
include(":ui:settings")
