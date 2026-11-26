# Security and Data-Integrity Remediation — Test Plan

## Scope and status
Target integration base: PR #71 head `db457cd244f91a9747a359a76f421bd1e8715dbd`.

This document describes required tests for the source migration in this branch. It does not claim that the migration has been applied or that tests have passed.

## Finding verification

| Finding | Verified on PR #71 tip | Evidence |
|---|---|---|
| Trusted issuer key overwrite | YES | `20261116142239_trusted_issuer_public_key_distribution.sql` grants ADMIN UPDATE; no immutability trigger existed. |
| Finance identity/lifecycle bypass | YES | `20261122190000_guard_staff_finance_access.sql` trigger omitted `user_id` and `is_active`. PR #68 contains a proposed fix but is not an ancestor of PR #71. |
| General audit event system | NOT YET FULLY VERIFIED | Existing gate logs exist; a repository-wide write-path inventory is still required before claiming no other event records exist. |
| Attendance overwrite | YES | Room DAO uses REPLACE; Supabase backend upserts on student/session natural key. |
| Exam exception overwrite | YES | Room DAO uses REPLACE; Supabase backend upserts on student/exam natural key. |
| Requirement defaults | YES | `StudentRequirement.kt` initializes all seven Boolean values to true. |
| Finance default | YES | `student_finances.fee_status` has SQL default CLEARED. |
| QR replay/copy | PARTIAL | V2 QR payload is static and signed; a signature detects alteration, not copying or replay. Full threat model remains outstanding. |

## Migration guard tests — run only in a disposable/staging database

Use synthetic schools, issuer keys, staff memberships and JWT subjects. Never use live school records.

### Trusted issuer tests
- Insert a new key as an authorized Admin: succeeds.
- Update `public_key_base64` under an existing `key_id`: rejected.
- Change `key_id`, `school_id`, label or enrollment timestamp: rejected.
- Revoke an active key (`false -> true`): succeeds for authorized Admin.
- Reactivate a revoked key (`true -> false`): rejected.
- Modify any field on a revoked key: rejected.
- Non-admin issuer enrollment/revocation: rejected by RLS.

### Finance membership tests
- Ordinary Admin changes `user_id` on finance-enabled membership: rejected.
- Ordinary Admin changes `is_active` on finance-enabled membership: rejected.
- Ordinary Admin changes protected finance flag, role or school: rejected.
- Finance-authorized Admin changes another finance-enabled membership: allowed only where RLS and the school recovery policy permit.
- Ordinary Admin attempts to grant finance to self: rejected.
- Cross-school transfer without finance authorization in both school contexts: rejected.
- Trusted SQL recovery path (`auth.uid() IS NULL`) is documented and restricted to owner-controlled database administration.

### Finance default tests
- Insert into `student_finances` without `fee_status`: rejected by NOT NULL, rather than silently becoming CLEARED.
- Insert with explicit permitted `OUTSTANDING` or `CLEARED`: subject to RLS and finance authorization.

## Attendance and examination write-path findings

- `AttendanceRecordDao.insertOrUpdate` and `ExamExceptionDao.insertOrUpdate` use `OnConflictStrategy.REPLACE`.
- `SupabaseCloudBackend.pushAttendanceRecords` upserts on `school_id,student_id,lesson_date,session_name`.
- `SupabaseCloudBackend.pushExamExceptions` upserts on `school_id,student_id,exam_code`.

These paths overwrite the existing natural-key record. A correction/revision schema is required before replacing them with append-only history. Do not simply switch to INSERT/ABORT: legitimate corrections would become impossible and offline sync would fail.

## Requirements checklist
All seven Boolean defaults currently evaluate to complete. Changing these to false alone is only a fail-closed interim measure; it cannot distinguish UNKNOWN, REQUIRED, WAIVED and NOT_APPLICABLE. A proper status model and migration must be designed with the requirements UI and persistence/sync path together.

## Audit-event system
A schema-only audit table is not sufficient. Events must be emitted transactionally from authoritative database write paths, with narrowly scoped payloads and immutable RLS/trigger protections. Complete the write-path inventory and event taxonomy before enabling broad row capture.

## QR threat model
A valid ECDSA signature establishes that a trusted issuer signed the payload. It does not prove the presenter is the student, that the QR has not been photographed, or that it has not already been used. A static QR can be copied and replayed. Countermeasures must be evaluated against offline operation, device compromise, card replacement, revocation latency and school workflow.

## Validation boundary
- Migration is committed to a feature branch only.
- No live Supabase migration has been applied.
- No production secrets or issuer keys have been changed.
- CI and staging database tests must be run before any merge recommendation.
