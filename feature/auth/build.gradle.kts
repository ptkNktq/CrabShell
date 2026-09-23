plugins {
    id("crabshell.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:auth"))
            implementation(project(":core:common"))
            implementation(project(":core:network"))
            implementation(project(":core:ui"))
        }
        jvmTest.dependencies {
            // SessionViewModelStoreOwnerTest で ImageComposeScene によりコンポジションを駆動するため
            implementation(compose.desktop.currentOs)
        }
    }
}
