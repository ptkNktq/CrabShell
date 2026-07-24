import org.gradle.api.tasks.testing.Test

plugins {
    id("crabshell.compose.wasmjs")
}

// ビルド時にコミットハッシュを BuildConfig.kt として生成
// 環境変数 COMMIT_HASH があればそれを使い、なければ git から取得
val commitHashProvider: Provider<String> =
    providers
        .environmentVariable("COMMIT_HASH")
        .orElse(
            providers
                .exec {
                    commandLine("git", "rev-parse", "--short", "HEAD")
                    workingDir = rootProject.projectDir
                }.standardOutput
                .asText
                .map { it.trim() },
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
                outputFileName = "app.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)

            implementation(project(":core:ui"))
        }
        wasmJsMain {
            kotlin.srcDir(generateBuildConfig.map { it.outputs.files.singleFile })
        }
        wasmJsMain.dependencies {
            implementation(compose.components.resources)

            // Ktor WASM engine（wasmJs ターゲットの実行に必要）
            implementation(libs.ktor.client.js.wasm)

            // Koin DI
            implementation(libs.koin.core)
            implementation(libs.koin.compose)

            // モジュール依存
            implementation(project(":core:auth"))
            implementation(project(":core:network"))
            implementation(project(":feature:auth"))
            implementation(project(":feature:dashboard"))
            implementation(project(":feature:feeding"))
            implementation(project(":feature:money"))
            implementation(project(":feature:payment"))
            implementation(project(":feature:report"))
            implementation(project(":feature:quest"))
            implementation(project(":feature:settings"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            // ImageComposeScene によるオフスクリーンレンダリングに必要な Skiko のネイティブライブラリ
            implementation(compose.desktop.currentOs)
        }
    }
}

// Sidebar は app.Screen に依存する app 固有のナビゲーションコンポーネントで、循環依存になるため
// feature モジュールからは参照できない。「画面単位」のスクリーンショット対象からは外れるが、
// ナビゲーションの見た目を確認する価値があるため例外的にここで生成する
// （仕組みは build-logic/CrabshellFeaturePlugin.kt の previewScreenshotTest と同じ）。
val previewTestClass = "app.PreviewScreenshotGeneratorTest"
tasks.named<Test>("jvmTest") {
    filter {
        excludeTestsMatching(previewTestClass)
        isFailOnNoMatchingTests = false
    }
}
tasks.register<Test>("previewScreenshotTest") {
    group = "verification"
    description = "手動実行専用: Sidebar のプレビュー用スクリーンショット PNG を生成する（CI には含まれない）"
    val jvmTestTask = tasks.named<Test>("jvmTest").get()
    testClassesDirs = jvmTestTask.testClassesDirs
    classpath = jvmTestTask.classpath
    filter {
        includeTestsMatching(previewTestClass)
        isFailOnNoMatchingTests = false
    }
    systemProperty("previewScreenshot.module", "app")
}
