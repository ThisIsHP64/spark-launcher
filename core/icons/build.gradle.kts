plugins {
    alias(libs.plugins.spark.android.library)
    alias(libs.plugins.spark.hilt)
}

android {
    namespace = "dev.gearturner.sparklauncher.core.icons"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:apps"))
    implementation(project(":core:data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
