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
        commonMain.dependencies {
            implementation(project(":core:ui"))
            implementation(project(":shared"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)

            implementation(libs.koin.core)
        }
        wasmJsMain.dependencies {
            implementation(project(":core:auth"))
            implementation(project(":core:network"))

            implementation(libs.ktor.client.core)

            implementation(libs.bundles.koin)
            implementation(libs.lifecycle.viewmodel.compose)
        }
    }
}
