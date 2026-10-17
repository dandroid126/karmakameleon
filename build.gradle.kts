buildscript {
    dependencies {
        constraints {
            // AGP brings in an older Bouncy Castle with known vulnerabilities, so require a fixed one.
            // Its modules have to be on the same version.
            classpath(libs.bouncycastle.bcprov)
            classpath(libs.bouncycastle.bcpkix)
            classpath(libs.bouncycastle.bcutil)
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
