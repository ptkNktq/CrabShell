plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.ktlint) apply false
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
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-stdlib")) {
                useVersion(libs.versions.kotlin.get())
            }
        }
    }
}
