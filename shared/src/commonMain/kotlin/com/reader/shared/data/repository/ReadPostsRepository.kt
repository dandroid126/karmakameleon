package com.reader.shared.data.repository

import com.reader.shared.domain.model.NsfwHistoryMode
import com.reader.shared.domain.model.Post
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ReadPostsRepository(private val settings: Settings) {

    private data class ReadPostEntry(
        val postId: String,
        val isNsfw: Boolean,
        val subredditIsNsfw: Boolean,
    )

    private val _readPostIds = MutableStateFlow(loadReadPostIds())
    val readPostIds: StateFlow<Set<String>> = _readPostIds.asStateFlow()

    private val _readPostEntries = MutableStateFlow(loadReadPostEntries())

    fun isRead(postId: String): Boolean = _readPostIds.value.contains(postId)

    fun isRead(post: Post, historyMode: NsfwHistoryMode): Boolean {
        val wouldBeExcluded = when (historyMode) {
            NsfwHistoryMode.SAVE_ALL -> false
            NsfwHistoryMode.DONT_SAVE_NSFW_SUBREDDITS -> if (post.subredditNsfwKnown) post.subredditIsNsfw else post.isNsfw
            NsfwHistoryMode.DONT_SAVE_ANY_NSFW -> post.isNsfw
        }
        if (wouldBeExcluded) return false
        return _readPostIds.value.contains(post.id)
    }

    fun markAsRead(post: Post, historyMode: NsfwHistoryMode) {
        val shouldSkip = when (historyMode) {
            NsfwHistoryMode.SAVE_ALL -> false
            NsfwHistoryMode.DONT_SAVE_NSFW_SUBREDDITS -> if (post.subredditNsfwKnown) post.subredditIsNsfw else post.isNsfw
            NsfwHistoryMode.DONT_SAVE_ANY_NSFW -> post.isNsfw
        }
        if (shouldSkip) return

        val entry = ReadPostEntry(post.id, post.isNsfw, post.subredditIsNsfw)
        val updatedEntries = _readPostEntries.value + entry
        _readPostEntries.value = updatedEntries
        saveReadPostEntries(updatedEntries)

        val updatedIds = _readPostIds.value + post.id
        _readPostIds.value = updatedIds
    }

    fun purgeNsfwReadPosts() {
        val filtered = _readPostEntries.value.filter { !it.isNsfw && !it.subredditIsNsfw }.toSet()
        _readPostEntries.value = filtered
        saveReadPostEntries(filtered)

        val filteredIds = filtered.map { it.postId }.toSet()
        _readPostIds.value = filteredIds
    }

    private fun loadReadPostIds(): Set<String> {
        return loadReadPostEntries().map { it.postId }.toSet()
    }

    private fun loadReadPostEntries(): Set<ReadPostEntry> {
        val stored = settings.getStringOrNull(KEY_READ_POSTS) ?: return emptySet()
        return stored.split(",").filter { it.isNotBlank() }.mapNotNull { raw ->
            val parts = raw.split("|")
            when (parts.size) {
                1 -> ReadPostEntry(parts[0], isNsfw = false, subredditIsNsfw = false)
                3 -> ReadPostEntry(parts[0], parts[1] == "1", parts[2] == "1")
                else -> null
            }
        }.toSet()
    }

    private fun saveReadPostEntries(entries: Set<ReadPostEntry>) {
        settings[KEY_READ_POSTS] = entries.joinToString(",") { "${it.postId}|${if (it.isNsfw) "1" else "0"}|${if (it.subredditIsNsfw) "1" else "0"}" }
    }

    companion object {
        private const val KEY_READ_POSTS = "read_post_ids"
    }
}
