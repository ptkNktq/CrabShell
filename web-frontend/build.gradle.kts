plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

// ビルド時にコミットハッシュを BuildConfig.kt として生成
// 環境変数 COMMIT_HASH があればそれを使い、なければ git から取得
val commitHashProvider: Provider<String> =
    providers.environmentVariable("COMMIT_HASH")
        .orElse(
            providers.exec {
                commandLine("git", "rev-parse", "--short", "HEAD")
                workingDir = rootProject.projectDir
            }.standardOutput.asText.map { it.trim() },
        )

val generateBuildConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildconfig")
    val hash = commitHashProvider
    inputs.property("commitHash", hash)
    outputs.dir(outputDir)
    doLast {
        val commitHash = hash.get()
        val file = outputDir.get().file("app/BuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package app
            |
            |object BuildConfig {
            |    const val VERSION: String = "$commitHash"
            |}
            |
            """.trimMargin(),
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

            // Koin DI
            implementation(libs.koin.core)
            implementation(libs.koin.compose)

            // モジュール依存
            implementation(project(":core:auth"))
            implementation(project(":core:network"))
            implementation(project(":core:ui"))
            implementation(project(":feature:auth"))
            implementation(project(":feature:dashboard"))
            implementation(project(":feature:feeding"))
            implementation(project(":feature:money"))
            implementation(project(":feature:payment"))
            implementation(project(":feature:report"))
            implementation(project(":feature:settings"))
        }
    }
}
