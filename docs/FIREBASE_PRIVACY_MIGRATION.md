# LTC Firestore privacy and migration runbook

## What changes

- The retired `/users/{uid}` schema is denied to all client accounts. The application must use the school-scoped `/schools/ltc_school_01` data model.
- The shared `students` collection must not contain `guardianName`, `guardianPhone`, `emergencyContact`, or `notes`.
- Those fields live in `schools/ltc_school_01/student_private/{studentId}`, readable/writable only by active Admin and Head Teacher accounts.
- Exam recommendations must be created by active finance staff with `recommendedByUid == request.auth.uid`. Approval is a one-way transition attributed to the authenticated Admin/Head Teacher UID.
- Staff role lookup uses a server read so cached membership cannot authorize a disabled account.

## Before deployment

The repository currently references this unverified database ID:

`ai-studio-android-oakridge-c82a4d82-3596-4f68-a7ca-a7d4c71aaaba`

Do not migrate data or deploy rules to it merely because it is checked in. An authorized LTC owner must confirm the exact Firebase project ID, Android app registration, and named Firestore database from the Firebase Console. The authenticator deliberately fails closed while the checked-in AI Studio/Oakridge identifier remains.

## Migration procedure

Run from a trusted administrator workstation with Firebase Admin SDK Application Default Credentials. Never commit service-account keys to Git or embed them in the APK.

1. Confirm the exact production project/database with the school owner.
2. Set the environment to those verified values:
   ```sh
   export GOOGLE_CLOUD_PROJECT="<verified-ltc-project-id>"
   export FIRESTORE_DATABASE_ID="<verified-ltc-firestore-database-id>"
   ```
3. Install `firebase-admin` in the trusted workstation environment.
4. Run the dry run first:
   ```sh
   node scripts/migrate-student-private-fields.cjs
   ```
5. Review the project ID, database ID, scanned student count, and number of records containing private fields. The script refuses the current unverified database ID and makes no changes without `--apply`.
6. Only after explicit owner approval, apply the migration:
   ```sh
   node scripts/migrate-student-private-fields.cjs --apply
   ```
7. Verify sample records with an Admin account, and verify that Gate, Meal, and Teacher accounts cannot read the private collection.
8. Deploy `firestore.rules` to the same confirmed named database. Emulator tests do not deploy production rules.

## Release gates

Do not call the app production-ready until all are complete:

- Android and Firestore rules CI pass on the release commit.
- The private-field migration has been reviewed and applied to the verified production database.
- Production rules have been deployed to that same database.
- Cross-device student/card sync and exam recommendation/approval are verified with real staff accounts.
- The complete role-permission matrix and inactive-staff denial are tested.
- Physical devices confirm offline queue recovery, card revocation propagation, and no loss of records.
