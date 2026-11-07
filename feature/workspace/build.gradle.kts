plugins {
    alias(libs.plugins.spark.feature)
}

android {
    namespace = "dev.gearturner.sparklauncher.feature.workspace"
}

dependencies {
    implementation(project(":core:apps"))
    implementation(project(":core:icons"))
    implementation(libs.compose.material.icons.extended)
}
