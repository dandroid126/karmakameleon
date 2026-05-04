# Data Layer Reference

Repositories wrap `RedditApi` and add caching, optimistic updates, and persisted state. All live in `shared/src/commonMain/kotlin/com/karmakameleon/shared/data/repository/`.

Wired in `shared/di/SharedModule.kt` as `single { }`.

## Repositories

### PostRepository.kt (~254 lines)
- Depends on `RedditApi`.
- Feed fetching with pagination + blocked-subreddit filtering.
- **Optimistic updates**: `toggleVote`, `toggleSave`, `toggleHide` update an in-memory post cache and call the API; revert on failure.
- Exposes posts via lookup helpers so ViewModels share cached state.

### CommentRepository.kt (~93 lines)
- Depends on `RedditApi`.
- `getCommentsForPost`, `loadMoreComments`, `submit`, `edit`, `delete`.
- Also handles vote/save for comments through `RedditApi.vote/save`.

### CommentDraftRepository.kt (~23 lines)
- Depends on `Settings`.
- `getDraft(parentId)` / `setDraft(parentId, text)` / `clearDraft(parentId)` - persisted reply drafts keyed by parent thing ID. Storage key prefix: `comment_draft_`.

### SubredditRepository.kt (~124 lines)
- Depends on `RedditApi`, `Settings`.
- Subscribed-subreddits cache + pagination.
- `search(query)`, `getPopular(after)`, `getSidebar(name)`.
- Subscribe/unsubscribe updates the cached set.

### UserRepository.kt (~154 lines)
- Depends on `RedditApi`, `Settings`.
- `currentAccount: StateFlow<Account?>` - nullable when logged out.
- `loadCurrentUser()` - called from `KarmaKameleonApp` LaunchedEffect to populate the account on cold start.
- `login(...)` / `logout()` - trigger auth flow + clear state.

### MessageRepository.kt (~119 lines)
- Depends on `RedditApi`.
- Inbox fetching per `InboxFilter`, send message, mark read/unread, mark all read.

### InboxPoller.kt (~65 lines)
- Depends on `MessageRepository`, `SettingsRepository`.
- Shared scheduling logic. Android side uses WorkManager (`InboxNotificationWorker`) that consults this for "new messages since last poll".
- Poll interval read from `SettingsRepository.notificationInterval` (`NotificationInterval` enum).

### ReadPostsRepository.kt (~85 lines)
- Depends on `Settings`.
- Tracks set of read post IDs (with an internal `ReadPostEntry` data class for timestamp bookkeeping). Used to grey-out already-read posts in feeds.
- Has capacity/TTL eviction (see `companion object` constants).

### SettingsRepository.kt (~201 lines)
- Depends on `Settings`.
- All persisted prefs, each as `StateFlow<T>`:
  - `inlineImagesEnabled: Boolean`
  - `notificationInterval: NotificationInterval`
  - `blockedSubreddits: Set<String>`
  - `favoriteSubreddits: Set<String>`
  - `nsfwEnabled: Boolean`
  - `nsfwCacheMedia: Boolean`
  - `nsfwPreviewMode: NsfwPreviewMode`
  - `nsfwSearchEnabled: Boolean`
  - `nsfwHistoryMode: NsfwHistoryMode`
  - `spoilerPreviewsEnabled: Boolean`
  - `useSuggestedSort: Boolean`
  - `subredditNsfwCache: Map<String, Boolean>` - cached per-subreddit NSFW flag to avoid re-fetching `about.json`.
- Keys declared in `companion object` as `KEY_*` constants.

## Pattern for adding a setting

1. Add `KEY_FOO` constant in `SettingsRepository.companion object`.
2. Add `private val _foo = MutableStateFlow(...)` + `val foo: StateFlow<T> = _foo.asStateFlow()`.
3. Add loader (`loadFoo()`) if the value needs parsing (enums, sets).
4. Add setter `fun setFoo(value: T) { settings[KEY_FOO] = ...; _foo.value = value }`.
5. Inject `SettingsRepository` wherever it's consumed; observe the `StateFlow`.
6. Surface UI in `androidApp/ui/settings/SettingsScreen.kt`.

## Pattern for adding a repository

1. New file in `shared/data/repository/NewRepository.kt`.
2. Register in `SharedModule.kt`: `single { NewRepository(get()) }`.
3. Inject via Koin (`koinInject<NewRepository>()` in Compose, constructor-inject in ViewModels).
4. Unit test in `commonTest/.../data/repository/NewRepositoryTest.kt` using `FakeRedditApi` + `MapSettings`.

## Testing

- `MapSettings` (from `multiplatform-settings-test`) is the in-memory `Settings` impl used in tests.
- `FakeRedditApi` overrides API methods to return `TestData` fixtures.
- Run a specific repository test:
  ```bash
  ./gradlew :shared:testDebugUnitTest --tests "com.karmakameleon.shared.data.repository.PostRepositoryTest"
  ```
