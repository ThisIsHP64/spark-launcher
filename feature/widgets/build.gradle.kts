plugins {
    alias(libs.plugins.spark.feature)
}

android {
    namespace = "dev.gearturner.sparklauncher.feature.widgets"
}

dependencies {
    implementation(project(":core:apps"))
    implementation(project(":core:icons"))
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.robolectric)
}
