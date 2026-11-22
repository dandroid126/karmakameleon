package com.karmakameleon.android.ui.settings

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import com.karmakameleon.android.testing.FakeActivityResults
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.testing.clickWithoutRipple
import com.karmakameleon.android.testing.saveScreenshot
import com.karmakameleon.android.testing.saveScreenshotWithDialog
import com.karmakameleon.android.testing.showScreen
import com.karmakameleon.shared.data.api.AuthManager
import com.karmakameleon.shared.data.backup.BackupCategory
import com.karmakameleon.shared.data.repository.BackupRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupSettingsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeReddit = FakeRedditRule()

    private val pickers = FakeActivityResults()
    private val defaultTimeZone = TimeZone.getDefault()

    // The file list shows dates, which would otherwise depend on the machine's time zone.
    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(defaultTimeZone)
    }

    private fun showSettings(darkTheme: Boolean = false) {
        composeRule.showScreen(darkTheme) {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides pickers) {
                SettingsScreen(onBackClick = {})
            }
        }
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Export Files"))
    }

    private fun clickSectionButton(text: String) {
        composeRule.onNode(hasText(text) and hasClickAction() and !hasAnyAncestor(isDialog())).clickWithoutRipple()
    }

    @Test
    fun exportFiles() {
        val repository = GlobalContext.get().get<BackupRepository>()
        runBlocking {
            repository.export("content://docs/karmakameleon-2027-01-14.json", BackupCategory.entries.toSet())
            repository.export("content://docs/karmakameleon-2027-01-15.json", setOf(BackupCategory.SETTINGS))
        }
        showSettings()
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("karmakameleon-2027-01-14.json"))

        composeRule.saveScreenshot("settings_export_files")
    }

    // With a client ID, so every category can be chosen.
    @Test
    fun exportDialog() {
        GlobalContext.get().get<AuthManager>().setClientId("client")
        showSettings()
        clickSectionButton("Export")

        composeRule.waitForIdle()
        saveScreenshotWithDialog("settings_export_dialog")
    }

    @Test
    fun exportDialogDark() {
        GlobalContext.get().get<AuthManager>().setClientId("client")
        showSettings(darkTheme = true)
        clickSectionButton("Export")

        composeRule.waitForIdle()
        saveScreenshotWithDialog("settings_export_dialog_dark")
    }

    // With every note the dialog can show.
    @Test
    fun importDialog() {
        fakeReddit.loggedIn = true
        fakeReddit.backupFiles.contents["content://docs/new.json"] =
            """{"version":2,"settings":{},"blockedSubreddits":[],"readHistory":[],"redditApp":{"clientId":"new","redirectUri":"app://new"}}"""
        pickers.results += Uri.parse("content://docs/new.json")
        showSettings()
        clickSectionButton("Import")

        composeRule.waitForIdle()
        saveScreenshotWithDialog("settings_import_dialog")
    }
}
