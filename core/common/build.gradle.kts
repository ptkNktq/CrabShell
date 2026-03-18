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
            implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3")
        }
    }
}
