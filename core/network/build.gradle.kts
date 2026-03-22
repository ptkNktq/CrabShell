plugins {
    id("crabshell.compose.wasmjs")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:auth"))
            implementation(project(":shared"))

            implementation(libs.bundles.ktor.client)

            implementation(libs.koin.core)
        }
    }
}
