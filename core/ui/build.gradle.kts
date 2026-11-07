plugins {
    alias(libs.plugins.spark.android.library)
    alias(libs.plugins.spark.android.compose)
}

android {
    namespace = "dev.gearturner.sparklauncher.core.ui"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:theme"))
    api(project(":core:icons"))
    api(project(":core:apps"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.compose.material.icons.extended)
}
