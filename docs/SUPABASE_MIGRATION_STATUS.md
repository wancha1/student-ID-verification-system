# Supabase Migration — LTC V1

## Target and operating model
- Supabase project: `uobhtoqbenrgpzlwbsco` (eu-central-1).
- School tenant: `ltc_school_01` (Lira Town College).
- Room remains the on-device source of truth and durable offline queue. Supabase is the remote backend.
- This is an intentional clean start. No Firebase student records are being imported, and the existing Firebase project/data are not deleted.

## V1 cloud scope
- Authenticated staff membership and role resolution.
- `students`, leadership-only `student_private`, `cards`, `gate_logs`, and `meals`.
- Private Storage bucket `student-photos`, canonical path `<school_id>/<student_id>/photo.jpg`.
- Private `school-exports` bucket reserved for server-generated exports; no direct client policy.
- Attendance and exam-exception records remain local-only and are deliberately excluded from V1 cloud sync.

## Android implementation
- Production `RoomStudentRepository` now constructs `SupabaseCloudBackend`.
- `SupabaseStaffAuthenticator` handles Supabase sign-in/session restoration and resolves role only from the authenticated user's active `staff_memberships` row for `ltc_school_01`.
- `EncryptedSupabaseSessionManager` stores the Supabase session through the Android Keystore-backed encrypted session vault.
- Supabase Kotlin SDK 3.5.0 Auth, PostgREST and Storage are configured with a publishable key only. Never embed a service-role/secret key in Android or GitHub.
- Backend operations cover student/private-profile sync, card sync, gate audit uploads, meal upserts, fee updates, soft deletion, and private photo upload/download.
- Roster, card, meal and photo queries now use paginated PostgREST range requests to avoid the default row cap truncating schools with 1,000+ records.
- Meal upload uses the database natural key `school_id,student_id,meal_type,meal_date` to prevent duplicate servings across devices.
- Room remains responsible for offline persistence and retry/outbox orchestration. Live multi-device and offline-recovery acceptance is still required.

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
5. Supabase Auth leaked-password protection is still disabled. Enable it in Dashboard → Authentication → Settings → Password Security before production onboarding.
6. The intentionally policy-free private invitation-plan table is reported as an informational RLS finding; this is deliberate because client roles have no grants. Duplicate standalone student/card unique indexes were removed in migration `20261112000400_remove_duplicate_school_identifier_indexes`; the equivalent unique constraints remain.
7. Do not merge PR #44 or call this production-ready until the checks above pass.

## Current status
- Database schema/buckets/security hardening: applied to the Supabase project.
- Android production cloud backend: switched to Supabase in PR #44.
- Firebase Android runtime/build integration: removed in the PR branch; no Firebase project/data deletion performed.
- CI: previous run failed on two Supabase photo API compile errors; those calls have been corrected. A fresh run for the Firebase-free, paginated backend is pending/running.
- APK: no verified APK artifact from the corrected build yet.
- PR #44 remains draft and unmerged.
