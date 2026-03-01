package com.karmakameleon.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Listing<T>(
    val items: List<T>,
    val after: String?,
    val before: String?,
    val dist: Int?
) {
    val hasMore: Boolean get() = after != null
}

@Serializable
data class SearchResult(
    val posts: Listing<Post>,
    val subreddits: List<Subreddit>,
    val users: List<User>
)

enum class SearchSort(val value: String) {
    RELEVANCE("relevance"),
    HOT("hot"),
    TOP("top"),
    NEW("new"),
    COMMENTS("comments")
}

enum class SearchType(val value: String) {
    POST("link"),
    SUBREDDIT("sr"),
    USER("user")
}
