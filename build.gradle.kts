plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.ktlint)
}

subprojects {
    // --- ktlint ---
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        filter {
            // Gradle が自動生成するファイルを除外
            exclude { it.file.path.contains("/build/") }
            exclude { it.file.path.contains("generated") }
        }
    }

    // --- Kotlin stdlib バージョン統一 ---
    // Koin 4.2.0-RC1 は Kotlin 2.3.20-Beta1 でビルドされており、
    // kotlin-stdlib-wasm-js 2.3.20-Beta1 を推移的に要求する。
    // wasmJs ターゲットでは stdlib とコンパイラのバージョンが一致しないとビルドが失敗するため、
    // stdlib をプロジェクトの Kotlin バージョンに強制する。
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-stdlib")) {
                useVersion(libs.versions.kotlin.get())
            }
        }
    }
}

// --- プレビュー用スクリーンショットの集約 ---
// 各 feature モジュールの previewScreenshotTest は自分の build/preview-screenshots/ に
// PNG と manifest.tsv（file, module, screen, size のタブ区切り）を出力する（source of truth）。
// このタスクは全モジュール分の previewScreenshotTest を実行させたうえで、その出力を
// ルートの build/preview-screenshots/ に集約コピーし、フィルタ・拡大表示可能な index.html を生成する。
val previewScreenshotIndex =
    tasks.register("previewScreenshotIndex") {
        group = "verification"
        description = "手動実行専用: 全 feature モジュールのプレビュー用スクリーンショットを集約し index.html を生成する（CI には含まれない）"
    }

// サブプロジェクトの `previewScreenshotTest` タスクはそれぞれのビルドスクリプト評価後にしか
// 存在しないため、全プロジェクト評価完了後（projectsEvaluated）に依存関係と処理内容を配線する。
gradle.projectsEvaluated {
    val moduleTasks =
        subprojects.mapNotNull { sub -> sub.tasks.findByName("previewScreenshotTest")?.let { sub to it } }

    previewScreenshotIndex.configure {
        dependsOn(moduleTasks.map { it.second })

        doLast {
            val outputDir =
                layout.buildDirectory
                    .dir("preview-screenshots")
                    .get()
                    .asFile
            outputDir.deleteRecursively()
            outputDir.mkdirs()

            val entries = mutableListOf<String>()
            moduleTasks.forEach { (sub, _) ->
                val moduleDir = sub.file("build/preview-screenshots")
                val manifestFile = moduleDir.resolve("manifest.tsv")
                if (!manifestFile.exists()) return@forEach

                manifestFile.readLines().forEach lines@{ line ->
                    if (line.isBlank()) return@lines
                    val parts = line.split("\t")
                    if (parts.size != 4) return@lines
                    val (file, module, screen, size) = parts

                    val sourcePng = moduleDir.resolve(file)
                    if (!sourcePng.exists()) return@lines
                    // ファイル名だけをキーに1フォルダへ集約すると、モジュール間で同名ファイルが
                    // できた場合にサイレントに上書きされてしまう。モジュール名のサブディレクトリに
                    // 分けることで、命名規約に関わらず衝突しないようにする。
                    val moduleOutputDir = outputDir.resolve(module).apply { mkdirs() }
                    val relativePath = "$module/$file"
                    sourcePng.copyTo(moduleOutputDir.resolve(file), overwrite = true)

                    entries +=
                        """{"file":"${jsonEscape(relativePath)}","tags":{"module":"${jsonEscape(module)}",""" +
                        """"screen":"${jsonEscape(screen)}","size":"${jsonEscape(size)}"}}"""
                }
            }

            val manifestJson = if (entries.isEmpty()) "[]" else "[\n  " + entries.joinToString(",\n  ") + "\n]"
            val templateFile = rootDir.resolve("gradle/preview-screenshot-index-template.html")
            val html = templateFile.readText().replace("__MANIFEST_JSON__", manifestJson)
            outputDir.resolve("index.html").writeText(html)

            logger.lifecycle("プレビュースクリーンショット index を生成しました: ${outputDir.resolve("index.html")}")
        }
    }
}

// manifest 由来の値は <script type="application/json"> ブロックへ埋め込まれるため、
// "<" もエスケープして "</script>" によるタグの早期終了を防ぐ。
fun jsonEscape(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("<", "\\u003c")
