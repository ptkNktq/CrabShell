plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()
    wasmJs {
        browser()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
    }
}
