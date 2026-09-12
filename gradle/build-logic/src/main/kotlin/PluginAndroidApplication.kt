import com.fitplan.gradle.extensions.alias
import com.fitplan.gradle.extensions.fitx
import com.fitplan.gradle.extensions.libs
import com.fitplan.gradle.extensions.plugins
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("UNUSED")
class PluginAndroidApplication : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        plugins {
            alias(libs.plugins.android.application)
            alias(fitx.plugins.android.base)
        }
    }
}
