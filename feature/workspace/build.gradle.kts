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
    implementation(libs.androidx.activity.compose)
}

dependencies {
    // widgets are hosted on home pages, so the workspace builds on the widget host feature
    implementation(project(":feature:widgets"))
}
