plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

// ビルド時にコミットハッシュを BuildConfig.kt として生成
val generateBuildConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildconfig")
    outputs.dir(outputDir)
    doLast {
        val commitHash = providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
        }.standardOutput.asText.get().trim()
        val file = outputDir.get().file("app/BuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package app
            |
            |object BuildConfig {
            |    const val VERSION: String = "$commitHash"
            |}
            """.trimMargin()
        )
    }
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
        wasmJsMain {
            kotlin.srcDir(generateBuildConfig.map { it.outputs.files.singleFile })
        }
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
