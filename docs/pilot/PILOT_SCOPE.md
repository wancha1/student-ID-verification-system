# Scholink — LTC Gate Pilot Scope

**Status:** Draft for school approval. **Pilot is not yet authorised.**

## Objective
Prove that one supervised gate can identify enrolled LTC students using signed QR cards, record entry decisions reliably, and recover safely from network/device failures.

## Included
- Staff sign-in and server-resolved Gate Officer role.
- Student roster import with validation, preview, duplicate detection and an import report.
- Signed V2 QR scanning and one verification pipeline.
- Gate decision display containing only the minimum information needed by gate staff.
- Append-only gate event capture, local persistence and retryable sync.
- Basic gate event history and operational sync status.
- Manual exception procedure only if explicitly approved in the school policy; every manual entry must be attributable and audited.
- Offline behaviour that is explicit and bounded by the approved policy.

## Excluded / disabled
- Meals and meal serving.
- Attendance registers and analytics.
- Exams and exam exceptions.
- Fee collection, balances, guardian contacts and finance workflows in the gate interface.
- Any automatic rule that blocks entry for unpaid fees until the school signs the policy.
- Multi-gate rollout, parent/student apps, payments, timetable and other modules.

## Pilot operating conditions
- One gate, one school-approved Gate Officer account, two tested Android phones and a roster of 20 real students.
- A manual paper register remains available throughout the pilot.
- A named school supervisor is present for the supervised pilot.
- No student is denied entry solely because of an unapproved software rule.
- Stop the pilot immediately if identity matching, audit logging, permissions or sync integrity is uncertain.

## Exit criteria
- All mandatory test cases in `PILOT_TEST_PLAN.md` pass with evidence.
- No unresolved critical/high security or data-integrity defects.
- School policy is signed by the Head Teacher and Bursar.
- Two-device tests pass, including offline/restart/retry and staff revocation.
- School formally authorises the supervised pilot.

## Rollback
Disable app-based gate decisions and return to the school's paper register. Preserve local/cloud audit evidence for investigation; do not delete or overwrite records to conceal a failed test.
