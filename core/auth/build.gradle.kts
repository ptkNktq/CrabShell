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
            // compose.runtime が coroutines を推移的に提供
            api(compose.runtime)
            api(project(":shared"))
        }
        wasmJsMain.dependencies {
            implementation(libs.koin.core)
        }
    }
}
