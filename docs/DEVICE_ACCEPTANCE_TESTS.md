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
- [ ] Valid active V2 card displays **POLICY DECISION PENDING** in amber while LTC entry policy remains unapproved; it must not look like an ordinary denial or an approval.
- [ ] A pending identity scan is recorded as `POLICY_PENDING`, not `NOT_APPROVED`, in gate logs and exports.
- [ ] Invalid QR is rejected.
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

### Two-physical-device card issuance and synchronisation

Use two actual Android phones, Device A and Device B, signed in with separately provisioned test staff accounts in the same school. Keep both app builds and school IDs recorded.

- [ ] Install the same release-candidate APK on both phones and record each APK SHA-256 and signing certificate fingerprint.
- [ ] Enrol/refresh the trusted issuer public key on both phones before card verification.
- [ ] On Device A, register a synthetic test student and issue a new signed V2 card.
- [ ] Confirm the card record and signed QR payload are present on Device A.
- [ ] Trigger sync on Device A and wait for an explicit successful sync result.
- [ ] Trigger sync on Device B; confirm the same student and card identifier arrive without manually recreating either record.
- [ ] Scan the newly issued card on Device B; verify signature, issuer trust, card lifecycle, and student lookup all succeed.
- [ ] Confirm Device B shows the amber policy-pending outcome, not an ordinary denial, while entry policy is pending.
- [ ] Revoke/deactivate the card on Device A, sync, then refresh Device B.
- [ ] Scan the old QR on Device B and confirm it is rejected as inactive/revoked.
- [ ] Verify the same immutable scan event does not appear twice after repeated sync/retry.
- [ ] Record the result, timestamp, device identifiers, student test number, card identifier, and relevant log IDs. Never include private keys or real student data.

### Offline and multi-device behaviour
- [ ] Download/sync the roster on Device A.
- [ ] Disconnect Device A from the network and verify an authorized test card.
- [ ] Confirm the event is queued locally and no data is silently lost.
- [ ] Reconnect and confirm queued events upload once.
- [ ] Device B receives the synced records and the card lifecycle state.
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


## Physical-device execution record

| Field | Device A | Device B |
|---|---|---|
| Model |  |  |
| Android version |  |  |
| App version |  |  |
| APK SHA-256 |  |  |
| Signing certificate SHA-256 |  |  |
| Staff role/test account label |  |  |
| Test date/time |  |  |

**Two-phone outcome:** Not run / Pass / Fail

**Evidence to attach:** redacted screenshots of the issued card record, successful sync state on both devices, Device B scan result, revocation result, and relevant audit-log IDs.
