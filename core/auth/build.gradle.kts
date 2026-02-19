plugins {
    id("crabshell.compose.wasmjs")
}

kotlin {
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
