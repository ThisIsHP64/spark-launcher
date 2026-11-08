plugins {
    alias(libs.plugins.spark.feature)
}

android {
    namespace = "dev.gearturner.sparklauncher.feature.search"
}

dependencies {
    implementation(project(":core:apps"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
