# Android UI Reference

Android-only Compose code: `androidApp/src/main/kotlin/com/karmakameleon/android/`.

## Top-level structure

| File | Role |
|---|---|
| `KarmaKameleonApplication.kt` | Application class. `startKoin { modules(sharedModule, platformModule(), androidModule) }`. Napier setup. |
| `MainActivity.kt` | Single activity hosting Compose. Sets theme + calls `KarmaKameleonApp()`. |
| `QuoteTextActivity.kt` | Invisible activity registered as `android.intent.action.PROCESS_TEXT` handler. Captures selected text from other apps and funnels into `PendingQuote` so the next PostDetail reply bar prefills with it. |
| `auth/OAuthActivity.kt` | Deep link `karmakameleon://oauth`. Parses `code` from URI, passes to `AuthManager.exchangeCode`, returns to MainActivity. |

## Navigation graph - `ui/KarmaKameleonApp.kt`

Single source of truth for routes. Edit this file for any nav change.

### Bottom-tab routes (`sealed class Screen`)
- `Feed` (`"feed"`)
- `Subreddits` (`"subreddits"`)
- `Search` (`"search"`)

### Detail routes (`sealed class DetailScreen`)
Each has `val route: String` (with optional query/path args) and a `createRoute(...)` helper. Always use the helper.

- `SubredditDetail` - `subreddit/{subredditName}`
- `PostDetail` - `post/{subreddit}/{postId}?commentId={commentId}&context={context}`
- `UserProfile` - `user/{username}`
- `OwnProfile` - `profile`
- `Inbox` - `inbox?filter={filter}` (filter is `InboxFilter.name`)
- `WebBrowser` - `web/{url}` (URL-encoded)
- `Settings` - `settings`
- `ImageViewer` - `image/{url}` (URL-encoded)
- `VideoViewer` - `video/{url}` (URL-encoded)
- `YouTubeViewer` - `youtube/{videoId}`

Adding a new route: add a new `data object` to `DetailScreen`, add a `composable(...) { }` block in the `NavHost`, wire any callers.

### NavigationHandler wiring

`KarmaKameleonApp` contains a `DisposableEffect(navController)` that sets every lambda on the Koin-provided `NavigationHandler`. This is the **central routing for link clicks from markdown**, share-link resolution, etc. When a user taps an intra-Reddit link rendered inside `MarkdownText`, it flows:

```
MarkdownText (link click) -> NavigationHandler.handleLink(url)
  -> parseRedditLink(url) / isImageUrl / isVideoUrl / isYouTubeUrl
  -> onSubredditClick / onUserClick / onPostClick / onCommentClick / onShareLinkClick / onImageLinkClick / onVideoLinkClick / onYouTubeLinkClick / onExternalLinkClick
  -> navController.navigate(DetailScreen.XYZ.createRoute(...))
```

Same pattern for `GlobalMenuManager` (profile/inbox/settings nav from app-bar overflow menus).

### Share-link resolution

Reddit share URLs (`/r/<sub>/s/<id>`) don't include a post ID. `onShareLinkClick` awaits `redditApi.resolveShareLink(...)` then navigates to PostDetail.

## Feature screens (`ui/<feature>/`)

| Screen | VM | Notes |
|---|---|---|
| `feed/FeedScreen.kt` | `FeedViewModel` | Front page / popular / all / user subs. Scroll-based pagination. |
| `post/PostDetailScreen.kt` | `PostDetailViewModel` | Post + comment tree. Supports deep-link to specific comment with context. `onGoToCommentNav` re-navigates to a focused-comment view. |
| `subreddit/SubredditListScreen.kt` | Uses `UserRepository` + `SubredditRepository` directly via `koinInject` | User's subscribed list. |
| `subreddit/SubredditScreen.kt` | `SubredditViewModel` | Single-subreddit feed + sidebar. |
| `profile/ProfileScreen.kt` | `ProfileViewModel` | Tabbed: Posts / Comments / Saved / Upvoted / Downvoted. Tab composables: `ProfilePostsTab.kt`, `ProfileCommentsTab.kt`, `ProfileSavedTab.kt`, `ProfileUpvotedTab.kt`, `ProfileDownvotedTab.kt`. `username==null` -> own profile. |
| `inbox/InboxScreen.kt` | `InboxViewModel` | Inbox with `InboxFilter` sub-tabs. Deep-linked from notifications via `PendingNotificationAction`. |
| `search/SearchScreen.kt` | `SearchViewModel` | Unified search (posts/subs/users). |
| `settings/SettingsScreen.kt` | Uses `SettingsRepository` directly via `koinInject` | All app prefs UI. |

## Reusable components (`ui/components/`)

| Component | Purpose |
|---|---|
| `PostCard.kt` | Post list item used by Feed / Subreddit / Search / Profile. |
| `CommentItem.kt` | Single comment row with vote/reply/collapse. |
| `MessageItem.kt` | Inbox message row. |
| `MarkdownText.kt` | Renders `MarkdownBlock` list -> Compose. Clicks route through `NavigationHandler`. |
| `VideoPlayer.kt` | Inline Media3 ExoPlayer with HLS support. Uses `VideoCacheProvider` (SimpleCache). |
| `YouTubePlayer.kt` / `YouTubeVideoScreen.kt` | Android YouTube Player integration + full-screen host. |
| `FullScreenImageViewer.kt` | Gesture-driven zoom/pan image viewer (pager-aware, supports galleries). |
| `FullScreenVideoScreen.kt` | Full-screen video host. |
| `WebBrowser.kt` | `WebBrowserScreen` - in-app WebView for external links. |
| `ReplyBar.kt` | Inline reply text field used by PostDetail / CommentItem. Writes drafts via `CommentDraftRepository`. |
| `SortBottomSheet.kt` | Sort selection bottom sheet (reused across post/comment/search screens). |
| `UniversalTopAppBar.kt` | Shared top app bar with overflow menu wired to `GlobalMenuManager`. |
| `ProgressiveAsyncImage.kt` | Coil image with low/high-res progressive loading. |
| `MediaLongPressMenu.kt` | Long-press menu on media (save/share/copy). |
| `NsfwPreviewBox.kt` | NSFW overlay on previews based on `NsfwPreviewMode`. |
| `SpoilerPreviewBox.kt` | Spoiler overlay. |
| `FullscreenHelper.kt` | Edge-to-edge + system bar helpers. |

## Theme (`ui/theme/`)

- `Theme.kt` - **all colors live here** as `val`s + `darkColorScheme()` / `lightColorScheme()` builders, exposed via `KarmaKameleonTheme { content }`. Reference them via `MaterialTheme.colorScheme.*` inside composables. Do NOT hardcode colors elsewhere.
- `Typography.kt` - Material 3 typography.

## Cross-activity state handoffs (`data/`)

- `PendingNotificationAction.kt` - `openInboxUnread: SharedFlow<Boolean>`. Notification tap posts to this; `KarmaKameleonApp` collects and navigates to Inbox (UNREAD filter). Call `consumeOpenInboxUnread()` after handling.
- `PendingQuote.kt` - holds quote text captured by `QuoteTextActivity` for the next PostDetail reply.
- `VideoCacheProvider.kt` - lazy-init Media3 `SimpleCache` (shared across video players).

## Notifications (`notifications/`)

- `NotificationHelper.kt` - channel creation + `postInboxNotification(count)`.
- `InboxNotificationWorker.kt` - WorkManager worker. Polls via `MessageRepository` + `InboxPoller`, posts notification if new unread. Schedules itself based on `SettingsRepository.notificationInterval`.
- `BootReceiver.kt` - `BOOT_COMPLETED` receiver that re-enqueues the worker.

## Resources (`res/`)

- `values/` - strings, themes.
- `drawable/` - icons (launcher etc.).
- `mipmap-anydpi-v26/` - adaptive launcher icon.
- `xml/` - `network_security_config.xml`, backup rules.

`AndroidManifest.xml` declares: MainActivity (LAUNCHER), OAuthActivity (deep link `karmakameleon://oauth`), QuoteTextActivity (PROCESS_TEXT), BootReceiver (BOOT_COMPLETED).

## Adding a new screen - checklist

1. Create `ui/<area>/NewScreen.kt` as a `@Composable fun NewScreen(... onBackClick: () -> Unit, ...)`. Hoist state via VM if non-trivial.
2. If it needs a VM, add `shared/ui/<area>/NewViewModel.kt` with `NewUiState` + register in `AndroidModule.kt` (`viewModel { NewViewModel(get()) }`).
3. Add a `DetailScreen.New` object in `KarmaKameleonApp.kt` with `route` + `createRoute(...)`.
4. Add a `composable(DetailScreen.New.route, arguments = ...) { NewScreen(...) }` block in the NavHost.
5. Wire callers to call `navController.navigate(DetailScreen.New.createRoute(...))`.
6. Follow the slide/fade animation pattern used by sibling routes.
