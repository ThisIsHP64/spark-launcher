plugins {
    alias(libs.plugins.spark.android.library)
    alias(libs.plugins.spark.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.gearturner.sparklauncher.core.data"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
}
