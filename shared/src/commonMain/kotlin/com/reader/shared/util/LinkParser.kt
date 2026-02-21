package com.reader.shared.util

import io.ktor.http.Url

private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif", "heic", "heif", "ico")
private val videoExtensions = setOf("mp4", "webm")
private val youTubeHosts = setOf(
    "youtube.com", "www.youtube.com", "m.youtube.com",
    "youtu.be", "www.youtu.be",
    "youtube-nocookie.com", "www.youtube-nocookie.com"
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
    data class Subreddit(val name: String) : RedditLink()
    data class User(val name: String) : RedditLink()
    data class Post(val subreddit: String, val postId: String) : RedditLink()
    data class Comment(val subreddit: String, val postId: String, val commentId: String, val context: Int? = null) : RedditLink()
    data class External(val url: String) : RedditLink()
}

private val redditHostRegex = Regex("^(www\\.|old\\.|new\\.|np\\.)?reddit\\.com$", RegexOption.IGNORE_CASE)

fun parseRedditLink(url: String): RedditLink {
    val normalizedUrl = if (url.startsWith("/r/") || url.startsWith("/u/") || url.startsWith("/user/")) {
        "https://www.reddit.com$url"
    } else {
        url
    }

    val parsedUrl = try {
        Url(normalizedUrl)
    } catch (_: Exception) {
        return RedditLink.External(url)
    }

    val host = parsedUrl.host
    if (!redditHostRegex.matches(host)) {
        return RedditLink.External(url)
    }

    val pathSegments = parsedUrl.segments.filter { it.isNotEmpty() }
    val contextParam = parsedUrl.parameters["context"]?.toIntOrNull()

    return when {
        pathSegments.size >= 6
                && pathSegments[0] == "r"
                && pathSegments[2] == "comments"
                && pathSegments[5].isNotEmpty() -> {
            RedditLink.Comment(
                subreddit = pathSegments[1],
                postId = pathSegments[3],
                commentId = pathSegments[5],
                context = contextParam
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
