plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    wasmJs {
        browser()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":core:auth"))
            implementation(project(":shared"))

            implementation(libs.bundles.ktor.client)

            implementation(libs.koin.core)
        }
    }
}
