plugins {
    id("crabshell.feature")
}

kotlin {
    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:network"))
            implementation(project(":core:ui"))
            implementation(project(":shared"))
        }
    }
}
