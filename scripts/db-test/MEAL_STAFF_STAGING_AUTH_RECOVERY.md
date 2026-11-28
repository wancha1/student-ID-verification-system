# Meal Staff staging Auth recovery

## Evidence

PR #89 diagnostic CI run #1910:
https://github.com/wancha1/student-ID-verification-system/actions/runs/46135850977

The staging role-boundary job fails before it reaches the Meal Staff membership, row-visibility, RPC, or finance assertions:

```text
DIAG: MEAL_STAFF check: Auth password sign-in
(POST /auth/v1/token?grant_type=password)
MEAL_STAFF Supabase password sign-in failed (HTTP 400).
Response: {"code":400,"error_code":"invalid_credentials","msg":"Invalid login credentials"}
```

The workflow's required-secret guard passed and invoked the script. Therefore the Meal Staff email/password secrets were non-empty; this does not prove that the password value is current.

A read-only check of the connected staging project found:
- One active `MEAL_STAFF` membership for `staging_school_01`.
- The Auth user exists, its email is confirmed, and it is not currently banned.
- The membership email matches the configured test account.
- The staging school has two student rows and two meal rows; both meal rows reference valid students.

The GitHub secret values and the sentinel IDs are not readable through the repository/database APIs. No credential or student data is recorded here.

## Operator recovery steps

Perform these steps yourself. Do not paste the password into a chat, issue, commit, workflow variable, or log.

1. Open the **staging** Supabase project in the Dashboard. Confirm the project reference is the one configured by the repository's `SUPABASE_URL` secret. Do not use the production project.
2. Open **Authentication → Users** and locate the existing Meal Staff test account using the value stored in `TEST_MEAL_STAFF_EMAIL`.
3. Confirm it is the same existing Auth user associated with the active `MEAL_STAFF` membership in school `staging_school_01`. Do not delete/recreate the user; doing so can invalidate the existing membership's `user_id` relationship.
4. Reset that existing user's password to a new strong staging-only password. Keep the email and user identity unchanged.
5. In GitHub, open **Settings → Secrets and variables → Actions** for this repository. Update only `TEST_MEAL_STAFF_PASSWORD` with the new password. Do not create a workflow variable and do not expose the value in logs.
6. Re-run the failed **Authenticated Supabase role-boundary tests (staging)** job for PR #89, run #1910, or trigger a new CI run on the same PR head.
7. Require the job to complete all four role paths. A skipped step, missing-secret failure, or run that stops before the Meal Staff assertions is not a pass.

If sign-in still returns HTTP 400, verify that `TEST_MEAL_STAFF_EMAIL` exactly matches the existing confirmed Auth user's email and that the password was saved to the repository secret (not an environment-level secret with a different scope). Do not weaken the test or change expected HTTP statuses to make it pass.

## Migration parity note

The repository chain at PR #89's head replays 27 migrations in CI. The connected staging project's migration ledger lists 25. It is not a simple "apply the missing migrations" repair:

- Present in repository replay but absent from the staging ledger:
  - `20261111131755_restore_rls_auto_enable_event_trigger`
  - `20261126190000_scholink_v1_role_boundary_hardening`
  - `20261127090000_scholink_append_only_event_sync`
  - `20261127120000_scholink_meal_and_academic_audit`
  - `20261127150000_seed_ltc_school`
- Present in staging ledger but absent from the repository chain:
  - `20261127174001_scholink_v1_role_boundary_hardening`
  - `20261127174005_scholink_append_only_event_sync`
  - `20261127174009_scholink_meal_and_academic_audit`

The relevant live definitions inspected in staging—meal SELECT/INSERT policies, staff-membership policies, `scholink_insert_meal_once(jsonb)`, and `private.is_active_staff(text,text[])`—match the corresponding repository policy/function definitions in behavior. The meal RPC is SECURITY INVOKER and authenticated-only; the membership helper checks the authenticated user, active membership, and active school. The migration-ledger divergence remains a separate reproducibility/deployment issue and must be reconciled in a separately reviewed migration plan. Do not blindly replay the repository seed migration: it creates `ltc_school_01`, whereas the isolated test tenant is `staging_school_01`.

## Scope

This is an operator runbook only. It does not change application code, SQL, migrations, staging fixtures, credentials, or production resources. The diagnosis is a rejected Auth credential pair, not evidence of a Meal Staff RLS or RPC assertion failure. The next successful run must still prove the complete staging role-boundary job.
