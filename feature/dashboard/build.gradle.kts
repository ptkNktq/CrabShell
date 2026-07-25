plugins {
    id("crabshell.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:ui"))
            implementation(project(":shared"))
        }
        wasmJsMain.dependencies {
            implementation(project(":core:auth"))
            implementation(project(":core:common"))
            implementation(project(":core:network"))
        }
    }
}
