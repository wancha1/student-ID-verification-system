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

The project contained 18 migration-history rows. Every row contained exactly one SQL statement. The resulting 18 files were compared byte-for-byte after trimming outer whitespace against the stored statement. **All 18 matched.**

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

## Noncanonical files

The nine SQL files previously in `supabase/migrations/` on `main` used different timestamps and/or SQL contents from the applied migration history. They were moved to:

`supabase/archive/noncanonical-migrations/`

They are retained for traceability and must not be treated as part of the canonical migration chain.

In particular, `20261112000300_mark_clean_ltc_start.sql` had no corresponding row in the live migration history. It is archived, not restored as an executable migration. Do not run it without a separate review and an explicit decision.

## Validation performed

- Live migration-history rows: 18.
- Canonical SQL files: 18.
- Exact SQL comparisons: 18/18 matched.
- Migration versions: strictly chronological.
- Archived noncanonical files: 9.
- Live database writes: none.
- Clean-database replay: **not yet run**.
- Staging replay: **not yet run**.

## Required next checks

1. Review the recovered SQL, especially the initial RLS and Storage policies, as historical source rather than assuming they are still the desired final security posture.
2. Run the full sequence against a disposable empty Supabase/Postgres environment using the project's pinned Supabase CLI version.
3. Compare the resulting schema, functions, triggers, policies, grants, extensions, and Storage configuration against the live project using schema-only comparison.
4. Reconcile the later open PR migrations (#68 and #72–#74) against this canonical history. Do not rewrite a migration after it has been applied.
5. Only after clean replay and review, use a disposable staging project for the #72/#73 security tests.

A successful SQL text recovery is not proof that the migrations replay cleanly or that the resulting schema is production-safe.
