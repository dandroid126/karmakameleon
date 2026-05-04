# Shared ViewModels Reference

All VMs live in `shared/src/commonMain/kotlin/com/karmakameleon/shared/ui/`. They use the multiplatform `lifecycle-viewmodel` (org.jetbrains.androidx.lifecycle), so the same class is consumed from Android Compose via Koin `viewModel { }` and (eventually) iOS.

Registered in `androidApp/src/main/kotlin/com/karmakameleon/android/di/AndroidModule.kt`.

## Convention

Every VM has:
- An immutable `data class XxxUiState(...)` with all screen state.
- `private val _uiState = MutableStateFlow(XxxUiState())`.
- `val uiState: StateFlow<XxxUiState> = _uiState.asStateFlow()`.
- Public action methods (e.g. `onRefresh()`, `onVote(postId, direction)`) that launch coroutines in `viewModelScope`.
- Updates state via `_uiState.update { it.copy(...) }` (atomic).

## ViewModels

### FeedViewModel.kt (~259 lines)
- Dependencies: `PostRepository`, `SettingsRepository`, `ReadPostsRepository`, `UserRepository`.
- State: `FeedUiState(feedType, posts, sort, timeFilter, isLoading, error, isRefreshing, hasMore)`.
- `FeedType` enum: FRONTPAGE, POPULAR, ALL (and user-subs cases). `displayName` + nullable `subreddit` target.
- Actions: switch feed, change sort, refresh, loadMore, vote, save, hide, markRead.

### PostDetailViewModel.kt (~311 lines)
- Dependencies: `PostRepository`, `CommentRepository`, `SettingsRepository`, `ReadPostsRepository`, `UserRepository`, `CommentDraftRepository`, plus `CommentViewModel` factory params.
- Takes post coordinates via Koin `parametersOf`: `subreddit`, `postId`, optional `commentId`, optional `context`.
- State: `PostDetailUiState(post, comments, commentSort, isLoading, error, ...)`.
- Delegates comment list rendering to `CommentViewModel` logic.

### CommentViewModel.kt (~683 lines - largest VM)
- Shared between PostDetail and profile "saved comments" style screens.
- `FlatCommentItem` sealed class: flattened comment tree (Comment | MoreComments | Loading | Deleted).
- `CommentUiState(items, sort, collapsedIds, loadingIds, ...)`.
- Handles collapse/expand, load-more-children, optimistic vote/save, submit/edit/delete, draft persistence via `CommentDraftRepository`.

### SubredditViewModel.kt (~233 lines)
- Dependencies: `SubredditRepository`, `PostRepository`, `SettingsRepository`, `ReadPostsRepository`, `UserRepository`.
- State: `SubredditUiState(name, posts, sort, timeFilter, sidebar, isSubscribed, ...)`.
- Constructor takes subreddit name via Koin `parametersOf` (`params.get()`).

### ProfileViewModel.kt (~666 lines)
- Dependencies: `UserRepository`, `PostRepository`, `CommentRepository`, `SettingsRepository`.
- `ProfileTab` enum: POSTS, COMMENTS, SAVED, UPVOTED, DOWNVOTED.
- `SavedContentType` enum: POSTS, COMMENTS (for the saved-tab sub-toggle).
- State: `ProfileUiState(username, user, tab, posts, comments, savedContentType, isLoading, ...)`.
- Handles pagination per tab.

### InboxViewModel.kt (~180 lines)
- Dependencies: `MessageRepository`, `UserRepository`, `SettingsRepository`.
- State: `InboxUiState(filter, items, isLoading, error, ...)`.
- Accepts an initial `InboxFilter?` (from deep-link notifications).

### SearchViewModel.kt (~193 lines)
- Dependencies: `PostRepository` (for vote/save on search results), `SubredditRepository`, `UserRepository`/`RedditApi` for results.
- State: `SearchUiState(query, type, sort, timeFilter, subreddit, posts, subreddits, users, isLoading, ...)`.
- `SearchType`: POSTS, SUBREDDITS, USERS.

## Registering a ViewModel

In `AndroidModule.kt`:
```kotlin
viewModel { MyViewModel(get(), get()) }
// With runtime params:
viewModel { params -> MyViewModel(params[0], get()) }
```

Consuming in Compose:
```kotlin
val vm: MyViewModel = koinViewModel()
// Or with params:
val vm: MyViewModel = koinViewModel(parameters = { parametersOf(arg1, arg2) })
val state by vm.uiState.collectAsState()
```

## Testing ViewModels

Each VM has a `*Test.kt` in `commonTest/.../ui/<area>/`. Pattern:
- Use `runTest { }` from `kotlinx-coroutines-test`.
- Set `Dispatchers.setMain(StandardTestDispatcher())` before creating the VM; reset in `@AfterTest`.
- Pass `FakeRedditApi` + `MapSettings`-backed repositories.
- Assert on `vm.uiState.value` after actions.
