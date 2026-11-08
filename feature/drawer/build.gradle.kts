plugins {
    alias(libs.plugins.spark.feature)
}

android {
    namespace = "dev.gearturner.sparklauncher.feature.drawer"
}

dependencies {
    implementation(project(":core:apps"))
    implementation(project(":core:icons"))
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
}

dependencies {
    // the drawer only reads which apps offer widgets, to show a widgets entry in their menu
    implementation(project(":feature:widgets"))
    implementation(project(":feature:search"))
}
