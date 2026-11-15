# Supabase Migration — LTC V1

## Target and operating model
- Supabase project: `uobhtoqbenrgpzlwbsco` (eu-central-1), currently ACTIVE_HEALTHY.
- School tenant: `ltc_school_01` (Lira Town College).
- Room remains the on-device source of truth and durable offline queue. Supabase is the remote backend.
- This is an intentional clean start. No Firebase student records are being imported, and the existing Firebase project/data are not deleted.

## V1 cloud scope
- Authenticated staff membership and role resolution.
- School-scoped students, restricted student-private details, cards, gate logs, meals, attendance, exam exceptions and finance records.
- Private Storage buckets: `student-photos` and `school-exports`.
- Android uses Supabase Auth, PostgREST and Storage with a publishable key only. A service-role/secret key must never be embedded in the Android app.

## Android implementation
- Production Room repository uses `SupabaseCloudBackend`.
- `SupabaseStaffAuthenticator` signs in with Supabase Auth and resolves the role only from the authenticated user's active `staff_memberships` row for `ltc_school_01`.
- Encrypted session storage uses the Android Keystore-backed session vault; restored sessions are revalidated against the server.
- V1 allowlists ADMIN, HEAD_TEACHER, GATE_OFFICER and MEAL_STAFF. Reserved legacy roles are rejected.
- Backend supports student/private-profile sync, card sync, gate audit uploads, meal upserts, attendance sync, exam-exception request/approval sync, fee updates, soft deletion and private photo upload/download.
- Paginated PostgREST range requests avoid default row-cap truncation.
- Meal upload uses the database natural key `school_id,student_id,meal_type,meal_date` to prevent duplicate servings across devices.
- Room owns offline persistence and retry/outbox orchestration. Live offline-recovery and two-device behaviour still require physical acceptance.

## Finance boundary
- Migration `20261114180000_student_finance_access_boundary.sql` is applied to production (recorded migration version `20261114191936`).
- Monetary balances are held in `student_finances`, not `student_private`.
- Finance RLS requires an active ADMIN membership with `has_finance_access = true`; gate and general access flows do not expose balances.
- The finance trigger synchronizes fee status and blocks unauthorized direct changes.
- The sole active ADMIN has explicitly been granted finance access. HEAD_TEACHER, GATE_OFFICER and MEAL_STAFF remain without it.
- SQL/RLS boundary tests passed using simulated JWT subject claims inside database transactions. These are not real Supabase Auth-token integration tests.

## Database privilege hardening
- Migrations `20261114143342_restrict_authenticated_table_privileges` and `20261114143436_restrict_future_table_privileges` are applied.
- Broad client grants were removed; client permissions are limited to the operations used by the app.
- The private invitation-plan table intentionally has no client policies or grants.
- The security advisor previously reported no critical finding. Leaked-password protection is unavailable on the current Free plan and remains a documented limitation.

## Live production snapshot — 2026-11-15
- Project status: ACTIVE_HEALTHY.
- Auth users: 4.
- Active LTC staff memberships: 4 (one each for ADMIN, HEAD_TEACHER, GATE_OFFICER and MEAL_STAFF).
- Student records in `ltc_school_01`: 0.
- Finance rows: 0.
- No approved student roster file has been imported. Do not create placeholder student records or load real student data until the school-approved source file and authorization are available.

## Validation status
### Completed
- Latest PR #49 CI run `43631374250` passed Android compilation, unit tests and Android emulator smoke tests at commit `422f7b1f3b108c3cdc8e32e45f1355bc26f5771a`.
- Finance RLS tests passed for explicitly authorized Admin and denied access for Admin without the capability, Head Teacher, Gate Officer and Meal Staff.
- Unauthorized finance inserts and direct fee-status changes were blocked.
- Finance migration and production permission grant were verified.

### Still required before production
1. Real-token Supabase integration tests using the four actual Auth accounts, including wrong-school and inactive-membership denial. Existing SQL subject-claim tests are not a substitute.
2. Physical-device acceptance on two Android phones: role login, camera/QR, photo sync, meal idempotency, offline queue, network recovery, logout and membership revocation.
3. Obtain and validate the school-approved roster export, map its columns, deduplicate student numbers, and run a reviewed import with a post-import reconciliation report.
4. Verify the permanent JKS and alias against the owner-held certificate fingerprint by running the Production APK workflow.
5. Install and verify the resulting signed APK on the physical test devices.

## Release control
- PR #49 remains open and unmerged; the production workflow is not yet on the default branch.
- The permanent signing secrets are configured in GitHub's `production` environment, but no production-signed artifact has yet been built or certificate-verified.
- The latest successful CI debug artifact is not a production release.
- Do not call this product production-ready until all required tests above pass and the school approves the pilot.


## Real-token integration workflow prepared — 2026-11-15
A manual GitHub Actions workflow now exists at `.github/workflows/supabase-real-token-tests.yml`, with its non-destructive smoke-test script at `scripts/supabase-real-token-smoke.sh`. It obtains real access tokens through the Supabase password-grant endpoint for the four existing staff accounts, verifies each account's active LTC membership and role, checks that the token cannot read a membership in a different school tenant, and probes the finance INSERT boundary using a deliberately nonexistent student ID so no test row can be created.

**Execution is pending protected credentials.** Add these as GitHub Environment secrets in the existing `production` environment; never place passwords in source control or chat:
The workflow uses the same public Supabase URL and publishable client key already configured for the Android app; these are not service-role credentials.
- `TEST_ADMIN_EMAIL`, `TEST_ADMIN_PASSWORD`
- `TEST_HEAD_TEACHER_EMAIL`, `TEST_HEAD_TEACHER_PASSWORD`
- `TEST_GATE_OFFICER_EMAIL`, `TEST_GATE_OFFICER_PASSWORD`
- `TEST_MEAL_STAFF_EMAIL`, `TEST_MEAL_STAFF_PASSWORD`

Use the four already provisioned LTC staff accounts and their current passwords. The workflow is manual and does not run as part of ordinary CI. It cannot be dispatched until the workflow file is available on the default branch. The test validates the existing active accounts; an inactive-membership denial test still requires a separately provisioned, school-approved inactive test account or a controlled temporary deactivation window.
