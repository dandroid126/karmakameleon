plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    id("jacoco")
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
}

kotlin {
    androidLibrary {
        namespace = "com.karmakameleon.shared"
        compileSdk = 36
        minSdk = 24

        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.multiplatform.settings.coroutines)
            implementation(libs.napier)
            implementation(libs.lifecycle.viewmodel)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.multiplatform.settings.test)
        }
        
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)
        }
        
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

// JaCoCo configuration
jacoco {
    toolVersion = "0.8.14"
}

// Configure JaCoCo for Android tests
tasks.register("jacocoTestReport", org.gradle.testing.jacoco.tasks.JacocoReport::class) {
    dependsOn("testAndroidHostTest")
    
    group = "Reporting"
    description = "Generate Jacoco coverage reports for Android tests"
    
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    
    // Source files for coverage
    sourceDirectories.setFrom(
        files(
            "$projectDir/src/commonMain/kotlin",
            "$projectDir/src/androidMain/kotlin"
        )
    )
    
    // Class files for coverage
    classDirectories.setFrom(
        files(
            fileTree(layout.buildDirectory.dir("classes/kotlin/android/main").get()) {
                exclude("**/R.class")
                exclude("**/R$*.class")
                exclude("**/BuildConfig.*")
                exclude("**/Manifest*.*")
            }
        )
    )
    
    // Execution data from test runs
    executionData.setFrom(
        files(
            layout.buildDirectory.file("jacoco/testAndroidHostTest.exec").get()
        )
    )
}

// Configure test environment variables and JaCoCo for all test tasks
tasks.withType<Test> {
    envVars.forEach { (key, value) ->
        systemProperty(key, value)
    }
    
    // Configure JaCoCo for test tasks
    extensions.configure(org.gradle.testing.jacoco.plugins.JacocoTaskExtension::class) {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}
