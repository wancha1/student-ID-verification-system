# LTC Firestore privacy and deployment runbook

## Current authorized Firebase resources

The LTC owner-authorized configuration is:

- Firebase project: `gen-lang-client-0207053136`
- Firestore database: `ai-studio-android-oakridge-c82a4d82-3596-4f68-a7ca-a7d4c71aaaba`
- School document ID: `ltc_school_01`

The repository's `firebase.json` must continue to target exactly this named database. These repository settings do not prove that the same rules have been deployed to the live database.

## Security model

- The retired `/users/{uid}` schema is denied to all client accounts. The application uses the school-scoped `/schools/ltc_school_01` data model.
- The shared `students` collection must not contain `guardianName`, `guardianPhone`, `emergencyContact`, or `notes`.
- Those fields live in `schools/ltc_school_01/student_private/{studentId}`, restricted to active Admin and Head Teacher accounts.
- Exam recommendations must be created by active finance staff with `recommendedByUid == request.auth.uid`. Approval is a one-way transition attributed to the authenticated Admin/Head Teacher UID.
- Staff role lookup uses a server read so cached membership cannot authorize a disabled account.
- The initial administrator must be provisioned through a trusted Firebase Admin SDK environment, never from the Android client.

## Deploy the repository rules

Run from the repository root on a trusted workstation with the Firebase CLI installed and an authorized operator signed in. The guarded script verifies that `firebase.json` contains exactly one Firestore target and that it is the authorized LTC database.

```sh
firebase login
bash scripts/deploy-ltc-firestore-rules.sh
```

Read the project/database confirmation carefully. The script requires typing `DEPLOY-LTC-RULES` before it calls `firebase deploy --only firestore:rules --project gen-lang-client-0207053136`. It refuses to run if the configured Firestore target is missing, duplicated, or points elsewhere.

After deployment, inspect the live Rules page and run the Firestore rules tests. Emulator tests alone do not deploy production rules.

## Provision the first administrator

Create the intended administrator's account in Firebase Authentication first. Confirm the account belongs to the authorized school administrator and copy its actual UID from Authentication → Users. Then, from a trusted workstation/server using Application Default Credentials, install `firebase-admin` and set:

```sh
export GOOGLE_CLOUD_PROJECT="gen-lang-client-0207053136"
export FIRESTORE_DATABASE_ID="ai-studio-android-oakridge-c82a4d82-3596-4f68-a7ca-a7d4c71aaaba"
```

Run the provisioning script with the real UID, email and name:

```sh
node scripts/provision-ltc-staff.cjs \
  --uid "<actual-auth-uid>" \
  --email "<actual-auth-email>" \
  --name "LTC System Administrator" \
  --role ADMIN \
  --confirm-authorization
```

The script checks the exact project and database, confirms the Firebase Authentication UID exists and its email matches, refuses disabled accounts, and creates the staff document only if it does not already exist. It does not create an Authentication user or overwrite an existing staff profile. Obtain explicit school-owner authorization before using `--confirm-authorization`. Do not commit service-account keys or embed credentials in the APK.

Provision subsequent staff with the least-privilege role needed, using the same script and an explicitly approved role: `HEAD_TEACHER`, `BURSAR`, `GATE_OFFICER`, `SECURITY_HEAD`, `MATRON_PATRON`, `MEAL_STAFF`, `DIRECTOR_OF_STUDIES`, `TEACHER`, or `EXAM_STAFF`.

## Student privacy migration

This is separate from first-admin provisioning. Do not run a data migration simply to make the first administrator sign in.

Run from a trusted workstation with Firebase Admin SDK Application Default Credentials:

1. Set the exact project and database environment variables shown above.
2. Install `firebase-admin` in the trusted workstation environment.
3. Run the dry run:
   ```sh
   node scripts/migrate-student-private-fields.cjs
   ```
4. Review the reported project, database, scanned student count, and records containing private fields. The script now refuses to run unless both the project ID and database ID match the authorized LTC resources.
5. Only after explicit owner approval, apply the migration:
   ```sh
   node scripts/migrate-student-private-fields.cjs --apply
   ```
6. The script re-reads the shared roster and refuses to set `studentPrivacyMigrationComplete` if any shared student record still contains private fields.
7. Verify sample records with an Admin account and verify that Gate, Meal, and Teacher accounts cannot read the private collection.

If the database is empty, do not run a migration with the intention of inventing records. Verify the empty state and follow the repository's intended privacy-migration release gate before enabling non-leadership roster access.

## Release gates

Do not call the app production-ready until all are complete:

- Android and Firestore rules CI pass on the release commit.
- The intended staff profiles are provisioned in the verified production database.
- Production rules have been deployed to that same database.
- Student privacy migration is completed and verified if existing shared student records contain private fields.
- Cross-device student/card sync and exam recommendation/approval are verified with real staff accounts.
- The complete role-permission matrix and inactive-staff denial are tested.
- Physical devices confirm offline queue recovery, card revocation propagation, and no loss of records.
