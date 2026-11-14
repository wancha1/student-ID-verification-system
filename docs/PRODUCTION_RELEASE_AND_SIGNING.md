# LTC Student Access — Production Release and Signing

## Purpose

This runbook creates the permanent Android signing identity and produces a production-signed APK through GitHub Actions. The private keystore belongs to the application owner and must remain under owner control.

**Never commit the keystore, its passwords, or a base64 copy to this repository.** Do not send the keystore or passwords through chat, email, WhatsApp, or issue comments.

## 1. Generate the keystore on a trusted Windows computer

Install a current JDK (Java 17 or 21 is suitable for this project) and open PowerShell or Command Prompt.

Create a dedicated folder outside the repository:

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\LTC-Production-Signing"
```

Generate the key:

```powershell
keytool -genkeypair -v -storetype JKS -keystore "$env:USERPROFILE\LTC-Production-Signing\ltc-release.jks" -alias ltc-release -keyalg RSA -keysize 2048 -validity 10000 -sigalg SHA256withRSA
```

Keytool will prompt for a strong keystore password, key password, and certificate identity. Use the same strong password for the keystore and private key if you want a simpler Gradle configuration. Record the passwords in a reputable password manager and keep an offline recovery copy.

## 2. Verify the key locally

```powershell
keytool -list -v -storetype JKS -keystore "$env:USERPROFILE\LTC-Production-Signing\ltc-release.jks" -alias ltc-release
```

Confirm:
- Alias is `ltc-release`.
- Entry type is `PrivateKeyEntry`.
- Key algorithm is RSA.
- The SHA-256 certificate fingerprint is recorded in the owner's secure records.

Do not send the keystore or password to anyone. The certificate fingerprint is not the private key, but keep the record with the release documentation.

## 3. Make two protected backups

Keep:
1. The primary keystore in the dedicated secure folder on the owner's computer.
2. An encrypted offline backup on a separate USB drive.
3. A second encrypted backup in a separately controlled location if available.

Test that the backup can be read. Do not rely on a single laptop, a single USB drive, or GitHub Secrets as the only copy.

## 4. Add GitHub Actions secrets

On the trusted Windows computer, convert the keystore bytes to base64 and copy the output:

```powershell
$keystore = "$env:USERPROFILE\LTC-Production-Signing\ltc-release.jks"
[Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore)) | Set-Clipboard
```

In GitHub, open **Settings → Environments → production** and add these as **environment secrets** (the release workflow explicitly targets the `production` environment). Add:

| Secret name | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Base64 text copied from PowerShell |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | `ltc-release` |
| `ANDROID_KEY_PASSWORD` | Private-key password |

Paste each value directly into GitHub's secret form. Never paste secret values into repository files, workflow YAML, chat, or logs.

## 5. Build the production APK

After the release workflow has been merged into the repository's default branch, open **Actions → Production APK → Run workflow** and select that default branch. The workflow:
- Requires all four signing secrets.
- Decodes the keystore into the runner's temporary directory.
- Verifies that the alias exists.
- Runs Android unit tests and emulator smoke tests.
- Builds the release APK using the permanent signing key.
- Checks APK alignment and signature validity.
- Uploads the signed APK as a workflow artifact.

A CI-only temporary key is not a production signing key. Only the artifact from the **Production APK** workflow after the permanent secrets are configured is suitable for a production pilot.

## 6. Verify the downloaded APK

On a computer with Android SDK Build Tools installed:

```powershell
apksigner verify --verbose --print-certs .\app-release.apk
```

Also check the package identity and version:

```powershell
aapt dump badging .\app-release.apk
```

Confirm the APK package is `com.aistudio.studentaccess.xpkz`, record the signing certificate SHA-256 fingerprint, and compare it with the owner's recorded fingerprint.

## 7. Update compatibility warning

Android only accepts an in-place app update when the new APK is signed with the same signing identity as the installed APK (or a valid signing-key rotation lineage). Earlier debug or CI-only APKs may have a different certificate. If so, the first production install will require uninstalling the old test build; that removes its local app data unless it has been backed up/exported. Test this before school rollout.

Do not change the Android `applicationId` after the first production release. Changing it creates a different Android app identity and prevents ordinary in-place updates.

## 8. Release controls

- Use monotonically increasing `versionCode` values.
- Keep the signing key and backups under owner control.
- Rotate GitHub secrets if repository access changes or a secret is exposed.
- Never generate a replacement key merely because the original is inconvenient to access.
- If the key is lost, future APKs signed with a different key will not update existing installations.
