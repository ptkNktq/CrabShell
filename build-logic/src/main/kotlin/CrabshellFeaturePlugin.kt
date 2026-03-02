import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
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
                    }
                }
            }
        }
    }
}
