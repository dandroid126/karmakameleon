plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dependencyAnalysis)
    alias(libs.plugins.kover)
}

// Load .env file for test configuration
val envFile = rootProject.file(".env")
val envVars = mutableMapOf<String, String>()
if (envFile.exists()) {
    envFile.readLines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
            val (key, value) = trimmed.split("=", limit = 2)
            envVars[key.trim()] = value.trim()
        }
    }
}

tasks.withType<Test> {
    envVars.forEach { (key, value) ->
        systemProperty(key, value)
    }
    // The saved Reddit responses some tests read, so changing one runs them again.
    inputs.dir(rootProject.file("sharedTesting/fixtures")).withPathSensitivity(PathSensitivity.RELATIVE)
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }

    android {
        namespace = "com.karmakameleon.shared"
        compileSdk = 37
        minSdk = 24

        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            // The app sees these in shared's API: the HTTP client, flows, JSON, the Koin modules, settings and
            // view models.
            api(libs.ktor.client.core)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.koin.core)
            api(libs.multiplatform.settings)
            api(libs.androidx.lifecycle.viewmodel)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.ktor.http)
            implementation(libs.ktor.serialization)
            implementation(libs.ktor.utils)
            implementation(libs.napier)
        }

        commonTest.dependencies {
            implementation(project(":sharedTesting"))
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.multiplatform.settings.test)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.okhttp)
            // Provides Dispatchers.Main, found at runtime.
            runtimeOnly(libs.kotlinx.coroutines.android)
        }
    }
}

// Keeps shared's line coverage from slipping below 80%, on top of diffCoverage's check of the changed lines. CI has no
// client ID, so the integration tests don't run there and don't count; run koverVerify without .env to match it.
kover {
    reports {
        verify {
            rule { minBound(80) }
        }
    }
}
