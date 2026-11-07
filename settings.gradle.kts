pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "spark-launcher"

include(":app")
include(":core:model")
include(":core:data")
include(":core:apps")
include(":core:icons")
include(":core:theme")
include(":core:ui")
include(":feature:workspace")
include(":feature:drawer")
include(":feature:widgets")
include(":feature:search")
include(":feature:settings")
include(":widgetpack")
