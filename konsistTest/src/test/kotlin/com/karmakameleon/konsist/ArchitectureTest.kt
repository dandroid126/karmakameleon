package com.karmakameleon.konsist

import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.ext.list.modifierprovider.withModifier
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

// The rules in CLAUDE.md, and the architecture rules from docs/code-quality-plan.md, as tests. Add a test here
// whenever a rule goes into CLAUDE.md. Every check is strict, so it fails if it finds nothing to check instead of
// passing without checking anything.
class ArchitectureTest {
    @Test
    fun colorsAreOnlyDefinedInTheme() {
        productionScope.files
            .filterNot { it.path.endsWith("/ui/theme/Theme.kt") }
            .assertFalse(
                strict = true,
                additionalMessage =
                    "Colors go in ui/theme/Theme.kt (see CLAUDE.md). Use a MaterialTheme.colorScheme role, or add a " +
                        "field to one of the colors classes there.",
            ) { Rules.hardcodedColor.containsMatchIn(it.text) }
    }

    @Test
    fun commonMainOnlyImportsMultiplatformCode() {
        commonMainScope.imports.assertTrue(
            strict = true,
            additionalMessage =
                "shared/commonMain can only import multiplatform code. Put platform code in androidMain, or add a " +
                    "multiplatform library to Rules.commonMainImportPrefixes.",
        ) { import -> Rules.commonMainImportPrefixes.any { import.name.startsWith(it) } }
    }

    @Test
    fun commonMainDoesNotUsePlatformCode() {
        commonMainScope.files.assertFalse(
            strict = true,
            additionalMessage = "shared/commonMain can't use Android or JVM packages. Put that code in androidMain.",
        ) { Rules.platformReference.containsMatchIn(it.text) }
    }

    @Test
    fun uiCodeDoesNotUseTheApiClient() {
        productionScope.files
            .withPackage("..ui..")
            .assertFalse(
                strict = true,
                additionalMessage = "Screens and view models get data through a repository, not from RedditApi or Ktor.",
            ) { Rules.apiClientUse.containsMatchIn(it.text) }
    }

    // Replaces CodeQL's java/android/websettings-allow-content-access, which can't read our Kotlin (see
    // .github/codeql/codeql-config.yml). Pages never need the device's files or content:// URIs, like the app's own
    // FileProvider. Counts the settings per file, so a second WebView in a file needs its own.
    @Test
    fun webViewsTurnOffContentAndFileAccess() {
        productionScope.files
            .filter { Rules.webViewConstruction.containsMatchIn(it.text) }
            .assertTrue(
                strict = true,
                additionalMessage = "Set allowContentAccess = false and allowFileAccess = false on every WebView.",
            ) { file ->
                val webViews = Rules.webViewConstruction.findAll(file.text).count()
                listOf("allowContentAccess = false", "allowFileAccess = false").all { setting ->
                    Regex(Regex.escape(setting)).findAll(file.text).count() >= webViews
                }
            }
    }

    // Replaces CodeQL's java/android/websettings-javascript-enabled. The WebViews need JavaScript for the pages they
    // show, which is only safe while no page can call into the app.
    @Test
    fun noJavaScriptInterfaces() {
        productionScope.files.assertFalse(
            strict = true,
            additionalMessage = "Pages in the app's WebViews run JavaScript, so don't give them access to app code.",
        ) { "addJavascriptInterface" in it.text }
    }

    // Only checks suspend functions. A lambda that suspends, like the block passed to launch, can't be told apart
    // from any other lambda here.
    @Test
    fun suspendFunctionsDoNotUseRunCatching() {
        productionScope
            .functions()
            .withModifier(KoModifier.SUSPEND)
            .assertFalse(
                strict = true,
                additionalMessage =
                    "runCatching catches coroutine cancellation. Use runSuspendCatching in code that can suspend " +
                        "(see CLAUDE.md).",
            ) { Rules.runCatchingCall.containsMatchIn(it.text) }
    }
}
