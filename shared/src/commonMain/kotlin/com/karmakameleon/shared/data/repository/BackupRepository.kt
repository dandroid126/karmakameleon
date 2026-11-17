package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.data.api.AuthManager
import com.karmakameleon.shared.data.backup.Backup
import com.karmakameleon.shared.data.backup.BackupCategory
import com.karmakameleon.shared.data.backup.BackupFileInfo
import com.karmakameleon.shared.data.backup.BackupFileStore
import com.karmakameleon.shared.data.backup.BackupReadPost
import com.karmakameleon.shared.data.backup.BackupRedditApp
import com.karmakameleon.shared.data.backup.BackupSettings
import com.karmakameleon.shared.domain.model.NotificationInterval
import com.karmakameleon.shared.domain.model.NsfwHistoryMode
import com.karmakameleon.shared.domain.model.NsfwPreviewMode
import com.karmakameleon.shared.util.runSuspendCatching
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** What an import did beyond replacing the chosen categories. */
data class ImportResult(
    /** The file held a different client ID, and the login, which belongs to the old one, was dropped. */
    val loggedOut: Boolean,
    /** The file held values this version doesn't know, from a newer version, and they were skipped. */
    val skippedUnknownValues: Boolean,
)

/** Exports the app's data to files and imports it again, and keeps the list of those files. */
class BackupRepository(
    private val fileStore: BackupFileStore,
    private val settingsRepository: SettingsRepository,
    private val readPostsRepository: ReadPostsRepository,
    private val commentDraftRepository: CommentDraftRepository,
    private val authManager: AuthManager,
    private val userRepository: UserRepository,
) {
    val files: StateFlow<List<BackupFileInfo>> get() = fileStore.files

    // ==================== Files ====================

    suspend fun export(
        location: String,
        categories: Set<BackupCategory>,
    ): Result<Unit> {
        val written = runSuspendCatching { fileStore.write(location, encode(createBackup(categories))) }
        // Listing the file is a convenience: the export worked even when the app can't keep access to it.
        if (written.isSuccess) runSuspendCatching { fileStore.remember(location) }
        return written
    }

    suspend fun read(location: String): Result<Backup> = runSuspendCatching { decode(fileStore.read(location)) }

    /** Replaces the chosen categories with the file's, and lists the file so it can be deleted later. */
    suspend fun import(
        location: String,
        backup: Backup,
        categories: Set<BackupCategory>,
    ): ImportResult {
        val result = apply(backup, categories)
        // Listing the file is a convenience: the import worked even when the app can't keep access to it.
        runSuspendCatching { fileStore.remember(location) }
        return result
    }

    /** Loads the details of the listed files, and drops those that are gone. */
    suspend fun refreshFiles() = fileStore.refresh()

    suspend fun delete(location: String): Result<Unit> = runSuspendCatching { fileStore.delete(location) }

    // ==================== Contents ====================

    fun createBackup(categories: Set<BackupCategory>): Backup =
        Backup(
            version = Backup.CURRENT_VERSION,
            settings = if (BackupCategory.SETTINGS in categories) exportSettings() else null,
            blockedSubreddits =
                if (BackupCategory.BLOCKED_SUBREDDITS in categories) settingsRepository.blockedSubreddits.value.sorted() else null,
            favoriteSubreddits =
                if (BackupCategory.FAVORITE_SUBREDDITS in categories) settingsRepository.favoriteSubreddits.value.sorted() else null,
            readHistory =
                if (BackupCategory.READ_HISTORY in categories) {
                    readPostsRepository.entries.map { BackupReadPost(it.postId, it.isNsfw, it.subredditIsNsfw) }
                } else {
                    null
                },
            drafts = if (BackupCategory.DRAFTS in categories) commentDraftRepository.allDrafts() else null,
            redditApp =
                if (BackupCategory.REDDIT_APP in categories) {
                    BackupRedditApp(authManager.getClientId(), authManager.getRedirectUri())
                } else {
                    null
                },
        )

    private fun exportSettings(): BackupSettings =
        with(settingsRepository) {
            BackupSettings(
                inlineImages = inlineImagesEnabled.value,
                compactFeed = compactFeedEnabled.value,
                notificationInterval = notificationInterval.value.name,
                nsfwEnabled = nsfwEnabled.value,
                nsfwCacheMedia = nsfwCacheMedia.value,
                nsfwPreviewMode = nsfwPreviewMode.value.name,
                nsfwSearch = nsfwSearchEnabled.value,
                nsfwHistoryMode = nsfwHistoryMode.value.name,
                spoilerPreviews = spoilerPreviewsEnabled.value,
                useSuggestedSort = useSuggestedSort.value,
            )
        }

    /** Replaces each of [categories] that [backup] holds, and leaves the rest alone. */
    fun apply(
        backup: Backup,
        categories: Set<BackupCategory>,
    ): ImportResult {
        fun <T> chosen(
            category: BackupCategory,
            value: T?,
        ): T? = value.takeIf { category in categories }

        var loggedOut = false
        var skipped = false
        chosen(BackupCategory.REDDIT_APP, backup.redditApp)?.let { loggedOut = importRedditApp(it) }
        // Before the read history, which leaves out what the imported history mode doesn't save.
        chosen(BackupCategory.SETTINGS, backup.settings)?.let { skipped = !importSettings(it) }
        chosen(BackupCategory.BLOCKED_SUBREDDITS, backup.blockedSubreddits)?.let { settingsRepository.setBlockedSubreddits(it.toSet()) }
        chosen(BackupCategory.FAVORITE_SUBREDDITS, backup.favoriteSubreddits)?.let { settingsRepository.setFavoriteSubreddits(it.toSet()) }
        chosen(BackupCategory.READ_HISTORY, backup.readHistory)?.let { posts ->
            readPostsRepository.replaceAll(
                posts.map { ReadPostEntry(it.id, it.nsfw, it.subredditNsfw) },
                settingsRepository.nsfwHistoryMode.value,
            )
        }
        chosen(BackupCategory.DRAFTS, backup.drafts)?.let(commentDraftRepository::replaceAllDrafts)
        return ImportResult(loggedOut = loggedOut, skippedUnknownValues = skipped || backup.isFromNewerVersion)
    }

    /** Whether importing [backup]'s Reddit app setup would log out: the login belongs to the client ID it was made with. */
    fun importLogsOut(backup: Backup): Boolean {
        val clientId = backup.redditApp?.clientId ?: return false
        return clientId.filterNot { it.isWhitespace() } != authManager.getClientId() && authManager.isLoggedIn()
    }

    private fun importRedditApp(redditApp: BackupRedditApp): Boolean {
        val loggedOut = importLogsOut(Backup(Backup.CURRENT_VERSION, redditApp = redditApp))
        authManager.setClientId(redditApp.clientId)
        authManager.setRedirectUri(redditApp.redirectUri)
        if (loggedOut) userRepository.logout()
        return loggedOut
    }

    // Returns false when a choice had a name this version doesn't know, which leaves that setting as it is.
    private fun importSettings(backup: BackupSettings): Boolean {
        val repo = settingsRepository
        backup.inlineImages?.let(repo::setInlineImagesEnabled)
        backup.compactFeed?.let(repo::setCompactFeedEnabled)
        backup.nsfwEnabled?.let(repo::setNsfwEnabled)
        backup.nsfwCacheMedia?.let(repo::setNsfwCacheMedia)
        backup.nsfwSearch?.let(repo::setNsfwSearchEnabled)
        backup.spoilerPreviews?.let(repo::setSpoilerPreviewsEnabled)
        backup.useSuggestedSort?.let(repo::setUseSuggestedSort)
        // Each one runs, so a choice this version doesn't know doesn't stop the others.
        return listOf(
            importChoice(backup.notificationInterval, NotificationInterval.entries, repo::setNotificationInterval),
            importChoice(backup.nsfwPreviewMode, NsfwPreviewMode.entries, repo::setNsfwPreviewMode),
            importChoice(backup.nsfwHistoryMode, NsfwHistoryMode.entries, repo::setNsfwHistoryMode),
        ).all { it }
    }

    private fun <T : Enum<T>> importChoice(
        name: String?,
        entries: List<T>,
        set: (T) -> Unit,
    ): Boolean {
        if (name == null) return true
        val value = entries.firstOrNull { it.name == name } ?: return false
        set(value)
        return true
    }

    companion object {
        // Minified, and without the empty categories and default values.
        private val json =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = false
            }

        fun encode(backup: Backup): String = json.encodeToString(backup)

        /** Throws [InvalidBackupException] when [text] isn't an export file. */
        fun decode(text: String): Backup =
            try {
                json.decodeFromString<Backup>(text)
            } catch (e: SerializationException) {
                throw InvalidBackupException(e)
            } catch (e: IllegalArgumentException) {
                throw InvalidBackupException(e)
            }
    }
}

class InvalidBackupException(
    cause: Throwable,
) : Exception("Not an export file", cause)
