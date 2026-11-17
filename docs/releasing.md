# Releasing

Pushing a `v*` tag runs `build-release.yml`. It builds the release APK, signs it with the release key and publishes it as a GitHub release. The release key only exists in the `release` environment on GitHub, plus your backups. It is never on the development laptop, in `~/.gradle/gradle.properties` or in a local build (#122).

| Build type | Package | Key | Built by |
|---|---|---|---|
| `debug` | `com.karmakameleon.android.debug` | debug | every push to `main` (`debug-latest`), and locally |
| `profileable` | `com.karmakameleon.android.debug` | debug | every push to `main` (`debug-latest`), and locally |
| `release` | `com.karmakameleon.android` | release | pushing a `v*` tag |

## Why the release key is kept apart

Android only installs an update that's signed with the same key as the installed app, so the release key is the app's identity:

- If it leaks, anyone can sign an APK that installs over the app as an update and gets its data, including the Reddit login. A key can't be revoked.
- If it's lost, users can't update. They have to uninstall, losing their data, and install the app again.

The debug key is easy to reach on purpose (every local Gradle build can read it, and CI uses it on every push to `main`), so it signs only the `.debug` builds.

## One-time setup

1. Create the keystore in a temporary folder outside the repo, such as a new folder in `/tmp`. `keytool` asks for a password: choose a long one and save it in your password manager. PKCS12 uses the same password for the key.

   ```bash
   keytool -genkeypair -v -storetype PKCS12 -keystore karmakameleon-release.p12 -alias release -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=KarmaKameleon"
   ```

2. Back up `karmakameleon-release.p12` in at least two places that aren't the laptop, such as the password manager (as an attachment) and an offline copy. Without it, the app can't be updated.
3. Create the `release` environment, limited to `v*` tags:

   ```bash
   gh api -X PUT repos/{owner}/{repo}/environments/release --input - <<< '{"deployment_branch_policy": {"protected_branches": false, "custom_branch_policies": true}}'
   ```

   ```bash
   gh api -X POST repos/{owner}/{repo}/environments/release/deployment-branch-policies -f name='v*' -f type=tag
   ```

   The environment only checks the tag's name, not which commit it points at.
4. Allow only admins to create, move or delete `v*` tags, so nobody else can get a commit of their choice signed. This is what protects the key: the workflow checks that the tagged commit is on `main`, but whoever pushes a tag also chooses the workflow that runs, so that check only catches mistakes. Repository role 5 is admin.

   ```bash
   gh api -X POST repos/{owner}/{repo}/rulesets --input - <<< '{"name": "Release tags", "target": "tag", "enforcement": "active", "conditions": {"ref_name": {"include": ["refs/tags/v*"], "exclude": []}}, "rules": [{"type": "creation"}, {"type": "update"}, {"type": "deletion"}], "bypass_actors": [{"actor_id": 5, "actor_type": "RepositoryRole", "bypass_mode": "always"}]}'
   ```

   For a second check that also covers a stolen admin token, add yourself under Settings > Environments > release > Required reviewers, then approve each signing in the workflow run.
5. Add the secrets to the environment, not to the repository. The second command asks for the password, so it doesn't end up in your shell history.

   ```bash
   base64 -w0 karmakameleon-release.p12 | gh secret set RELEASE_KEYSTORE_BASE64 --env release
   ```

   ```bash
   gh secret set RELEASE_KEYSTORE_PASSWORD --env release
   ```

6. Delete the local copy (`shred -u karmakameleon-release.p12`), after checking the backups open.

## Making a release

1. Raise `versionCode` and set `versionName` in `androidApp/build.gradle.kts`. Android doesn't install an update with a lower `versionCode`.
2. Merge that through a PR, then tag the merge commit on `main` and push the tag:

   ```bash
   git tag v0.1.0 origin/main && git push origin v0.1.0
   ```

3. The workflow publishes `androidApp-release.apk` as a prerelease. Its log shows the signing certificate's SHA-256, which must be the same for every release.

## If the key leaks or is lost

A key can't be revoked. Every phone with the app installed accepts an update signed with the leaked key until it has moved to a new one, and some never move (see rotation below).

**Leaked:**

1. Delete both secrets from the `release` environment, and find out how the key leaked.
2. Tell users that only APKs from this repo's GitHub releases are genuine.
3. Move to a new key in one of two ways:
   - **A new package (recommended).** Create a new key and change `applicationId`, so the new app installs next to the old one. Users export their settings from the old app, import them into the new one, and uninstall the old one. It's a clean break, but every user has to act.
   - **Key rotation** (`apksigner rotate`), which keeps the package. Its limits:
     - Only Android 9 (API 28) and later read the rotation (APK Signature Scheme v3). `minSdk` is 24, so phones on Android 7–8.1 keep checking the original key. Those builds still have to be signed with the leaked key, and an attacker can still update those phones.
     - It doesn't stop phones that haven't installed the rotated update from accepting the attacker's APKs. The old key also stays trusted unless you remove its capabilities when rotating.
     - `build-release.yml` signs with one key. Rotated releases need both keys and the lineage file from `apksigner rotate` (`apksigner sign --ks <old> --next-signer --ks <new> --lineage <file>`), so the workflow and its secrets have to change. Back up the lineage file like the key.

**Lost (no backup):** there is no recovery, and rotation needs the old key. Create a new key and use a new package, as above.
