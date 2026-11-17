package com.karmakameleon.android.ui.settings

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.work.testing.WorkManagerTestInitHelper
import com.karmakameleon.android.testing.FakeActivityResults
import com.karmakameleon.android.testing.FakeRedditRule
import com.karmakameleon.android.ui.theme.KarmaKameleonTheme
import com.karmakameleon.shared.data.backup.BackupCategory
import com.karmakameleon.shared.data.repository.BackupRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class BackupSettingsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val fakeReddit = FakeRedditRule()

    private val pickers = FakeActivityResults()
    private val files get() = fakeReddit.backupFiles
    private val settings get() = fakeReddit.settings

    @Before
    fun setUp() {
        // An import reschedules the inbox notifications.
        WorkManagerTestInitHelper.initializeTestWorkManager(RuntimeEnvironment.getApplication())
    }

    private fun showSettings() {
        composeRule.setContent {
            KarmaKameleonTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides pickers) {
                    SettingsScreen(onBackClick = {})
                }
            }
        }
    }

    // The section is at the bottom of the list, which only composes what's scrolled to.
    private fun scrollTo(text: String): SemanticsNodeInteraction {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        return composeRule.onNodeWithText(text)
    }

    private fun sectionButton(text: String): SemanticsNodeInteraction {
        scrollTo("Export Files")
        return composeRule.onNode(hasText(text) and hasClickAction() and !hasAnyAncestor(isDialog()))
    }

    // Dialog buttons and checkboxes, which can share their text with the settings behind them.
    private fun inDialog(text: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(text) and hasClickAction() and hasAnyAncestor(isDialog()))

    private fun pickFile(location: String?) {
        pickers.nextResult = location?.let(Uri::parse)
    }

    @Test
    fun exportWritesTheChosenCategoriesAndListsTheFile() {
        settings.setBlockedSubreddits(setOf("spam"))
        pickFile("content://docs/export.json")
        showSettings()

        sectionButton("Export").performClick()
        // Read history and drafts start unticked.
        inDialog("Settings").performClick()
        inDialog("Favorite Subreddits").performClick()
        inDialog("Reddit App Setup").performClick()
        inDialog("Export").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("karmakameleon-${LocalDate.now()}.json"), pickers.launched)
        assertEquals("""{"version":1,"blockedSubreddits":["spam"]}""", files.contents["content://docs/export.json"])
        composeRule.onNodeWithText("Exported export.json").assertIsDisplayed()
        scrollTo("export.json").assertIsDisplayed()
    }

    @Test
    fun exportNeedsACategory() {
        showSettings()

        sectionButton("Export").performClick()
        listOf("Settings", "Blocked Subreddits", "Favorite Subreddits", "Reddit App Setup").forEach { inDialog(it).performClick() }

        inDialog("Export").assertIsNotEnabled()
    }

    @Test
    fun exportReportsAFileThatCantBeWritten() {
        files.failing += "content://docs/export.json"
        pickFile("content://docs/export.json")
        showSettings()

        sectionButton("Export").performClick()
        inDialog("Export").performClick()

        composeRule.onNodeWithText("Couldn't export: Can't write content://docs/export.json").assertIsDisplayed()
    }

    @Test
    fun cancellingThePickerDoesNothing() {
        pickFile(null)
        showSettings()

        sectionButton("Export").performClick()
        inDialog("Export").performClick()
        sectionButton("Import").performClick()
        composeRule.waitForIdle()

        assertTrue(files.contents.isEmpty())
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun importReplacesTheChosenCategoriesThenOffersToDeleteTheFile() {
        settings.setBlockedSubreddits(setOf("spam"))
        settings.setFavoriteSubreddits(setOf("android"))
        files.contents["content://docs/old.json"] = """{"version":1,"blockedSubreddits":["news"],"favoriteSubreddits":["kotlin"]}"""
        pickFile("content://docs/old.json")
        showSettings()

        sectionButton("Import").performClick()
        assertArrayEquals(arrayOf("application/json", "text/plain", "application/octet-stream"), pickers.launched.single() as Array<*>)
        // Only what the file holds can be chosen.
        inDialog("Settings").assertDoesNotExist()
        inDialog("Favorite Subreddits").performClick()
        inDialog("Replace").performClick()
        composeRule.waitForIdle()

        assertEquals(setOf("news"), settings.blockedSubreddits.value)
        assertEquals(setOf("android"), settings.favoriteSubreddits.value)
        composeRule.onNodeWithText("Replaced Blocked Subreddits.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("You can delete old.json now", substring = true).assertIsDisplayed()

        inDialog("Delete File").performClick()
        composeRule.waitForIdle()

        assertTrue(files.contents.isEmpty())
        composeRule.onNodeWithText("Deleted old.json").assertIsDisplayed()
    }

    @Test
    fun importWarnsAboutLoggingOutAndANewerVersion() {
        fakeReddit.loggedIn = true
        files.contents["content://docs/new.json"] = """{"version":2,"redditApp":{"clientId":"new_client","redirectUri":"app://new"}}"""
        pickFile("content://docs/new.json")
        showSettings()

        sectionButton("Import").performClick()
        composeRule.onNodeWithText("The file has a different client ID, so you'll be logged out.").assertIsDisplayed()
        composeRule.onNodeWithText("A newer version of the app made this file", substring = true).assertIsDisplayed()
        inDialog("Replace").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("so you were logged out", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("came from a newer version", substring = true).assertIsDisplayed()
        inDialog("Keep File").performClick()
        assertEquals(setOf("content://docs/new.json"), files.contents.keys)
    }

    @Test
    fun importRejectsAFileThatIsntAnExport() {
        files.contents["content://docs/notes.txt"] = "Shopping list"
        pickFile("content://docs/notes.txt")
        showSettings()

        sectionButton("Import").performClick()

        composeRule.onNodeWithText("This file isn't a Karma Kameleon export.").assertIsDisplayed()
        inDialog("OK").performClick()
        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun importReportsAFileThatCantBeRead() {
        files.failing += "content://docs/gone.json"
        pickFile("content://docs/gone.json")
        showSettings()

        sectionButton("Import").performClick()

        composeRule.onNodeWithText("The file couldn't be read: Can't read content://docs/gone.json").assertIsDisplayed()
    }

    @Test
    fun importOfAnEmptyFileHasNothingToReplace() {
        files.contents["content://docs/empty.json"] = """{"version":1}"""
        pickFile("content://docs/empty.json")
        showSettings()

        sectionButton("Import").performClick()

        composeRule.onNodeWithText("This export file is empty.").assertIsDisplayed()
    }

    @Test
    fun deletingAListedFileAsksFirst() {
        val repository = GlobalContext.get().get<BackupRepository>()
        runBlocking { repository.export("content://docs/backup.json", setOf(BackupCategory.SETTINGS)) }
        showSettings()

        scrollTo("backup.json")
        composeRule.onNodeWithContentDescription("Delete backup.json").performClick()
        composeRule.onNodeWithText("This deletes backup.json itself", substring = true).assertIsDisplayed()
        inDialog("Cancel").performClick()
        assertTrue("content://docs/backup.json" in files.contents)

        composeRule.onNodeWithContentDescription("Delete backup.json").performClick()
        inDialog("Delete").performClick()
        composeRule.waitForIdle()

        assertTrue(files.contents.isEmpty())
        scrollTo("No export files").assertIsDisplayed()
    }

    @Test
    fun aFileThatCantBeDeletedStaysListed() {
        val repository = GlobalContext.get().get<BackupRepository>()
        runBlocking { repository.export("content://docs/backup.json", setOf(BackupCategory.SETTINGS)) }
        files.failing += "content://docs/backup.json"
        showSettings()

        scrollTo("backup.json")
        composeRule.onNodeWithContentDescription("Delete backup.json").performClick()
        inDialog("Delete").performClick()

        composeRule.onNodeWithText("Couldn't delete backup.json", substring = true).assertIsDisplayed()
        scrollTo("backup.json").assertIsDisplayed()
    }
}
