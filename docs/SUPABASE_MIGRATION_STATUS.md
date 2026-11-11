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
The four Auth users have now been confirmed in `auth.users` and linked to active rows in `public.staff_memberships` for school `ltc_school_01`.

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
- Firebase Storage photo upload/download is embedded in `FirestoreCloudBackend`; it must be replaced with private Supabase Storage operations.
- Firebase dependencies and `google-services.json` are still required by the Android build. Do not remove them until all Firebase call sites have been migrated and the app builds without them.
- The Android app has not yet been switched to Supabase. No production backend cutover has occurred.

## Cutover requirements
1. Add a Supabase client using a publishable key only; keep URL/key in local build configuration, never hardcode a secret key.
2. Implement Supabase Auth session persistence and revalidation.
3. Derive app roles only from `staff_memberships` for the authenticated user and the fixed school ID.
4. Implement a Supabase-backed `RemoteCloudDataSource` for students, cards, gate logs and meals, preserving the existing Room outbox and retry semantics.
5. Map sensitive student fields to `student_private`; do not expose them through the ordinary student DTO or broad staff reads.
6. Replace Firebase Storage photo upload/download with private Supabase Storage paths: `<school_id>/<student_id>/<filename>`.
7. Preserve offline QR scanning, signature verification, card revocation monotonicity, idempotent log uploads and last-write-wins rules.
8. Verify school isolation and role-specific RLS using separate authenticated test accounts.
9. Run Android unit tests, instrumented tests and a clean GitHub Actions build.
10. Do not remove Firebase configuration or switch production endpoints until the full acceptance checklist passes.

## Status
- Supabase schema and core RLS migration: applied.
- RLS auto-enable function public execute grant: revoked.
- Security advisor: last check returned zero findings.
- LTC school row: created and active.
- Four Supabase Auth users: confirmed.
- Four `staff_memberships` rows: created and active.
- Student/photo/log data transfer: not started.
- Supabase Kotlin SDK compatibility and dependency versions: under evaluation against this repository's Kotlin toolchain.
- Android Supabase backend replacement: not yet implemented or built.
- Firebase remains the active Android backend.
