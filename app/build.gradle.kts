plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// The release workflow derives both from the git tag and passes them in, so a published
// APK can never disagree with the tag the update check compares against. Local builds keep
// the placeholder.
val appVersionName = (findProperty("appVersionName") as String?) ?: "0.0.0-dev"
val appVersionCode = (findProperty("appVersionCode") as String?)?.toInt() ?: 1

android {
    namespace = "com.findev.fintrack"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.findev.fintrack"
        minSdk = 31
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        // AGP 9 turns both off by default; the build types use them for app_name and the
        // release channel.
        resValues = true
        buildConfig = true
    }

    // Only configured in CI, where the keystore arrives through secrets. Without it a
    // release build stays unsigned instead of silently getting the debug key - a debug-signed
    // release would install once and then refuse every update, because CI regenerates that
    // key on every run and the signature would not match.
    val keystorePath = System.getenv("KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // A separate application id, so the build from Android Studio installs alongside the
        // release rather than fighting it for one slot. They are signed with different keys
        // and Android refuses to replace an app with a differently signed one; without the
        // suffix, every switch between the two would mean uninstalling and losing the data.
        debug {
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "FinTrack dev")
            // Points at the beta stream so the update screen has something to find, but it
            // cannot install what it downloads: this is a different application id, signed
            // with the debug key. Installing an update is what the beta build is for.
            buildConfigField("String", "RELEASE_CHANNEL", "\"BETA\"")
        }

        release {
            resValue("string", "app_name", "FinTrack")
            buildConfigField("String", "RELEASE_CHANNEL", "\"STABLE\"")
            optimization {
                enable = false
            }
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }

        // A shippable build that is not the real app: same release key, so it can update
        // itself from GitHub, but its own application id and data. This is where the whole
        // download-and-install path gets exercised without risking the stable install.
        create("beta") {
            initWith(getByName("release"))
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            resValue("string", "app_name", "FinTrack beta")
            buildConfigField("String", "RELEASE_CHANNEL", "\"BETA\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// exportSchema = true needs a destination for the generated schema JSON.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":loanengine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.vico.compose.m3)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    // Shadows the Android stub of org.json, which throws "not mocked" in JVM tests.
    testImplementation(libs.json.unit.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
