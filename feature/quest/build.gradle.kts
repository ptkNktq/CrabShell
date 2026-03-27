plugins {
    id("crabshell.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:network"))
            implementation(project(":core:ui"))
        }
        wasmJsMain.dependencies {
            implementation(project(":core:auth"))
        }
    }
}
