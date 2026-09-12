import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.CommonExtension
import com.fitplan.gradle.extensions.alias
import com.fitplan.gradle.extensions.android
import com.fitplan.gradle.extensions.api
import com.fitplan.gradle.extensions.debugApi
import com.fitplan.gradle.extensions.implementation
import com.fitplan.gradle.extensions.libs
import com.fitplan.gradle.extensions.plugins
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

@Suppress("UNUSED")
class PluginComposeAndroid : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.kotlin.compose.compiler)
        }

        android {
            buildFeatures {
                compose = true
            }
        }

        dependencies {
            implementation(platform(libs.androidx.compose.bom))

            // Compose @Preview tooling
            api(libs.androidx.compose.uiToolingPreview)
            debugApi(libs.androidx.compose.uiTooling)
        }
    }
}

private fun CommonExtension.buildFeatures(block: BuildFeatures.() -> Unit) {
    buildFeatures.apply(block)
}
