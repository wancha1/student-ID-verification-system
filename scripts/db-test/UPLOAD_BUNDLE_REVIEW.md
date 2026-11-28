# Uploaded DB replay and RLS bundle review

Reviewed on 2026-11-27 against `fix/ltc-school-seed-and-migration-cleanup`.

## Integration decision

The uploaded bundle is retained as review input, not copied wholesale into this branch. The branch already has a canonical Supabase migration chain and a CI workflow that runs `supabase db reset --local` against a disposable local Supabase stack. The uploaded workflow and replay script would duplicate that coverage while using a weaker Supabase stub.

## Migration files

The four uploaded security migrations correspond to security work already represented in the branch:

- `20261124130000_security_integrity_guardrails.sql`
- `20261124140000_append_only_school_audit_events.sql`
- `20261125152300_trusted_issuer_lifecycle_audit.sql`
- `20261125153000_block_unapproved_student_school_changes.sql`

The later issuer-lifecycle and student-school-change migrations in the branch are the canonical versions used by its migration chain. Do not add the uploaded copies as extra migrations: doing so risks duplicate triggers, policies, and audit structures. Preserve the existing canonical files and migration order.

## Why the uploaded replay script is not used

The supplied `replay.sh` drops and recreates the database named by `DB_NAME`. It is safe only when pointed at a dedicated disposable database. More importantly, it inserts `ltc_school_01` as `LTC synthetic seed` before replaying migrations. The branch's canonical school seed expects `Lira Town College` and deliberately raises an exception on an identity mismatch. Running the supplied script unchanged would therefore conflict with the canonical seed.

The branch's existing CI clean-replay job is preferred because it starts a disposable local Supabase stack and applies the complete canonical migration chain from an empty database.

## Why the uploaded RLS test script is not copied unchanged

The supplied `rls_tests.sh` assumes a different schema and authorization fixture, including `staff_memberships`, `student_finances`, and `audit_events`, plus its own JWT/role simulation. Those objects and assumptions are not provided by this branch's `scripts/db-test/bootstrap.sql`. In particular, a missing relation or mismatched helper must not be counted as a successful denial test.

The existing branch integration assertions already exercise these areas using the branch's own schema and fixture:

- trusted-issuer enrollment restrictions, one-way revocation, and append-only issuer lifecycle events;
- prevention of unapproved cross-school student reassignment;
- Admin versus Head Teacher private-profile and student-photo write boundaries;
- append-only, idempotent Gate and Meal event sync;
- attendance correction history and exam exception decision/audit behaviour;
- canonical school seed uniqueness and idempotent replay.

### Explicit remaining test gap

The uploaded script highlights additional finance-membership and finance-row isolation cases. Those cases are **not claimed as covered** by the current synthetic database assertions. They need a fixture aligned with the canonical membership/finance schema before the tests can be ported reliably. Also not yet claimed as covered by the current assertions: the uploaded script's exact cross-school audit-event read matrix and staff-membership before/after actor-attribution checks.

## Validation performed

- `bash -n` passed for the uploaded `replay.sh` and `rls_tests.sh` scripts.
- Static inspection confirmed the replay script's destructive database reset and its conflicting synthetic school seed.
- The uploaded RLS script was not executed against the branch fixture because its assumed tables/roles are absent; running it unchanged would yield invalid results.
- Existing PR #86 CI run 46010272438: PostgreSQL migration integration tests passed; canonical Supabase migration reset from an empty database passed; Android compile succeeded but Android unit tests failed; the staging role-test job completed successfully, but may skip when staging secrets are not configured.

No uploaded security migration was added as a duplicate. No live Supabase project was touched. No Git history was rewritten.
