plugins {
    alias(libs.plugins.spark.android.application)
    alias(libs.plugins.spark.android.compose)
    alias(libs.plugins.spark.hilt)
}

android {
    namespace = "dev.gearturner.sparklauncher"

    defaultConfig {
        applicationId = "dev.gearturner.sparklauncher"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    // f-droid and reproducible builds forbid the encrypted google dependency metadata blob
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:apps"))
    implementation(project(":core:icons"))
    implementation(project(":core:theme"))
    implementation(project(":core:ui"))
    implementation(project(":feature:workspace"))
    implementation(project(":feature:drawer"))
    implementation(project(":feature:widgets"))
    implementation(project(":feature:search"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
}

// the privacy test reads the merged manifest, so it must exist before unit tests run
tasks.withType<Test>().configureEach {
    dependsOn("processDebugMainManifest", "processReleaseMainManifest")
}

dependencies {
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
}
