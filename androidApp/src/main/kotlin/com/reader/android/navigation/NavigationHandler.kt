package com.reader.android.navigation

import com.reader.shared.util.RedditLink
import com.reader.shared.util.isImageUrl
import com.reader.shared.util.isVideoUrl
import com.reader.shared.util.isYouTubeUrl
import com.reader.shared.util.parseRedditLink

class NavigationHandler {
    var onSubredditClick: (String) -> Unit = {}
    var onUserClick: (String) -> Unit = {}
    var onExternalLinkClick: (String) -> Unit = {}
    var onPostClick: (subreddit: String, postId: String) -> Unit = { _, _ -> }
    var onCommentClick: (subreddit: String, postId: String, commentId: String, context: Int?) -> Unit = { _, _, _, _ -> }
    var onShareLinkClick: (subreddit: String, shareId: String) -> Unit = { _, _ -> }
    var onImageLinkClick: (String) -> Unit = {}
    var onVideoLinkClick: (String) -> Unit = {}
    var onYouTubeLinkClick: (String) -> Unit = {}

    fun handleLink(url: String) {
        when {
            isImageUrl(url) -> onImageLinkClick(url)
            isVideoUrl(url) -> onVideoLinkClick(url)
            isYouTubeUrl(url) -> onYouTubeLinkClick(url)
            else -> when (val link = parseRedditLink(url)) {
                is RedditLink.Subreddit -> onSubredditClick(link.name)
                is RedditLink.User -> onUserClick(link.name)
                is RedditLink.Post -> onPostClick(link.subreddit, link.postId)
                is RedditLink.Comment -> onCommentClick(link.subreddit, link.postId, link.commentId, link.context)
                is RedditLink.ShareLink -> onShareLinkClick(link.subreddit, link.shareId)
                is RedditLink.External -> onExternalLinkClick(link.url)
            }
        }
    }
}
