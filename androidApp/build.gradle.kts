plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dependencyAnalysis)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.karmakameleon.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.karmakameleon.android"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // Optionally sign debug builds with the key CI signs its debug releases with, so local
        // and GitHub builds can update each other. Set both properties in ~/.gradle/gradle.properties.
        val debugKeystore = providers.gradleProperty("karmakameleon.debugKeystore").orNull
        if (debugKeystore != null) {
            getByName("debug") {
                storeFile = file(debugKeystore)
                storePassword = providers.gradleProperty("karmakameleon.debugKeystorePassword").get()
                keyAlias = "debug"
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            // Robolectric needs the app's resources, like its theme and strings.
            isIncludeAndroidResources = true
            all {
                // Robolectric's Android 17 runtime sets file descriptors through this JDK-internal package, which
                // Java blocks unless it's opened.
                it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
            }
        }
    }

    lint {
        warningsAsErrors = true
        // These fail whenever something newer is released, so the same code could pass one day and fail the
        // next. Dependabot keeps the dependencies current.
        disable += setOf("NewerVersionAvailable", "GradleDependency", "AndroidGradlePluginVersion", "OldTargetApi")
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.runtime.saveable)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.annotation.experimental)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.androidx.compose)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.ktor)

    implementation(libs.napier)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okio)

    implementation(libs.androidx.work.runtime)

    implementation(libs.media3.exoplayer)
    // ExoPlayer finds the HLS support at runtime.
    runtimeOnly(libs.media3.exoplayer.hls)

    implementation(libs.android.youtube.player)

    detektPlugins(libs.compose.rules.detekt)

    testImplementation(project(":sharedTesting"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.coil.test)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.http)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.multiplatform.settings.test)
    constraints {
        // The Compose test library brings in Espresso 3.5, which calls InputManager.getInstance(). Android 17
        // removed it, so tests on Robolectric's Android 17 runtime fail without a newer Espresso.
        testImplementation(libs.espresso.core)
    }

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugRuntimeOnly(libs.androidx.compose.ui.test.manifest)
}
