# Scholink Gate Pilot — Failure Mode Specification

**Status:** Required behaviour to implement and verify before pilot. Policy-dependent items remain blocked pending `LTC_GATE_POLICY.md`.

| Failure | Required behaviour | Recovery / evidence |
|---|---|---|
| No internet | Clearly show offline state. Never claim a live server check. Apply only the offline rule approved by LTC; if not approved, fall back to paper register. | Queue immutable events locally; sync later without duplicates. |
| App process/device restart | Restore only a valid authenticated session under current role rules. Do not silently grant gate authority from stale local role data. | Re-authenticate or use paper register. Verify pending events survive restart. |
| Sync timeout / uncertain response | Keep event pending and retry idempotently. Do not tell operator it was synced until confirmed. | Server-side event identity prevents duplicate logs. |
| Duplicate camera frames | One physical presentation must not create repeated gate events during the defined cooldown/in-flight window. | Test same QR repeatedly and confirm one event. |
| Invalid/tampered QR | Reject with a neutral, actionable message. Do not reveal cryptographic internals or private student data. | Record a security-relevant rejection only if the audit model supports it. |
| Revoked card | Reject after revocation is known. Offline behaviour must follow the signed school policy; do not imply instant revocation while disconnected. | Confirm revocation propagates to both devices. |
| Revoked staff account | Stop privileged operations once revocation is received; require server validation before restoring privileges. | Test active session and fresh login on both phones. |
| Lost issuer phone | No new cards may be issued from an untrusted replacement device. Follow a controlled key recovery/re-enrolment procedure. | Revoke old issuer and verify replacement public key trust. |
| Damaged/lost student card | Follow approved identity-check and temporary-pass policy. Never create an unsigned QR fallback. | Record authorised manual exception and replacement/revocation audit. |
| Student absent from roster | Do not guess or create a record at the gate. Use paper/manual escalation defined by LTC. | Roster correction by authorised staff; retain exception trail. |
| Wrong student match | Stop use of app decisions; preserve evidence and investigate roster/card mapping. | Correct source data through authorised process and retest. |
| Device battery/camera failure | Switch to paper register immediately. | Reconcile paper records under school procedure. |
| Local storage corruption/full | Fail closed for app decisions; do not discard queued events silently. | Paper fallback, diagnostics and controlled recovery. |

## Non-negotiable invariants
- Never expose fee balances or guardian contact details to Gate Officers.
- Never silently overwrite roster records or silently discard failed sync events.
- Never treat an unsigned or malformed QR as valid.
- Never grant roles based solely on client-supplied role labels.
- Every manual exception must identify the operator, student, reason, time and authorisation evidence to the extent supported by the approved audit design.
- When the system cannot establish a trustworthy decision, use the school's manual process rather than inventing a decision.
