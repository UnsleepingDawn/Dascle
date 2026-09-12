import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.CompileOptions
import com.android.build.api.dsl.DefaultConfig
import com.fitplan.gradle.configurations.configureKotlin
import com.fitplan.gradle.extensions.android
import com.fitplan.gradle.extensions.configureTest
import com.fitplan.gradle.extensions.coreLibraryDesugaring
import com.fitplan.gradle.extensions.fitx
import com.fitplan.gradle.extensions.libs
import com.fitplan.gradle.extensions.release
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

@Suppress("UNUSED")
class PluginAndroidBase : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        configureKotlin()
        configureTest()

        android {
            defaultConfig {
                minSdk = fitx.versions.android.sdk.min.get().toInt()
                if (this is ApplicationDefaultConfig) {
                    targetSdk = fitx.versions.android.sdk.target.get().toInt()
                }
            }

            compileSdk {
                version = release(fitx.versions.android.sdk.compile)
            }

            compileOptions {
                isCoreLibraryDesugaringEnabled = true
            }
        }

        dependencies {
            coreLibraryDesugaring(libs.android.desugar)
        }
    }
}

private fun CommonExtension.defaultConfig(block: DefaultConfig.() -> Unit) {
    defaultConfig.apply(block)
}

private fun CommonExtension.compileOptions(block: CompileOptions.() -> Unit) {
    compileOptions.apply(block)
}
