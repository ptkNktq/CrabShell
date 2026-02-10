plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    application
}

application {
    mainClass.set("server.ApplicationKt")
}

// サーバー実行時の作業ディレクトリをプロジェクトルートに設定し、
// 起動前にポート 8080 を使用中のプロセスを停止する
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    doFirst {
        val port = 8080
        ProcessBuilder("lsof", "-ti:$port")
            .redirectErrorStream(true)
            .start()
            .let { proc ->
                val pids = proc.inputStream.bufferedReader().readText().trim()
                proc.waitFor()
                if (pids.isNotEmpty()) {
                    logger.lifecycle("Killing existing process on port $port (PID: $pids)")
                    ProcessBuilder("kill", *pids.lines().toTypedArray())
                        .start()
                        .waitFor()
                    Thread.sleep(1000)
                }
            }
    }
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
}

// Compose Wasm フロントエンドのビルド出力をサーバーの静的リソースにコピー
val copyWasmFrontend by tasks.registering(Copy::class) {
    dependsOn(":web-frontend:wasmJsBrowserDistribution")
    from(project(":web-frontend").layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
    into(layout.buildDirectory.dir("resources/main/static"))
}

tasks.named("processResources") {
    dependsOn(copyWasmFrontend)
}
