# Supabase Migration — LTC V1

## Source of truth
- Android source branch: `fix/ltc-production-reliability`
- Migration work branch: `feat/supabase-backend-migration`
- Do not use `main` as the migration source until it is reconciled with the development branch.

## Supabase target
- Project: School access
- Project ref: `uobhtoqbenrgpzlwbsco`
- Region: `eu-central-1`
- School row: `ltc_school_01` / Lira Town College / LTC
- Keep Room as the local offline database. Supabase is the remote backend.
- Firebase remains the active Android backend until the replacement is implemented and verified.

## V1 data scope
- `schools`
- `staff_memberships`
- `students`
- `student_private`
- `cards`
- `gate_logs`
- `meals`
- private `student-photos` Storage bucket

Attendance and exam-exceptions are not included in the active V1 cloud-sync scope. Do not add them to this migration without a separate product decision.

## Staff onboarding
The four Auth users have been confirmed in `auth.users` and linked to active rows in `public.staff_memberships` for school `ltc_school_01`.

| Email | Role | Profile name |
|---|---|---|
| `wanchaaaron@gmail.com` | `ADMIN` | wancha |
| `aaronwancha@gmail.com` | `HEAD_TEACHER` | aaron |
| `introvertcorner85@gmail.com` | `GATE_OFFICER` | introvert |
| `darklordlucian85@gmail.com` | `MEAL_STAFF` | dark |

The names above are the exact short names supplied by the project owner. Replace them with full staff names if these are only usernames.

The restricted table `private.staff_invitation_plan` records the onboarding state and has no grants for anon or authenticated clients. The four records are marked `ACCOUNT_LINKED`.

Never put a Supabase service-role key in the Android app or in GitHub. Never trust a role supplied by the Android client or user-editable JWT metadata. The Android app must fetch the signed-in user's active membership from the protected server-side membership table and let RLS enforce authorization.

## Android code findings
- The migration branch was created from `fix/ltc-production-reliability`.
- `RoomStudentRepository` is the production repository and currently constructs `FirestoreCloudBackend`.
- `SyncManager` already depends on the `RemoteCloudDataSource` abstraction and owns the durable Room outbox, retries, sync watermark, and conflict handling.
- `RemoteCloudDataSource` currently includes legacy attendance/exam-exception methods. The V1 Supabase implementation must not sync those datasets.
- `FirebaseStaffAuthenticator` currently signs in with Firebase Auth and reads the trusted staff profile from Firestore. It must be replaced by Supabase Auth plus a server-validated `staff_memberships` lookup.
- `MainViewModel` and `FirebaseStaffLoginScreen` are directly coupled to Firebase authentication. Auth migration must update the restore-session and login call paths together, not just replace the authenticator class.
- Firebase Storage photo upload/download is embedded in `FirestoreCloudBackend`; it must be replaced with private Supabase Storage operations.
- Firebase dependencies and `google-services.json` are still required by the Android build. Do not remove them until all Firebase call sites have been migrated and the app builds without them.
- The Android app has not yet been switched to Supabase. No production backend cutover has occurred.

## Build configuration
- Supabase Kotlin SDK dependencies are present: Supabase Kotlin 3.5.0 Auth, PostgREST and Storage; Ktor Android 3.0.3; Kotlin serialization plugin; Kotlin 2.2.21.
- The Secrets Gradle Plugin reads `.env` and falls back to `.env.example`; `.env` is ignored by Git.
- `.env.example` contains the project URL and a placeholder publishable key. The placeholder is not a usable credential; a developer build must supply the actual publishable key locally.
- The app enables BuildConfig generation. Confirm generated Supabase BuildConfig fields with a real CI build before adding client code that references them.

## Auth session security finding
- Supabase Kotlin Auth defaults to `SettingsSessionManager` when no custom manager is supplied.
- The SDK troubleshooting guidance explicitly allows a custom `SessionManager` to store tokens in encrypted preferences or another protected platform store. The default manager is not documented as encrypted storage.
- Therefore, do not ship Auth with the default manager as though tokens were encrypted. Implement and test a custom Android `SessionManager` backed by Android Keystore encryption before enabling persistent staff sessions.
- Preserve `autoLoadFromStorage`, `autoSaveToStorage` and automatic refresh behaviour only after secure storage is implemented and tested. Do not log access tokens, refresh tokens, passwords or full session objects.
- Session restoration must still revalidate the active membership from Supabase before granting an app role. If membership validation fails due to network loss, keep the user unauthorised and present a retry path; do not infer a role from cached profile data.

## Cutover requirements
1. Add a Supabase client using a publishable key only; keep URL/key in local build configuration, never hardcode a secret key.
2. Implement a custom Android Keystore-backed Auth `SessionManager`, and test save/load/delete plus key invalidation.
3. Implement Supabase Auth sign-in and session restoration.
4. Derive app roles only from `staff_memberships` for the authenticated user and fixed school ID `ltc_school_01`.
5. Implement a Supabase-backed `RemoteCloudDataSource` for students, cards, gate logs and meals, preserving the existing Room outbox and retry semantics.
6. Map sensitive student fields to `student_private`; do not expose them through the ordinary student DTO or broad staff reads.
7. Replace Firebase Storage photo upload/download with private Supabase Storage paths: `<school_id>/<student_id>/<filename>`.
8. Preserve offline QR scanning, signature verification, card revocation monotonicity, idempotent log uploads and last-write-wins rules.
9. Verify school isolation and role-specific RLS using separate authenticated test accounts.
10. Run Android unit tests, instrumented tests and a clean GitHub Actions build.
11. Do not remove Firebase configuration or switch production endpoints until the full acceptance checklist passes.

## Status
- Supabase schema and core RLS migration: applied.
- RLS auto-enable function public execute grant: revoked.
- LTC school row: created and active.
- Four Supabase Auth users: confirmed.
- Four `staff_memberships` rows: created and active.
- Student/photo/log data transfer: not started.
- Supabase dependency foundation and local configuration placeholders: added.
- Supabase client factory, Keystore-backed session persistence, Auth screen replacement and remote repository implementation: not yet added.
- CI workflow now includes `feat/**` push branches; no successful workflow run or Android build has yet been verified for this branch.
- Latest Supabase security advisor check: one warning remains — Auth leaked-password protection is disabled. Enable it in Supabase Dashboard → Authentication → Settings → Password Security before production onboarding.
- Firebase remains the active Android backend.
