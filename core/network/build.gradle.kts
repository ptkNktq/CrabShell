plugins {
    id("crabshell.compose.wasmjs")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:auth"))
            implementation(project(":shared"))

            implementation(libs.bundles.ktor.client)

            implementation(libs.koin.core)
        }
    }
}
