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

Before committing, run `./gradlew qualityGate` (the static checks) and `./gradlew :shared:testAndroidHostTest diffCoverage :shared:koverVerify :androidApp:verifyRoborazziDebug` (the unit tests, a check that at least 80% of the shared module's lines changed since `main` are covered by them, a check that at least 80% of all its lines are, and the app's tests, which compare screenshots with the recorded ones). The PR checks run the same tasks and must pass before a PR can merge. With a client ID in `.env`, the integration tests call Reddit and add coverage that CI doesn't get, so move `.env` aside to see CI's numbers. `diffCoverage` compares with `origin/main`, so fetch first if it's out of date.

The app's tests run on Robolectric, without an emulator. Screenshot tests save their images in `androidApp/src/test/screenshots/`. When a change to the UI is intended, run `./gradlew :androidApp:recordRoborazziDebug`, look at each changed image, and commit them with the change. When `verifyRoborazziDebug` fails, `androidApp/build/outputs/roborazzi/` has the new image and a comparison for each difference.

Fix what the checks report instead of adding it to a baseline or suppressing it. When a suppression really is right, put the reason next to it.

## PR size

Aim for PRs under about 500 changed lines, so the checks' findings and the review stay manageable. When a task looks bigger, propose a split into several PRs before starting. This is a guideline, not a rule: a bigger PR is fine when splitting it wouldn't make it easier to review, like a mechanical change across the codebase (#14 reformatted every file with ktlint). The `PR Size` job in the PR checks warns above 500 lines but doesn't fail.
