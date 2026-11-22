# Scholink LTC Gate Pilot — Test Plan

**Status:** Draft. Execute on staging/test data first; physical-device acceptance is still outstanding.

## Test setup
- Two physical Android phones with documented model, Android version and app build.
- One authorised Gate Officer account; a second account for role-separation checks.
- 20 LTC-approved test students and issued signed V2 cards.
- Staging Supabase project/database and real authenticated test tokens. Do not seed synthetic records into production.
- Network available, then deliberately disabled; paper register available for every test.

## Mandatory cases

| ID | Test | Pass condition | Evidence |
|---|---|---|---|
| ROSTER-01 | Import valid 20-row CSV | Preview counts 20 accepted; no commit until confirmation | CSV hash + preview |
| ROSTER-02 | Duplicate admission number | Duplicate rejected/reported; existing student unchanged | Rejection report |
| ROSTER-03 | Missing required name/class/admission number | Row rejected with row-level reason | Rejection report |
| ROSTER-04 | Leading-zero phone values | Values preserved as text | Imported record check |
| ROSTER-05 | Re-import same roster | No silent duplicate or overwrite | Before/after comparison |
| GATE-01 | Scan valid card | Correct student identity and approved neutral decision | Screen + gate event |
| GATE-02 | Repeat same QR rapidly | One presentation does not create duplicate events | Event count |
| GATE-03 | Invalid/tampered QR | Rejected; no private information shown | Screen + logs |
| GATE-04 | Unknown student/card | No guessed identity or automatic enrolment | Screen + logs |
| PRIV-01 | Inspect all gate result states | No fee amount/status, guardian contact or private notes | Screenshots |
| AUTH-01 | Gate Officer attempts admin/finance action | Server denies; UI does not grant access | API/test log |
| AUTH-02 | Revoke staff while session active | Privileges stop when revocation is observed; no stale role restoration | Timestamped test |
| SYNC-01 | Scan online on phone A | Event appears once in staging backend | DB evidence |
| SYNC-02 | Phone B syncs same roster/card | Same card verifies consistently | Both device records |
| SYNC-03 | Network lost during event upload | Event remains pending; no false synced state | UI + local queue |
| SYNC-04 | Restore network and retry | Event syncs once, without duplicate | Backend count |
| OFF-01 | Restart app while offline | No crash/data loss; offline state explicit | Video/log |
| OFF-02 | Test stale card/revocation offline | Behaviour exactly matches signed policy | Signed policy + test |
| REC-01 | Paper fallback after device failure | Staff can continue using approved register procedure | Signed observation |
| AUD-01 | Manual exception (if approved) | Operator, student, reason and authorisation are auditable | Event record |

## Acceptance thresholds
- 100% pass for security, privacy, roster integrity and duplicate-event cases.
- 100% pass for the policy-approved offline behaviour.
- Zero unexplained missing or duplicate gate events.
- Zero critical/high defects open.
- Both devices show consistent student/card state.
- Head Teacher and Bursar sign the policy; pilot owner signs the test evidence.

## Test report template
- Date / tester:
- App version and commit SHA:
- Phone A model / Android:
- Phone B model / Android:
- Staging project / environment:
- Passed / failed / blocked:
- Defects and severity:
- Evidence links:
- School approval:
