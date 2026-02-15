plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.ktlint) apply false
}

// 全サブプロジェクトに ktlint を適用
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        filter {
            // Gradle が自動生成するファイルを除外
            exclude { it.file.path.contains("/build/") }
            exclude { it.file.path.contains("generated") }
        }
    }

    // Koin が依存する kotlin-stdlib-wasm-js のバージョンをコンパイラと一致させる
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-stdlib")) {
                useVersion(libs.versions.kotlin.get())
            }
        }
    }
}
