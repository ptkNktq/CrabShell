plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    compileOnly("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${libs.versions.kotlin.get()}")
    compileOnly("org.jetbrains.compose:compose-gradle-plugin:${libs.versions.compose.multiplatform.get()}")
}

gradlePlugin {
    plugins {
        register("composeWasmJs") {
            id = "crabshell.compose.wasmjs"
            implementationClass = "CrabshellComposeWasmJsPlugin"
        }
    }
}
