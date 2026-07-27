pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GalleryVision"

include(":app")
include(":core:model")
include(":core:common")
include(":core:database")
include(":core:datastore")
include(":core:ui")
include(":core:testing")
include(":data:mediastore")
include(":data:index")
include(":domain")
include(":inference")
include(":feature:library")
include(":feature:viewer")
