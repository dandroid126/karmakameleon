package com.karmakameleon.shared.data.backup

import kotlinx.serialization.Serializable

/** The parts of the app's data that can be exported to a file and imported again. */
enum class BackupCategory(
    val displayName: String,
) {
    SETTINGS("Settings"),
    BLOCKED_SUBREDDITS("Blocked Subreddits"),
    FAVORITE_SUBREDDITS("Favorite Subreddits"),
    READ_HISTORY("Read History"),
    DRAFTS("Comment Drafts"),
    REDDIT_APP("Reddit App Setup"),
}

/**
 * The contents of an export file. A category the file doesn't hold is null, and importing the file leaves it as it is.
 * Choices are saved by name rather than as enums, so a name from a newer version, which this one doesn't know, is
 * skipped instead of making the whole file unreadable.
 */
@Serializable
data class Backup(
    val version: Int,
    val settings: BackupSettings? = null,
    val blockedSubreddits: List<String>? = null,
    val favoriteSubreddits: List<String>? = null,
    val readHistory: List<BackupReadPost>? = null,
    val drafts: Map<String, String>? = null,
    val redditApp: BackupRedditApp? = null,
) {
    val categories: Set<BackupCategory>
        get() =
            buildSet {
                if (settings != null) add(BackupCategory.SETTINGS)
                if (blockedSubreddits != null) add(BackupCategory.BLOCKED_SUBREDDITS)
                if (favoriteSubreddits != null) add(BackupCategory.FAVORITE_SUBREDDITS)
                if (readHistory != null) add(BackupCategory.READ_HISTORY)
                if (drafts != null) add(BackupCategory.DRAFTS)
                if (redditApp != null) add(BackupCategory.REDDIT_APP)
            }

    /** Whether a newer version of the app wrote this file, so it may hold data this version skips. */
    val isFromNewerVersion: Boolean get() = version > CURRENT_VERSION

    companion object {
        const val CURRENT_VERSION = 1
    }
}

// A setting missing from the file keeps its current value.
@Serializable
data class BackupSettings(
    val inlineImages: Boolean? = null,
    val compactFeed: Boolean? = null,
    val notificationInterval: String? = null,
    val nsfwEnabled: Boolean? = null,
    val nsfwCacheMedia: Boolean? = null,
    val nsfwPreviewMode: String? = null,
    val nsfwSearch: Boolean? = null,
    val nsfwHistoryMode: String? = null,
    val spoilerPreviews: Boolean? = null,
    val useSuggestedSort: Boolean? = null,
)

// Most posts aren't NSFW, and the defaults are left out of the file, so most entries are just an ID.
@Serializable
data class BackupReadPost(
    val id: String,
    val nsfw: Boolean = false,
    val subredditNsfw: Boolean = false,
)

@Serializable
data class BackupRedditApp(
    val clientId: String,
    val redirectUri: String,
)

/** An export file the app wrote or imported, for the list in the settings. */
data class BackupFileInfo(
    val location: String,
    val name: String,
    val sizeBytes: Long?,
    val lastModifiedMillis: Long?,
)
