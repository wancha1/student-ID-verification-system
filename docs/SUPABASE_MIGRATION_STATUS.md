# Supabase Migration — LTC V1

## Target and operating model
- Supabase project: `uobhtoqbenrgpzlwbsco` (eu-central-1).
- School tenant: `ltc_school_01` (Lira Town College).
- Room remains the on-device source of truth and durable offline queue. Supabase is the remote backend.
- This is an intentional clean start. No Firebase student records are being imported, and the existing Firebase project/data are not deleted.

## V1 cloud scope
- Authenticated staff membership and role resolution.
- `students`, leadership-only `student_private`, `cards`, `gate_logs`, `meals`, `attendance_records`, and `exam_exceptions`.
- Private Storage bucket `student-photos`, canonical path `<school_id>/<student_id>/photo.jpg`.
- Private `school-exports` bucket reserved for server-generated exports; no direct client policy.
- Attendance and exam-exception records sync to dedicated school-scoped Postgres tables. Attendance is writable by teaching staff and leadership; bursars submit pending exam exceptions; leadership approves them.

## Android implementation
- Production `RoomStudentRepository` now constructs `SupabaseCloudBackend`.
- `SupabaseStaffAuthenticator` handles Supabase sign-in/session restoration and resolves role only from the authenticated user's active `staff_memberships` row for `ltc_school_01`.
- `EncryptedSupabaseSessionManager` stores the Supabase session through the Android Keystore-backed encrypted session vault.
- Supabase Kotlin SDK 3.5.0 Auth, PostgREST and Storage are configured with a publishable key only. Never embed a service-role/secret key in Android or GitHub.
- Backend operations cover student/private-profile sync, card sync, gate audit uploads, meal upserts, attendance sync, exam-exception request/approval sync, fee updates, soft deletion, and private photo upload/download.
- Student class and stream values are persisted separately in Supabase and reassembled for the current Room model.
- Supabase Auth maps BURSAR, TEACHER, DIRECTOR_OF_STUDIES, and EXAM_STAFF to the app's existing role screens; database membership constraints and RLS role lists include those staff roles.
- Roster, card, meal and photo queries now use paginated PostgREST range requests to avoid the default row cap truncating schools with 1,000+ records.
- Meal upload uses the database natural key `school_id,student_id,meal_type,meal_date` to prevent duplicate servings across devices.
- Room remains responsible for offline persistence and retry/outbox orchestration. SyncManager now uploads unsynced attendance and exam exceptions, marks confirmed uploads, pulls remote records, and resolves conflicts using the record timestamps.
- Live multi-device and offline-recovery acceptance is still required.

## Firebase removal
- Firebase SDK dependencies and BOM removed from the Android app.
- Google Services Gradle plugin and its root plugin declaration removed.
- `google-services.json` and the Firebase Android resource configuration removed.
- Legacy Firestore repository/backend/auth/model/error adapter sources and Firebase emulator-dependent Android unit tests removed.
- Instrumented smoke test no longer initializes or asserts Firebase.
- CI no longer installs Firebase emulator tooling or runs Firebase emulator/security-rule jobs; it builds Android variants, runs Android unit tests, and runs emulator smoke tests.
- Firebase archival docs/rules/scripts may remain in the repository as historical material; they are not part of the Android runtime or build graph. Existing Firebase project/data are untouched.

## Supabase schema and security changes applied
- Enabled RLS on `private.staff_invitation_plan` and revoked client-role grants; no client policies by design.
- Created private `student-photos` and `school-exports` buckets.
- Replaced student-photo policies with school-membership and role-based access.
- Added gate audit fields needed to retain full verification context.
- Added student/card/meal uniqueness constraints and a database trigger preventing reactivation of revoked/deleted cards.
- Added `students.fee_status` and leadership-only `student_private.outstanding_amount`.
- Marked the LTC clean-start privacy migration complete only after confirming the school student roster was empty.

## Outstanding validation and manual security action
1. CI must pass on the latest PR commit, including debug/release compilation, unit tests and Android emulator smoke test.
2. Test Supabase RLS using separate ADMIN, HEAD_TEACHER, GATE_OFFICER and MEAL_STAFF accounts, including wrong-school and inactive-membership denial.
3. Test real phone camera scanning, login/logout/session restoration, photo upload/download, offline queue, network recovery and multi-device meal idempotency.
4. Verify class/stream mapping against the app's actual student model before loading the live school roster.
5. Exercise attendance creation/correction, exam exception submission and approval, and role denial with real authenticated staff sessions.
5. Supabase Auth leaked-password protection is still disabled. Enable it in Dashboard → Authentication → Settings → Password Security before production onboarding.
6. The intentionally policy-free private invitation-plan table is reported as an informational RLS finding; this is deliberate because client roles have no grants. Duplicate standalone student/card unique indexes were removed in migration `20261112000400_remove_duplicate_school_identifier_indexes`; the equivalent unique constraints remain.
7. Do not merge PR #44 or call this production-ready until the checks above pass.

## Latest production-readiness checkpoint — 2026-11-14

### Live Supabase checks
- Project `uobhtoqbenrgpzlwbsco` is `ACTIVE_HEALTHY` in `eu-central-1`.
- The live LTC school row is `ltc_school_01`; four active memberships exist for ADMIN, HEAD_TEACHER, GATE_OFFICER, and MEAL_STAFF.
- The public application tables have RLS enabled.
- The `student-photos` and `school-exports` buckets are private. Student photos are limited to JPEG, PNG, or WebP and 5 MiB.
- Read-only role-helper checks returned true for each of the four active roles in the LTC tenant and false for a different school ID.
- The security advisor reports no critical finding. It still reports leaked-password protection disabled and one intentional informational RLS-without-policy finding on the private invitation-plan table.

### Privilege hardening applied to production
- Migration `20261114143342_restrict_authenticated_table_privileges` removed broad table grants from `anon` and `authenticated`, then granted only the SELECT/INSERT/UPDATE operations used by the Android app.
- Verified after migration: `authenticated` has no DELETE or TRUNCATE privilege on the public application tables; `anon` has no SELECT or TRUNCATE privilege.
- Migration `20261114143436_restrict_future_table_privileges` removed broad default table privileges for future public-schema tables created by the current `postgres` migration owner.
- The separate `supabase_admin` default ACL could not be changed by the connected migration role. Any future public table created under that role must receive explicit least-privilege grants and an RLS review before exposure.

### Android CI
- Commit `d50a81add0340058603e8617e54996d02c57b3ac` (login contrast and password assistance) passed GitHub Actions run [#1083](https://github.com/wancha1/student-ID-verification-system/actions/runs/43602479156).
- The successful run completed Android compile/release compilation, unit tests, and Android emulator smoke tests.
- This is automated emulator validation, not physical-device acceptance.

### V1 role boundary
- V1 supports ADMIN, HEAD_TEACHER, GATE_OFFICER, and MEAL_STAFF.
- Additional legacy/future role values remain in the database schema and source models, but the production authenticator rejects them until their role permissions, interfaces, RLS rules, and tests are completed.

### Remaining release blockers
1. Enable Supabase Auth leaked-password protection in Dashboard → Authentication → Settings → Password Security.
2. Complete authenticated RLS integration tests with real test accounts, including wrong-school and inactive membership denial for every operation.
3. Complete two-device physical acceptance: QR scan, meal duplicate prevention, offline queue, network recovery, photo sync, logout, and membership revocation.
4. Verify the school roster import process before loading real student data. The clean-start Supabase database currently has no student records.
5. Generate the permanent owner-controlled Android signing key on a trusted computer, store encrypted backups, and configure the four GitHub Actions signing secrets.
6. Run the Production APK workflow and verify its artifact signature against the owner's recorded certificate fingerprint.
7. Do not declare the product production-ready until all blockers above have passed.

The historical Firebase migration notes above describe the staged transition. The current Android branch uses Supabase; the Firebase project and its historical data have not been deleted.
