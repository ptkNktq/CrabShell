rootProject.name = "CrabShell"

pluginManagement {
    includeBuild("build-logic")
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

include(":shared")
include(":server")
include(":app")

// core モジュール
include(":core:common")
include(":core:auth")
include(":core:network")
include(":core:ui")

// feature モジュール
include(":feature:auth")
include(":feature:dashboard")
include(":feature:feeding")
include(":feature:money")
include(":feature:payment")
include(":feature:report")
include(":feature:settings")
include(":feature:quest")
include(":feature:pet-management")
