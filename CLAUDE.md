# Repo rules

## Colors

All colors go in the color constants file, `androidApp/src/main/kotlin/com/karmakameleon/android/ui/theme/Theme.kt`. Never hardcode a color (`Color(0x...)`, `Color.Black`, `Color.White`, `Color.Gray`, etc.) anywhere else.

- Prefer a `MaterialTheme.colorScheme` role when one fits.
- Otherwise add a field to the matching colors class in `Theme.kt` (`VoteColors`, `PostFlairColors`, `CommentColors`, `MessageColors`, `MediaColors`, `ReadStateColors`), or add a new class there following the same pattern. Read it through its composable, e.g. `mediaColors().onOverlayColor`.
- `Color.Transparent` (no color) and `Color.Unspecified` (inherit) aren't colors, so they can be used anywhere.
- Tests should use these constants too where they can. Tests in the `shared` module can't see `Theme.kt`, so they may use literal values.

## Errors

In code that can suspend, use `runSuspendCatching` (`shared/.../util/RunSuspendCatching.kt`) instead of `catch (e: Exception)`, so coroutine cancellation isn't caught. Elsewhere, catch the specific exceptions the code can throw.

## Checks

Before committing, run `./gradlew qualityGate` (the static checks) and `./gradlew :shared:testAndroidHostTest` (the unit tests). The PR checks run the same tasks and must pass before a PR can merge.

Fix what the checks report instead of adding it to a baseline or suppressing it. When a suppression really is right, put the reason next to it.
