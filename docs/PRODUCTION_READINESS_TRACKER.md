# Scholink Production Readiness Tracker

**Current release status: NOT APPROVED FOR PRODUCTION OR LIVE STUDENT DATA.**

This tracker distinguishes code changes visible in GitHub from external configuration, school decisions and physical-device evidence. A source-code change does not count as a production pass until the relevant test evidence is attached.

## Working branches

- PR #62: scholink/v1-clean-modern-fixes
- PR #64: feat/scholink-production-readiness (stacked on PR #62)
- PR #65: fix/scholink-production-gates (based on PR #64)
- PR #67: fix/ci-event-concurrency (based on PR #65)
- PR #68: fix/finance-membership-guard-complete (based on PR #65)
- PR #69: docs/offline-session-safety (based on PR #65)
- PR #70: fix/gate-policy-pending-both-directions (based on PR #65)
- PR #66: docs/ltc-pilot-plan (retargeted to PR #67)

Merge in dependency order; reconcile follow-up branches after PR #65. Do not merge around failed required checks.

## Gate 0 — school and product decisions

| Item | Status | Evidence / next action |
|---|---|---|
| Written entry/exit/boarder/exeat/override/lost-card policy | BLOCKED — school decision | Complete and sign GATE_POLICY_DECISION_RECORD.md with Head Teacher, Bursar and Gate lead. |
| Final Android applicationId | BLOCKED — owner decision | Current value is com.aistudio.studentaccess.xpkz; choose the permanent identifier before first production install. |
| Supabase plan, region, backup and recovery target | PARTIALLY VERIFIED | Live project is in eu-central-1 on PostgreSQL 17 and is ACTIVE_HEALTHY; plan/billing status is not exposed. Verify plan in dashboard, confirm backups, and test off-site restore before choosing any paid upgrade. |
| Accountable data owner and backup admin | BLOCKED — school decision | Name both and define access recovery. |

## Gate 1 — engineering blockers

| Audit item | Current assessment | Required closure evidence |
|---|---|---|
| Gate/meal upsert UPDATE permission mismatch | PARTIALLY ADDRESSED in PR #62 by append-only/idempotent writes | Authenticated real-backend insert-twice tests; prove no UPDATE grant is needed. |
| Finance capability privilege escalation | CODE CHANGE PRESENT in PR #62; extended in PR #68 | PR #62 protects finance capability, role and school changes; PR #68 additionally protects user_id and is_active. Neither guard is applied to live DB. Test grant, revoke, identity transfer, deactivation and role/school changes with authenticated synthetic accounts in staging. |
| Canonical Supabase baseline schema and RLS | REPOSITORY DRIFT CONFIRMED | Live migration history includes the initial schema migration 20261111131447, but that migration file is absent from current main and PR #64. Restore the exact original migration from source/history; do not reconstruct it by guesswork. Live RLS is enabled on all inspected public tables; role-specific policy testing remains open. |
| Static QR bearer token / anti-passback | OPEN | Threat model and approved design for replay, duplicate scans and concurrent devices. |
| Issuer key storage, trust and signing controls | PARTIALLY ADDRESSED in PR #62 | Review SharedPreferences trust persistence, require protected key operations, define secure enrollment, revocation and rotation; add tests. |
| Offline session restore | OPEN | Test reboot without network; define bounded offline session policy and revocation freshness. |
| Production environment staff passwords and signing keys | AUDIT-REPORTED; NOT INDEPENDENTLY VERIFIED | Inspect GitHub environment settings directly; rotate exposed credentials; enforce reviewer protection. |
| Firebase key in Git history / old project | AUDIT-REPORTED; NOT INDEPENDENTLY VERIFIED | Restrict/rotate key, audit Firebase rules and project usage, then disable or delete the obsolete project after confirming no dependency. |
| Gate direction, boarders and exeat policy | BLOCKED — school policy; safe interim behavior in PR #70 | PR #70 keeps both ENTRY and EXIT as POLICY_PENDING, records neither as approved, and uses an explicit amber result. Final direction-aware evaluator and exeat rules remain blocked until LTC signs its policy. |
| Meal eligibility and serving policy | BLOCKED — school decision; safe interim behavior in PR #71 | PR #71 removes inferred day-scholar/boarder entitlements and station-name heuristics. No meal is recorded as served until meal periods, eligibility, exceptions and fallback are approved. |
| Examination admission policy | BLOCKED — school decision; safe interim behavior in PR #71 | PR #71 removes automatic clearance based only on fee status or an exception record. Admission remains pending until requirements, authority and exceptions are approved. |
| Roster import | IMPLEMENTED IN PR #64; CI NOT GREEN | CSV/XLSX parser, preview and batch import exist; fix CI test failures and complete dry-run/idempotency/second-person approval tests. |
| Manual gate fallback | PRESENT IN PR #64; VERIFY | Confirm lookup scope, mandatory reason, audit-log integrity, privacy controls and real-device usability. |

## Gate 2 — pilot readiness

| Item | Current assessment | Required closure evidence |
|---|---|---|
| Two-phone sync and card issuance | NOT RUN | Complete DEVICE_ACCEPTANCE_TESTS.md on two physical Android phones. |
| Offline reboot and session restoration | NOT RUN | Attach test record for fresh, stale and revoked sessions with network unavailable. |
| Production-signed APK and in-place update | NOT DONE | Permanent owner-controlled signing key, certificate fingerprint, signed APK verification and upgrade test. |
| Crash reporting / remote diagnostics | OPEN | Select a provider and configure PII minimisation; test a synthetic crash and access controls. |
| Lost/stolen device response | OPEN | Define device revocation, local data protection/wipe, re-enrolment and incident procedure. |
| Privacy, retention, correction, export and incident response | OPEN | Obtain qualified Ugandan data-protection advice and school sign-off; record regulator obligations and operational procedures. |
| Synthetic dry run and supervised pilot | NOT RUN | Start only after all critical gates pass; use a small, supervised cohort and rollback plan. |

## Live Supabase observations (2026-11-23)\n\nSee LIVE_SUPABASE_AUDIT_2026-11-23.md for the read-only evidence. Highlights: RLS is enabled on all inspected public tables; authenticated gate_logs/meals grants are SELECT+INSERT only; the finance-membership guard is not yet applied; leaked-password protection is disabled; and the live initial schema migration is missing from the repository migration chain. The live student-finance fee-status default is CLEARED while the student roster default is OUTSTANDING, which requires review.\n\n## Gate 3 — hardening before expansion

- Anti-passback and campus occupancy view.
- Issuer key biometric / hardware-backed access, rotation and recovery.
- Remove unused dependencies only after source and runtime usage verification.
- Add Dependabot or equivalent dependency vulnerability monitoring.
- Enable R8 and test release minification.
- Add MFA for privileged accounts.
- Split oversized UI/view-model components after correctness and security gates.
- Add other modules only after gate operation is stable.

## CI status (2026-11-24)

- Earlier run 45059800590: debug/release compilation passed; unit tests failed during Robolectric SDK artifact acquisition (MavenArtifactFetcher / java.io.IOException); emulator tests were skipped. This does not establish application test failures or passes.
- PR #67 separates push and pull_request concurrency groups. Its latest observed runs were still in progress at unit tests; no complete result was available at last check.
- PR #68 and PR #70 require their own completed CI runs; do not infer success from earlier compilation.
- Physical devices: NOT TESTED.

Do not describe the build as production-ready until the required CI, backend, security, policy, signing and physical-device evidence is complete.
