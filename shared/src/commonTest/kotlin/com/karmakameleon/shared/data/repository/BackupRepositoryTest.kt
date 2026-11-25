package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.data.backup.Backup
import com.karmakameleon.shared.data.backup.BackupCategory
import com.karmakameleon.shared.data.backup.BackupFileStore
import com.karmakameleon.shared.data.backup.BackupReadPost
import com.karmakameleon.shared.data.backup.BackupRedditApp
import com.karmakameleon.shared.data.backup.BackupSettings
import com.karmakameleon.shared.domain.model.NotificationInterval
import com.karmakameleon.shared.domain.model.NsfwHistoryMode
import com.karmakameleon.shared.domain.model.NsfwPreviewMode
import com.karmakameleon.shared.testing.FakeBackupFiles
import com.karmakameleon.shared.testing.MockReddit
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BackupRepositoryTest {
    private val settings = MapSettings()
    private val files = FakeBackupFiles()
    private val reddit = MockReddit()
    private val authManager = reddit.authManager
    private val settingsRepository = SettingsRepository(settings)
    private val readPostsRepository = ReadPostsRepository(settings)
    private val draftRepository = CommentDraftRepository(settings)
    private val dispatcher = UnconfinedTestDispatcher()
    private val userRepository =
        UserRepository(reddit.api(ioDispatcher = dispatcher), authManager, CoroutineScope(dispatcher))

    private val repository =
        BackupRepository(
            BackupFileStore(settings, files),
            settingsRepository,
            readPostsRepository,
            draftRepository,
            authManager,
            userRepository,
        )

    private val everything = BackupCategory.entries.toSet()

    // Changes every setting from its default, and fills every list.
    private fun fillEverything() {
        with(settingsRepository) {
            setInlineImagesEnabled(false)
            setCompactFeedEnabled(true)
            setNotificationInterval(NotificationInterval.ONE_HOUR)
            setNsfwEnabled(true)
            setNsfwCacheMedia(true)
            setNsfwPreviewMode(NsfwPreviewMode.entries.last())
            setNsfwSearchEnabled(true)
            setNsfwHistoryMode(NsfwHistoryMode.SAVE_ALL)
            setSpoilerPreviewsEnabled(true)
            setUseSuggestedSort(false)
            setBlockedSubreddits(setOf("Spam", "memes"))
            setFavoriteSubreddits(setOf("kotlin"))
        }
        readPostsRepository.replaceAll(
            listOf(
                ReadPostEntry("a1", isNsfw = false, subredditIsNsfw = false),
                ReadPostEntry("b2", isNsfw = true, subredditIsNsfw = true),
            ),
            NsfwHistoryMode.SAVE_ALL,
        )
        draftRepository.saveDraft("t3_post", "Half a comment")
        authManager.setClientId("old_client")
        authManager.setRedirectUri("app://old")
    }

    // ==================== Export ====================

    @Test
    fun createBackup_holdsOnlyTheChosenCategories() {
        fillEverything()

        val backup = repository.createBackup(setOf(BackupCategory.BLOCKED_SUBREDDITS, BackupCategory.DRAFTS))

        assertEquals(setOf(BackupCategory.BLOCKED_SUBREDDITS, BackupCategory.DRAFTS), backup.categories)
        assertEquals(listOf("memes", "spam"), backup.blockedSubreddits)
        assertEquals(mapOf("t3_post" to "Half a comment"), backup.drafts)
    }

    @Test
    fun createBackup_holdsEverySetting() {
        fillEverything()

        val backup = repository.createBackup(everything)

        assertEquals(
            BackupSettings(
                inlineImages = false,
                compactFeed = true,
                notificationInterval = "ONE_HOUR",
                nsfwEnabled = true,
                nsfwCacheMedia = true,
                nsfwPreviewMode = NsfwPreviewMode.entries.last().name,
                nsfwSearch = true,
                nsfwHistoryMode = "SAVE_ALL",
                spoilerPreviews = true,
                useSuggestedSort = false,
            ),
            backup.settings,
        )
        assertEquals(listOf("kotlin"), backup.favoriteSubreddits)
        assertEquals(setOf(BackupReadPost("a1"), BackupReadPost("b2", nsfw = true, subredditNsfw = true)), backup.readHistory?.toSet())
        assertEquals(BackupRedditApp("old_client", "app://old"), backup.redditApp)
    }

    @Test
    fun createBackup_leavesOutTheRedditAppSetupWithoutAClientId() {
        assertFalse(repository.hasRedditApp())

        assertNull(repository.createBackup(everything).redditApp)
    }

    @Test
    fun encode_isMinifiedAndLeavesOutDefaultsAndMissingCategories() {
        val backup = Backup(Backup.CURRENT_VERSION, readHistory = listOf(BackupReadPost("a1"), BackupReadPost("b2", nsfw = true)))

        assertEquals("""{"version":1,"readHistory":[{"id":"a1"},{"id":"b2","nsfw":true}]}""", BackupRepository.encode(backup))
    }

    @Test
    fun encodeThenDecode_givesTheSameBackup() {
        fillEverything()
        val backup = repository.createBackup(everything)

        assertEquals(backup, BackupRepository.decode(BackupRepository.encode(backup)))
    }

    @Test
    fun decode_ignoresWhatANewerVersionAdded() {
        val backup = BackupRepository.decode("""{"version":2,"themes":["dark"],"settings":{"inlineImages":false,"fontSize":3}}""")

        assertTrue(backup.isFromNewerVersion)
        assertEquals(BackupSettings(inlineImages = false), backup.settings)
    }

    @Test
    fun decode_rejectsFilesThatArentExports() {
        assertFailsWith<InvalidBackupException> { BackupRepository.decode("not json") }
        // Without a version, it's some other JSON file.
        assertFailsWith<InvalidBackupException> { BackupRepository.decode("""{"name":"something else"}""") }
    }

    // ==================== Import ====================

    @Test
    fun apply_replacesTheChosenCategoriesAndKeepsTheRest() {
        fillEverything()
        val backup =
            Backup(
                Backup.CURRENT_VERSION,
                blockedSubreddits = listOf("News"),
                favoriteSubreddits = listOf("android"),
                drafts = mapOf("t1_comment" to "New draft"),
            )

        // Favorites are in the file but not chosen, and the settings are chosen but not in the file.
        repository.apply(backup, setOf(BackupCategory.BLOCKED_SUBREDDITS, BackupCategory.DRAFTS, BackupCategory.SETTINGS))

        assertEquals(setOf("news"), settingsRepository.blockedSubreddits.value)
        assertEquals(setOf("kotlin"), settingsRepository.favoriteSubreddits.value)
        assertEquals(mapOf("t1_comment" to "New draft"), draftRepository.allDrafts())
        assertNull(draftRepository.loadDraft("t3_post"))
        assertTrue(settingsRepository.compactFeedEnabled.value)
        assertEquals(setOf("a1", "b2"), readPostsRepository.readPostIds.value)
    }

    @Test
    fun apply_restoresAnExportOnAFreshInstall() {
        fillEverything()
        val backup = BackupRepository.decode(BackupRepository.encode(repository.createBackup(everything)))
        val freshSettings = MapSettings()
        val fresh = SettingsRepository(freshSettings)
        val freshReadPosts = ReadPostsRepository(freshSettings)
        val freshDrafts = CommentDraftRepository(freshSettings)
        val freshAuth = MockReddit().authManager
        val freshRepository =
            BackupRepository(
                BackupFileStore(freshSettings, FakeBackupFiles()),
                fresh,
                freshReadPosts,
                freshDrafts,
                freshAuth,
                userRepository,
            )

        val result = freshRepository.apply(backup, everything)

        assertEquals(ImportResult(loggedOut = false, skippedUnknownValues = false), result)
        assertEquals(backup, freshRepository.createBackup(everything))
        // Read again from storage, as after a restart.
        assertEquals(setOf("a1", "b2"), ReadPostsRepository(freshSettings).readPostIds.value)
        assertEquals(NotificationInterval.ONE_HOUR, SettingsRepository(freshSettings).notificationInterval.value)
    }

    @Test
    fun apply_settingsMissingFromTheFileKeepTheirValue() {
        settingsRepository.setCompactFeedEnabled(true)

        repository.apply(Backup(Backup.CURRENT_VERSION, settings = BackupSettings(inlineImages = false)), everything)

        assertFalse(settingsRepository.inlineImagesEnabled.value)
        assertTrue(settingsRepository.compactFeedEnabled.value)
    }

    @Test
    fun apply_skipsChoicesThisVersionDoesntKnow() {
        settingsRepository.setNsfwPreviewMode(NsfwPreviewMode.entries.last())
        val backup =
            Backup(
                Backup.CURRENT_VERSION,
                settings = BackupSettings(nsfwPreviewMode = "HOLOGRAM", notificationInterval = "ONE_HOUR"),
            )

        val result = repository.apply(backup, everything)

        assertTrue(result.skippedUnknownValues)
        assertEquals(NsfwPreviewMode.entries.last(), settingsRepository.nsfwPreviewMode.value)
        assertEquals(NotificationInterval.ONE_HOUR, settingsRepository.notificationInterval.value)
    }

    @Test
    fun apply_reportsAFileFromANewerVersionAsSkipped() {
        val result = repository.apply(Backup(Backup.CURRENT_VERSION + 1, blockedSubreddits = listOf("spam")), everything)

        assertTrue(result.skippedUnknownValues)
        assertEquals(setOf("spam"), settingsRepository.blockedSubreddits.value)
    }

    @Test
    fun apply_readHistoryFollowsTheImportedHistoryMode() {
        val backup =
            Backup(
                Backup.CURRENT_VERSION,
                settings = BackupSettings(nsfwHistoryMode = NsfwHistoryMode.DONT_SAVE_NSFW_SUBREDDITS.name),
                readHistory =
                    listOf(
                        BackupReadPost("plain"),
                        BackupReadPost("nsfwPost", nsfw = true),
                        BackupReadPost("nsfwSub", nsfw = true, subredditNsfw = true),
                    ),
            )

        repository.apply(backup, everything)

        assertEquals(setOf("plain", "nsfwPost"), readPostsRepository.readPostIds.value)
    }

    @Test
    fun apply_readHistoryLeavesOutNsfwPostsWhenNoneAreSaved() {
        settingsRepository.setNsfwHistoryMode(NsfwHistoryMode.DONT_SAVE_ANY_NSFW)
        val backup = Backup(Backup.CURRENT_VERSION, readHistory = listOf(BackupReadPost("plain"), BackupReadPost("nsfw", nsfw = true)))

        repository.apply(backup, setOf(BackupCategory.READ_HISTORY))

        assertEquals(setOf("plain"), readPostsRepository.readPostIds.value)
    }

    @Test
    fun apply_aDifferentClientIdLogsOut() {
        authManager.setClientId("old_client")
        authManager.fakeIsLoggedIn = true
        val backup = Backup(Backup.CURRENT_VERSION, redditApp = BackupRedditApp("new_client", "app://new"))

        assertTrue(repository.importLogsOut(backup))
        val result = repository.apply(backup, everything)

        assertTrue(result.loggedOut)
        assertFalse(authManager.isLoggedIn())
        assertEquals("new_client", authManager.getClientId())
        assertEquals("app://new", authManager.getRedirectUri())
    }

    @Test
    fun apply_theSameClientIdKeepsTheLogin() {
        authManager.setClientId("my_client")
        authManager.fakeIsLoggedIn = true
        val backup = Backup(Backup.CURRENT_VERSION, redditApp = BackupRedditApp(" my_client\n", "app://new"))

        assertFalse(repository.importLogsOut(backup))
        val result = repository.apply(backup, everything)

        assertFalse(result.loggedOut)
        assertTrue(authManager.isLoggedIn())
        assertEquals("app://new", authManager.getRedirectUri())
    }

    @Test
    fun apply_ignoresABlankClientId() {
        authManager.setClientId("my_client")
        authManager.fakeIsLoggedIn = true
        // What an older export from a device that was never set up holds.
        val backup = Backup(Backup.CURRENT_VERSION, redditApp = BackupRedditApp(" ", "karmakameleon://oauth"))

        assertFalse(BackupCategory.REDDIT_APP in backup.categories)
        val result = repository.apply(backup, everything)

        assertFalse(result.loggedOut)
        assertEquals("my_client", authManager.getClientId())
        assertTrue(authManager.isLoggedIn())
    }

    @Test
    fun importLogsOut_isFalseWhenLoggedOutOrTheFileHasNoClientId() {
        authManager.setClientId("old_client")

        assertFalse(repository.importLogsOut(Backup(Backup.CURRENT_VERSION, redditApp = BackupRedditApp("new_client", "app://new"))))
        authManager.fakeIsLoggedIn = true
        assertFalse(repository.importLogsOut(Backup(Backup.CURRENT_VERSION)))
    }

    // ==================== Files ====================

    @Test
    fun export_writesTheFileAndListsIt() =
        runTest {
            settingsRepository.setBlockedSubreddits(setOf("spam"))

            assertTrue(repository.export("content://docs/backup.json", setOf(BackupCategory.BLOCKED_SUBREDDITS)).isSuccess)

            assertEquals("""{"version":1,"blockedSubreddits":["spam"]}""", files.contents["content://docs/backup.json"])
            assertEquals(listOf("backup.json"), repository.files.value.map { it.name })
            assertEquals(setOf("content://docs/backup.json"), files.kept)
        }

    @Test
    fun export_reportsAFileThatCantBeWritten() =
        runTest {
            files.failing += "content://docs/backup.json"

            assertTrue(repository.export("content://docs/backup.json", everything).isFailure)
            assertTrue(repository.files.value.isEmpty())
        }

    @Test
    fun export_worksWhenTheFileCantBeListed() =
        runTest {
            files.notKeepable += "content://docs/backup.json"

            assertTrue(repository.export("content://docs/backup.json", everything).isSuccess)

            assertTrue("content://docs/backup.json" in files.contents)
            assertTrue(repository.files.value.isEmpty())
        }

    @Test
    fun import_worksWhenTheFileCantBeListed() =
        runTest {
            files.contents["content://docs/old.json"] = """{"version":1,"blockedSubreddits":["spam"]}"""
            files.notKeepable += "content://docs/old.json"

            val backup = repository.read("content://docs/old.json").getOrThrow()
            repository.import("content://docs/old.json", backup, everything)

            assertEquals(setOf("spam"), settingsRepository.blockedSubreddits.value)
            assertTrue(repository.files.value.isEmpty())
        }

    @Test
    fun readThenImport_listsTheImportedFile() =
        runTest {
            files.contents["content://docs/old.json"] = """{"version":1,"favoriteSubreddits":["kotlin"]}"""

            val backup = repository.read("content://docs/old.json").getOrThrow()
            repository.import("content://docs/old.json", backup, everything)

            assertEquals(setOf("kotlin"), settingsRepository.favoriteSubreddits.value)
            assertEquals(listOf("old.json"), repository.files.value.map { it.name })
        }

    @Test
    fun read_failsForAFileThatIsntAnExport() =
        runTest {
            files.contents["content://docs/photo.jpg"] = "ÿØ"

            assertTrue(repository.read("content://docs/photo.jpg").exceptionOrNull() is InvalidBackupException)
        }

    @Test
    fun refreshFiles_dropsFilesThatAreGoneAndListsTheNewestFirst() =
        runTest {
            repository.export("content://docs/first.json", everything)
            repository.export("content://docs/second.json", everything)
            repository.export("content://docs/third.json", everything)
            files.contents.remove("content://docs/second.json")

            // Read again from storage, as after a restart.
            val restarted =
                BackupRepository(
                    BackupFileStore(settings, files),
                    settingsRepository,
                    readPostsRepository,
                    draftRepository,
                    authManager,
                    userRepository,
                )
            restarted.refreshFiles()

            assertContentEquals(listOf("third.json", "first.json"), restarted.files.value.map { it.name })
            assertFalse("content://docs/second.json" in files.kept)
        }

    @Test
    fun aFileListedDuringARefreshStaysListed() =
        runTest {
            repository.export("content://docs/old.json", everything)
            val gate = CompletableDeferred<Unit>()
            files.infoGate = gate

            // The refresh waits on the old file's details while an export lists a new one.
            val refresh = launch { repository.refreshFiles() }
            runCurrent()
            val export = launch { repository.export("content://docs/new.json", everything) }
            runCurrent()
            gate.complete(Unit)
            refresh.join()
            export.join()

            assertEquals(
                setOf("old.json", "new.json"),
                repository.files.value
                    .map { it.name }
                    .toSet(),
            )
            // Read again from storage, as after a restart.
            val restarted =
                BackupRepository(
                    BackupFileStore(settings, files),
                    settingsRepository,
                    readPostsRepository,
                    draftRepository,
                    authManager,
                    userRepository,
                )
            restarted.refreshFiles()
            assertEquals(
                setOf("old.json", "new.json"),
                restarted.files.value
                    .map { it.name }
                    .toSet(),
            )
        }

    @Test
    fun delete_deletesTheFileAndUnlistsIt() =
        runTest {
            repository.export("content://docs/backup.json", everything)

            assertTrue(repository.delete("content://docs/backup.json").isSuccess)

            assertTrue(files.contents.isEmpty())
            assertTrue(repository.files.value.isEmpty())
            assertTrue(files.kept.isEmpty())
        }

    @Test
    fun delete_keepsAFileThatCantBeDeletedListed() =
        runTest {
            repository.export("content://docs/backup.json", everything)
            files.failing += "content://docs/backup.json"

            assertTrue(repository.delete("content://docs/backup.json").isFailure)

            assertEquals(listOf("backup.json"), repository.files.value.map { it.name })
        }
}
