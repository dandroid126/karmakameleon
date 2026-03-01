package com.karmakameleon.shared

import com.karmakameleon.shared.data.api.CommentOrMore
import com.karmakameleon.shared.data.api.RedditApi
import com.karmakameleon.shared.domain.model.Account
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.domain.model.CommentSort
import com.karmakameleon.shared.domain.model.InboxFilter
import com.karmakameleon.shared.domain.model.Listing
import com.karmakameleon.shared.domain.model.Message
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.domain.model.PostSort
import com.karmakameleon.shared.domain.model.SearchSort
import com.karmakameleon.shared.domain.model.Subreddit
import com.karmakameleon.shared.domain.model.TimeFilter
import com.karmakameleon.shared.domain.model.User
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode

class FakeRedditApi(
    fakeAuthManager: FakeAuthManager = FakeAuthManager()
) : RedditApi(
    httpClient = HttpClient(MockEngine { respond("", HttpStatusCode.OK) }),
    authManager = fakeAuthManager
) {
    var postsResult: Listing<Post> = createTestListing()
    var postResult: Post? = null
    var postWithCommentsResult: Pair<Post, List<CommentOrMore>>? = null
    var moreCommentsResult: List<CommentOrMore> = emptyList()
    var voteResult: Boolean = true
    var saveResult: Boolean = true
    var unsaveResult: Boolean = true
    var hideResult: Boolean = true
    var unhideResult: Boolean = true
    var subredditResult: Subreddit? = null
    var subscribedSubredditsResult: Listing<Subreddit> = createTestListing()
    var subscribeResult: Boolean = true
    var unsubscribeResult: Boolean = true
    var searchSubredditsResult: List<Subreddit> = emptyList()
    var popularSubredditsResult: Listing<Subreddit> = createTestListing()
    var meResult: Account? = null
    var userResult: User? = null
    var userPostsResult: Listing<Post> = createTestListing()
    var userCommentsResult: Listing<Comment> = createTestListing()
    var savedPostsResult: Listing<Post> = createTestListing()
    var savedCommentsResult: Listing<Comment> = createTestListing()
    var upvotedPostsResult: Listing<Post> = createTestListing()
    var downvotedPostsResult: Listing<Post> = createTestListing()
    var searchResult: Listing<Post> = createTestListing()
    var submitCommentResult: Comment? = null
    var editCommentResult: Boolean = true
    var deleteCommentResult: Boolean = true
    var inboxResult: Listing<Message> = createTestListing()
    var sendMessageResult: Boolean = true
    var markMessageReadResult: Boolean = true
    var markMessageUnreadResult: Boolean = true
    var blockUserResult: Boolean = true
    var markAllMessagesReadResult: Boolean = true
    var submitPostResult: String? = null
    var fetchPostsByIdsResult: List<Post> = emptyList()

    var shouldThrow: Exception? = null

    private fun <T> throwOrReturn(value: T): T {
        shouldThrow?.let { throw it }
        return value
    }

    override suspend fun getPosts(
        subreddit: String?, sort: PostSort, time: TimeFilter?, after: String?, limit: Int
    ): Listing<Post> = throwOrReturn(postsResult)

    override suspend fun fetchPostsByIds(fullnames: List<String>): List<Post> =
        throwOrReturn(fetchPostsByIdsResult)

    override suspend fun getPost(subreddit: String, postId: String): Post? =
        throwOrReturn(postResult)

    override suspend fun getPostWithComments(
        subreddit: String, postId: String, sort: CommentSort, limit: Int,
        commentId: String?, context: Int?
    ): Pair<Post, List<CommentOrMore>> =
        throwOrReturn(postWithCommentsResult ?: throw Exception("No result configured"))

    override suspend fun getMoreComments(
        linkId: String, children: List<String>, sort: CommentSort
    ): List<CommentOrMore> = throwOrReturn(moreCommentsResult)

    override suspend fun vote(thingId: String, direction: Int): Boolean =
        throwOrReturn(voteResult)

    override suspend fun save(thingId: String): Boolean =
        throwOrReturn(saveResult)

    override suspend fun unsave(thingId: String): Boolean =
        throwOrReturn(unsaveResult)

    override suspend fun hide(thingId: String): Boolean =
        throwOrReturn(hideResult)

    override suspend fun unhide(thingId: String): Boolean =
        throwOrReturn(unhideResult)

    override suspend fun getSubreddit(name: String): Subreddit? =
        throwOrReturn(subredditResult)

    override suspend fun getSubscribedSubreddits(after: String?, limit: Int): Listing<Subreddit> =
        throwOrReturn(subscribedSubredditsResult)

    override suspend fun subscribe(subredditName: String): Boolean =
        throwOrReturn(subscribeResult)

    override suspend fun unsubscribe(subredditName: String): Boolean =
        throwOrReturn(unsubscribeResult)

    override suspend fun searchSubreddits(query: String, limit: Int, includeOver18: Boolean): List<Subreddit> =
        throwOrReturn(searchSubredditsResult)

    override suspend fun getPopularSubreddits(after: String?, limit: Int): Listing<Subreddit> =
        throwOrReturn(popularSubredditsResult)

    override suspend fun getMe(): Account? =
        throwOrReturn(meResult)

    override suspend fun getUser(username: String): User? =
        throwOrReturn(userResult)

    override suspend fun getUserPosts(
        username: String, sort: PostSort, timeFilter: TimeFilter?, after: String?, limit: Int
    ): Listing<Post> = throwOrReturn(userPostsResult)

    override suspend fun getUserComments(
        username: String, sort: PostSort, timeFilter: TimeFilter?, after: String?, limit: Int
    ): Listing<Comment> = throwOrReturn(userCommentsResult)

    override suspend fun getSavedPosts(
        username: String, after: String?, limit: Int
    ): Listing<Post> = throwOrReturn(savedPostsResult)

    override suspend fun getSavedComments(
        username: String, after: String?, limit: Int
    ): Listing<Comment> = throwOrReturn(savedCommentsResult)

    override suspend fun getUpvotedPosts(
        username: String, after: String?, limit: Int
    ): Listing<Post> = throwOrReturn(upvotedPostsResult)

    override suspend fun getDownvotedPosts(
        username: String, after: String?, limit: Int
    ): Listing<Post> = throwOrReturn(downvotedPostsResult)

    override suspend fun search(
        query: String, subreddit: String?, sort: SearchSort, time: TimeFilter,
        after: String?, limit: Int
    ): Listing<Post> = throwOrReturn(searchResult)

    override suspend fun submitComment(parentId: String, text: String): Comment? =
        throwOrReturn(submitCommentResult)

    override suspend fun editComment(thingId: String, text: String): Boolean =
        throwOrReturn(editCommentResult)

    override suspend fun deleteComment(thingId: String): Boolean =
        throwOrReturn(deleteCommentResult)

    override suspend fun getInbox(
        filter: InboxFilter, after: String?, limit: Int
    ): Listing<Message> = throwOrReturn(inboxResult)

    override suspend fun sendMessage(to: String, subject: String, body: String): Boolean =
        throwOrReturn(sendMessageResult)

    override suspend fun markMessageRead(thingId: String): Boolean =
        throwOrReturn(markMessageReadResult)

    override suspend fun markMessageUnread(thingId: String): Boolean =
        throwOrReturn(markMessageUnreadResult)

    override suspend fun blockUser(username: String): Boolean =
        throwOrReturn(blockUserResult)

    override suspend fun markAllMessagesRead(): Boolean =
        throwOrReturn(markAllMessagesReadResult)

    override suspend fun submitPost(
        subreddit: String, title: String, kind: String, text: String?, url: String?,
        nsfw: Boolean, spoiler: Boolean, flairId: String?, flairText: String?
    ): String? = throwOrReturn(submitPostResult)
}
