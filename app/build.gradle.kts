plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.wonderplay"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.wonderplay"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            val path = providers.environmentVariable("WONDERPLAY_KEYSTORE").orNull
            if (path != null) {
                storeFile = file(path)
                storePassword = providers.environmentVariable("WONDERPLAY_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("WONDERPLAY_KEY_ALIAS").orElse("wonderplay").get()
                keyPassword = providers.environmentVariable("WONDERPLAY_KEY_PASSWORD").orNull
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (providers.environmentVariable("WONDERPLAY_KEYSTORE").isPresent) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging { resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1") }
    lint { abortOnError = true; checkReleaseBuilds = true }
}
kotlin { jvmToolchain(17) }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5") {
        // Android uses Rhino directly in interpreted mode, not the desktop JSR-223 bridge.
        exclude(group="org.mozilla", module="rhino-engine")
    }
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation(libs.androidx.core)
    implementation(libs.androidx.splash)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)
    implementation(libs.work.runtime)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.coroutines)
    implementation(libs.okhttp)
    implementation(libs.androidx.palette)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.espresso)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.coroutines.test)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
}
