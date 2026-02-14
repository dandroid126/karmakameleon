package com.reader.android.ui.components

import androidx.core.net.toUri

sealed class RedditLink {
    data class Subreddit(val name: String) : RedditLink()
    data class User(val name: String) : RedditLink()
    data class Post(val subreddit: String, val postId: String) : RedditLink()
    data class Comment(val subreddit: String, val postId: String, val commentId: String) : RedditLink()
    data class External(val url: String) : RedditLink()
}

private val redditHostRegex = Regex("^(www\\.|old\\.|new\\.|np\\.)?reddit\\.com$", RegexOption.IGNORE_CASE)

fun parseRedditLink(url: String): RedditLink {
    val normalizedUrl = if (url.startsWith("/r/") || url.startsWith("/u/") || url.startsWith("/user/")) {
        "https://www.reddit.com$url"
    } else {
        url
    }

    val uri = try {
        normalizedUrl.toUri()
    } catch (_: Exception) {
        return RedditLink.External(url)
    }

    val host = uri.host ?: return RedditLink.External(url)
    if (!redditHostRegex.matches(host)) {
        return RedditLink.External(url)
    }

    val pathSegments = uri.pathSegments ?: return RedditLink.External(url)

    return when {
        pathSegments.size >= 6
                && pathSegments[0] == "r"
                && pathSegments[2] == "comments"
                && pathSegments[5].isNotEmpty() -> {
            RedditLink.Comment(
                subreddit = pathSegments[1],
                postId = pathSegments[3],
                commentId = pathSegments[5]
            )
        }

        pathSegments.size >= 4
                && pathSegments[0] == "r"
                && pathSegments[2] == "comments" -> {
            RedditLink.Post(
                subreddit = pathSegments[1],
                postId = pathSegments[3]
            )
        }

        pathSegments.size >= 2 && pathSegments[0] == "r" -> {
            RedditLink.Subreddit(name = pathSegments[1])
        }

        pathSegments.size >= 2 && (pathSegments[0] == "u" || pathSegments[0] == "user") -> {
            RedditLink.User(name = pathSegments[1])
        }

        else -> RedditLink.External(url)
    }
}
