import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class CrabshellFeaturePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("crabshell.compose.wasmjs")

            val compose = ComposePlugin.Dependencies(this)
            val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
            val featureBundle = libs.findBundle("feature").get()

            extensions.configure<KotlinMultiplatformExtension>("kotlin") {
                sourceSets.getByName("commonMain") {
                    dependencies {
                        implementation(compose.runtime)
                        implementation(compose.foundation)
                        implementation(compose.material3)
                        implementation(compose.ui)
                        implementation(compose.materialIconsExtended)
                        implementation(featureBundle)
                    }
                }
                sourceSets.getByName("jvmTest") {
                    dependencies {
                        implementation(kotlin("test"))
                        implementation(libs.findLibrary("mockk").get())
                        implementation(libs.findLibrary("kotlinx-coroutines-test").get())
                        // ImageComposeScene によるオフスクリーンレンダリングに必要な Skiko のネイティブライブラリ
                        implementation(compose.desktop.currentOs)
                    }
                }
            }

            // PreviewScreenshotGeneratorTest は @Preview 本導入を判断するための PNG 生成専用テストで、
            // アサーションを持たないため通常の jvmTest（CI がそのまま実行する）からは除外し、
            // 手動実行専用の previewScreenshotTest タスクに切り出す。除外後にテストが0件になる
            // モジュール（jvmTest が PreviewScreenshotGeneratorTest のみのモジュール）でも
            // ビルドを失敗させないよう isFailOnNoMatchingTests = false を設定する。
            val previewTestClass = "feature.${target.name}.PreviewScreenshotGeneratorTest"
            tasks.named<Test>("jvmTest") {
                filter {
                    excludeTestsMatching(previewTestClass)
                    isFailOnNoMatchingTests = false
                }
            }
            tasks.register<Test>("previewScreenshotTest") {
                group = "verification"
                description = "手動実行専用: プレビュー用スクリーンショット PNG を生成する（CI には含まれない）"
                val jvmTestTask = tasks.named<Test>("jvmTest").get()
                testClassesDirs = jvmTestTask.testClassesDirs
                classpath = jvmTestTask.classpath
                filter {
                    includeTestsMatching(previewTestClass)
                    isFailOnNoMatchingTests = false
                }
            }
        }
    }
}
