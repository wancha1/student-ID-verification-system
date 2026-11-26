# Append-only school audit events

## Scope
Migration: `20261124140000_append_only_school_audit_events.sql`.

This migration adds a central database audit stream for mutations to:
- students
- cards
- staff memberships
- student finance status
- gate logs
- meals
- attendance records
- examination exceptions
- trusted issuer registrations and revocations

Database triggers write events in the same transaction as the underlying mutation. If an audit insert fails, the underlying write rolls back.

## Privacy model
The audit snapshots use an explicit per-table allowlist. They deliberately exclude:
- student names and contact details
- guardian details
- QR payloads
- issuer public-key material
- payment amounts
- free-text notes and recommendation reasons

For finance rows, the event records the fee-status transition and changed field names, not the monetary amount.

## Access model
- RLS is enabled.
- Authenticated clients receive SELECT only.
- Only active ADMIN and HEAD_TEACHER memberships can read events.
- Client INSERT, UPDATE and DELETE privileges are revoked.
- A database trigger rejects UPDATE and DELETE even if privileges are later broadened.
- The trigger functions live in the private schema and are not executable by client roles.

## Event semantics
Each event includes school, authenticated actor where available, event type, entity type and identifier, timestamp, transaction ID, optional correlation ID, changed fields and safe before/after snapshots.

Service/database operations without an authenticated JWT have a null actor. They remain distinguishable by the database-trigger source and transaction metadata.

## Explicit limitations
This first database-backed audit layer does not yet record:
- exports and downloads (application/edge-function events)
- failed authorization attempts (no business row mutation occurs)
- UI-only actions
- events from tables not listed in the migration

Those require explicit, authenticated server-side event endpoints and must not be faked by client-supplied actor identities.

Audit retention, export authority and school-specific access policy require approval before production use.

## Staging acceptance tests
Use synthetic school and staff accounts only.

1. Insert/update/delete each covered entity and verify exactly one corresponding event.
2. Verify event actor and school match the authenticated request.
3. Verify a failed business write creates no audit event.
4. Verify an audit insert failure rolls back the business write.
5. Verify anonymous and ordinary non-leadership users cannot read events.
6. Verify ADMIN and HEAD_TEACHER can read only their own school's events.
7. Verify authenticated clients cannot insert, update or delete events.
8. Attempt UPDATE and DELETE under a privileged test role; the immutability trigger must reject both.
9. Verify sensitive columns are absent from snapshots.
10. Verify finance events never include outstanding amounts or payment details.
11. Verify UPDATE statements that do not change allowlisted fields do not create noisy events.
12. Verify DELETE events retain school and entity identifiers after the source row is removed.

## Status
Source migration only. Not applied to live Supabase. Staging execution, RLS tests and retention approval remain outstanding.
