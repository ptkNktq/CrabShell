plugins {
    id("crabshell.feature")
}

kotlin {
    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":core:auth"))
            implementation(project(":core:network"))
            implementation(project(":core:ui"))
            implementation(project(":shared"))
        }
    }
}
