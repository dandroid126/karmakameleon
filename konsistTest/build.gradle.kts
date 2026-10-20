// Architecture tests: Konsist reads the other modules' source code and fails on code that breaks the repo's rules.
// The module has no code of its own, only tests.
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dependencyAnalysis)
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.konsist)
}

tasks.test {
    // The tests read the source files at runtime, so Gradle has to know about them, or it would skip the tests as
    // up to date after a source change.
    inputs
        .files(
            rootProject.fileTree("androidApp/src") { include("**/*.kt") },
            rootProject.fileTree("shared/src") { include("**/*.kt") },
            rootProject.fileTree("androidApp/src/main") { include("AndroidManifest.xml", "res/xml/*.xml") },
        ).withPropertyName("checkedSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
