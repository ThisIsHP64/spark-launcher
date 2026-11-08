import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")
        // ksp was twice judged up to date or restored from cache with empty output after an
        // upstream module changed, silently dropping generated hilt code and crashing the app.
        // always regenerating costs a few seconds and cannot go stale.
        tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("Kotlin") }.configureEach {
            outputs.upToDateWhen { false }
            outputs.cacheIf { false }
        }
        dependencies {
            add("implementation", libs.findLibrary("hilt-android").get())
            add("ksp", libs.findLibrary("hilt-compiler").get())
        }
    }
}
