# Code quality plan

The code in this repo is written by AI, and there's too much of it for a human to review line by line. The PR checks are the gatekeeper: a change merges only when every required check passes. This doc lists the checkers we plan to add, what each one is worth, and which are done.

**How to use this doc:** implement items in any order within a phase, preferably one PR per item. Tick the box in the same PR that adds the checker, and note anything worth remembering (thresholds chosen, rules disabled and why) under the item.

## Decisions

- **All changes go through PRs.** Work happens on a branch and merges into `main` only after the required checks pass. The admin bypass for pushing directly to `main` stays, but only for emergencies.
- **Baseline existing findings and gate only new code.** Detekt, Android Lint and the duplication checker would report hundreds of findings on the existing code. Each records them in a baseline file so new code is checked strictly, and the baselines are reduced over time. Baselines should only shrink; a PR that grows one needs a good reason.
- **One local command.** All static checks run from a single task, `./gradlew qualityGate`, listed in CLAUDE.md, so the agent can run the same checks before committing instead of waiting for CI to fail.

## Phase 1: make the gate real

- [x] **Branch ruleset on `main`.** Require PRs and require the PR checks to pass before merging; keep the admin bypass for emergencies.
  *Value:* without this, every other checker only reports and doesn't block anything.
  *Notes:* the "default branch" ruleset requires a PR, the Unit Tests, Debug Build, Release Build, Gitleaks, Dependency Review and Quality Gate checks, an up-to-date branch and squash merges, with an admin bypass. Add each new checker's job to the required checks once it has run on a PR. It also requires 1 approval, which GitHub doesn't let you give on your own PR, so for now every merge goes through the admin bypass after you've checked the PR. That stays until all phases are done and the checks have earned enough trust to set approvals to 0.
- [x] **Clean up the workflows.**
  - Add `permissions: contents: read` to `pr-checks.yml` (it has none, so it gets the default token permissions).
  - Remove the `develop` branch target from `pr-checks.yml`; that branch doesn't exist.
  - Delete `unit-tests.yml`; `debug-release.yml` already runs the same tests on every push to `main`.
  - Combine the PR jobs where it saves setup time, ending in one quality gate task.

  *Value:* less CI time, and a smaller token scope if a workflow step is compromised.
  *Notes:* the PR jobs stay separate: they run in parallel, so the PR finishes sooner, and Actions minutes are free for public repos. Combining them into one gate task happens in Phase 2. The repo's default workflow token is already read-only.
- [x] **Dependabot** for Gradle (the version catalog) and GitHub Actions, with related updates grouped (e.g. all AndroidX, all Ktor) to keep the PR count manageable.
  *Value:* dependencies stay current without manual work, and the required checks validate each update PR automatically.
  *Notes:* configured in `.github/dependabot.yml`, with a 7-day cooldown on new releases (security updates skip it). Dependabot alerts and security updates are on in the repo settings. Check whether the Gradle wrapper gets updates; if not, update it by hand. The devcontainer it also updated was removed, since JetBrains' devcontainer support doesn't work well with Android Studio. `gradle/actions` is on v6 with `cache-provider: basic` on every step: v6's default cache is a proprietary Gradle component covered by Gradle's Terms of Use, and the basic one is open source and caches the same way.
- [x] **Dependency review action** on PRs, failing on known-vulnerable dependencies and on licenses that conflict with ours.
  *Value:* Dependabot fixes what's already in the repo; this stops new problems from coming in through a PR.
  *Notes:* GitHub can't read Gradle dependencies itself, so `dependency-review.yml` builds the dependency graph with Gradle and submits it on every PR and push to main. Only GitHub's own code sees the write token. Allowed licenses are the ones compatible with GPL-3.0. It blocks on vulnerabilities in build tooling and tests too, not only in libraries that ship in the app, since the tooling runs in CI next to the signing key. Licenses are only checked for what goes into the debug and release APKs, since build tooling and tests aren't distributed with the app; the graph labels those libraries "runtime" and everything else "development". A library whose license GitHub can't detect passes without a license check (today that's some AndroidX and JetBrains lifecycle libraries, which are Apache-2.0), so check a new library's license by hand when the review lists it as undetected. Its first runs had no graph for `main` to compare with, so they checked every dependency. They found vulnerable versions of Bouncy Castle (1 critical), jose4j, jdom2, commons-lang3 and httpclient, all brought in by AGP, in the build classpath and in each module's lint tooling (`androidLintTool`). The review only reported some of them, so the rest were found by checking every resolved dependency against GitHub's advisory database.
  *Fixing a flagged build tooling library:* add its fixed version to the `security-fixes` bundle in `gradle/libs.versions.toml`, and add it to the matching Dependabot group. `build.gradle.kts` requires every library in the bundle as a constraint, which only raises a version and never adds a library. Remove an entry once AGP brings in a fixed version itself.
- [x] **GitHub secret scanning with push protection** (repo setting; free for public repos).
  *Value:* Gitleaks reports a secret after it's been pushed. Push protection rejects the push.
  *Notes:* both are on. Scanning for generic (non-provider) patterns and checking whether found secrets are still valid are left off.

## Phase 2: static analysis on every PR

- [ ] **Detekt** with the Compose rules plugin, plus a baseline.
  *Value:* hard limits on function length, complexity and nesting; catches magic numbers, swallowed exceptions, unused private code and Compose mistakes (modifier order, unstable parameters, state hoisting). Models tend to write long functions that work but are hard to maintain.
- [ ] **ktlint** (through Spotless or the ktlint Gradle plugin), with auto-fix available locally.
  *Value:* one deterministic style for every model, so diffs show real changes instead of formatting churn.
- [ ] **Duplication detection** (PMD CPD with Kotlin support, or jscpd), failing if duplication increases compared with `main`.
  *Value:* models often copy a similar function instead of extracting it or reusing one. This is the main problem we want to stop. Lower the threshold over time.
- [x] **Android Lint** with `warningsAsErrors`, a baseline, and `checkDependencies` so it also covers `shared`.
  *Value:* catches API calls above `minSdk 24`, manifest security issues, accessibility problems (missing content descriptions, small touch targets) and unused resources: Android-specific bugs the other tools don't know about.
  *Notes:* runs as `:androidApp:lintDebug` through `qualityGate`, which CI runs as the Quality Gate check. It only covers `androidApp`: the KMP library plugin `shared` uses has no lint for its main code, and `checkDependencies` doesn't reach it either (tested with a deliberate issue). `shared` is mostly plain Kotlin, so Detekt covers it instead. Of the 21 starting issues, 16 were fixed or suppressed with a reason (JavaScript in the two WebViews, the launcher icon's long vector paths), and the other 3 were fixed afterwards, so there's no baseline: the launcher icon got a monochrome layer for themed icons, generated from the icon's own shapes, and the reply bar now uses the window height instead of `Configuration.screenHeightDp`. The checks that fail when something new is released are off, so results don't change from one day to the next: `NewerVersionAvailable`, `GradleDependency`, `AndroidGradlePluginVersion` and `OldTargetApi`. Dependabot covers the dependencies, but nothing covers `targetSdk`, so update it by hand after each Android release. It's 37, the same as `compileSdk`; going from 36 needed no code changes, since none of Android 17's changes for apps targeting it touch anything the app uses. Testing found one platform bug: on the first Android 17 release (emulator build CE2A.260420.019), apps targeting 37 get the new lock-free `MessageQueue`, and video decoding fails because SELinux denies the decoder a `MessageQueue` memfd. Android 17 QPR2 (CP41.260831.007) and a Pixel 11 Pro on CD1A.261005.003 play video fine, so it was fixed before 37 was merged. If videos ever show black on an Android 17 phone, check its build first.
- [x] **Kotlin compiler `allWarningsAsErrors`** in both modules.
  *Value:* nearly free. Stops deprecated APIs, unused variables and unchecked casts from piling up as warnings nobody reads.
  *Notes:* on for every compilation in `shared` and `androidApp`, tests included. The only warning was an unnecessary `!!`. Kotlin 2's compiler doesn't report unused variables (the IDE does), so Detekt has to catch those. Warnings Gradle reports about the build scripts themselves aren't covered; the only one, a deprecated `androidLibrary` block, was renamed to `android`.

## Phase 3: security and architecture

- [ ] **CodeQL** (java-kotlin, `security-extended` queries) on PRs and on a weekly schedule.
  *Value:* data-flow analysis that follows untrusted input (intents, deep links, network responses) to dangerous sinks: injection, unsafe WebView settings, logged secrets, weak crypto. Matters because the app handles Reddit OAuth tokens.
- [ ] **zizmor and actionlint** for the workflow files, and pin every action to a commit SHA (Dependabot keeps the SHAs current).
  *Value:* the workflows handle the signing key and a `contents: write` token, which makes them the most attractive target in the repo. These tools catch template injection, overly broad permissions and unpinned actions.
  Also fail when a `gradle/actions` step doesn't set `cache-provider: basic`, so the proprietary cache can't come back unnoticed.
- [ ] **Architecture tests with Konsist.** Start with:
  - No hardcoded colors outside `Theme.kt` (the existing CLAUDE.md rule)
  - No `android.*` imports in `shared/commonMain`
  - UI code doesn't call the API client directly

  *Value:* turns the written rules in CLAUDE.md into failing tests. Models forget or ignore written rules; they can't ignore a failing test. Add a test whenever a new rule goes into CLAUDE.md.
- [ ] **Diff coverage gate with Kover** (replacing the unused JaCoCo setup), e.g. 80% of changed lines covered.
  *Value:* models skip tests unless something forces them. Measuring only changed lines is fair while `androidApp` has no tests.

## Phase 4: deeper checks

- [ ] **Tests for `androidApp`:** Robolectric for UI logic, and screenshot tests (Roborazzi or Paparazzi).
  *Value:* the 49 files in `androidApp` have no tests. Screenshot diffs show exactly what changed visually, without anyone having to look at every UI change in the app (the recent unreadable flair colors are the kind of bug this catches).
- [ ] **Dependency Analysis Gradle Plugin.**
  *Value:* finds unused dependencies, `implementation` vs `api` mistakes and transitive dependencies used without being declared. Models add libraries and rarely remove them.
- [ ] **APK size check:** fail when the release APK grows more than N% compared with `main`.
  *Value:* catches a large library added by accident, or R8 rules that stop shrinking.
- [ ] **PR size check:** warn above about 500 changed lines.
  *Value:* smaller PRs make each checker's findings easier to act on and keep an AI reviewer accurate.
- [ ] **AI review bot** (Claude Code GitHub Action), advisory only, not a required check.
  *Value:* static tools find patterns; a reviewing model can tell whether the code makes sense and spot logic bugs, poor abstractions and missed reuse. It isn't deterministic, so it doesn't block merges.
- [ ] **mobsfscan** (optional; only if Lint and CodeQL leave gaps).
  *Value:* mobile-specific security checks from OWASP MASVS: insecure storage, backup flags, certificate pinning.
- [ ] **Mutation testing with Pitest** (stretch goal; run weekly or manually, not on every PR).
  *Value:* finds tests that run code without asserting anything, a common way AI raises coverage without testing. Kotlin support is limited, so it's last.

## Issues found while planning

The checkers above would likely flag these too; fix them as separate PRs, or along with the checker that catches them.

- [x] `shared/build.gradle.kts` copies the `.env` values into tests in two separate `tasks.withType<Test>` blocks.
  *Notes:* the second block now only configures JaCoCo.
- [x] `shared/src/iosMain` exists, and `ktor-client-darwin` is declared for it, but there's no iOS target, so that code never compiles. Either add the target or remove the source set and the dependency.
  *Notes:* removed, along with the dependency. It's in git history for when an iOS target is added.
- [x] `android:allowBackup="true"` in the manifest lets the stored OAuth tokens end up in device backups. Disable backup or exclude the token storage with backup rules.
  *Notes:* the tokens were in the same SharedPreferences file as everything else, so they moved to their own file, `auth_tokens`, which the backup rules exclude from cloud backup and device transfer, along with the login WebView's data (`app_webview/`). A token saved the old way moves over on the next start, so nobody gets logged out. Settings, the client ID, read posts and drafts are still backed up.
- [ ] Gradle warns that the build uses deprecated features that won't work in Gradle 10. Run a build with `--warning-mode all` to find them, and fix them before Gradle 10.
- [ ] Android plans to deprecate the manifest's `usesCleartextTraffic`. Move the setting into a network security configuration, which can also set certificate transparency and Encrypted Client Hello explicitly.
