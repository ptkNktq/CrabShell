plugins {
    id("crabshell.compose.wasmjs")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:auth"))
            implementation(project(":core:network"))
            implementation(project(":core:ui"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)

            implementation(libs.bundles.koin)
            implementation(libs.lifecycle.viewmodel.compose)
        }
    }
}
