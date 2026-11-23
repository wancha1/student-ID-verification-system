# Scholink — Lira Town College Pilot Plan

**Status:** Planning only — not approved for live student data or operational use  
**Pilot customer/site:** Lira Town College (LTC)  
**Product approach:** Multi-school product with an LTC-specific configuration  
**Document owner:** Scholink project team  
**Approval:** Pending authorised LTC representatives

## 1. Purpose and scope

LTC is the first intended customer and pilot site for Scholink. The pilot should establish whether the product can reliably support student identity verification and the school's approved access workflows.

The pilot is not permission to deploy against the live school population. It begins with workflow discovery, a synthetic-data demonstration, written policy approval, controlled testing, and only then a separately authorised limited pilot.

The product must remain configurable. LTC-specific rules belong in school configuration and policy, not in generic identity-verification code.

## 2. Decisions required from LTC

The following decisions must be recorded by authorised school representatives before operational gate decisions are enabled:

| Decision | Required approver(s) | Notes |
|---|---|---|
| Student entry rules | Head Teacher / delegated authority | Define approved, restricted, and exception cases |
| Student exit rules | Head Teacher / delegated authority | Do not infer exit restrictions from fee status |
| Day-scholar workflow | Head Teacher / relevant staff | Define any distinct checks |
| Boarding workflow | Boarding lead / Head Teacher | Define routine movement and permitted exceptions |
| Exeat / permission-to-leave | Boarding lead / authorised approver | Define who grants, records, expires, and revokes permission |
| Fee-related restrictions | Bursar + Head Teacher | Specify the exact lawful and approved action; fee data must not silently determine access |
| Emergency and safeguarding overrides | Head Teacher / safeguarding lead | Define authority, audit trail, and fallback process |
| Manual identity lookup | Gate/Security lead | Define acceptable evidence and escalation |
| Offline operation | Gate/Security lead + IT/data owner | Define maximum acceptable policy age and fallback |
| Lost or stolen card | School administration | Define suspension, replacement, and identity re-verification |
| Data retention and access | School data owner | Define purposes, authorised roles, retention, correction and deletion process |

A role title in this table is a suggested governance responsibility, not an assertion that a named person has approved anything.

## 3. Proposed pilot stages

### Stage 0 — Discovery and approval

- Walk through actual arrival, departure, boarding, day-scholar, meal, and exception workflows with staff.
- Record each decision as an explicit policy rule with an owner and approval date.
- Identify the school's data controller/contact and the people authorised to access student information.
- Agree pilot scope, success criteria, incident contacts, retention, and rollback procedure.
- Do not import real student data at this stage.

**Exit gate:** written policy and pilot-scope approval from LTC's authorised representatives.

### Stage 1 — Synthetic-data demonstration

- Use fabricated student records only.
- Demonstrate student import, duplicate detection, card issuance, signed QR scanning, policy outcomes, and audit logs.
- Demonstrate offline behaviour, stale policy, revoked cards, suspended students, and manual fallback.
- Verify that fees, boarding status, and identity verification remain separate inputs.
- Confirm that every privileged action is attributable to a staff account.

**Exit gate:** documented acceptance tests pass; no unresolved critical/high security defect.

### Stage 2 — Controlled staff testing

- Use designated staff accounts and synthetic records.
- Test on at least two physical Android devices, including device restart and loss of connectivity.
- Verify sync, duplicate/conflict handling, role permissions, session recovery, and device-loss response.
- Reconcile local and server-side logs after reconnecting.
- Record defects and retest evidence.

**Exit gate:** signed device test report and security review; no unresolved critical/high defects.

### Stage 3 — Limited live pilot (separate authorisation required)

Only after the school has approved the data-processing arrangements and the project owner has confirmed production controls:

- Use the smallest practical, school-approved cohort.
- Obtain and document the required notices/permissions and lawful basis as advised by the school's qualified data-protection/legal adviser.
- Keep a tested manual fallback available.
- Monitor failed scans, false denials, false approvals, sync failures, and staff overrides daily.
- Maintain an incident log and a clear stop/rollback trigger.
- Do not use the pilot to make disciplinary or financial decisions automatically.

**Exit gate:** school review of measured outcomes and explicit decision to continue, modify, or stop.

## 4. Product requirements for the LTC pilot

1. **Identity is not authorisation.** A valid signed QR proves that a presented card payload is authentic; it does not by itself prove permission to enter or leave.
2. **Direction-aware policy.** Gate decisions must receive an explicit direction (entry/exit) and the relevant approved policy context.
3. **No guessed school rules.** Missing, expired, or unapproved policy must produce a safe, clearly explained pending/manual-review outcome, never silent approval.
4. **Fee separation.** Fee status is a finance record. It must not implicitly block entry or exit without a specific, approved policy and lawful basis.
5. **Boarding separation.** Day-scholar/boarder classification may inform a policy rule, but must not itself grant or deny access.
6. **Auditable overrides.** Record actor, timestamp, reason, decision, and relevant policy version for every override.
7. **Least privilege.** Staff permissions must be narrowly scoped. A user must not be able to grant themselves or another user higher privileges without a separately authorised workflow.
8. **Offline safety.** Define policy freshness limits and explicit fallback behaviour. A device must not silently treat an unknown or stale policy as approval.
9. **Privacy by design.** QR payloads should not expose student personal data. Collect only data needed for the approved purpose and restrict profile/photo access.
10. **Recovery.** Document device loss, staff revocation, backup, restore, and incident response before live use.

## 5. Proposed acceptance measures

The school and project team must agree numerical thresholds before Stage 2. At minimum, measure:

- Correct scan outcomes across approved, restricted, revoked, malformed, and unknown cards.
- False approval rate (target: zero in the test set).
- False denial rate and time to resolve a legitimate exception.
- Median scan-to-result time, both online and offline.
- Sync success rate and conflict recovery after reconnecting.
- Completeness of audit records for overrides and administrative changes.
- Successful staff access revocation and lost-device response.
- Staff ability to use the manual fallback without relying on the app.

## 6. Data protection and security prerequisites

Before any live student information is processed, obtain qualified advice on Uganda's Data Protection and Privacy Act, 2019 and applicable regulations, and confirm the school's obligations as data controller and Scholink's role as processor or other applicable role.

At minimum, document:

- Processing purpose, lawful basis, data categories, and data subjects.
- Privacy notice and any required consent/authorisation process.
- Data minimisation, retention schedule, correction and deletion procedure.
- Staff access matrix and periodic access review.
- Hosting location, subprocessors, cross-border transfers, and contracts.
- Encryption in transit and at rest, secrets management, and key rotation.
- Backup schedule, offsite copy, and a tested restoration procedure.
- Security incident reporting, response ownership, and notification process.
- Device security, screen lock, local database protection, and remote revocation/wipe approach.

This checklist is engineering and operational planning, not legal advice or a determination that the product is compliant.

## 7. Pilot stop conditions

Pause the pilot immediately if any of the following occurs:

- An unauthorised person can view or change student records or staff permissions.
- The system produces an unexplained false approval.
- Audit records are missing or materially inconsistent.
- Student data is exposed, lost, or sent to an unapproved destination.
- The system cannot safely fall back when connectivity or backend services fail.
- A critical/high security defect is discovered.
- School policy or required authorisation is withdrawn or unclear.

The school must be able to revert to its existing manual process without losing the records needed to reconcile activity.

## 8. Items that remain unapproved

- Exact LTC entry and exit rules.
- Fee-related access policy.
- Boarding, day-scholar, and exeat rules.
- Emergency override authority.
- Pilot cohort and dates.
- Live student data import.
- Production hosting plan and backup commitments.
- Final application ID, signing/release process, and production secrets.
- Legal/data-protection review and contractual arrangements.

**Current decision:** LTC is the intended first pilot site. This document does not authorise live deployment.
