# Scholink Release Readiness Status

**Verification date:** 2026-11-26  
**Status:** Not production-ready; security and migration work is staged in draft pull requests.

## Integration stack

The intended dependency order is:

1. **PR #76 — canonical migration recovery:** 19 migrations, including the supplemental RLS event-trigger prerequisite.
2. **PR #80 — finance/security integrity and append-only audit:** 21 migrations.
3. **PR #81 — issuer lifecycle and fail-closed app protections:** 23 migrations.
4. **PR #79 — finance fee-status synchronization guard:** 24 migrations.

All four PRs are draft. No PR has been merged. The branches are directly stacked, with each child branch based on its intended parent. The 19-, 21-, 23- and 24-migration clean-replay jobs have passed in disposable local Supabase runs; the latest rebased CI runs are being repeated. Targeted PostgreSQL integration tests for issuer lifecycle, school-transfer blocking and finance fee-status synchronization have passed.

## Validated in disposable staging

Synthetic transactions and identities were used. Test writes were rolled back.

- Revoked issuer keys cannot be reactivated; issuer identity and public-key material are immutable; lifecycle events are append-only.
- Direct student school reassignment is blocked.
- Ordinary Admins cannot self-grant or alter finance membership; finance-authorized Admins can transfer or deactivate another finance membership.
- Audit event actor attribution, school isolation, PII-safe snapshots, client write denial, append-only enforcement and rollback-on-audit-failure were tested.
- Finance fee-status changes now synchronize through the nested trigger path. Direct non-finance edits remain blocked. A forced downstream failure rolls back the finance change and restores the internal transaction flag.

## Remaining production blockers

### 1. Issuer enrollment and school transfers — partially mitigated

The current implementation fails closed: issuer enrollment remains restricted until a server-authoritative approval path is implemented, and direct cross-school reassignment is blocked. The approved enrollment and audited transfer workflows are not yet implemented or school-approved.

### 2. Server-authoritative synchronization — still open

The fee-status nested-trigger defect is fixed. The wider sync design still needs a server-generated version/cursor and deterministic stale-write conflict handling; client-clock timestamps remain in parts of the sync path.

### 3. Attendance and examination corrections — still open

The append-only school audit event stream exists. It does not yet replace mutable attendance/exam records with explicit append-only correction records and reasoned approvals.

### 4. Student requirements — interim fail-closed state only

The seven Boolean defaults are false and inferred completion/bulk-clear behavior has been removed. The required persistent, school/academic-year/term-scoped five-state model is not implemented:

- UNKNOWN
- REQUIRED
- COMPLETE
- WAIVED
- NOT_APPLICABLE

The replacement still needs actor, timestamp, evidence reference, correction reason, authorized waiver handling, Room persistence, Supabase synchronization and audit coverage.

### 5. Local PII recovery and device acceptance — still open

Local PII protection, encrypted backup/restore and recovery acceptance are not complete. No physical Android 11/16 acceptance test has been performed on a real device in this work.

### 6. School policy acceptance — still open

The current finance guard permits the sole finance-authorized Admin to deactivate their own membership. The school must decide whether this is allowed and define a second-approver or recovery process. Gate movement and meal-serving policies also remain fail-closed pending formal school approval.

## Environment safety

- Production Supabase project was not modified.
- Staging changes were limited to the previously authorized security migrations and synthetic validation.
- Synthetic test transactions were rolled back; no CI validation identities or rows remained. The pre-existing synthetic staging baseline records were left intact.
- No paid services were created.
- No PR was merged.

## Release decision

**Do not release or deploy to production yet.** Passing migration replay and targeted database tests are necessary gates, not proof of full operational acceptance. Release requires the remaining blockers above, green application CI, approved school policies, local PII recovery testing and physical-device acceptance.
