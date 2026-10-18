buildscript {
    dependencies {
        constraints {
            // AGP brings in older versions of some libraries with known vulnerabilities, so require fixed ones.
            libs.bundles.security.fixes
                .get()
                .forEach { classpath("${it.module}:${it.version}") }
        }
    }
}

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt) apply false
}

// Formats Kotlin with ktlint, using the rules in .editorconfig. Run spotlessApply to fix what it reports.
spotless {
    val buildOutput = listOf("**/build/**", ".gradle/**", ".kotlin/**")
    kotlin {
        target("**/*.kt")
        targetExclude(buildOutput)
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude(buildOutput)
        ktlint(libs.versions.ktlint.get())
    }
}

subprojects {
    plugins.withId("dev.detekt") {
        extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
            buildUponDefaultConfig = true
            config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            baseline = file("detekt-baseline.xml")
        }
    }

    val constraintHandler = dependencies.constraints
    // AGP runs lint with its own copy of the tooling, which brings in the same old libraries.
    configurations.matching { it.name == "androidLintTool" }.configureEach {
        libs.bundles.security.fixes.get().forEach {
            dependencyConstraints.add(constraintHandler.create("${it.module}:${it.version}"))
        }
    }
}

// Every static check in one task, so they can all be run locally before committing. CI runs it too.
tasks.register("qualityGate") {
    group = "verification"
    description = "Runs all static checks."
    dependsOn(
        ":androidApp:lintDebug",
        "spotlessCheck",
        // Detekt with type resolution. The app's debug and release variants have the same code, so one is enough.
        ":androidApp:detektDebug",
        ":shared:detektMainAndroid",
        ":shared:detektHostTestAndroid",
    )
}
