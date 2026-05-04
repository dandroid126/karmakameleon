---
name: codebase-map
description: Navigation map for the Karma Kameleon Reddit client (Kotlin Multiplatform + Jetpack Compose). Use this skill at the start of any task that involves finding, reading, or modifying code in this repo - including adding features, fixing bugs, adding API endpoints, modifying UI/screens, updating repositories or ViewModels, wiring navigation, changing settings, or writing tests. Consult it BEFORE searching the codebase so you jump directly to the right file. This skill is self-maintaining: after any change to the codebase, update this skill to reflect the new state (see "Maintaining this skill" section).
---

# Karma Kameleon - Codebase Map

## Maintaining this skill (read first, every time)

This skill is **self-maintaining**. It is only useful if it stays accurate, so treat updating it as part of every task - not an optional extra.

**After completing any task that changes the codebase, before marking the task done, update this skill to reflect the changes.** Specifically:

1. **New files / renamed / deleted files** -> update the relevant section of `SKILL.md` and any matching `references/*.md`. Keep the directory listings and "Where to find things" index accurate.
2. **New feature area** (new screen, new repository, new major component) -> add an entry in the appropriate section and, if the area is large, add or extend a `references/*.md` file.
3. **New Reddit API endpoint** -> add it to `references/api.md` under the correct category.
4. **New settings / new `StateFlow` on `SettingsRepository`** -> add to the list in `references/data-layer.md`.
5. **New route / `DetailScreen`** -> add to the Detail routes list in `references/android-ui.md`.
6. **New ViewModel or new fields on a `UiState`** -> update `references/viewmodels.md`.
7. **New convention or pattern adopted across the codebase** -> update the "Key conventions" section of `SKILL.md`.
8. **Changed build/verify commands, new Gradle task, toolchain bump** -> update the "Build & verify" section and the toolchain/version numbers at the bottom.
9. **Task recipe changed** (e.g. "add a new screen" now requires one more step) -> update the "Common task recipes" section.

**If you discover something in this skill is wrong** (a stale path, a renamed file, a command that doesn't work, a pattern that's been replaced, a file count that's drifted, a line-count estimate that's way off, etc.) - **fix it immediately** as part of the current task. Don't leave it for later. Accuracy is the only thing that makes this skill useful.

**When in doubt, update.** It is better to over-update this skill than to let it drift. A slightly noisy map is still useful; a stale map is actively harmful because it will send future tasks to the wrong files.

**Keep it lean.** Don't turn this into prose documentation. Favor bullet lists, short descriptions, and links over long explanations. If a reference file exceeds ~200 lines, consider splitting it. The main `SKILL.md` should stay under ~300 lines.

**Do not duplicate.** Information lives in either `SKILL.md` or one `references/*.md` - never both. If you add detail to a reference file, remove it from `SKILL.md` (replace with a link).

**Commit the skill update in the same commit as the code change** when possible, so the map and the code stay in sync in history.

## What this app is

Modern Reddit client. Kotlin Multiplatform (KMP) shared module + Android Compose app.
iOS targets exist in the build but have no UI yet.

## Module layout

```
shared/            # KMP: all business logic, API, repositories, ViewModels, domain models, markdown parser
  commonMain       # code shared across platforms (Android + future iOS)
  commonTest       # unit tests (Ktor MockEngine, MapSettings) - 75+ tests
  androidMain      # Android actuals: HttpClientFactory.android.kt, PlatformModule.android.kt
  androidHostTest  # JVM integration tests hitting the real Reddit API
  iosMain          # iOS actuals: HttpClientFactory.ios.kt, PlatformModule.ios.kt

androidApp/        # Android-only Compose UI, navigation, notifications, auth activity
```

Package prefix: `com.karmakameleon.shared.*` (shared), `com.karmakameleon.android.*` (android).

## Architecture (MVVM + Repository)

```
Compose Screen (androidApp/ui/)
  -> ViewModel (shared/ui/*/ - KMP ViewModels with StateFlow UiState)
    -> Repository (shared/data/repository/)
      -> RedditApi (shared/data/api/RedditApi.kt)
        -> Ktor HttpClient (+ AuthManager for OAuth2 token refresh)
```

DI: Koin. `SharedModule` (shared/di) wires API + repositories. `AndroidModule` (androidApp/di) wires NavigationHandler + GlobalMenuManager + ViewModels. Platform-specific factories are in `PlatformModule.android.kt` / `PlatformModule.ios.kt`.

State: `StateFlow<XxxUiState>` pattern. Each ViewModel exposes a `uiState` StateFlow with an immutable data class.

Settings storage: `multiplatform-settings` (key-value). All persisted prefs go through `SettingsRepository`.

Logging: Napier (multiplatform). Use `Napier.d/i/e(...)`.

## Where to find things (quick index)

Whenever a task mentions one of these areas, go straight to the listed paths.

### Reddit API / networking
- `shared/src/commonMain/kotlin/com/karmakameleon/shared/data/api/RedditApi.kt` - single class, ~1300 lines, all Reddit endpoints (posts, comments, vote/save/hide, subreddits, users, search, inbox, submit, share-link resolve). Methods are `open suspend` so they can be faked in tests.
- `AuthManager.kt` - OAuth2 tokens (access + refresh), auto-refresh, userless tokens for anonymous browsing, cookie management for r/all.
- `HttpClientFactory.kt` (commonMain) + `.android.kt` / `.ios.kt` actuals - Ktor client setup (JSON, logging, Auth plugin, User-Agent).
- `RedditApiException.kt` - typed API errors.
- `dto/` - DTOs mapped to domain models (AuthDto, CommentDto, MessageDto, RedditResponse/Listing/Thing, SubredditDto, UserDto). Post/Media/Gallery DTOs live inline in RedditResponse.kt.
- Detail: see [references/api.md](references/api.md) for the full RedditApi method list and endpoint map.

### Data layer (repositories)
- `shared/src/commonMain/kotlin/com/karmakameleon/shared/data/repository/`
  - `PostRepository.kt` - post fetching + caching, optimistic vote/save.
  - `CommentRepository.kt` - comment threads, submit/edit/delete, more-children expansion.
  - `CommentDraftRepository.kt` - persisted comment drafts (Settings-backed).
  - `SubredditRepository.kt` - subscriptions, popular list, search, sidebar info, subscribed-cache.
  - `UserRepository.kt` - current account (StateFlow `currentAccount`), user profile, login state.
  - `MessageRepository.kt` - inbox/messages/replies/mentions.
  - `InboxPoller.kt` - background-poll scheduler shared logic (notification trigger).
  - `ReadPostsRepository.kt` - tracks read post IDs locally.
  - `SettingsRepository.kt` - all user prefs as `StateFlow`: NSFW modes, inline images, favorites, blocked subs, notification interval, spoiler previews, suggested sort, subreddit-NSFW cache.
- Detail: see [references/data-layer.md](references/data-layer.md).

### Domain models
- `shared/src/commonMain/kotlin/com/karmakameleon/shared/domain/model/`
  - `Post.kt` (Post, Preview, PreviewImage, ImageSource, Media, RedditVideo, GalleryData, GalleryItem, VoteState, PostSort, TimeFilter)
  - `Comment.kt` (Comment, MoreComments, FlairRichtext, CommentSort)
  - `Subreddit.kt` (Subreddit, SubredditRule)
  - `User.kt` (User, UserSubreddit, Account, Trophy, KarmaBreakdown)
  - `Message.kt` (Message, MessageType, Inbox, InboxFilter)
  - `Listing.kt` (Listing<T>, SearchResult, SearchSort, SearchType)
  - `NsfwPreviewMode.kt`, `NsfwHistoryMode.kt`, `NotificationInterval.kt`
- `domain/markdown/` - `MarkdownBlock.kt`, `MarkdownInline.kt` (parsed markdown AST).

### Shared ViewModels (`shared/src/commonMain/kotlin/com/karmakameleon/shared/ui/`)
One VM per screen; each exposes `uiState: StateFlow<XxxUiState>`:
- `feed/FeedViewModel.kt` + `FeedUiState` + `FeedType` (FrontPage/Popular/All)
- `post/PostDetailViewModel.kt` + `PostDetailUiState`
- `comment/CommentViewModel.kt` + `CommentUiState` + `FlatCommentItem` - shared comment-tree state used by PostDetail + profile tabs
- `subreddit/SubredditViewModel.kt` + `SubredditUiState`
- `profile/ProfileViewModel.kt` + `ProfileUiState` + `ProfileTab` + `SavedContentType`
- `inbox/InboxViewModel.kt` + `InboxUiState`
- `search/SearchViewModel.kt` + `SearchUiState`
- Detail: see [references/viewmodels.md](references/viewmodels.md).

### Android UI (`androidApp/src/main/kotlin/com/karmakameleon/android/`)
- `KarmaKameleonApplication.kt` - Application class, Koin init.
- `MainActivity.kt` - hosts Compose.
- `QuoteTextActivity.kt` - system "Share text" / process-text entry to post a quote reply.
- `auth/OAuthActivity.kt` - handles `karmakameleon://oauth` redirect.
- `ui/KarmaKameleonApp.kt` - **top-level nav graph** (Screen + DetailScreen routes, NavHost, bottom bar). Every new route starts here.
- `navigation/NavigationHandler.kt` - link-click router (subreddit/user/post/comment/share/image/video/youtube/external). Wired once in `KarmaKameleonApp`.
- `ui/menu/GlobalMenuManager.kt` - global menu callbacks (profile/inbox/settings navigation from the app-bar).
- `ui/feed/FeedScreen.kt`, `ui/post/PostDetailScreen.kt`, `ui/subreddit/{SubredditListScreen,SubredditScreen}.kt`, `ui/profile/ProfileScreen.kt` (+ tab composables), `ui/inbox/InboxScreen.kt`, `ui/search/SearchScreen.kt`, `ui/settings/SettingsScreen.kt`.
- `ui/components/` - reusable Compose components (PostCard, CommentItem, MessageItem, MarkdownText, VideoPlayer, YouTubePlayer, FullScreenImageViewer, FullScreenVideoScreen, WebBrowser, ReplyBar, SortBottomSheet, UniversalTopAppBar, ProgressiveAsyncImage, MediaLongPressMenu, Nsfw/SpoilerPreviewBox, FullscreenHelper).
- `ui/theme/` - `Theme.kt` (all colors, Material3 color schemes - light/dark), `Typography.kt`.
- `data/` - `PendingNotificationAction.kt`, `PendingQuote.kt` (cross-activity state handoffs), `VideoCacheProvider.kt` (Media3 SimpleCache).
- `notifications/` - `NotificationHelper.kt`, `InboxNotificationWorker.kt` (WorkManager), `BootReceiver.kt`.
- `res/` - icons, strings, xml network-security-config, backup rules.
- Detail: see [references/android-ui.md](references/android-ui.md).

### Markdown system
- Parser (shared): `shared/src/commonMain/kotlin/com/karmakameleon/shared/util/markdown/`
  - `MarkdownParser.kt` (entry point) -> `BlockParser.kt` -> `InlineParser.kt`
- AST: `shared/.../domain/markdown/MarkdownBlock.kt`, `MarkdownInline.kt`.
- Rendering (Android Compose): `androidApp/.../ui/components/MarkdownText.kt`.
- Tests: `commonTest/.../util/markdown/{BlockParserTest,InlineParserTest}.kt`.
- Detail: see [references/markdown.md](references/markdown.md).

### Link parsing
- `shared/.../util/LinkParser.kt` - `isImageUrl`, `isVideoUrl`, `isYouTubeUrl`, `extractYouTubeVideoId`, `parseRedditLink(url) -> RedditLink` (sealed: Subreddit/User/Post/Comment/ShareLink/External). This is what `NavigationHandler` dispatches on.

### Dependency injection
- `shared/.../di/SharedModule.kt` (`sharedModule`) - HttpClient, Settings, AuthManager, RedditApi, all repositories.
- `shared/.../di/PlatformModule.{android,ios}.kt` (expect/actual `platformModule()`).
- `androidApp/.../di/AndroidModule.kt` (`androidModule`) - NavigationHandler, GlobalMenuManager, all ViewModels (Koin `viewModel { }`).
- Koin startup: `KarmaKameleonApplication.onCreate`.

### Tests
- Unit: `shared/src/commonTest/kotlin/com/karmakameleon/shared/` - repositories, ViewModels, domain models, LinkParser, markdown.
- Fakes: `commonTest/.../FakeRedditApi.kt`, `FakeAuthManager.kt`, `TestData.kt`.
- Android integration (real API): `shared/src/androidHostTest/.../RedditApiIntegrationTest.kt` - requires `.env` with `REDDIT_CLIENT_ID`.

## Key conventions (follow existing patterns)

- **RedditApi methods are `open suspend`** so tests can subclass `FakeRedditApi` and override.
- **ViewModels** live in `shared/ui/` (KMP). UI remains platform-specific; business logic never in Compose.
- **UiState** is always an immutable data class + `MutableStateFlow` + public `asStateFlow()`.
- **Optimistic UI** for vote/save: update state first, call API, revert on failure (see `PostRepository`).
- **Settings** go through `SettingsRepository` (no direct `Settings` access in features).
- **Navigation** from a feature: call a lambda passed in from `KarmaKameleonApp.kt`, OR call a method on the injected `NavigationHandler` (for link clicks from markdown/etc.).
- **Routes** are declared in `DetailScreen` inside `KarmaKameleonApp.kt` with a `createRoute(...)` helper. Always use the helper, never hand-concat.
- **Icons**: Material Icons Extended (`androidx.compose.material.icons.*`).
- **Theme colors**: always reference `MaterialTheme.colorScheme.*` (defined in `ui/theme/Theme.kt`). No hardcoded colors.
- **No deprecated APIs.** No redundant qualifiers. Remove unused imports.
- **New features require tests.** Use `FakeRedditApi` + `MapSettings` (from `multiplatform-settings-test`) + `runTest`.

## Build & verify

```bash
./gradlew :androidApp:assembleDebug      # build Android
./gradlew test                           # all unit tests
./gradlew :shared:jacocoTestReport       # coverage report -> shared/build/reports/jacoco/
./gradlew :shared:testDebugUnitTest --tests "com.karmakameleon.shared.data.repository.PostRepositoryTest"
```

Toolchain: AGP 9.1.0, Kotlin 2.3.10, Compose Multiplatform 1.10.1, JDK 25, compileSdk 36, minSdk 24.
Dependency versions: `gradle/libs.versions.toml`.

## Common task recipes

- **Add a new Reddit API endpoint**: add `open suspend fun` in `RedditApi.kt`; add/extend DTO in `data/api/dto/`; map to domain model in the same RedditApi method; call from the relevant Repository; surface via a ViewModel method; add test in `commonTest/.../data/api` or the relevant repository test using `FakeRedditApi`.
- **Add a new screen**: create Compose screen in `androidApp/ui/<area>/`; add a `DetailScreen` route + `createRoute` in `KarmaKameleonApp.kt`; add `composable(...)` entry in the NavHost; if it needs a VM, add it to `shared/ui/<area>/` and register in `AndroidModule.kt`.
- **Add a new setting**: add a `StateFlow` + getter/setter + `KEY_` constant in `SettingsRepository.kt`; expose in `SettingsScreen.kt`; inject `SettingsRepository` anywhere it's consumed.
- **Add a new link type**: extend `RedditLink` sealed class in `LinkParser.kt`; handle in `NavigationHandler.handleLink`; wire callback in `KarmaKameleonApp.kt`.
- **Add a new UI component**: drop in `androidApp/ui/components/` and mirror the style of existing ones (stateless composables driven by params, callbacks passed in).

## When to load reference files

Read the matching `references/*.md` only when you need the full detail - don't load them speculatively:
- Adding/changing API endpoints -> `references/api.md`
- Changing repositories or settings storage -> `references/data-layer.md`
- Changing/adding ViewModel state -> `references/viewmodels.md`
- Non-trivial screen or navigation changes -> `references/android-ui.md`
- Markdown parsing or rendering -> `references/markdown.md`
