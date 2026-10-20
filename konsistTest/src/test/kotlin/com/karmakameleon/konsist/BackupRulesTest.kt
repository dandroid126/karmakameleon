package com.karmakameleon.konsist

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// Replaces CodeQL's java/android/backup-enabled, which only sees android:allowBackup="true" and not the rules (see
// .github/codeql/codeql-config.yml). The app's data is backed up so settings and drafts survive a new phone, except
// the login tokens and the login WebView's cookies, which must never leave the device. Android 12 and newer read
// data_extraction_rules.xml (cloud backup and device transfer), older versions read backup_rules.xml, so each of them
// has to exclude both.
class BackupRulesTest {
    // The tests run in the konsistTest directory.
    private val appMain = File("../androidApp/src/main")

    private val tokenFile =
        Regex("""TOKEN_SETTINGS_FILE = "([^"]+)"""")
            .find(File("../shared/src/androidMain/kotlin/com/karmakameleon/shared/di/PlatformModule.android.kt").readText())
            ?.groupValues
            ?.get(1)

    private val requiredExcludes = setOf("sharedpref:$tokenFile.xml", "root:app_webview/")

    // The excludes in the first <section> of the file, as "domain:path".
    private fun excludes(
        fileName: String,
        section: String,
    ): Set<String> {
        val document =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(File(appMain, "res/xml/$fileName"))
        val element = assertNotNull(document.getElementsByTagName(section).item(0) as Element?, "$fileName has no <$section>")
        val nodes = element.getElementsByTagName("exclude")
        return (0 until nodes.length)
            .map { nodes.item(it) as Element }
            .map { "${it.getAttribute("domain")}:${it.getAttribute("path")}" }
            .toSet()
    }

    @Test
    fun tokenFileName_isFoundInTheCode() {
        assertNotNull(tokenFile, "TOKEN_SETTINGS_FILE not found in PlatformModule.android.kt")
    }

    @Test
    fun cloudBackup_excludesTokensAndCookies() {
        assertTrue(excludes("data_extraction_rules.xml", "cloud-backup").containsAll(requiredExcludes))
    }

    @Test
    fun deviceTransfer_excludesTokensAndCookies() {
        assertTrue(excludes("data_extraction_rules.xml", "device-transfer").containsAll(requiredExcludes))
    }

    @Test
    fun oldBackupRules_matchTheNewOnes() {
        val old = excludes("backup_rules.xml", "full-backup-content")
        assertTrue(old.containsAll(requiredExcludes))
        assertEquals(excludes("data_extraction_rules.xml", "cloud-backup"), old, "Keep the two rule files in sync.")
    }

    @Test
    fun manifest_usesTheRules() {
        val manifest = File(appMain, "AndroidManifest.xml").readText()
        assertTrue("""android:dataExtractionRules="@xml/data_extraction_rules"""" in manifest)
        assertTrue("""android:fullBackupContent="@xml/backup_rules"""" in manifest)
    }
}
