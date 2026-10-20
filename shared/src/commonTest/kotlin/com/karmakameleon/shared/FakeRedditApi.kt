package com.karmakameleon.shared

import com.karmakameleon.shared.data.api.CommentOrMore
import com.karmakameleon.shared.data.api.RedditApi
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
import kotlinx.coroutines.delay

class FakeRedditApi(
    fakeAuthManager: FakeAuthManager = FakeAuthManager(),
) : RedditApi(
        httpClient = HttpClient(MockEngine { respond("", HttpStatusCode.OK) }),
        authManager = fakeAuthManager,
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
    var meResult: User? = null
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
    var resolveShareLinkResult: Pair<String, String>? = null

    var shouldThrow: Exception? = null

    // What the code under test asked for, in order.
    val postWithCommentsRequests = mutableListOf<CommentsRequest>()
    val moreCommentsRequests = mutableListOf<MoreCommentsRequest>()
    val submittedComments = mutableListOf<Pair<String, String>>()
    val editedComments = mutableListOf<Pair<String, String>>()
    val deletedComments = mutableListOf<String>()
    val userContentRequests = mutableListOf<UserContentRequest>()
    val searchRequests = mutableListOf<SearchRequest>()
    val subredditSearchesIncludingNsfw = mutableListOf<Boolean>()
    val messagesMarkedUnread = mutableListOf<String>()
    val blockedUsers = mutableListOf<String>()
    val inboxRequests = mutableListOf<InboxFilter>()
    val postsRequests = mutableListOf<String?>()
    val subscriptionChanges = mutableListOf<String>()

    /** Results for the threads of particular comments, by comment ID. Other requests get [postWithCommentsResult]. */
    val commentThreadResults = mutableMapOf<String, Pair<Post, List<CommentOrMore>>>()

    data class CommentsRequest(
        val sort: CommentSort?,
        val commentId: String?,
        val context: Int?,
    )

    /** A request for one of a user's lists, such as "submitted" or "saved_comments". */
    data class UserContentRequest(
        val list: String,
        val username: String,
        val sort: PostSort? = null,
        val timeFilter: TimeFilter? = null,
        val after: String? = null,
    )

    data class SearchRequest(
        val query: String,
        val sort: SearchSort,
        val time: TimeFilter,
        val after: String?,
    )

    data class MoreCommentsRequest(
        val linkId: String,
        val children: List<String>,
        val sort: CommentSort,
    )

    /** How long getPosts, search and getInbox take, so a test can act while one is in flight. */
    var responseDelayMillis = 0L

    private suspend fun respondLater() {
        if (responseDelayMillis > 0) delay(responseDelayMillis)
    }

    private fun <T> throwOrReturn(value: T): T {
        shouldThrow?.let { throw it }
        return value
    }

    override suspend fun getPosts(
        subreddit: String?,
        sort: PostSort,
        time: TimeFilter?,
        after: String?,
        limit: Int,
    ): Listing<Post> {
        postsRequests += subreddit
        respondLater()
        return throwOrReturn(postsResult)
    }

    override suspend fun fetchPostsByIds(fullnames: List<String>): List<Post> = throwOrReturn(fetchPostsByIdsResult)

    override suspend fun resolveShareLink(
        subreddit: String,
        shareId: String,
    ): Pair<String, String>? = throwOrReturn(resolveShareLinkResult)

    override suspend fun getPost(
        subreddit: String,
        postId: String,
    ): Post? = throwOrReturn(postResult)

    override suspend fun getPostWithComments(
        subreddit: String,
        postId: String,
        sort: CommentSort?,
        limit: Int,
        commentId: String?,
        context: Int?,
    ): Pair<Post, List<CommentOrMore>> {
        postWithCommentsRequests += CommentsRequest(sort, commentId, context)
        val result = commentId?.let { commentThreadResults[it] } ?: postWithCommentsResult
        return throwOrReturn(result ?: error("No result configured"))
    }

    override suspend fun getMoreComments(
        linkId: String,
        children: List<String>,
        sort: CommentSort,
    ): List<CommentOrMore> {
        moreCommentsRequests += MoreCommentsRequest(linkId, children, sort)
        return throwOrReturn(moreCommentsResult)
    }

    override suspend fun vote(
        thingId: String,
        direction: Int,
    ): Boolean = throwOrReturn(voteResult)

    override suspend fun save(thingId: String): Boolean = throwOrReturn(saveResult)

    override suspend fun unsave(thingId: String): Boolean = throwOrReturn(unsaveResult)

    override suspend fun hide(thingId: String): Boolean = throwOrReturn(hideResult)

    override suspend fun unhide(thingId: String): Boolean = throwOrReturn(unhideResult)

    override suspend fun getSubreddit(name: String): Subreddit? = throwOrReturn(subredditResult)

    override suspend fun getSubscribedSubreddits(
        after: String?,
        limit: Int,
    ): Listing<Subreddit> = throwOrReturn(subscribedSubredditsResult)

    override suspend fun subscribe(subredditName: String): Boolean {
        subscriptionChanges += "sub $subredditName"
        return throwOrReturn(subscribeResult)
    }

    override suspend fun unsubscribe(subredditName: String): Boolean {
        subscriptionChanges += "unsub $subredditName"
        return throwOrReturn(unsubscribeResult)
    }

    override suspend fun searchSubreddits(
        query: String,
        limit: Int,
        includeOver18: Boolean,
    ): List<Subreddit> {
        subredditSearchesIncludingNsfw += includeOver18
        return throwOrReturn(searchSubredditsResult)
    }

    override suspend fun getPopularSubreddits(
        after: String?,
        limit: Int,
    ): Listing<Subreddit> = throwOrReturn(popularSubredditsResult)

    override suspend fun getMe(): User? = throwOrReturn(meResult)

    override suspend fun getUser(username: String): User? = throwOrReturn(userResult)

    override suspend fun getUserPosts(
        username: String,
        sort: PostSort,
        timeFilter: TimeFilter?,
        after: String?,
        limit: Int,
    ): Listing<Post> {
        userContentRequests += UserContentRequest("submitted", username, sort, timeFilter, after)
        return throwOrReturn(userPostsResult)
    }

    override suspend fun getUserComments(
        username: String,
        sort: PostSort,
        timeFilter: TimeFilter?,
        after: String?,
        limit: Int,
    ): Listing<Comment> {
        userContentRequests += UserContentRequest("comments", username, sort, timeFilter, after)
        return throwOrReturn(userCommentsResult)
    }

    override suspend fun getSavedPosts(
        username: String,
        after: String?,
        limit: Int,
    ): Listing<Post> {
        userContentRequests += UserContentRequest("saved_posts", username, after = after)
        return throwOrReturn(savedPostsResult)
    }

    override suspend fun getSavedComments(
        username: String,
        after: String?,
        limit: Int,
    ): Listing<Comment> {
        userContentRequests += UserContentRequest("saved_comments", username, after = after)
        return throwOrReturn(savedCommentsResult)
    }

    override suspend fun getUpvotedPosts(
        username: String,
        after: String?,
        limit: Int,
    ): Listing<Post> {
        userContentRequests += UserContentRequest("upvoted", username, after = after)
        return throwOrReturn(upvotedPostsResult)
    }

    override suspend fun getDownvotedPosts(
        username: String,
        after: String?,
        limit: Int,
    ): Listing<Post> {
        userContentRequests += UserContentRequest("downvoted", username, after = after)
        return throwOrReturn(downvotedPostsResult)
    }

    override suspend fun search(
        query: String,
        subreddit: String?,
        sort: SearchSort,
        time: TimeFilter,
        after: String?,
        limit: Int,
    ): Listing<Post> {
        searchRequests += SearchRequest(query, sort, time, after)
        respondLater()
        return throwOrReturn(searchResult)
    }

    override suspend fun submitComment(
        parentId: String,
        text: String,
    ): Comment? {
        submittedComments += parentId to text
        return throwOrReturn(submitCommentResult)
    }

    override suspend fun editComment(
        thingId: String,
        text: String,
    ): Boolean {
        editedComments += thingId to text
        return throwOrReturn(editCommentResult)
    }

    override suspend fun deleteComment(thingId: String): Boolean {
        deletedComments += thingId
        return throwOrReturn(deleteCommentResult)
    }

    override suspend fun getInbox(
        filter: InboxFilter,
        after: String?,
        limit: Int,
    ): Listing<Message> {
        inboxRequests += filter
        respondLater()
        return throwOrReturn(inboxResult)
    }

    override suspend fun sendMessage(
        to: String,
        subject: String,
        body: String,
    ): Boolean = throwOrReturn(sendMessageResult)

    override suspend fun markMessageRead(thingId: String): Boolean = throwOrReturn(markMessageReadResult)

    override suspend fun markMessageUnread(thingId: String): Boolean {
        messagesMarkedUnread += thingId
        return throwOrReturn(markMessageUnreadResult)
    }

    override suspend fun blockUser(username: String): Boolean {
        blockedUsers += username
        return throwOrReturn(blockUserResult)
    }

    override suspend fun markAllMessagesRead(): Boolean = throwOrReturn(markAllMessagesReadResult)

    override suspend fun submitPost(
        subreddit: String,
        title: String,
        kind: String,
        text: String?,
        url: String?,
        nsfw: Boolean,
        spoiler: Boolean,
        flairId: String?,
        flairText: String?,
    ): String? = throwOrReturn(submitPostResult)
}
