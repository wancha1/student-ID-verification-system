# Live Supabase Read-Only Audit — 2026-11-23

**Scope:** read-only inspection of the connected Supabase project. No schema, policy, data, plan, or secret was changed.

## Project metadata

- Project: School access
- Region: eu-central-1
- PostgreSQL: 17.11.0.018
- Connector-reported status: ACTIVE_HEALTHY
- The connector did not expose the subscribed plan or billing status; do not assume Free or Pro.

## Schema and migration history

- The live database reports the initial migration version 20261111131447 named ltc_v1_school_access_schema, followed by later backend, RLS, finance, access-default and issuer-key migrations.
- The repository's current main branch and PR #64 branch do not contain that initial migration file. The live schema exists, but the repo migration chain is not currently a complete reproducible source of truth.
- The live database contains the core school, staff, student, private-profile, card, gate-log, meal, finance, attendance, exam-exception and trusted-issuer tables.
- The live table inventory includes private.staff_invitation_plan.

**Required action:** recover the exact initial migration from the original source/history or a controlled schema export, compare it with the live schema, and restore it to version control. Do not reconstruct a guessed baseline or apply a replacement baseline to the live project.

## RLS, grants and triggers

- RLS is enabled on all inspected public tables and on private.staff_invitation_plan.
- No anon table grants were returned by the role-grant inspection.
- No anon table grants were returned by the role-grant inspection. authenticated has SELECT and INSERT, but not UPDATE, on gate_logs and meals. This matches the append-only/idempotent client strategy in PR #62; verify it with authenticated integration tests. The live RLS policy for staff_memberships allows an active ADMIN to update other staff memberships, including the finance flag and active state.
- Other tables have role-specific RLS policies and authenticated table grants; a grant alone does not establish access because RLS still applies.
- Live triggers currently include card reactivation prevention, student fee-status permission enforcement, and synchronisation of finance fee status into the student roster.
- The finance-membership capability guard from PR #62 is not in the live migration history and no corresponding trigger was found. This confirms the current live database still permits an ordinary active ADMIN to attempt changes to another staff membership's finance flag under the broad UPDATE policy. The PR #62/65 trigger is intended to close this gap, but remains unapplied and unverified.

## Supabase Security Advisor snapshot

- WARN: leaked-password protection is disabled in Supabase Auth. Enable it after confirming the Auth configuration and test account impact.
- INFO: private.staff_invitation_plan has RLS enabled and no policies. This is fail-closed; verify the intended service-side access path and retain the restriction unless an approved workflow requires otherwise.
- No health advisor lints were returned in this snapshot.

## Defaults requiring review

- public.students.access_status defaults to RESTRICTED_FEES.
- public.students.fee_status defaults to OUTSTANDING.
- public.student_finances.fee_status defaults to CLEARED.
- The differing defaults may be intentional for separate roster and finance semantics, but the finance-table default could create an unexpected clearance if a finance row is inserted without an explicit fee status. Review this against the app's write paths and policy before changing it.
- The current app policy-pending implementation returns POLICY_PENDING for a valid identity while gate policy is unapproved. This must remain consistent across Room, Supabase defaults, exports and dashboards.

## Backups and inactivity

The current project plan was not exposed by the project metadata tool, so its actual backup and pause behaviour is **unverified**.

Current Supabase documentation states:
- Free projects may be paused after one week of inactivity and do not receive automatic scheduled database backups; Supabase recommends regular off-site exports.
- Pro, Team and Enterprise projects receive daily backups. Pro retains 7 days, Team 14 days and Enterprise up to 30 days.
- Storage objects are not included in database backups; photo/object recovery requires a separate plan.
- PITR is an add-on for paid plans and requires at least a Small compute add-on.

Before production, verify the project's plan in the Supabase dashboard, configure an off-site backup process, and test restoration. Do not upgrade until the owner approves the quoted recurring cost.

## What this audit does not prove

- It does not prove every RLS predicate is correct; policy expressions still require role-by-role negative and positive tests.
- It does not prove the finance guard migration is safe to apply.
- It does not establish the actual billing plan, scheduled backup configuration, backup restore success, or Storage backup.
- It does not validate Android sync or physical-device behaviour.
