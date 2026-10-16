# Firebase staff authentication and provisioning

## Security model

The Android client signs in with Firebase Authentication email/password. It then reads the signed-in UID's trusted profile at:

`schools/ltc_school_01/staff/{firebase-auth-uid}`

The document must exist and contain `active: true`. The client derives its duty role from the document's `role` field; it does not accept a role chosen by the login screen. The Firestore rules also require an authenticated user with an active staff document. The first administrator must be provisioned through a trusted Firebase Admin SDK environment.

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
  "active": true,
  "hasExplicitFinanceAccess": false
}
```

3. Grant finance access only when explicitly approved by school leadership. Set `hasExplicitFinanceAccess: true` only for an administrator who has been authorized to perform finance operations.
4. Sign in through the Android app. The app will reject the account if the staff document is absent, inactive, or has an unsupported role.
5. Provision subsequent staff the same way with least-privilege roles such as `BURSAR`, `GATE_OFFICER`, `MEAL_STAFF`, `TEACHER`, or `EXAM_STAFF`.

## Student privacy migration and access

Guardian name, guardian phone, emergency contact, and free-form notes belong in
`schools/ltc_school_01/student_private/{studentId}`, not in the shared
`students` roster. Only active Admin and Head Teacher staff can read or write
that private collection. Non-leadership staff are denied access to legacy shared
student documents while those documents still contain private fields.

Before deploying the stricter rules, follow
[`PRODUCTION_READINESS_AUDIT.md`](PRODUCTION_READINESS_AUDIT.md) and run the
dry-run first, then the explicit `--apply` migration script with verified project
and database IDs. Do not deploy the stricter rule set before migrating the legacy
documents, or gate/meal/teacher reads of those records will fail closed.

## Deactivate a staff member

Disable the Firebase Authentication user and set `active: false` or remove that user's staff document using a trusted administrative process. For sensitive departures, do both. Firestore rules reject operations from inactive staff. Existing ID tokens can remain valid until refreshed, so disabling the Auth user is also important.

## Operational note

The first Firebase sign-in requires a working network connection. This change does not yet implement a dedicated offline re-unlock flow for a previously verified staff identity. Do not treat offline access as production-ready until that flow is implemented and tested on multiple devices.
