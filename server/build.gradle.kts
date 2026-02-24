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

    implementation(libs.bundles.ktor.server)
    implementation(libs.logback.classic)
    implementation(libs.firebase.admin)
    implementation(libs.webauthn4j.core)
    implementation(libs.bundles.exposed)
    implementation(libs.sqlite.jdbc)
    implementation(libs.dotenv.java)
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.ktor.openapi)
    implementation(libs.ktor.swagger.ui)

    // Ktor Client (Gemini API + Webhook 送信用)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.java)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

// Compose Wasm フロントエンドのビルド出力をサーバーの静的リソースにコピー
val copyWasmFrontend by tasks.registering(Copy::class) {
    dependsOn(":app:wasmJsBrowserDistribution")
    from(project(":app").layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
    into(layout.buildDirectory.dir("resources/main/static"))
}

// -PskipFrontend を指定すると WASM フロントエンドのビルド・コピーをスキップ
if (!project.hasProperty("skipFrontend")) {
    tasks.named("processResources") {
        dependsOn(copyWasmFrontend)
    }
}
