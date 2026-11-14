# LTC Student Access — Device Acceptance Checklist

## Test environment

The app's current Android minimum SDK is 26 (Android 8.0). Use at least two physical Android devices, preferably on different Android versions and with different screen sizes. Use test accounts only; do not use real student personal data during acceptance.

Record device model, Android version, app version, APK SHA-256, signing certificate fingerprint, date, tester, and result for each test.

## Required tests

### Installation and identity
- [ ] Fresh install succeeds on Android 8.0+.
- [ ] App launches without a crash.
- [ ] Package identity is `com.aistudio.studentaccess.xpkz`.
- [ ] App icon, name, school branding, and version are correct.
- [ ] Production APK signature is valid.
- [ ] Installing a newer APK signed by the same key updates the existing install without clearing Room data.

### Authentication and session security
- [ ] Valid Admin credentials open only the Admin interface.
- [ ] Valid Head Teacher credentials open the Head Teacher interface.
- [ ] Valid Gate Officer credentials open the Gate interface.
- [ ] Valid Meal Staff credentials open the Dining interface.
- [ ] Invalid password is rejected with a useful message.
- [ ] Unknown, inactive, and wrong-school memberships are rejected.
- [ ] Logout clears the authenticated session.
- [ ] Revoking a staff membership blocks access after the next server validation.
- [ ] Login fields remain readable in both light and dark system themes.
- [ ] Forgot-password assistance opens the configured administrator contact path and does not expose passwords.

### Role boundaries
- [ ] Admin can manage student records and cards.
- [ ] Head Teacher sees oversight information and cannot perform restricted Admin actions.
- [ ] Gate Officer can scan and record gate decisions but cannot manage student records or cards.
- [ ] Meal Staff can record meal service but cannot access Admin-only student management.
- [ ] A role cannot gain additional permissions by changing local UI state or client metadata.

### QR and gate operation
- [ ] Valid active card is identified correctly.
- [ ] Invalid QR is rejected.
- [ ] Unknown student/card is rejected.
- [ ] Lost, replaced, revoked, or deactivated card is rejected.
- [ ] Gate decision records the correct officer, time, direction/location, and reason.
- [ ] Offline scan uses the last successful local sync and clearly indicates offline decision state.

### Meal operation
- [ ] Meal Staff can record a valid serving.
- [ ] Duplicate serving for the same student, meal type, and date is blocked across two devices.
- [ ] Meal Staff cannot edit student records or card status.
- [ ] Meal history synchronizes to the second device after network recovery.

### Offline and multi-device behaviour
- [ ] Download/sync the roster on Device A.
- [ ] Disconnect Device A from the network and verify an authorized test card.
- [ ] Confirm the event is queued locally and no data is silently lost.
- [ ] Reconnect and confirm queued events upload once.
- [ ] Device B receives the synced records.
- [ ] Conflict handling does not overwrite newer server data with stale data.
- [ ] Photo upload/download works with a private bucket and authorized staff only.

### Accessibility and reliability
- [ ] Login fields, labels, hints, and buttons are readable in light and dark themes.
- [ ] QR scanner permission denial is handled without a crash.
- [ ] Camera scanning works in normal indoor lighting.
- [ ] Navigation, Android Back, lock, and logout behave consistently.
- [ ] App survives process death and restores only a server-validated staff session.
- [ ] No critical crash, ANR, or unhandled exception during the test run.

## Acceptance rule

Do not mark a test as passed based only on source-code inspection or GitHub emulator CI. Physical-device tests must be performed on actual devices, and the results must be recorded. Any failed security-boundary test blocks release.
