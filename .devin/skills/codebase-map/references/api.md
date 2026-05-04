# Reddit API Reference

All networking lives in `shared/src/commonMain/kotlin/com/karmakameleon/shared/data/api/`.

## Files

| File | Purpose |
|---|---|
| `RedditApi.kt` | Single ~1300-line class with every Reddit endpoint. Methods are `open suspend` so `FakeRedditApi` can override them in tests. |
| `AuthManager.kt` | OAuth2 token storage, refresh, userless (anonymous) tokens, cookie handling for `r/all`. |
| `HttpClientFactory.kt` + `.android.kt` / `.ios.kt` | `createHttpClientWithConfig()` (expect) builds a Ktor `HttpClient` with JSON content-negotiation, logging, Auth plugin, User-Agent. |
| `RedditApiException.kt` | Typed API errors (with HTTP status + body). |
| `dto/AuthDto.kt` | OAuth token response payloads. |
| `dto/CommentDto.kt` | Comment + MoreChildrenResponse + MoreCommentsDto. |
| `dto/MessageDto.kt` | Inbox/message payloads. |
| `dto/RedditResponse.kt` | `RedditResponse<T>`, `ListingData<T>`, `ThingData<T>` envelopes + `PostDto`, `MediaDto`, `MediaMetadataDto`, `PreviewDto`, `GalleryDataDto`. |
| `dto/SubredditDto.kt` | Subreddit listing items + `UserSubredditDto`. |
| `dto/UserDto.kt` | User + `AccountDto`. |

## Auth

- `AuthManager` is constructed with `HttpClient` + `Settings`.
- Stores: access token, refresh token, userless token, expiry timestamps.
- `ensureValidAccessToken()` - refreshes if expired. Called by `authenticatedRequest` in `RedditApi`.
- Userless token: used for anonymous reads (no logged-in user). Refreshed on demand.
- Cookies: certain endpoints (e.g. `r/all`) require session cookies; `AuthManager` manages those.
- OAuth flow: `androidApp/auth/OAuthActivity.kt` handles `karmakameleon://oauth` redirect, passes the code back to `AuthManager.exchangeCode(...)`.

## RedditApi method map

All methods are `open suspend`. Most return nullable on failure or `Boolean` for mutations.

### Posts / feeds
- `getPosts(subreddit, sort, timeFilter, after, limit)` - feed endpoint. Handles r/all, popular, frontpage, subreddit listings.
- `fetchPostsByIds(fullnames)` - `by_id` endpoint for batch fetching (used by ProfileViewModel upvoted/downvoted tabs).
- `getPost(subreddit, postId)` - single post metadata.
- `getPostWithComments(subreddit, postId, commentId, context, sort)` - post + full comment tree; supports deep-link commentId+context.
- `getMoreComments(linkId, childrenIds, sort)` - expands a `MoreComments` node.
- `resolveShareLink(subreddit, shareId)` - resolves `/r/x/s/abc123` to `(subreddit, postId)`.

### Voting / saving / hiding (all take `thingId` = `t1_...` or `t3_...`)
- `vote(thingId, direction)` - direction: 1/0/-1.
- `save(thingId)` / `unsave(thingId)`
- `hide(thingId)` / `unhide(thingId)`

### Subreddits
- `getSubreddit(name)` - sidebar info (about.json).
- `getSubscribedSubreddits(after, limit)` - logged-in user's subs.
- `subscribe(name)` / `unsubscribe(name)`
- `searchSubreddits(query, limit, includeOver18)`
- `getPopularSubreddits(after, limit)`

### Users
- `getMe()` - current `Account` (requires auth).
- `getUser(username)` - public profile.
- `getUserPosts(username, sort, timeFilter, after, limit)`
- `getUserComments(username, sort, timeFilter, after, limit)`
- `getSavedPosts(username, after, limit)` / `getSavedComments(username, after, limit)`
- `getUpvotedPosts(username, after, limit)` / `getDownvotedPosts(username, after, limit)`

### Search
- `search(query, type, sort, timeFilter, subreddit, after, limit, includeOver18)` - unified. Returns posts/subs/users depending on `SearchType`.

### Comments (write)
- `submitComment(parentId, text)` - reply to post (`t3_`) or comment (`t1_`).
- `editComment(thingId, text)`
- `deleteComment(thingId)`

### Inbox / messages
- `getInbox(filter, after, limit)` - `InboxFilter` = INBOX, UNREAD, MESSAGES, COMMENT_REPLIES, MENTIONS, SENT.
- `sendMessage(to, subject, body)`
- `markMessageRead(thingId)` / `markMessageUnread(thingId)`
- `markAllMessagesRead()`
- `blockUser(username)`

### Post submission
- `submitPost(...)` - large signature: handles self-text, link, image/video, NSFW, spoiler flags, flair.

### Private helpers
- `authenticatedRequest(...)` - central request wrapper. Adds Bearer token, handles 401 -> refresh -> retry.
- `fetchSubredditsBatch(names)` / `fetchAndCacheSubredditNsfw(name)` - internal subreddit NSFW cache hydration.

## DTO -> Domain mapping

DTO classes (`@Serializable`) mirror Reddit's JSON wire format. Mapping happens inside `RedditApi` methods (look for `.toPost()`, `.toComment()`, etc. extension functions defined within the same file or domain-model file).

- `PostDto` -> `Post` (with `Preview`, `Media`, `GalleryData`).
- `CommentDto` -> `Comment` tree, with `MoreCommentsDto` -> `MoreComments` placeholders.
- `SubredditDto` -> `Subreddit`.
- `UserDto` / `AccountDto` -> `User` / `Account`.
- `MessageDto` -> `Message`.

## Testing

Use `FakeRedditApi` in `commonTest/.../FakeRedditApi.kt` - subclass of `RedditApi` that overrides methods to return fixtures from `TestData.kt`. For raw HTTP-level tests, use Ktor `MockEngine`.

`FakeAuthManager` provides a no-network AuthManager for tests.

Real-API integration tests live in `shared/src/androidHostTest/.../RedditApiIntegrationTest.kt` and require `.env` with `REDDIT_CLIENT_ID`.
