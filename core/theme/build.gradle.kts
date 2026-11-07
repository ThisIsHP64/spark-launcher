plugins {
    alias(libs.plugins.spark.android.library)
    alias(libs.plugins.spark.android.compose)
}

android {
    namespace = "dev.gearturner.sparklauncher.core.theme"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.materialkolor)
}
