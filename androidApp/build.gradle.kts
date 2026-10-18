plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.karmakameleon.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.karmakameleon.android"
        minSdk = 24
        targetSdk = 36
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
                "proguard-rules.pro"
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

    lint {
        warningsAsErrors = true
        // Issues that need design or behavior changes; fix them and remove them from here.
        baseline = file("lint-baseline.xml")
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
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    
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
    
    implementation(libs.androidx.work.runtime)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)

    implementation(libs.android.youtube.player)
    
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
