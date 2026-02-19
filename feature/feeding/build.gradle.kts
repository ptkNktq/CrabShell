plugins {
    id("crabshell.compose.wasmjs")
}

kotlin {
    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":core:network"))
            implementation(project(":core:ui"))
            implementation(project(":shared"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)

            implementation(libs.ktor.client.core)

            implementation(libs.bundles.koin)
            implementation(libs.lifecycle.viewmodel.compose)
        }
    }
}
