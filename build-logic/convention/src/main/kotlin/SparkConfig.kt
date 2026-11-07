import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

// single source of truth for sdk levels so every module agrees
object SparkConfig {
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 37
    // android 12 gives native wallpaper colors and the modern widget sizing apis
    const val MIN_SDK = 31
    val JAVA_VERSION = JavaVersion.VERSION_21
    val JVM_TARGET = JvmTarget.JVM_21
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.apply {
        compileSdk = SparkConfig.COMPILE_SDK
        defaultConfig.minSdk = SparkConfig.MIN_SDK
        compileOptions.sourceCompatibility = SparkConfig.JAVA_VERSION
        compileOptions.targetCompatibility = SparkConfig.JAVA_VERSION
        testOptions.unitTests.isIncludeAndroidResources = true
        testOptions.unitTests.isReturnDefaultValues = true
        lint.warningsAsErrors = false
        lint.abortOnError = true
    }
    configureKotlin()
    // hilt generates test sources in modules that have no tests yet, which gradle would treat as an error
    tasks.withType(Test::class.java).configureEach { failOnNoDiscoveredTests.set(false) }
}

internal fun Project.configureKotlin() {
    extensions.configure(KotlinBaseExtension::class.java) {
        when (this) {
            is KotlinAndroidProjectExtension -> compilerOptions {
                jvmTarget.set(SparkConfig.JVM_TARGET)
                allWarningsAsErrors.set(false)
            }
            is KotlinJvmProjectExtension -> compilerOptions {
                jvmTarget.set(SparkConfig.JVM_TARGET)
            }
        }
    }
}
