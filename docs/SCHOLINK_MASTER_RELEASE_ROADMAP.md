# Scholink Master Release Roadmap

**Project:** Scholink — Lira Town College pilot  
**Prepared:** 2026-11-28  
**Status:** Planning baseline; not a production-readiness certificate  
**Scope:** Android client, Supabase backend, school operations, privacy, pilot operations and commercial readiness.

## 1. Purpose and product boundary

Scholink's first release is a school-authorised student identity and gate-verification system. A staff member scans a student's printed QR card and receives a policy-governed result with an auditable event record. Students do not sign in.

The initial release is deliberately narrower than a full school-management system:

- Staff authentication and role-authorised access.
- Student registry, controlled roster import and student-photo handling.
- Signed QR ID issuance, verification, revocation and lost-card handling.
- Approved gate entry/exit workflow, local event persistence and synchronisation.
- Auditability, recovery and school-approved manual fallback.

Meals, attendance, examinations and finance remain separate capabilities. They should not delay a reliable gate release. Parent/student portals, school-fee collection and anti-screenshot QR claims are outside V1.

The school owns its movement and safeguarding rules. Scholink must implement formally approved rules; it must not infer fee, boarding, transport, attendance or examination policy.

## 2. Evidence baseline and limits

This plan is grounded in repository state and CI evidence available on 2026-11-28. It is not a complete independent code review of every Kotlin file, SQL policy or legal instrument.

### Repository references

- `app/build.gradle.kts` — Android namespace and application ID.
- `gradle/libs.versions.toml` and module Gradle files — declared dependency versions; not a resolved dependency/licence inventory.
- `scripts/supabase-real-token-smoke.sh` — authenticated staging role-boundary checks.
- `scripts/db-test/` — synthetic PostgreSQL integration fixture and assertions.
- `supabase/migrations/` — canonical database migration chain.
- `docs/OFFLINE_STAFF_SESSION_SAFETY.md` — current offline staff re-unlock limitation and manual fallback.
- `docs/SCHOLINK_SUBSCRIPTION_CONTINUITY_POLICY.md` on PR #87 — proposed subscription continuity policy, documentation only.
- `docs/PRODUCTION_RELEASE_AND_SIGNING.md` — release/signing requirements.
- `docs/DEVICE_ACCEPTANCE_TESTS.md` — device acceptance baseline.
- `.github/workflows/` — CI and release workflows.

### Verified CI snapshot

GitHub Actions run **46116070656** (PR #86 validation, 2026-11-28):

| Job | Result |
|---|---|
| Android compile and unit tests | PASS |
| Canonical Supabase migration replay from empty database | PASS |
| PostgreSQL migration integration tests | PASS |
| Authenticated Supabase role-boundary tests against staging | FAIL |

The authenticated staging job passed Admin, Head Teacher and Gate Officer checks, then exited non-zero during the Meal Staff path. The available log did not identify a sufficiently clear final assertion to diagnose the cause. Do not guess at the defect or weaken the test. Capture the exact failing command, HTTP response/body and expected result in a follow-up run.

This is a point-in-time result. Recheck the latest run before using this document as a release status report.

### Explicitly unverified

- End-to-end QR issuance and scanning on a representative set of physical phones.
- Complete real-device acceptance on Android 11 and Android 16.
- Secure staff re-unlock after reboot without network connectivity.
- Full local-data backup, restore and lost-device recovery.
- Complete resolved dependency and transitive licence inventory.
- Final production signing and upgrade test.
- Formal LTC policy approval, data-processing agreement and privacy/legal review.
- Production readiness of any module not explicitly tested.

## 3. Release gates and priority

Use the following order. A later phase does not override an earlier failed gate.

### Gate 0 — Scope and authority

**Deliverables**
- Signed school approval naming the authorised school decision-makers.
- Written gate movement, safeguarding and exception policy.
- Named data owner/controller and Scholink processor responsibilities.
- Approved pilot scope, staff list, device list and support contacts.
- Explicit approval before importing real student data or photographs.

**Acceptance**
- No real student data is loaded until the school has approved the processing and the operational rules in writing.
- The app's decision labels and access rules map to an approved school policy.
- Unresolved policy choices are visibly marked as unresolved, not silently hard-coded.

### Gate 1 — Release-candidate security and CI

**Priority: release blocker**

1. Diagnose the failing Meal Staff staging role test without changing the intended permission contract.
2. Preserve real Auth-token tests for all four current V1 roles: ADMIN, HEAD_TEACHER, GATE_OFFICER and MEAL_STAFF.
3. Prove tenant isolation for reads and writes, not only filtered SELECT visibility.
4. Prove role-specific access to student private data and photos.
5. Prove finance data is isolated to explicitly finance-authorised staff; the current synthetic fixture has previously documented gaps in finance-membership and finance-row isolation coverage.
6. Prove issuer enrolment, rotation and revocation fail closed; a revoked issuer must not be silently reactivated or re-enrolled.
7. Prove append-only audit events, actor attribution and cross-school audit isolation.
8. Verify idempotent gate/meal sync and duplicate/retry handling.
9. Replay the complete canonical migration chain against an empty disposable database.
10. Keep staging isolated from production; never run CI destructive tests against production.

**Acceptance**
- All required CI jobs pass on the same PR head.
- No security test is skipped, stubbed or interpreted as a pass because the endpoint returned an error.
- Every negative authorization test has a positive control proving the permitted role can perform the intended operation.
- Test reports preserve HTTP status, response body and relevant request details without exposing credentials or student data.

**Known test boundary**
The current CI snapshot shows Android unit tests, canonical migration replay and PostgreSQL integration tests passing. The authenticated staging role suite is not green. Finance row-isolation and cross-school audit/actor-attribution gaps have been explicitly recorded in the PR #86 review notes and must be closed with fixtures matching the canonical schema.

### Gate 2 — Gate V1 functional completeness

**Priority: release blocker**

- Staff login, logout and session expiry.
- Role-specific navigation and least-privilege controls.
- Student create, view, update, deactivate and approved deletion lifecycle.
- CSV import with validation, preview, duplicate detection and error report.
- Student photo capture, private upload, download and replacement rules.
- Signed QR card issuance and verification using the approved issuer trust model.
- Card revocation, replacement and lost-card workflow.
- Gate entry/exit decision and event recording.
- Damaged/unreadable card exception workflow defined by the school.
- Durable local persistence before showing a successful event result.
- Retry-safe, idempotent synchronisation with visible sync status.
- Audit trail for consequential actions.

**Acceptance**
- A school administrator can import a test roster and issue cards.
- A Gate Officer can scan valid, invalid, expired/revoked and damaged-card cases and receives unambiguous results.
- Events survive app closure and network interruption without silent loss.
- Duplicate retries do not create duplicate gate events.
- The app never presents a locally cached student record as proof that the staff operator is authorised.

### Gate 3 — Offline continuity and recovery

**Priority: release blocker for any claim of fully offline staff operation**

The current documented behaviour is that staff membership is revalidated online after app/device restart. Cached role data alone is not sufficient to unlock staff functionality. If the device cannot revalidate staff, the current documented fallback is the school's manual/paper gate register.

Do not bypass this control to make the product appear offline-ready.

**Implementation sequence**
1. Define which minimum Gate Officer actions may be performed offline.
2. Issue a server-authorised, device-bound, time-bounded offline lease.
3. Protect lease material using Android Keystore; do not store reusable bearer credentials in ordinary preferences or Room.
4. Define lease expiry, policy freshness, revocation propagation and maximum offline duration.
5. Define reboot, clock rollback, device restore and app-data-clearing behaviour.
6. Persist signed/attributable local gate events with stable idempotency identifiers.
7. Reconcile server responses and conflicts without silently overwriting audit history.
8. Define lost/stolen device revocation and staff reassignment procedures.
9. Retain manual fallback when lease validity or device integrity cannot be established.

**Acceptance tests**
- Airplane mode after a valid online authorisation.
- App process death and device reboot.
- Expired lease.
- Staff membership revoked before and during offline period.
- Device revoked or replaced.
- System clock rollback/forward.
- Policy/key rotation while device is offline.
- Reconnect with queued events and repeat uploads.
- Storage exhaustion and interrupted writes.

Do not claim offline staff re-unlock is supported until these tests pass on real devices and the school approves the operating procedure.

### Gate 4 — Device UX, performance and accessibility

**Priority: release blocker**

Test on physical low/mid-range Android phones representative of the school, not only an emulator.

- Android 11 baseline and current supported Android versions, including Android 16 where available.
- Camera permission denied, revoked and permanently denied.
- Low light, glare, damaged cards and camera focus.
- Large text, small screens and display scaling.
- Fast repeated scans at gate rush.
- Weak/intermittent mobile data and Wi-Fi.
- App backgrounding, process death and low storage.
- Battery and thermal behaviour during sustained scanning.

**Acceptance**
- Primary scan action is immediately discoverable.
- Verification states are readable at a glance and do not rely on colour alone.
- No duplicate event is created by repeated taps or camera callbacks.
- A staff member can recover from a failed scan without navigating through unrelated screens.
- No critical crash, ANR, data loss or unhandled permission dead end.

### Gate 5 — Data protection, legal and school governance

**Priority: before real student data / pilot**

Obtain qualified Uganda-specific legal review. This document is an engineering and business checklist, not legal advice or a statement that every listed legal obligation applies in a particular way.

Verify against current official sources and counsel:
- Data Protection and Privacy Act 2019 and applicable regulations.
- Whether the school and Scholink require PDPO registration and in which capacity.
- Lawful basis and safeguards for children's personal data, photographs and guardian details.
- Controller/processor responsibilities and a written Data Processing Agreement.
- Cross-border processing and hosting conditions for Supabase and other subprocessors.
- Privacy notice, retention/deletion schedule and data-subject request handling.
- Security incident response, escalation and applicable notification deadlines.
- Subprocessor inventory, access controls and contractual terms.
- School authority to approve the pilot and share the selected student data.
- Data export, return and deletion at pilot termination.

**Minimum operational documents**
- Pilot MoU/service agreement.
- Data Processing Agreement.
- Privacy notice for the school-approved processing.
- Access-control and staff onboarding/offboarding procedure.
- Data retention and deletion schedule.
- Incident/breach response plan.
- Backup, restoration and business continuity procedure.
- Subprocessor register.
- School policy and exception approval record.

Do not upload identifiable student data, photos, guardian details or real access records into public AI tools, personal chat accounts or unapproved test environments.

### Gate 6 — Production build and operational readiness

**Priority: before production distribution**

- Choose a permanent Android namespace and application ID. The current configuration has a mismatch: Kotlin namespace `com.example`, application ID `com.aistudio.studentaccess.xpkz`.
- Do not use a domain-based package root implying domain ownership unless the organisation controls that domain. Domain ownership is not a technical prerequisite for a syntactically valid Android application ID.
- Update manifests, provider authorities, release checks and documentation consistently.
- Produce a resolved dependency inventory, including transitive runtime, test and build-plugin dependencies.
- Record SPDX/licence metadata and manually review unknown/proprietary components. Do not infer licences from dependency names or unused version-catalog entries.
- Review ML Kit's applicable Google SDK terms separately from open-source dependency licences.
- Remove unused dependencies only after confirming no transitive or build-time use.
- Securely generate and back up the permanent signing key; restrict access and document recovery.
- Build a signed release APK and verify its signature.
- Test fresh install, upgrade, app data migration and rollback/recovery.
- Verify Supabase production configuration is distinct from staging and contains no test credentials.
- Set up monitoring, support contacts, incident escalation and release rollback instructions.

**Acceptance**
- A reproducible release build is signed with the intended permanent key.
- Upgrade tests pass on supported Android versions.
- No debug endpoint, test account, service-role secret or staging URL is embedded in the production app.
- Backup and recovery are demonstrated, not merely documented.

### Gate 7 — Controlled LTC pilot

**Pilot shape**
- One school, one gate and a limited authorised staff group.
- One approved roster import and a controlled card-issuance batch.
- A defined pilot start/end date.
- Named technical and school-side incident owners.
- Manual gate register and card exception process available throughout.
- No fee collection or student payment handling by Scholink.

**Measure**
- Median and 95th-percentile scan-to-decision time.
- Valid/invalid/revoked card outcomes.
- False acceptance and false rejection incidents.
- Unreadable/damaged-card rate.
- Duplicate event rate.
- Sync queue age and failed sync rate.
- Staff training time and support requests.
- Device battery/thermal issues.
- Recovery time after connectivity/device failure.

**Acceptance**
- School signs off on the pilot results and known limitations.
- No unresolved critical security, privacy or safety incident.
- Data export and pilot termination/deletion process are demonstrated.
- School provides written permission before any testimonial or case study is published.

### Gate 8 — Post-pilot modules

Only add modules after the gate workflow is stable and school governance has approved each module's data access.

Suggested sequence:
1. Meals and duplicate-serving prevention.
2. Attendance sessions and correction history.
3. Examination clearance exceptions and leadership decisions.
4. Finance views for explicitly authorised finance staff.
5. Reporting and exports.

Teacher and Bursar roles must be treated as proposed until their role definitions, policies, database authorisation and tests are implemented. Do not imply they are part of the four-role V1 baseline.

Each module requires its own data classification, RLS matrix, audit requirements, offline/sync rules, acceptance tests and school approval.

### Gate 9 — Commercial readiness and monetisation

Do not charge for an unproven or unsafe gate product. Validate willingness to pay with schools after the pilot.

Potential revenue lines:
- Setup and onboarding.
- Per-student subscription per term.
- Card printing and replacement.
- Optional support/maintenance agreement.
- Optional reporting or communication modules, with third-party messaging cost disclosed.

Commercial safeguards:
- Written service scope, support hours and service limitations.
- Invoice schedule and payment terms.
- Clear data ownership, exit/export and deletion terms.
- No percentage cut of school fees and no holding school/student funds.
- Use licensed payment providers if payment collection is later introduced.
- Subscription grace and continuity policy must never disable gate safety functions.

PR #87 documents a proposed billing-continuity policy only. Billing state, invoicing, subscription enforcement and premium feature controls have not been implemented by that documentation change.

Before formal monetisation, verify current URSB, URA, PDPO and any applicable local trading/licensing requirements with qualified advisers. Do not rely on remembered tax thresholds or rates.

## 4. Role and permission baseline

The current V1 staging contract tests four roles:

| Role | V1 responsibility | Boundary to prove |
|---|---|---|
| ADMIN | School configuration, student registry, ID lifecycle and authorised administration | Finance access only when explicit finance capability is granted; no cross-school access |
| HEAD_TEACHER | Leadership review and approved school operations | No unrestricted finance access; only approved leadership actions |
| GATE_OFFICER | Gate verification and gate event recording | No student-finance or academic-record access |
| MEAL_STAFF | Meal serving and meal event recording | No gate administration, finance or academic-record access |

This table is a target summary, not a replacement for the actual SQL policies and Kotlin permission checks. For every table/RPC/storage bucket, maintain an explicit matrix of SELECT/INSERT/UPDATE/DELETE and test both allowed and denied paths.

Teacher and Bursar are not to be assumed implemented in the V1 baseline.

## 5. Billing continuity invariant

Scholink subscription billing is separate from school-owned student fee/access decisions.

A billing state must never:
- Set `schools.is_active` false.
- Deactivate `staff_memberships.is_active`.
- Revoke staff sessions, issuer keys or devices.
- Change student fee/access fields.
- Block gate verification or local gate-event recording.
- Block security-critical sync, revocation, backup, audit access or incident response.

The policy document on PR #87 proposes a default 30-calendar-day grace period and later limits only for explicitly designated non-safety premium features. That proposal requires commercial approval and eventual server-side implementation. Missing/unavailable billing state must not be treated as authority to block gate operations.

## 6. Release decision checklist

A release may proceed to the LTC pilot only when all applicable boxes are evidenced:

- [ ] School approval and gate policy signed.
- [ ] Real student-data processing approved and documented.
- [ ] All required CI jobs green on one commit.
- [ ] Four V1 role boundaries pass against isolated staging.
- [ ] Finance isolation and audit attribution gaps closed or finance module disabled.
- [ ] Issuer lifecycle and revocation tests pass.
- [ ] Gate V1 functional acceptance passes on physical devices.
- [ ] Offline limitation is honestly disclosed and manual fallback rehearsed, or offline lease implementation fully tested.
- [ ] Local persistence, sync retry and duplicate handling pass.
- [ ] Backup and recovery demonstrated.
- [ ] Production signing and upgrade tests pass.
- [ ] Dependency/licence review completed.
- [ ] Privacy, DPA, retention and incident procedures approved.
- [ ] Production/staging separation independently checked.
- [ ] Pilot metrics, support ownership and rollback plan agreed.

A failed checkbox is a release blocker unless the item is explicitly marked not applicable by an authorised decision-maker with a recorded reason.

## 7. Immediate next execution tasks

1. Re-run and diagnose the authenticated staging role suite, beginning with the Meal Staff failure. Preserve the current test contract and capture exact response/error evidence.
2. Close the documented finance-row isolation, cross-school audit visibility and actor-attribution test gaps using the canonical schema fixture.
3. Reconcile PR #86 and PR #87 without merging either automatically; keep their branch dependencies explicit.
4. Complete a code-backed Gate V1 gap audit: map each user workflow to Compose screen, ViewModel/repository, Room entity/DAO, Supabase table/RPC/storage policy and test.
5. Run physical-device acceptance and document failures with device/API details.
6. Agree the offline lease threat model and acceptance suite before implementing it.
7. Obtain school approval and legal review before importing real student records.
8. Complete package identity, resolved dependency/licence and release-signing work.

## 8. Change control

- Do not touch the production Supabase project during development or CI.
- Use isolated staging and disposable local databases for destructive tests.
- Do not rewrite Git history or replace canonical migrations without explicit review.
- Do not merge PRs or retarget existing branches as part of roadmap documentation.
- Do not claim a feature is complete from a policy document, mock, emulator-only test or successful compilation.
- Every implementation task must include code changes, regression tests, evidence and a clear acceptance result.
