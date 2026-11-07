plugins {
    alias(libs.plugins.spark.android.library)
    alias(libs.plugins.spark.android.compose)
    alias(libs.plugins.spark.hilt)
}

android {
    namespace = "dev.gearturner.sparklauncher.widgetpack"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:theme"))
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
}
