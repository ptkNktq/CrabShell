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
            api(project(":shared"))

            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.materialIconsExtended)
            api(compose.ui)
            api(compose.components.resources)
        }
    }
}

compose.resources {
    packageOfResClass = "core.ui.generated"
    generateResClass = always
}
