rootProject.name = "CrabShell"

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

include(":shared")
include(":server")
include(":app")

// core モジュール
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
