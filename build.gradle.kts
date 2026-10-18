buildscript {
    dependencies {
        constraints {
            // AGP brings in older versions of some libraries with known vulnerabilities, so require fixed ones.
            libs.bundles.security.fixes.get().forEach { classpath("${it.module}:${it.version}") }
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
}

subprojects {
    val constraintHandler = dependencies.constraints
    // AGP runs lint with its own copy of the tooling, which brings in the same old libraries.
    configurations.matching { it.name == "androidLintTool" }.configureEach {
        libs.bundles.security.fixes.get().forEach {
            dependencyConstraints.add(constraintHandler.create("${it.module}:${it.version}"))
        }
    }
}
