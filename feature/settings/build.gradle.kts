plugins {
    id("crabshell.feature")
    alias(libs.plugins.aboutlibraries)
}

// OSS ライセンス一覧の JSON を compose-resources として読み込むためのリソースクラス生成
compose.resources {
    packageOfResClass = "feature.settings.generated"
    generateResClass = always
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:auth"))
            implementation(project(":core:common"))
            implementation(project(":core:network"))
            implementation(project(":core:ui"))
            implementation(project(":shared"))
            // OSS ライセンス情報のパース。UI 同梱版 (compose-m3) は Popup を使うため core のみ採用
            implementation(libs.aboutlibraries.core)
        }
        jvmTest.dependencies {
            // ImageComposeScene によるオフスクリーンレンダリングに必要な Skiko のネイティブライブラリ
            implementation(compose.desktop.currentOs)
        }
    }
}

// PreviewScreenshotGeneratorTest は @Preview 本導入を判断するための PoC で、
// アサーションを持たず PNG を生成するだけなので、通常の jvmTest（CI がそのまま実行する）
// からは除外し、手動実行専用の previewScreenshotTest タスクに切り出す。
tasks.named<Test>("jvmTest") {
    filter {
        excludeTestsMatching("feature.settings.PreviewScreenshotGeneratorTest")
    }
}

tasks.register<Test>("previewScreenshotTest") {
    group = "verification"
    description = "settings 画面のプレビュー用スクリーンショット PNG を手動生成する（CI には含まれない）"
    val jvmTestTask = tasks.named<Test>("jvmTest").get()
    testClassesDirs = jvmTestTask.testClassesDirs
    classpath = jvmTestTask.classpath
    filter {
        includeTestsMatching("feature.settings.PreviewScreenshotGeneratorTest")
        isFailOnNoMatchingTests = false
    }
}

// 依存ライブラリのライセンス情報を composeResources へ書き出す。
// outputFile を composeResources 配下に向けると自動メタデータ検出は無効化されるため、export を明示する。
aboutLibraries {
    // ブラウザへ配信される wasmJs バンドルに載る依存のみ収集（jvm/desktop variant を除外）
    collect {
        filterVariants.add("wasmJs")
    }
    export {
        outputFile = file("src/commonMain/composeResources/files/aboutlibraries.json")
        variant = "wasmJs"
        prettyPrint = true
    }
}

// compose-resources の取り込みより前に JSON を必ず生成し、依存追加時も自動反映させる
// （生成物は git 管理外。タスク名は環境差を吸収するため接頭辞マッチで配線）
tasks
    .matching { task ->
        task.name.startsWith("generateResourceAccessors") ||
            task.name.startsWith("copyNonXmlValueResources") ||
            task.name.startsWith("prepareComposeResources")
    }.configureEach {
        dependsOn("exportLibraryDefinitions")
    }
