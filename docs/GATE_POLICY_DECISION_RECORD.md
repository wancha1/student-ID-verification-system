# LTC Gate Policy Decision Record

**Status: awaiting school approval.** This is a decision record, not an implementation of school policy. No gate rule should be inferred from fee, boarding, transport, or attendance fields until authorised school representatives approve it.

## Required decision owners

- Head Teacher: ______________________________
- Bursar / Finance lead: ______________________
- Gate / Security lead: _______________________
- Data owner: ________________________________
- Decision date: _____________________________
- Policy effective date: _____________________
- Approved document / minute reference: ______

## Decisions required before policy enforcement

| Topic | Decision to record | Approved rule / reference |
|---|---|---|
| Entry — outstanding fees | Do unpaid fees affect entry? If yes, which authorised role can set the restriction and what notice is shown? | Pending |
| Exit — outstanding fees | Record the school's lawful, safeguarding-compatible procedure and escalation path. | Pending |
| Boarders | Define ordinary movement at the main gate, including leave, return, weekends and school closure. | Pending |
| Day scholars | Define entry and exit rules separately; do not infer permission from the isDayScholar field alone. | Pending |
| Exeat | Define who may request, approve, revoke and verify an exeat; specify validity window and offline verification requirements. | Pending |
| Direction | Define supported directions (ENTRY, EXIT, and any other approved movement) and how the officer records direction. | Pending |
| Overrides | Define roles permitted to override, required reason, second approver, expiry and audit requirements. | Pending |
| Lost / stolen card | Define identity fallback, temporary pass issuance, card revocation and re-issuance. | Pending |
| Manual lookup | Define allowed search fields, minimum verification checks, mandatory reason and audit retention. | Pending |
| Offline operation | Define what happens with stale policy, expired staff session, no network or unconfirmed revocation. | Pending |
| Emergencies / safeguarding | Define emergency escalation. Administrative fee or card rules must not obstruct emergency response or lawful safeguarding action. | Pending |

## Recommended policy-engine contract

The implementation should receive an explicit movement direction and return a typed decision. Identity verification and authorisation must remain separate.

- Identity result: signed credential, trusted issuer, card lifecycle and student record validity.
- Policy input: direction, approved policy version, student status, boarder/day-scholar classification, exeat state, time window, authorised overrides and freshness of cached policy.
- Policy output: APPROVED, DENIED or POLICY_PENDING, with a stable reason code and human-readable explanation.
- Every decision must record direction, policy version, officer, device, timestamp, offline/freshness state and override reason where applicable.
- Missing or stale policy must never silently become approval.
- Entry and exit must be evaluated independently. Fee status must not be used as a proxy for identity or as an implicit exit prohibition.
- A valid identity scan with unresolved policy remains POLICY_PENDING; staff follow the school's approved manual procedure.

## Approval

The signatories confirm that the table above reflects the school's authorised policy and that the school has reviewed the operational, safeguarding and data-protection implications.

| Role | Name | Signature | Date |
|---|---|---|---|
| Head Teacher |  |  |  |
| Bursar / Finance lead |  |  |  |
| Gate / Security lead |  |  |  |
| Data owner |  |  |  |

## Other department policies required before enforcement

These decisions are separate from gate movement policy. Do not assume the examples below are approved LTC rules.

### Meal service

- Which meal periods are actually served (breakfast, lunch, supper, special meals)?
- Are entitlements different for day scholars, boarders, staff, visitors or students with approved exceptions?
- Are meal entitlements tied to fees, meal plans, attendance, medical/dietary needs or another record?
- Who can grant a temporary meal exception, for how long, and how is it audited?
- What is the manual fallback when policy or roster data is unavailable?

### Examination admission

- What are the actual admission requirements, and which are academic, financial or administrative?
- Does fee status affect examination admission? If so, what is the authorised decision process and what exceptions or safeguards apply?
- Which documents or other requirements are checked, and who owns each status?
- Who may recommend, approve, revoke or expire an exception?
- What is the manual fallback for disputed or stale records?

Until the relevant school policy is documented and approved, the app must not automatically serve/deny meals or admit/deny examination entry based on inferred rules. It may verify identity and direct staff to the approved manual process.
