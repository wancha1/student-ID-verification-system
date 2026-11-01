# Firebase staff authentication and provisioning

## Security model

The Android client signs in with Firebase Authentication email/password. It then reads the signed-in UID's trusted profile at:

`schools/ltc_school_01/staff/{firebase-auth-uid}`

The document must exist and contain `active: true`. The client derives its duty role from the document's `role` field; it does not accept a role chosen by the login screen. The Firestore rules also require an authenticated user with an active staff document. Firebase Storage cannot read this school's named Firestore database from Storage rules, so private student-photo access uses trusted Firebase Authentication custom claims (`ltcSchoolId`, `ltcRole`, `ltcActive`). The first administrator must be provisioned through a trusted Firebase Admin SDK environment.

Do not restore local self-provisioning or client-side staff document creation. Do not place service-account JSON, private keys, passwords, or administrator credentials in this repository or APK.

## Firebase project setup

1. In Firebase Console, select the actual LTC Firebase project and enable **Authentication → Sign-in method → Email/Password**.
2. Add the Android app using the exact application ID from `app/build.gradle.kts`.
3. Download that project's `google-services.json` into `app/google-services.json` for a local build. Do not commit project-specific credentials/configuration unless the repository's owner explicitly approves that policy.
4. Ensure the named Firestore database ID matches `app/src/main/res/values/firebase_applet_config.xml`, `firebase.json`, and `FirestoreCloudBackend.FIRESTORE_DATABASE_ID`.
5. Deploy `firestore.rules` to that exact database. A successful emulator test does not deploy rules to the live project.

## Provision the first administrator

1. In Firebase Console → Authentication → Users, create the administrator's account and verify the UID.
2. From a trusted administrator workstation or secured server using Firebase Admin SDK credentials, create `schools/ltc_school_01/staff/{UID}` with fields similar to:

```json
{
  "name": "LTC System Administrator",
  "email": "admin@your-school-domain.example",
  "role": "ADMIN",
  "active": true
}
```

3. The provisioning script creates the staff document and sets the matching custom claims. If claim creation fails, it rolls back the new staff document. Version 1 does not include finance permissions. Do not add finance flags or legacy finance roles to Version 1 staff profiles.
4. Sign in through the Android app. The app will reject the account if the staff document is absent, inactive, or has an unsupported role.
5. Provision subsequent staff the same way with least-privilege roles using only `ADMIN`, `HEAD_TEACHER`, `GATE_OFFICER`, or `MEAL_STAFF`. Each staff member must receive a fresh ID token after provisioning before Storage access will work.


## Migrate existing staff claims

Existing staff profiles must be migrated one UID at a time before those accounts can read student photos from Storage. Use a trusted workstation/server with Firebase Admin SDK Application Default Credentials and school-owner authorization. Do not run this from the Android app or a student device.

Preview first (no writes):

```sh
GOOGLE_CLOUD_PROJECT=gen-lang-client-0207053136 \
FIRESTORE_DATABASE_ID=ai-studio-android-oakridge-c82a4d82-3596-4f68-a7ca-a7d4c71aaaba \
node scripts/migrate-ltc-staff-claims.cjs --uid FIREBASE_AUTH_UID
```

Review the UID, email, role, Firestore active status, Auth disabled status, and proposed claims. The script refuses missing staff documents, unsupported roles, and mismatched emails. It derives `ltcActive` from both the Firestore `active` flag and whether the Auth account is disabled. It preserves unrelated custom claims and never creates or edits staff documents.

Only after explicit school-owner authorization, apply the reviewed change:

```sh
GOOGLE_CLOUD_PROJECT=gen-lang-client-0207053136 \
FIRESTORE_DATABASE_ID=ai-studio-android-oakridge-c82a4d82-3596-4f68-a7ca-a7d4c71aaaba \
node scripts/migrate-ltc-staff-claims.cjs --uid FIREBASE_AUTH_UID --apply --confirm-authorization
```

The default mode is dry-run. Apply mode changes only that user's Auth custom claims. The staff member must obtain a fresh ID token (normally by signing out and signing in again) before Storage access is expected to work. Repeat separately for each existing staff UID; do not bulk-run without reviewing each account.

## Student photo Storage

Student photos belong under `schools/{schoolId}/students/{studentId}/photo.jpg`. Storage rules require the matching school ID, an active claim, and one of the four Version 1 roles to read. Only `ADMIN` and `HEAD_TEACHER` can upload, replace, or delete photos. Uploads are limited to JPEG, PNG, or WebP and 5 MiB. The Storage emulator rules tests run in CI; this does not create a bucket or deploy rules to the live Firebase project.

Existing staff accounts provisioned before custom claims were introduced do not automatically receive these claims. Do not assume they can access photos until a trusted Admin SDK migration has set and verified their claims. Any future role change or deactivation process must update the Firestore staff document and the matching Auth claims together. Auth claim changes take effect when a new ID token is issued; previously issued tokens can remain valid until expiry, so the current claims design is not an instant-revocation mechanism.

## Deactivate a staff member

Using a trusted Admin SDK process, set the staff document's `active` field to `false`, set the user's `ltcActive` custom claim to `false`, disable the Firebase Authentication account, and revoke refresh tokens. Existing ID tokens can remain usable until they expire; this design therefore has a bounded revocation delay and must not be represented as instantaneous.

## Operational note

The first Firebase sign-in requires a working network connection. This change does not yet implement a dedicated offline re-unlock flow for a previously verified staff identity. Do not treat offline access as production-ready until that flow is implemented and tested on multiple devices.
