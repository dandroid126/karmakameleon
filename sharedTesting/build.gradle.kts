// Test helpers that shared's and the app's tests both use: a fake Reddit server, a fake login and saved Reddit
// responses. Multiplatform has no test fixtures, so they're a module of their own that only tests depend on.
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dependencyAnalysis)
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }

    android {
        namespace = "com.karmakameleon.shared.testing"
        compileSdk = 37
        minSdk = 24
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared"))
            api(libs.ktor.client.core)
            api(libs.ktor.client.mock)
            api(libs.ktor.http)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.multiplatform.settings.test)
            implementation(libs.kotlinx.io.core)
        }
    }
}
