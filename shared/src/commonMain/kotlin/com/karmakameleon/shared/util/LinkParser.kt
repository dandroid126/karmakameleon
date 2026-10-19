package com.karmakameleon.shared.util

import io.ktor.http.Url

private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "heif", "ico")
private val videoExtensions = setOf("mp4", "webm")
private val youTubeHosts =
    setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "youtu.be",
        "www.youtu.be",
        "youtube-nocookie.com",
        "www.youtube-nocookie.com",
    )

fun isImageUrl(url: String): Boolean {
    val path = url.substringBefore("?").substringBefore("#")
    val ext = path.substringAfterLast(".", "").lowercase()
    return ext in imageExtensions
}

fun isVideoUrl(url: String): Boolean {
    val path = url.substringBefore("?").substringBefore("#")
    val ext = path.substringAfterLast(".", "").lowercase()
    return ext in videoExtensions
}

fun extractYouTubeVideoId(url: String): String? {
    return try {
        val parsedUrl = Url(url)
        val host = parsedUrl.host.lowercase()
        if (host !in youTubeHosts) return null
        val segments = parsedUrl.segments.filter { it.isNotEmpty() }
        when {
            host == "youtu.be" || host == "www.youtu.be" -> segments.firstOrNull()
            segments.firstOrNull() == "watch" -> parsedUrl.parameters["v"]
            segments.firstOrNull() == "embed" && segments.size >= 2 -> segments[1]
            segments.firstOrNull() == "v" && segments.size >= 2 -> segments[1]
            segments.firstOrNull() == "shorts" && segments.size >= 2 -> segments[1]
            else -> null
        }?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}

fun isYouTubeUrl(url: String): Boolean = extractYouTubeVideoId(url) != null

sealed class RedditLink {
    data class Subreddit(
        val name: String,
    ) : RedditLink()

    data class User(
        val name: String,
    ) : RedditLink()

    data class Post(
        val subreddit: String,
        val postId: String,
    ) : RedditLink()

    data class Comment(
        val subreddit: String,
        val postId: String,
        val commentId: String,
        val context: Int? = null,
    ) : RedditLink()

    data class ShareLink(
        val subreddit: String,
        val shareId: String,
    ) : RedditLink()

    data class External(
        val url: String,
    ) : RedditLink()
}

private val redditHostRegex = Regex("^(www\\.|old\\.|new\\.|np\\.)?reddit\\.com$", RegexOption.IGNORE_CASE)

fun parseRedditLink(url: String): RedditLink {
    val normalizedUrl =
        if (url.startsWith("/r/") || url.startsWith("/u/") || url.startsWith("/user/")) {
            "https://www.reddit.com$url"
        } else {
            url
        }

    val parsedUrl =
        try {
            Url(normalizedUrl)
        } catch (_: Exception) {
            return RedditLink.External(url)
        }
    if (!redditHostRegex.matches(parsedUrl.host)) return RedditLink.External(url)

    val pathSegments = parsedUrl.segments.filter { it.isNotEmpty() }
    val context = parsedUrl.parameters["context"]?.toIntOrNull()
    return redditLinkFromPath(pathSegments, context) ?: RedditLink.External(url)
}

private fun redditLinkFromPath(
    pathSegments: List<String>,
    context: Int?,
): RedditLink? {
    val name = pathSegments.getOrNull(1) ?: return null
    return when (pathSegments[0]) {
        "r" -> subredditPathLink(name, pathSegments, context)
        "u", "user" -> RedditLink.User(name)
        else -> null
    }
}

// A link under /r/<subreddit>/: the subreddit itself, a post, a comment or a share link.
private fun subredditPathLink(
    subreddit: String,
    pathSegments: List<String>,
    context: Int?,
): RedditLink {
    val id = pathSegments.getOrNull(3) ?: return RedditLink.Subreddit(subreddit)
    val commentId = pathSegments.getOrNull(5)
    return when (pathSegments[2]) {
        "comments" -> if (commentId != null) RedditLink.Comment(subreddit, id, commentId, context) else RedditLink.Post(subreddit, id)
        "s" -> RedditLink.ShareLink(subreddit, id)
        else -> RedditLink.Subreddit(subreddit)
    }
}
