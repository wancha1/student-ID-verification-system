# Production-readiness hardening: audit items 1–5

This branch addresses code-level risks found during the LTC audit. It is not a declaration that the application is production-ready. The live Firebase project, deployed rules, and physical devices must still be verified by the school owner.

## 1. Card lifecycle validation for every department

The meal workflow now delegates to `QrUnifiedVerifier` when the Room-backed repository is active. The requirements scanner uses `StudentRepository.verifyStudentByQr`. Both paths reject unsigned/invalid credentials, invalid dates, unknown cards, inactive/revoked/replaced cards, and unavailable/deleted student records instead of treating a valid signature as proof that a card is still active.

Regression verification required:
- ACTIVE signed card succeeds for the selected department.
- LOST, REPLACED, and DEACTIVATED cards are rejected by gate, meals, and requirements.
- Unknown card IDs and unsigned legacy QR values are rejected.
- Meal serving permission is enforced independently of the scanned card's validity.

## 2. Durable meal, attendance, and exam records

The Room-backed shared scan pipeline persists departmental events in Room and queues them for sync. The ViewModel now observes the Room tables instead of seeding operational meal history with fake sample records or adding a second in-memory copy of a persisted event. Existing unique Room indexes prevent duplicate student/meal/date and student/lesson/session records.

Test on a device before release:
1. Scan a valid card for lunch.
2. Force-stop the app and reopen it.
3. Confirm the meal remains visible and the student cannot be served a second time for the same meal/date.
4. Repeat while offline, then reconnect and verify exactly one cloud event.
5. Repeat for classroom attendance and exam exceptions.

The mock/test repository fallback is intentionally in-memory; do not use it as a production data source.

## 3. Exam-exception attribution and approval

Firestore rules now require `recommendedByUid == request.auth.uid` for a new recommendation. Approval must be a one-way `false → true` transition by an active Admin/Head Teacher account, with `approvedByUid == request.auth.uid`. Student ID, exam code, and original recommendation UID are immutable during approval. Identical retry writes remain idempotent.

The cloud sync backend writes the recommender UID from the currently authenticated staff account and reads the authenticated staff name for display. It does not silently create an approved record when the original recommendation is missing: sync fails closed and asks that the authenticated Bursar recommendation be synced first. This is deliberate to avoid inventing audit attribution for an offline approval.

## 4. Student privacy separation

Guardian name, guardian phone, emergency contact, and notes are being separated into `schools/ltc_school_01/student_private/{studentId}`. Only active Admin/Head Teacher accounts can read/write that collection. Shared roster documents containing any of those private fields are blocked for non-leadership staff until migrated. This is a fail-closed rule; old records will temporarily be unreadable to gate/meal/teacher accounts until the migration is completed.

### Required one-time migration

Run from a trusted admin workstation with Firebase Admin SDK Application Default Credentials, after confirming the real LTC Firebase project and Firestore database:

```sh
npm install firebase-admin
export GOOGLE_CLOUD_PROJECT="<verified-ltc-firebase-project-id>"
export FIRESTORE_DATABASE_ID="<verified-ltc-firestore-database-id>"
node scripts/migrate-student-private-fields.cjs
node scripts/migrate-student-private-fields.cjs --apply
```

The first command is a dry run. Review the project ID, database ID, and affected record count before using `--apply`. The script copies sensitive fields to `student_private` and deletes them from shared student documents. Do not run against a guessed project/database. Verify the migrated documents with an authorized Admin account, then test gate access using a Gate Staff account.

## 5. Firebase configuration, sync, and deployment

The repository currently contains a named Firestore database ID with an AI Studio/Oakridge identifier:
`ai-studio-android-oakridge-c82a4d82-3596-4f68-a7ca-a7d4c71aaaba`.
That identifier is present in the app resource, the cloud backend default, and `firebase.json`. The intended live LTC Firebase project/database has not been independently confirmed in this repository. Do not assume this identifier is the correct production target or replace it with a guessed value.

Before deployment, an authorized owner must:
1. Confirm the Firebase project ID, Android application ID, and named Firestore database ID from Firebase Console.
2. Align `app/google-services.json`, `app/src/main/res/values/firebase_applet_config.xml`, `FirestoreCloudBackend.FIRESTORE_DATABASE_ID`, and `firebase.json` to the confirmed project/database.
3. Deploy `firestore.rules` to that exact named database. Emulator tests do not deploy live rules.
4. Provision the first Admin through a trusted Firebase Admin SDK process; never store service-account keys or private signing keys in Git or the APK.
5. Verify staff authentication, multi-device sync, offline queue/retry, revocation propagation, and the full role-permission matrix on physical devices.
6. Test backup/restore, app upgrade/migrations, duplicate retries, and loss of network during each write path.

## Current status

- Code and rule changes are committed to the audit branch.
- CI must pass on the resulting branch before review.
- The private-field migration and live Firebase deployment have not been run by this change.
- Physical-device visual, offline, and multi-device verification remains outstanding.
- Do not merge or deploy until these steps are complete.
