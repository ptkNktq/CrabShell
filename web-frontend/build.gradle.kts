plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "web-frontend.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)

            // Ktor WASM engine（wasmJs ターゲットの実行に必要）
            implementation(libs.ktor.client.js.wasm)

            // モジュール依存
            implementation(project(":core:auth"))
            implementation(project(":core:ui"))
            implementation(project(":feature:auth"))
            implementation(project(":feature:dashboard"))
        }
    }
}
