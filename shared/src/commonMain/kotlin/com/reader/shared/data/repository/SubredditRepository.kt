package com.reader.shared.data.repository

import com.reader.shared.data.api.RedditApi
import com.reader.shared.domain.model.Listing
import com.reader.shared.domain.model.Subreddit
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

    val sortedSubscribedSubreddits = combine(
        _subscribedSubreddits,
        settingsRepository.favoriteSubreddits
    ) { subreddits, favorites ->
        subreddits.sortedWith(
            compareByDescending<Subreddit> { it.displayName.lowercase() in favorites }
                .thenBy { it.displayName.lowercase() }
        )
    }
    
    private val _cachedSubreddits = MutableStateFlow<Map<String, Subreddit>>(emptyMap())

    suspend fun getSubreddit(name: String): Result<Subreddit> {
        // Check cache first
        _cachedSubreddits.value[name.lowercase()]?.let { return Result.success(it) }
        
        return try {
            val subreddit = redditApi.getSubreddit(name)
            if (subreddit != null) {
                _cachedSubreddits.update { it + (name.lowercase() to subreddit) }
                Result.success(subreddit)
            } else {
                Result.failure(Exception("Subreddit not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadSubscribedSubreddits(): Result<List<Subreddit>> {
        return try {
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
            _cachedSubreddits.update { cache ->
                cache + sorted.associateBy { it.displayName.lowercase() }
            }
            
            Result.success(sorted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun subscribe(subreddit: Subreddit): Result<Subreddit> {
        return try {
            val success = if (subreddit.isSubscribed) {
                redditApi.unsubscribe(subreddit.displayName)
            } else {
                redditApi.subscribe(subreddit.displayName)
            }
            
            if (success) {
                val updated = subreddit.copy(isSubscribed = !subreddit.isSubscribed)
                _cachedSubreddits.update { it + (subreddit.displayName.lowercase() to updated) }
                
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
                
                Result.success(updated)
            } else {
                Result.failure(Exception("Subscribe/unsubscribe failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchSubreddits(query: String, includeOver18: Boolean = true): Result<List<Subreddit>> {
        return try {
            val subreddits = redditApi.searchSubreddits(query, includeOver18 = includeOver18)
            Result.success(subreddits)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPopularSubreddits(after: String? = null): Result<Listing<Subreddit>> {
        return try {
            val listing = redditApi.getPopularSubreddits(after)
            Result.success(listing)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCachedSubreddit(name: String): Subreddit? = _cachedSubreddits.value[name.lowercase()]
}
