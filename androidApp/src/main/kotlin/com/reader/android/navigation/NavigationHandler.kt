package com.reader.android.navigation

import com.reader.shared.util.RedditLink
import com.reader.shared.util.parseRedditLink

class NavigationHandler {
    var onSubredditClick: (String) -> Unit = {}
    var onUserClick: (String) -> Unit = {}
    var onExternalLinkClick: (String) -> Unit = {}
    var onPostClick: (subreddit: String, postId: String) -> Unit = { _, _ -> }
    var onCommentClick: (subreddit: String, postId: String, commentId: String) -> Unit = { _, _, _ -> }

    fun handleLink(url: String) {
        when (val link = parseRedditLink(url)) {
            is RedditLink.Subreddit -> onSubredditClick(link.name)
            is RedditLink.User -> onUserClick(link.name)
            is RedditLink.Post -> onPostClick(link.subreddit, link.postId)
            is RedditLink.Comment -> onCommentClick(link.subreddit, link.postId, link.commentId)
            is RedditLink.External -> onExternalLinkClick(link.url)
        }
    }
}
