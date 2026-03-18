plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()
    wasmJs {
        browser()
    }
}
