plugins {
    alias(libs.plugins.spark.android.library)
    alias(libs.plugins.spark.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.gearturner.sparklauncher.core.data"
    // migration tests read the exported schemas, so every past version stays verifiable
    sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:apps"))
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
    testImplementation(libs.androidx.test.runner)
}

// exported schemas are committed so future migrations can be generated and tested against them
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}
