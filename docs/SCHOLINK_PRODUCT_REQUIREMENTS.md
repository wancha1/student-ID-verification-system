# Scholink Product Requirements

**Document status:** Product baseline, version 0.1  
**First deployment:** LTC pilot  
**Commercial direction:** Multi-school SaaS platform with a web dashboard and Android staff application

## 1. Product definition

Scholink is a school operations platform centred on reliable student identity. It connects student records and QR credentials to controlled school workflows such as gate access, meal service, attendance and operational reporting.

The first release must solve a narrow set of school problems reliably. It must not attempt to become a complete finance, learning-management, payroll or parent-communication suite.

## 2. Target users and core jobs

| User | Primary job | Main interface |
|---|---|---|
| School Admin | Configure the school, manage students and staff, issue/revoke cards, review records | Web dashboard; limited Android support |
| Head Teacher | Monitor daily school activity, review exceptions and access reports | Web dashboard |
| Gate Officer | Verify a student's identity and record entry/exit | Android |
| Meal Staff | Verify eligibility and record a meal serving without duplicates | Android |
| Platform Operator | Provision schools, manage subscriptions and provide support without browsing school data unnecessarily | Restricted platform console (later phase) |

Permissions must be enforced by the backend, not merely hidden in the interface.

## 3. Product surfaces

### 3.1 Scholink Dashboard (web)

Initial scope:
- Secure staff sign-in and password recovery.
- School profile and configuration.
- Student register: create, view, edit, deactivate and search.
- Student photo upload, preview and backup status.
- Class and stream management.
- Staff invitations, role assignment and account deactivation.
- QR card issue, replacement, expiry and revocation.
- Gate and meal activity views.
- Attendance and exception summaries.
- Exportable operational reports with permission checks.
- Visible data freshness and sync/error indicators.

### 3.2 Scholink Staff (Android)

Initial scope:
- Secure sign-in and role-specific workspace.
- Gate Officer: camera scanner, QR validation, student photo/name/class, clear decision, entry/exit capture and recent scan history.
- Meal Staff: scanner, identity confirmation, meal eligibility, duplicate-serving prevention, serving confirmation and recent history.
- Admin: essential student lookup and operational status; full management remains easier on web.
- Head Teacher: concise operational overview and exception review where appropriate.
- Offline-first local operation for approved workflows.
- Durable sync queue, retry visibility and last successful sync.

The app must never silently treat an unverified or expired credential as valid. Offline decisions must follow an explicit, documented policy and be visibly identified as offline decisions.

## 4. Core domain model

Every school-owned record must be scoped to a school_id. At minimum the model must account for:
- Schools and school configuration.
- Staff identities, memberships and roles.
- Students and student-private data.
- Classes and streams.
- Student photos and private storage paths.
- Student cards and QR credential lifecycle.
- Trusted issuer public keys and revocation state.
- Gate events.
- Meal eligibility and meal-serving events.
- Attendance events.
- Audit events.
- Sync/outbox state and conflict/error records.
- Subscription and licence state (commercial phase).

Use stable identifiers, timestamps and actor identifiers. Store timestamps consistently (UTC in the backend; render in the school's local timezone). Define retention and deletion rules before storing sensitive student data at scale.

## 5. Multi-school tenancy and security

Multi-tenancy is a release requirement for the commercial platform, not an optional UI feature.

- Every school-owned row must carry a non-null school identifier.
- Supabase Row Level Security must enforce tenant boundaries using trusted membership records.
- Never trust a client-supplied school identifier without checking the authenticated user's membership.
- Platform-level support access must be separately authorised, audited and limited.
- Private student photos must remain in private storage; access should use authorised retrieval, not public bucket URLs.
- Private QR signing keys must remain on the authorised device/key store. Only public verification keys are distributed.
- Card revocation and trusted-key refresh must have a defined propagation and offline policy.
- Audit sensitive actions: student changes, staff/role changes, card issuance/revocation, access decisions and administrative overrides.
- Do not put student personal data or secrets in logs, analytics events or QR payloads unnecessarily.
- Provide least-privilege role permissions and test them with real authenticated accounts.

Before multi-school onboarding, review every table, view, RPC, storage policy, background job and sync query for tenant isolation.

## 6. Offline and synchronisation requirements

Offline capability is a core product requirement, not a best-effort enhancement.

- Cache only the minimum data required for the device's assigned role.
- Persist accepted events locally before showing success.
- Use a durable outbox with unique event IDs and idempotent server writes.
- Retry transient failures with bounded backoff and expose permanent failures clearly.
- Do not advance sync watermarks while required records remain unconfirmed.
- Define conflict rules for edits to student profiles and card status.
- Show online/offline state, pending item count, last successful sync and actionable errors.
- Protect against duplicate meal servings and duplicate gate events after retries.
- Test interruption during upload, app restart, device restart and two-device operation.

## 7. QR identity and access control

- Use signed, versioned QR credentials with cryptographic verification.
- Validate issuer trust, signature, card status, validity window and student status.
- Support card replacement, expiry and revocation.
- Never accept unsigned or malformed cards as valid.
- Provide a clear operator result: valid, expired, revoked, unknown issuer, invalid signature, inactive student or unable to verify.
- Define the allowed offline trust window and how revoked keys/cards are handled when a device is disconnected.
- Avoid placing unnecessary personal information in the QR payload.

## 8. UX acceptance principles

Each role must have a distinct workspace with only relevant tasks and permissions.

### Gate Officer
- Scanner is the primary action and opens quickly.
- Result is readable at arm's length and understandable within two seconds.
- Student photo and core identity details appear after a valid lookup.
- Entry/exit direction is explicit.
- Recent scans and failed/offline states are accessible.

### Meal Staff
- Scanner is the primary action.
- Eligibility is clear before a serving is recorded.
- Duplicate serving is blocked and explained.
- Successful serving gives an unmistakable confirmation.
- Recent serving history and pending sync status are visible.

### Admin
- Student registration is a guided, validated workflow.
- Required fields are explicit; no fabricated defaults.
- Photo upload and cloud-backup status are distinct.
- Card issue/revoke actions require confirmation and are audited.
- Staff and role management are easy to find.

### Head Teacher
- Dashboard prioritises today's operations and exceptions.
- Student directory is searchable and filterable.
- Reports are actionable, not decorative.
- Drill-down views explain the underlying counts.

## 9. Delivery phases

### Phase 0 — Stabilise LTC pilot
- Reconcile release and feature branches.
- Fix and verify student registration.
- Verify local persistence and Supabase sync.
- Verify photo upload and retrieval across devices.
- Verify issuer setup, QR signing, verification and revocation.
- Verify Gate and Meals flows on physical devices.
- Verify role separation with authenticated Supabase accounts.
- Resolve CI emulator reliability or document a dependable alternative.
- Complete visual acceptance for all four roles.

### Phase 1 — Pilot-ready Scholink Staff
- Apply the Scholink design system to Android.
- Complete the four role workspaces.
- Run a controlled pilot with real staff and test data approved by the school.
- Collect operational feedback and measure failure rates.

### Phase 2 — Scholink Dashboard
- Build the web administration experience on the same backend.
- Add student/staff management, card lifecycle, reports and school settings.
- Keep the Android app focused on field operations.

### Phase 3 — Multi-school commercial platform
- Replace all single-school assumptions and hardcoded IDs.
- Add school provisioning and onboarding.
- Enforce and test tenant isolation across database, storage, APIs and sync.
- Add subscriptions/licensing, support workflows and operational monitoring.
- Pilot with a second school before claiming general multi-school readiness.

## 10. Explicit non-goals for the first commercial release

Defer payroll, full accounting, parent portal, SMS gateway, e-learning, transport tracking, biometrics, payment collection and a full learning-management system. Add them only after the core product is stable and schools demonstrate demand.

## 11. Release gates

Do not call Scholink commercially ready until all of the following are demonstrated:
- All four roles work with least-privilege permissions.
- Student create/edit/deactivate works online and offline as designed.
- Photos upload and retrieve from another authorised device.
- QR issue, scan, expiry and revocation work.
- Gate and meal events survive offline operation and retry without duplication.
- Sync errors are visible and recoverable.
- Two-device acceptance passes.
- Tenant-isolation tests pass for at least two separate school tenants.
- Backups, retention, incident response and support procedures are documented.
- Signed production build is created using a protected, permanent signing key.
- Physical-device UX acceptance is complete.

A successful build or passing unit tests alone does not satisfy these gates.
