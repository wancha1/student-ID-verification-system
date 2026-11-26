# Supabase migration history recovery

## Purpose

Restore a reproducible migration directory from the exact SQL recorded by the connected Supabase project's migration history. This is a source-control recovery operation, not a database migration.

## Source and method

The authoritative recovery source was the read-only table:

`supabase_migrations.schema_migrations`

For each recorded row, the recovery used:
- `version` as the 14-digit migration prefix;
- `name` as the migration suffix;
- the stored `statements` array as the SQL file contents.

The project contained 18 migration-history rows. Every row contained exactly one SQL statement. The recovered source initially matched all 18 statements byte-for-byte after trimming outer whitespace. During release-safety review, the personal-email seed rows in `20261111133009_record_ltc_staff_invitation_plan.sql` were removed from the source-controlled copy. The table definition is preserved, while those invitation records must be provisioned through the authorized school-server workflow. Consequently, 17/18 recovered files remain byte-for-byte matches; the one deliberate sanitization is documented here.

A separate pre-migration prerequisite was also discovered: both live projects contain `public.rls_auto_enable()`, while production also has the `ensure_rls` DDL event trigger. Neither object appears in the 18-row migration history. The clean replay therefore failed at migration 2, which revokes execute on that function. A supplemental migration, `20261111131755_restore_rls_auto_enable_event_trigger.sql`, now captures this prerequisite between migrations `20261111131447` and `20261111131756`. It is idempotent against the existing function/event trigger. The supplemental migration is not one of the 18 originally recorded rows.

No live schema, data, migration-history row, key, secret, or database setting was modified.

## Restored migration sequence

| Version | Name |
|---|---|
| 20261111131447 | ltc_v1_school_access_schema |
| 20261111131756 | restrict_rls_auto_enable_rpc |
| 20261111133009 | record_ltc_staff_invitation_plan |
| 20261111133332 | add_staff_invitation_plan_full_name |
| 20261112083037 | secure_private_staff_and_storage_buckets |
| 20261112083118 | ltc_backend_hardening |
| 20261112083234 | student_fee_sync_fields |
| 20261112085027 | remove_duplicate_school_identifier_indexes |
| 20261114102130 | ltc_attendance_and_exam_exception_sync |
| 20261114102213 | ltc_expand_academic_staff_roles |
| 20261114143342 | restrict_authenticated_table_privileges |
| 20261114143436 | restrict_future_table_privileges |
| 20261114191936 | student_finance_access_boundary |
| 20261116135438 | student_access_status_compatibility_default |
| 20261116141326 | fail_closed_student_access_defaults |
| 20261116142239 | trusted_issuer_public_key_distribution |
| 20261116154725 | meals_unique_serving_per_day |
| 20261116154821 | drop_redundant_meals_unique_index |

## Supplemental bootstrap migration

`20261111131755_restore_rls_auto_enable_event_trigger.sql` is a newly added, idempotent migration for a prerequisite that existed outside the recorded migration history. It recreates the `public.rls_auto_enable()` function and creates the `ensure_rls` event trigger only if absent; it fails closed if that trigger name is already bound to a different function. Production already has both objects, and staging already has the function but not the event trigger. Neither project was changed by this recovery step. Because this supplemental version sorts before an already-applied migration, any future deployment to an existing remote project must explicitly include out-of-order migrations and be reviewed before execution.

## Noncanonical files

The nine SQL files previously in `supabase/migrations/` on `main` used different timestamps and/or SQL contents from the applied migration history. They were moved to:

`supabase/archive/noncanonical-migrations/`

They are retained for traceability and must not be treated as part of the canonical migration chain.

In particular, `20261112000300_mark_clean_ltc_start.sql` had no corresponding row in the live migration history. It is archived, not restored as an executable migration. Do not run it without a separate review and an explicit decision.

## Validation performed

- Live migration-history rows: 18.
- Recovered canonical SQL files: 18.
- Supplemental prerequisite migration: 1 (not present in the original migration-history table).
- Exact SQL comparisons after privacy sanitization: 17/18 matched.
- `20261111133009_record_ltc_staff_invitation_plan.sql`: schema retained; historical personal-email seed data intentionally omitted.
- Migration versions: strictly chronological.
- Archived noncanonical files: 9.
- Live database writes: none.
- Clean-database replay: passed in the disposable Supabase local stack for the combined 24-migration sequence (18 recorded baseline + 1 supplemental prerequisite + 4 issuer/audit security migrations + 1 finance sync guard migration).
- Staging dry-run of the supplemental prerequisite created the missing `ensure_rls` event trigger inside a transaction; rollback was verified, so no staging object was persisted.
- Staging history remains unchanged; the supplemental migration was tested transactionally and rolled back.

## Required next checks

1. Review the recovered SQL, especially the initial RLS and Storage policies, as historical source rather than assuming they are still the desired final security posture.
2. Run the full sequence against a disposable empty Supabase/Postgres environment using the project's pinned Supabase CLI version.
3. Compare the resulting schema, functions, triggers, policies, grants, extensions, and Storage configuration against the live project using schema-only comparison.
4. Reconcile the later open PR migrations (#68 and #72–#74) against this canonical history. Do not rewrite an applied schema change; this sanitization removes only historical invitation seed data from the source copy, not schema DDL.
5. Only after clean replay and review, use a disposable staging project for the #72/#73 security tests.

A successful SQL text recovery is not proof that the migrations replay cleanly or that the resulting schema is production-safe.
