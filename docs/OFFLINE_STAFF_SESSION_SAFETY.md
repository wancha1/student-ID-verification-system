# Scholink — Offline Staff Session Safety

**Status:** Current implementation documented; secure offline re-unlock is not implemented.  
**Pilot posture:** Manual/paper fallback is required when staff authentication cannot be revalidated.

## 1. Current implementation

The current Supabase staff authenticator:

1. Waits for Supabase Auth session restoration to finish.
2. Requires an authenticated Supabase session.
3. Reads the current user's staff membership from the protected staff_memberships table.
4. Confirms the membership belongs to the configured LTC school, matches the authenticated user, is active, and has an enabled V1 role.
5. Grants the in-app staff role only after that server-side validation succeeds.

MainViewModel catches restore/configuration/network errors and leaves the login screen visible. A cached local role is not sufficient to restore staff access.

This is fail-closed and prevents a stale locally cached role from silently surviving staff revocation. The trade-off is that a device restarted without network access cannot restore a staff session. That is a known operational limitation, not a completed offline feature.

## 2. Required behaviour until a lease model is approved

- Do not grant staff access from a cached role, display name, local preference, or student record.
- If staff membership cannot be revalidated after restart, keep the app locked at staff login.
- Keep the existing school-approved manual/paper gate register available.
- Do not allow offline finance, staff administration, role changes, roster import, card issuance, or policy changes.
- Do not present an offline identity scan as a live server validation.
- Record any manual gate action through the school's approved process and reconcile it later.

## 3. Future offline lease design — not yet authorised for implementation

If LTC requires staff to resume gate operations during an outage, implement a separate, server-issued offline authorisation lease. It must not be a copy of the user's role stored in SharedPreferences.

Minimum design properties:

- **Server-issued:** created only after online authentication and current membership validation.
- **Device-bound:** encrypted using Android Keystore-backed key material and bound to an enrolled device identifier/key pair.
- **Least privilege:** initially limited to a designated Gate Officer device and the minimum gate operations approved by LTC.
- **Time-bounded:** short validity window selected by the school and enforced against a trustworthy time model. Do not trust a mutable wall clock alone.
- **Revocation-aware:** server records lease issuance, device status, staff revocation and lease expiry. Offline revocation cannot be instantaneous; this residual risk must be explicitly accepted.
- **No privileged administration:** no finance access, staff membership changes, roster imports, issuer-key management, or policy editing while offline.
- **Policy-bounded:** the lease must carry the approved policy version and its validity window. Missing, expired or unsupported policy means manual fallback, not approval.
- **Auditable:** every offline action records lease ID, device ID, staff subject, event ID, local timestamp, monotonic timing evidence where available, policy version, and sync status.
- **Replay-safe:** events have stable unique IDs and are uploaded idempotently after connectivity returns.
- **Tamper response:** invalid lease, device-key loss, detected rollback, expired lease or revoked device must lock the offline workflow and require online re-enrolment.

A reboot while offline is a special risk: Android's monotonic elapsed-time counter resets on reboot, while wall-clock time can be changed. The implementation must not extend a lease by trusting wall-clock time alone. Until a tested time/boot-integrity strategy exists, require online revalidation after device reboot.

## 4. Decisions required from LTC

- Is gate operation permitted during internet outages, or is the paper register the required fallback?
- Which specific devices may hold an offline lease?
- Which staff role may use it?
- Maximum offline duration and acceptable revocation delay.
- Whether offline operation is permitted after app process restart or device reboot.
- Which gate decisions can be made offline under the approved policy.
- Who reviews and reconciles offline events after connectivity returns.
- Who may disable a device or revoke an offline lease during an incident.

These are school operational and safeguarding decisions. The software team must not infer them.

## 5. Required test cases before offline lease implementation

| Test | Expected result |
|---|---|
| Valid lease, same enrolled device, network lost | Only approved lease-scoped operations available |
| Lease expired | Staff offline workflow locked; manual fallback |
| Staff revoked while device offline | Residual exposure bounded by lease expiry; event flagged for reconciliation |
| Device key unavailable or Keystore invalidated | Lease rejected |
| Lease copied to another device | Rejected |
| Wall clock moved backwards | Lease not extended |
| Device rebooted while offline | Revalidation required; no offline re-unlock |
| Policy version expired or unknown | No gate approval; manual fallback |
| Duplicate event retried after reconnect | One server event after idempotent reconciliation |
| Device reported lost | Device/lease revoked on server; new enrolment required |

## 6. Release gate

Do not advertise or deploy offline staff session restoration until the lease design is approved by LTC, implemented with Keystore-backed device binding, tested against clock manipulation/reboot/revocation cases, and reviewed for residual risk.

Until then, the truthful product statement is: student data and queued records may be stored locally for supported workflows, but staff authentication after restart requires online membership revalidation.
