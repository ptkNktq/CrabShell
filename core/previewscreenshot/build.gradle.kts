plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

dependencies {
    implementation(project(":core:ui"))

    implementation(compose.runtime)
    implementation(compose.ui)
    // ImageComposeScene によるオフスクリーンレンダリングに必要な Skiko のネイティブライブラリ
    implementation(compose.desktop.currentOs)
}
