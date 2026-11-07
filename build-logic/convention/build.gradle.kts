plugins {
    `kotlin-dsl`
}

group = "dev.gearturner.sparklauncher.buildlogic"

kotlin {
    jvmToolchain(21)
}

dependencies {
    // compileOnly keeps plugin versions owned by the root build's plugins block
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.hilt.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "spark.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "spark.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "spark.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("hilt") {
            id = "spark.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "spark.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("feature") {
            id = "spark.feature"
            implementationClass = "FeatureConventionPlugin"
        }
    }
}
