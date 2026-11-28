# Scholink — Subscription Grace Period and Gate Continuity Policy

**Status:** Proposed product and engineering requirement.  
**Default grace period:** 30 calendar days after the invoice due date.  
**Scope:** A school's payment obligation to Scholink. This policy does not change a school's own student-fee, admission, safeguarding, or movement rules.

## 1. Non-negotiable safety invariant

A school's overdue Scholink subscription must never, by itself, disable, deny, or delay a student's gate verification or a Gate Officer's ability to perform gate duties during the school day.

Billing state is not an authentication factor, staff role, student status, card status, or gate-policy input. No billing code may:
- deactivate the school tenant;
- deactivate staff memberships or revoke staff sessions;
- disable QR scanning, signature verification, or access to the locally cached roster and card-revocation state;
- prevent Gate Officers from recording gate events locally;
- block security-critical synchronization, including staff/device revocation, card revocation, roster/policy updates, and queued gate-event upload;
- erase, expire by billing policy, or remotely lock cached school data needed for safe gate operations.

In particular, `schools.is_active` is an operational tenant/security flag used by staff authorization. It must **not** be set to false because an invoice is overdue. Billing lifecycle must use a separate field/state and separate authorization logic.

## 2. Billing state machine

Use a dedicated subscription record, separate from `schools` and `staff_memberships`. Suggested states:

| State | Entry condition | Product behaviour |
|---|---|---|
| `ACTIVE` | Invoice paid and subscription current | Contracted features operate normally. |
| `GRACE` | Invoice overdue, within grace window | Full operational service continues. Notify school Admins and billing contacts; show due date and amount only in billing UI. |
| `BILLING_LIMITED` | Grace window elapsed and invoice remains unpaid | Gate operations and safety-critical services remain enabled. Restrict only explicitly designated non-safety premium functions; retain read-only access to school records and exports needed for continuity. |
| `SUSPENSION_REVIEW` | Billing dispute, prolonged non-payment, or manual review | No automatic gate shutdown. Require human review, notice, and continuity plan before any non-safety service change. |
| `CANCELLED` | School requests cancellation or contract ends | Preserve data export and a documented transition period. Do not remotely disable gate operation during an active school day. |

The default grace window is **30 calendar days** from the invoice due date. It must be configurable by contract, recorded per invoice/subscription, and calculated server-side. Do not rely on device wall clocks. Send notices before the due date and during the grace period. A disputed invoice pauses escalation until reviewed.

## 3. Behaviour during and after grace

### During the 30-day grace period

- Keep all contracted school workflows operational.
- Display non-blocking billing notices only to authorised school Admins.
- Do not show financial debt notices on Gate Officer screens or student scan results.
- Continue normal authentication, roster/card/policy sync, gate event sync, and audit logging.

### After grace expires

The school enters `BILLING_LIMITED`, not a tenant lockout.

**Always available:**
- Gate Officer sign-in and gate dashboard, subject to normal staff authorization.
- QR signature and card-lifecycle verification.
- Access to the latest locally cached roster, cards, issuer trust/revocation state, and approved gate policy.
- Local gate decisions under the school's approved policy.
- Durable, append-only local gate-event recording and retryable synchronization.
- Security-critical server synchronization and revocation delivery.
- Read-only access to existing school records and continuity exports.

**May be restricted after notice:**
- New optional premium modules unrelated to safety or statutory record access.
- New non-essential customisation or convenience features.
- Non-essential administrative write operations only where a safe, documented alternative exists.

Do not classify roster updates, card revocation, staff/device revocation, policy updates, security patches, backups, audit access, or gate-event synchronization as optional premium features. Never use a billing restriction to weaken security controls or force unsafe stale data.

## 4. Network outage and offline gate continuity

Billing state must not be used to decide whether offline gate operation is available. Offline operation is governed by a separate, school-approved security lease.

Required design before advertising offline staff re-unlock:
- Server-issued, time-bounded, device-bound offline authorization lease after online authentication and membership validation.
- Least privilege: Gate Officer operations only; no finance, staff administration, role changes, roster imports, card issuance, or policy editing.
- Encrypted storage backed by Android Keystore; copied leases must fail on another device.
- Lease contains school, staff subject, device, approved policy version, issue/expiry times, and unique lease ID.
- Cached signed roster, card status, trusted issuer keys/revocations, and policy are versioned and have explicit freshness limits.
- Gate events are durably queued with unique IDs and uploaded idempotently after reconnection.
- Expired lease, invalid device key, unknown/expired policy, or untrusted/revoked issuer must not silently approve a scan.
- Clock rollback and device reboot must not extend a lease. Until a tested boot/time-integrity strategy exists, require online revalidation after reboot.

**Current implementation limitation:** the existing staff-authentication flow requires online membership revalidation after an app/device restart; secure offline staff-session restoration is not implemented. Local cached student data and queued events do not, by themselves, authorize a staff member to unlock the app after restart. Until the offline lease is implemented, tested, and approved, the documented operational fallback remains the school's manual/paper gate register. Do not claim that a fully offline gate workflow is production-ready.

If an already authenticated Gate Officer device loses connectivity during a shift, supported local verification and durable event queuing may continue only within the currently validated session and approved policy/freshness limits. Clearly label offline decisions and their last-sync time.

## 5. Separation from student-fee policy

This policy concerns the school's subscription payment to Scholink. It does **not** automatically clear or alter individual student fee balances.

- Student fee status remains a separate school-owned data field governed by the school's approved policy.
- Do not change `FeeStatus`, `AccessStatus`, exam exceptions, meal entitlements, or student records when a Scholink invoice becomes overdue.
- Do not let a Scholink billing state appear as a student-level denial reason.
- Any change to the school's student-fee gate policy requires separate school approval and its own safeguarding/legal review.

## 6. Backend and implementation constraints

- Add a dedicated subscription/invoice model; do not overload `schools.is_active`, `staff_memberships.is_active`, or student fee fields.
- Keep the subscription status out of `private.is_active_staff(...)` and all gate-operation RLS predicates.
- Enforce billing restrictions only in explicitly designated non-safety feature entitlements. The server must not revoke operational staff memberships because of billing state.
- The mobile app must treat missing/unavailable billing data as **no authority to block gate operations**.
- Billing webhooks must be authenticated, idempotent, auditable, and unable to mutate school activation or staff membership.
- Record invoice, due date, grace start/end, state transitions, notices, dispute state, actor/source, and timestamps in an append-only billing audit trail.
- Payment restoration returns the subscription to `ACTIVE` without requiring staff re-provisioning or reinstalling the app.
- Provide school Admins with invoice visibility, payment instructions, dispute contact, and data-export/continuity guidance.

## 7. Acceptance tests

1. An overdue invoice in `GRACE` does not change school activation, staff membership, staff authentication, gate scanning, or gate-event recording.
2. Transition to `BILLING_LIMITED` leaves Gate Officer sign-in, QR verification, local gate logging, and security-critical sync available.
3. A billing webhook cannot set `schools.is_active = false`, deactivate memberships, revoke sessions, or change student fee/access fields.
4. A missing or unavailable subscription response never blocks a gate workflow.
5. Billing restrictions do not block card/staff/device revocation, security patches, backups, audit access, or gate-event upload.
6. A device offline during a valid session labels results as offline, records the last successful sync, and queues events durably.
7. Offline restart without a valid server-issued lease requires the manual gate procedure; it must not unlock from a cached role alone.
8. An expired lease or stale/unknown gate policy produces a clearly identified manual-review state, not an automatic approval.
9. Paying an overdue invoice restores paid features without re-provisioning staff or changing gate permissions.
10. Student-fee status and school subscription status remain independent in unit, integration, and RLS tests.

## 8. Release gate

This document defines the required policy; it does not claim that subscription billing or offline staff leases are currently implemented. The repository currently has no school subscription/billing enforcement to modify. Before introducing billing enforcement, implement this state machine and the tests above. Before advertising offline restart continuity, implement and pass the offline-lease security tests and obtain school approval.
