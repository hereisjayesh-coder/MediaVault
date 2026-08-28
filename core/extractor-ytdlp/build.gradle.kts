plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.chaquopy)
}

android {
    namespace = "com.mediavault.core.extractor.ytdlp"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Chaquopy's Python runtime is native; only 64-bit ABIs are supported for the
            // Python version pinned below, which matches every device MediaVault targets.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // The androidTest APK bundles Chaquopy's native libs independently of :app,
        // and needs the same uncompressed packaging to load them.
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

chaquopy {
    defaultConfig {
        // Must match the build machine's Python major.minor version.
        version = "3.14"
        pip {
            // Pinned so each extraction-engine version is reproducible and independent of
            // the app version, per the ExtractorEngine architecture rule. Both yt-dlp and
            // Instaloader are installed here, in this one module — Chaquopy's Gradle plugin
            // can only be applied in a single module per app (confirmed the hard way: applying
            // it a second time in a separate core:extractor-instaloader module built
            // successfully but silently dropped that module's Python source at runtime,
            // `ModuleNotFoundError: No module named 'mediavault_instaloader'` — see
            // PROJECT_MASTER.md's decision log). `InstaloaderExtractorEngine` still lives in
            // its own Kotlin package (`com.mediavault.core.extractor.instaloader`) for code
            // separation; only the Gradle module and Python/pip environment are shared.
            install("yt-dlp==2026.8.19")
            install("instaloader==4.15.3")
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))

    // @Inject / @ApplicationContext only — the Hilt Gradle plugin and annotation
    // processor run in :app, not here.
    implementation(libs.hilt.android)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
