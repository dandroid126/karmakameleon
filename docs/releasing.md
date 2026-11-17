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

   To approve each signing before it happens, add yourself under Settings > Environments > release > Required reviewers.
4. Add the secrets to the environment, not to the repository. The second command asks for the password, so it doesn't end up in your shell history.

   ```bash
   base64 -w0 karmakameleon-release.p12 | gh secret set RELEASE_KEYSTORE_BASE64 --env release
   ```

   ```bash
   gh secret set RELEASE_KEYSTORE_PASSWORD --env release
   ```

5. Delete the local copy (`shred -u karmakameleon-release.p12`), after checking the backups open.

## Making a release

1. Raise `versionCode` and set `versionName` in `androidApp/build.gradle.kts`. Android doesn't install an update with a lower `versionCode`.
2. Merge that through a PR, then tag the merge commit on `main` and push the tag:

   ```bash
   git tag v0.1.0 origin/main && git push origin v0.1.0
   ```

3. The workflow publishes `androidApp-release.apk` as a prerelease. Its log shows the signing certificate's SHA-256, which must be the same for every release.

## If the key leaks or is lost

- **Leaked:** remove the secrets from the `release` environment and rotate to a new key with `apksigner rotate`, signing the rotation with the old key, so installed apps accept updates signed with the new one. Tell users that APKs from anywhere other than the GitHub releases may be malicious.
- **Lost (no backup):** there is no recovery. Create a new key, and tell users to uninstall and install the new build, after exporting their settings from the app.
