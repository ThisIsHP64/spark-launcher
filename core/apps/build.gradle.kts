plugins {
    alias(libs.plugins.spark.android.library)
    alias(libs.plugins.spark.hilt)
}

android {
    namespace = "dev.gearturner.sparklauncher.core.apps"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.robolectric)
}
