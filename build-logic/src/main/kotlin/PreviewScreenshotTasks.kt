import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * PreviewScreenshotGeneratorTest は PNG 生成専用テストで、アサーションを持たないため
 * 通常の jvmTest（CI がそのまま実行する）からは除外し、
 * 手動実行専用の previewScreenshotTest タスクに切り出す。除外後にテストが0件になる
 * モジュール（jvmTest が PreviewScreenshotGeneratorTest のみのモジュール）でも
 * ビルドを失敗させないよう isFailOnNoMatchingTests = false を設定する。
 *
 * feature 配下の各モジュール（CrabshellFeaturePlugin 経由）と app モジュール（Sidebar/DrawerContent が
 * app.Screen に依存し循環依存になるため例外的に build.gradle.kts へ直接配線）の双方から呼ぶ。
 */
fun Project.registerPreviewScreenshotTestTask(
    moduleName: String,
    testClassName: String,
    taskDescription: String,
) {
    tasks.named<Test>("jvmTest") {
        filter {
            excludeTestsMatching(testClassName)
            isFailOnNoMatchingTests = false
        }
    }

    // 出力先は各モジュール配下の build/preview-screenshots/（source of truth）。
    // manifest.tsv はテスト側で appendText するため、clean を挟まず再実行すると
    // 前回分の行が残って重複する。実行前に出力先を必ずクリアする。
    // （doFirst 実行時に project へアクセスするのは非推奨のため、パスは設定時に確定させる）
    val previewOutputDir = layout.projectDirectory.dir("build/preview-screenshots").asFile

    tasks.register<Test>("previewScreenshotTest") {
        group = "verification"
        description = taskDescription
        val jvmTestTask = tasks.named<Test>("jvmTest").get()
        testClassesDirs = jvmTestTask.testClassesDirs
        classpath = jvmTestTask.classpath
        filter {
            includeTestsMatching(testClassName)
            isFailOnNoMatchingTests = false
        }
        // モジュール名はテスト側で manifest.tsv のタグ付けに使う（ハードコード不要にするため注入）。
        systemProperty("previewScreenshot.module", moduleName)
        doFirst {
            previewOutputDir.deleteRecursively()
        }
    }
}
