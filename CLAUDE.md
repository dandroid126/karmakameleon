# Repo rules

## Colors

All colors go in the color constants file, `androidApp/src/main/kotlin/com/karmakameleon/android/ui/theme/Theme.kt`. Never hardcode a color (`Color(0x...)`, `Color.Black`, `Color.White`, `Color.Gray`, etc.) anywhere else.

- Prefer a `MaterialTheme.colorScheme` role when one fits.
- Otherwise add a field to the matching colors class in `Theme.kt` (`VoteColors`, `PostFlairColors`, `CommentColors`, `MessageColors`, `MediaColors`, `ReadStateColors`), or add a new class there following the same pattern. Read it through its function, e.g. `mediaColors().onOverlayColor`; the function is `@Composable` only when the colors depend on the theme.
- `Color.Transparent` (no color) and `Color.Unspecified` (inherit) aren't colors, so they can be used anywhere.
- Tests should use these constants too where they can. Tests in the `shared` module can't see `Theme.kt`, so they may use literal values.

## Errors

In code that can suspend, use `runSuspendCatching` (`shared/.../util/RunSuspendCatching.kt`) instead of `catch (e: Exception)`, so coroutine cancellation isn't caught. Elsewhere, catch the specific exceptions the code can throw.

## Architecture

- `shared/src/commonMain` only uses multiplatform code. Android and JVM code (`android.*`, `java.*`, OkHttp) goes in `androidMain`. Android is the only target today, so platform code in `commonMain` compiles, but it would break as soon as another platform is added.
- UI code (screens and view models, in the `ui` packages) gets data through a repository, never from `RedditApi` or Ktor directly.

## WebViews and backup

- Every WebView sets `allowContentAccess = false` and `allowFileAccess = false`. WebViews may enable JavaScript, so never call `addJavascriptInterface`: pages must not be able to call into the app.
- App data is backed up, except the login tokens (`auth_tokens.xml`) and the WebView's data (`app_webview/`). Anything else that holds tokens or session cookies has to be excluded too, in both `res/xml/data_extraction_rules.xml` and `res/xml/backup_rules.xml`.

## Rule tests

The rules above are checked by `konsistTest/src/test/kotlin/com/karmakameleon/konsist/ArchitectureTest.kt` and `BackupRulesTest.kt`, except `catch (e: Exception)`, which Detekt catches. When you add a rule to this file, add a test for it there too.

## Checks

Before committing, run `./gradlew qualityGate` (the static checks) and `./gradlew :shared:testAndroidHostTest diffCoverage` (the unit tests, and a check that at least 80% of the shared module's lines changed since `main` are covered by them). The PR checks run the same tasks and must pass before a PR can merge. `diffCoverage` compares with `origin/main`, so fetch first if it's out of date.

Fix what the checks report instead of adding it to a baseline or suppressing it. When a suppression really is right, put the reason next to it.
