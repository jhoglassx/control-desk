rootProject.name = "control-desk"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":core:domain")
include(":core:protocol")
include(":core:data")
include(":core:logging")
include(":core:testing")
include(":composeApp")
include(":agent")
