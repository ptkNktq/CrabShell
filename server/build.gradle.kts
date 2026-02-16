plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    application
}

application {
    mainClass.set("server.ApplicationKt")
}

// run タスクの作業ディレクトリをルートプロジェクトに設定（firebase-service-account.json の解決用）
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

// fat JAR で gRPC の META-INF/services が正しくマージされるようにする
// Shadow 9.x はデフォルト DuplicatesStrategy.EXCLUDE のため明示的に INCLUDE が必要
tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>().configureEach {
    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.serialization.kotlinx.json.jvm)
    implementation(libs.logback.classic)
    implementation(libs.firebase.admin)
    implementation(libs.webauthn4j.core)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
}

// Compose Wasm フロントエンドのビルド出力をサーバーの静的リソースにコピー
val copyWasmFrontend by tasks.registering(Copy::class) {
    dependsOn(":web-frontend:wasmJsBrowserDistribution")
    from(project(":web-frontend").layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
    into(layout.buildDirectory.dir("resources/main/static"))
}

// -PskipFrontend を指定すると WASM フロントエンドのビルド・コピーをスキップ
if (!project.hasProperty("skipFrontend")) {
    tasks.named("processResources") {
        dependsOn(copyWasmFrontend)
    }
}
