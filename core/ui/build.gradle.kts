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
    implementation(project(":core:icons"))
    implementation(libs.compose.material.icons.extended)
}
