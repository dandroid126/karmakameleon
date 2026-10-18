package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.data.api.RedditApi
import com.karmakameleon.shared.domain.model.Listing
import com.karmakameleon.shared.domain.model.Subreddit
import com.karmakameleon.shared.util.runSuspendCatching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

class SubredditRepository(
    private val redditApi: RedditApi,
    private val settingsRepository: SettingsRepository,
) {
    private val _subscribedSubreddits = MutableStateFlow<List<Subreddit>>(emptyList())
    val subscribedSubreddits: StateFlow<List<Subreddit>> = _subscribedSubreddits.asStateFlow()

    val sortedSubscribedSubreddits =
        combine(
            _subscribedSubreddits,
            settingsRepository.favoriteSubreddits,
        ) { subreddits, favorites ->
            subreddits.sortedWith(
                compareByDescending<Subreddit> { it.displayName.lowercase() in favorites }
                    .thenBy { it.displayName.lowercase() },
            )
        }

    private val cachedSubreddits = MutableStateFlow<Map<String, Subreddit>>(emptyMap())

    suspend fun getSubreddit(name: String): Result<Subreddit> {
        // Check cache first
        cachedSubreddits.value[name.lowercase()]?.let { return Result.success(it) }

        return runSuspendCatching {
            val subreddit = redditApi.getSubreddit(name) ?: error("Subreddit not found")
            cachedSubreddits.update { it + (name.lowercase() to subreddit) }
            subreddit
        }
    }

    suspend fun loadSubscribedSubreddits(): Result<List<Subreddit>> =
        runSuspendCatching {
            val allSubreddits = mutableListOf<Subreddit>()
            var after: String? = null

            do {
                val listing = redditApi.getSubscribedSubreddits(after, 100)
                allSubreddits.addAll(listing.items)
                after = listing.after
            } while (after != null)

            val sorted = allSubreddits.sortedBy { it.displayName.lowercase() }
            _subscribedSubreddits.value = sorted

            // Cache all subscribed subreddits
            cachedSubreddits.update { cache ->
                cache + sorted.associateBy { it.displayName.lowercase() }
            }

            sorted
        }

    suspend fun subscribe(subreddit: Subreddit): Result<Subreddit> =
        runSuspendCatching {
            val success =
                if (subreddit.isSubscribed) {
                    redditApi.unsubscribe(subreddit.displayName)
                } else {
                    redditApi.subscribe(subreddit.displayName)
                }
            check(success) { "Subscribe/unsubscribe failed" }

            val updated = subreddit.copy(isSubscribed = !subreddit.isSubscribed)
            cachedSubreddits.update { it + (subreddit.displayName.lowercase() to updated) }

            // Update subscribed list
            if (updated.isSubscribed) {
                _subscribedSubreddits.update { list ->
                    (list + updated).sortedBy { it.displayName.lowercase() }
                }
            } else {
                _subscribedSubreddits.update { list ->
                    list.filter { it.id != subreddit.id }
                }
            }

            updated
        }

    suspend fun searchSubreddits(
        query: String,
        includeOver18: Boolean = true,
    ): Result<List<Subreddit>> = runSuspendCatching { redditApi.searchSubreddits(query, includeOver18 = includeOver18) }

    suspend fun getPopularSubreddits(after: String? = null): Result<Listing<Subreddit>> =
        runSuspendCatching { redditApi.getPopularSubreddits(after) }

    fun getCachedSubreddit(name: String): Subreddit? = cachedSubreddits.value[name.lowercase()]
}
