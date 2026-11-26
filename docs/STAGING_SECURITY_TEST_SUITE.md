# Scholink staging security and audit acceptance suite

**Status:** test specification and SQL harness draft; not executed.  
**Target:** PR #72 + PR #73 integration branch.  
**Environment:** disposable Supabase staging project only. Never run against production.  
**Data:** synthetic schools, users, memberships, students, cards and issuer keys only.

This suite deliberately separates tests that should pass from a known policy gap that should fail until issuer enrollment is moved behind a stronger approval boundary.

## 0. Preconditions — hard stop

Do not run until all of these are true:

1. The missing initial schema migration `20261111131447` has been recovered from a trusted source and checked into the repository.
2. The migration history is canonical: one file per timestamp/path, no divergent copies of `20261124130000_security_integrity_guardrails.sql`, and the finance guard has one authoritative definition.
3. A brand-new disposable database can apply the combined 24-migration chain in timestamp order without manual intervention. This replay has passed in the disposable local Supabase CI stack.
4. The staging schema matches the migration assumptions: `schools.id`, `trusted_issuers`, `staff_memberships`, `student_finances`, `audit_events`, and every audit-trigger source table have been inspected.
5. The operator has a staging-only SQL connection capable of `SET ROLE authenticated`. Do not place service-role keys or passwords in this file or commit them.
6. The fixture IDs below refer only to synthetic staging records and are visible to the intended test roles.

If any precondition is false, stop and report it. Do not reconstruct the baseline migration from memory or apply guessed SQL.

## 1. Synthetic fixture contract

Create two synthetic schools, `school_a` and `school_b`, and these users:

- `ordinary_admin_a`: active ADMIN in school A, no finance access.
- `finance_admin_a`: active ADMIN in school A, with finance access.
- `ordinary_staff_a`: active non-admin staff member in school A.
- `admin_b`: active ADMIN in school B.
- `target_user`: a synthetic auth user not currently owning the finance-enabled membership.

Seed one finance-enabled membership in school A, one active issuer key in each school, and at least one synthetic student row in each school. Keep the fixture records between test cases; each test below runs inside a transaction and rolls back its own changes.

Set the following session variables to the corresponding synthetic IDs before each test. Replace the example values; do not commit real IDs.

```sql
SELECT set_config('test.school_a', '<school-a-id>', false);
SELECT set_config('test.school_b', '<school-b-id>', false);
SELECT set_config('test.ordinary_admin_a', '<ordinary-admin-a-auth-uid>', false);
SELECT set_config('test.finance_admin_a', '<finance-admin-a-auth-uid>', false);
SELECT set_config('test.ordinary_staff_a', '<ordinary-staff-a-auth-uid>', false);
SELECT set_config('test.admin_b', '<admin-b-auth-uid>', false);
SELECT set_config('test.finance_membership_user', '<current-owner-auth-uid>', false);
SELECT set_config('test.target_user', '<target-auth-uid>', false);
SELECT set_config('test.issuer_key_id', '<active-issuer-key-id>', false);
SELECT set_config('test.student_id', '<synthetic-student-id>', false);
```

For each authenticated test, set both JWT claim formats before `SET LOCAL ROLE authenticated`:

```sql
SELECT set_config(
  'request.jwt.claims',
  jsonb_build_object(
    'sub', current_setting('test.ordinary_admin_a'),
    'role', 'authenticated'
  )::text,
  true
);
SELECT set_config(
  'request.jwt.claim.sub',
  current_setting('test.ordinary_admin_a'),
  true
);
SET LOCAL ROLE authenticated;
```

Use the relevant user ID in place of `test.ordinary_admin_a` for each scenario. The test connection must be the staging database owner so it can change role and inspect fixtures. Run the complete test file inside an outer transaction and finish with `ROLLBACK`.

## 2. Finance membership guard

### FIN-01 — ordinary Admin cannot grant finance access to self

Authenticate as `ordinary_admin_a`. Confirm the fixture membership is visible, then attempt:

```sql
UPDATE public.staff_memberships
SET has_finance_access = true
WHERE school_id = current_setting('test.school_a')
  AND user_id = current_setting('test.ordinary_admin_a')::uuid;
```

**Expected:** rejected by the finance guard. A zero-row update is not a pass; it means the fixture or RLS visibility is wrong.

### FIN-02 — ordinary Admin cannot transfer a finance-enabled membership

Authenticate as `ordinary_admin_a`. The finance-enabled fixture membership must be visible to this caller under the intended staff-management RLS policy.

```sql
UPDATE public.staff_memberships
SET user_id = current_setting('test.target_user')::uuid
WHERE school_id = current_setting('test.school_a')
  AND user_id = current_setting('test.finance_membership_user')::uuid
  AND has_finance_access = true;
```

**Expected:** rejected. Also verify the original `user_id` remains unchanged after the rejected statement.

### FIN-03 — ordinary Admin cannot deactivate a finance-enabled membership

Use the same fixture row and caller as FIN-02.

```sql
UPDATE public.staff_memberships
SET is_active = false
WHERE school_id = current_setting('test.school_a')
  AND user_id = current_setting('test.finance_membership_user')::uuid
  AND has_finance_access = true;
```

**Expected:** rejected.

### FIN-04 — ordinary Admin cannot demote or move a finance-enabled membership

Test these independently:

```sql
UPDATE public.staff_memberships SET role = 'GATE_OFFICER'
WHERE school_id = current_setting('test.school_a')
  AND user_id = current_setting('test.finance_membership_user')::uuid
  AND has_finance_access = true;

UPDATE public.staff_memberships SET school_id = current_setting('test.school_b')
WHERE school_id = current_setting('test.school_a')
  AND user_id = current_setting('test.finance_membership_user')::uuid
  AND has_finance_access = true;
```

**Expected:** both rejected. The second test must not leave a cross-school membership.

### FIN-05 — finance-authorized Admin can perform an approved finance-membership change

Authenticate as `finance_admin_a`. Perform a controlled change on a disposable finance membership, verify the expected result, and roll back.

**Expected:** allowed only when the existing RLS policy and the guard both authorize the operation. Record whether the school recovery policy permits self-deactivation or transfer; do not silently assume it.

### FIN-06 — finance status has no implicit CLEARED default

As an authorized synthetic finance writer, attempt an insert that omits `fee_status` while satisfying all other required columns.

**Expected:** rejected by NOT NULL. Then insert a second synthetic row with an explicit permitted status and verify it succeeds under the correct authorization.

Do not use a real student's finance row for this test.

## 3. Issuer key integrity and enrollment authority

Authenticate as `ordinary_admin_a` unless otherwise stated.

### ISS-01 — enrolled public key cannot be replaced

```sql
UPDATE public.trusted_issuers
SET public_key_base64 = public_key_base64 || 'A'
WHERE school_id = current_setting('test.school_a')
  AND key_id = current_setting('test.issuer_key_id');
```

**Expected:** rejected by the immutability trigger. Confirm the stored key is unchanged.

### ISS-02 — issuer identity metadata cannot be reassigned

Independently attempt to change `key_id`, `school_id`, `label`, and `enrolled_at_ms` on the fixture key.

**Expected:** every update is rejected.

### ISS-03 — revocation is one-way

Authenticate as an authorized Admin and revoke the active synthetic key:

```sql
UPDATE public.trusted_issuers
SET is_revoked = true
WHERE school_id = current_setting('test.school_a')
  AND key_id = current_setting('test.issuer_key_id')
  AND is_revoked = false;
```

**Expected:** one row updated. Repeating the same `true` value is idempotent. Attempting `is_revoked = false` afterward must be rejected. All changes are rolled back at the end of the test transaction.

### ISS-04 — ordinary staff cannot enroll or revoke issuer keys

Authenticate as `ordinary_staff_a`. Both enrollment and revocation must be rejected by RLS.

### ISS-05 — issuer enrollment must not be a unilateral ordinary-Admin action

Authenticate as `ordinary_admin_a` and attempt to insert a second synthetic key for school A.

**Expected:** rejected for ordinary Admins and ordinary staff. The current staging security migration revokes client INSERT privileges on trusted issuer tables, and the app-side enrollment UI fails closed.

**Remaining blocker:** there is not yet a supported server-authoritative issuer-enrollment workflow with a separately approved authority (for example, a distinct issuer role or a two-person approval path). Do not mark issuer onboarding complete merely because client enrollment is blocked; the approved enrollment path and its audit attribution still need to be implemented and tested.

### ISS-06 — revocation recovery exercise

In staging only, revoke the active test key, enroll a replacement through the approved authority, distribute the new public key to verifier devices, verify old-key rejection and new-key acceptance, and document the card re-issuance procedure. Confirm that a single compromised Admin cannot silently replace the trust root.

## 4. Audit event capture and access control

Use a synthetic student in school A. Authenticate as an authorized Admin in school A.

### AUD-01 — one business mutation creates exactly one audit event

Record the current event count for the fixture student, update one allowlisted field (for example, `is_active`) on the synthetic student, then verify exactly one new event with:

- `entity_type = 'students'`;
- the correct `entity_id`;
- `school_id = school_a`;
- `actor_user_id = ordinary_admin_a`;
- `event_type = 'students.update'`;
- the expected changed field.

Do not use a field outside the allowlist; the migration intentionally ignores changes that do not alter an allowlisted snapshot field.

### AUD-02 — no event for a non-allowlisted-only update

Update a field deliberately excluded from the safe snapshot, such as a synthetic student's free-text notes, if that column exists.

**Expected:** no new event. This confirms the migration does not create noisy or privacy-invasive snapshots for excluded fields.

### AUD-03 — failed business write produces no event

Attempt an invalid business mutation that fails a known constraint. Verify both that the business row is unchanged and that no corresponding audit event exists.

### AUD-04 — school isolation

Authenticate as `admin_b` and query:

```sql
SELECT count(*)
FROM public.audit_events
WHERE school_id = current_setting('test.school_a');
```

**Expected:** zero rows visible. Authenticate as school A leadership and confirm school A events are visible. Authenticate as ordinary staff and confirm audit events are not visible.

### AUD-05 — clients cannot insert, update or delete audit events

As `authenticated`, attempt each operation against a known synthetic audit-event ID.

**Expected:** INSERT denied by table privileges; UPDATE and DELETE denied by privileges/RLS. A zero-row result alone is not sufficient evidence—record the actual SQL error or privilege denial.

### AUD-06 — audit snapshots exclude sensitive data

Inspect the before/after JSON for synthetic student, card, finance and issuer events.

**Expected:** no student names, phone numbers, guardian details, QR payloads, issuer public keys, payment amounts, or free-text notes/reasons. Finance events may include fee-status transitions, not balances or payment amounts.

### AUD-07 — delete events retain the source identifiers

Delete a disposable synthetic row from a covered table inside the test transaction. Verify the event retains its school and entity identifiers even after the source row is gone.

### AUD-08 — trigger coverage is complete

Verify a `capture_school_audit_event` trigger exists on every intended table:

- `students`
- `cards`
- `staff_memberships`
- `student_finances`
- `gate_logs`
- `meals`
- `attendance_records`
- `exam_exceptions`
- `trusted_issuers`

The migration currently skips a missing table rather than failing. The test must therefore fail if any expected table is absent or lacks the trigger. Confirm each table has a usable `school_id` and a stable entity identifier; do not accept `entity_id = 'unknown'`.

## 5. Audit immutability and atomic rollback

These two checks require the staging database owner connection. They are not run as an ordinary authenticated client.

### AUD-09 — privileged UPDATE and DELETE are rejected

Select a synthetic audit event ID. As the database owner, attempt to update and delete it.

**Expected:** the `prevent_audit_event_mutation` trigger raises an exception for both operations. This test is important because client privilege tests alone do not prove the trigger itself works.

### AUD-10 — audit failure rolls back the business write

In a disposable transaction, install a temporary test-only BEFORE INSERT trigger on `public.audit_events` that deliberately raises an exception. Attempt to update an allowlisted field on the synthetic student.

**Expected:** the business update fails and the student's original value remains unchanged. Remove the temporary trigger and roll back the test transaction. Do not perform this test on production or on a shared staging environment used by other testers.

## 6. Current execution record

- Clean replay of the combined 23-migration chain: PASS in disposable local Supabase CI.
- Issuer lifecycle staging checks: PASS for revoked-key tombstones, enrollment/revocation audit events, immutable lifecycle history, blocked physical deletion, blocked direct student school reassignment and restricted authenticated-role privileges. Synthetic writes were rolled back.
- Finance membership checks: PASS for ordinary-Admin self-grant, enrollment, transfer, deactivation, demotion and school-move rejection; PASS for finance-Admin transfer and deactivation of another finance membership.
- Finance fee-status sync: the original nested trigger path failed. The proposed internal-context fix passed a staging transaction dry-run: OUTSTANDING/CLEARED changes synchronized, the internal flag reset, and direct non-finance edits remained blocked. The dry-run was rolled back; no migration was persisted to staging.
- Finance policy decision: the current guard permits the sole finance-authorized Admin to deactivate themselves. The school must define a recovery/second-approver rule before accepting this behavior.
- Audit checks AUD-01 through AUD-10: PASS for synthetic student event capture and actor attribution, PII-safe snapshots, non-allowlisted-field suppression, failed-write suppression, school isolation, ordinary-staff denial, client write denial, delete-event identity retention, append-only enforcement and business-write rollback when audit insertion fails. Trigger coverage was verified on nine protected tables; full event-capture exercises for every table remain outstanding.
- Issuer enrollment approval path and revocation recovery exercise: BLOCKED pending server-authoritative enrollment implementation.

The overall suite remains **NOT GREEN** until every applicable test has a recorded PASS, FAIL or BLOCKED result.

## 7. Required result record

For each test, record:

| Field | Required value |
|---|---|
| Test ID | FIN-01 through AUD-10 |
| Migration set | Exact ordered migration commit |
| Database | Staging project identifier only |
| Fixture | Synthetic fixture label, not a real person's name |
| Caller | Synthetic auth role/user label |
| Expected | Pass/fail condition above |
| Actual | SQL result/error and row count |
| Evidence | Sanitized output; no tokens, passwords, keys or personal data |
| Status | PASS / FAIL / BLOCKED |
| Defect | File, function, policy or migration requiring correction |

A test suite is not green if a test is skipped, returns zero rows because the fixture is invisible, or is blocked by a missing baseline. Report those as BLOCKED, not PASS.

## 8. Explicit exclusions

This suite does not validate Android offline behavior, Room encryption, lost-device revocation, app-side import/export events, failed authentication telemetry, retention policy, or legal/data-protection compliance. Those require separate acceptance work.

It also does not prove that a static QR cannot be copied or replayed. The QR threat model correctly treats cross-device offline replay as a residual risk; test online duplicate-event handling and document offline limitations rather than claiming anti-replay protection.
