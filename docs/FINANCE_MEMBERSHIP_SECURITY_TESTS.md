## Execution record (2026-11-26)

The following targeted checks were run against disposable staging using synthetic identities and transactions that were rolled back:

- **FIN-01:** ordinary Admin cannot grant finance access to self or another Admin — PASS.
- **FIN-02:** ordinary Admin cannot transfer a finance-enabled membership — PASS.
- **FIN-03:** ordinary Admin cannot deactivate a finance-enabled membership — PASS.
- **FIN-04:** ordinary Admin cannot demote or move a finance-enabled membership — PASS.
- **FIN-05:** finance-authorized Admin can transfer an active finance membership and deactivate another finance membership — PASS.
- **FIN-06:** student_finances.fee_status is NOT NULL with no default — confirmed. Direct non-finance edits to students.fee_status are rejected — PASS.
- **Fee-status synchronization:** the existing nested trigger path was blocked by the student fee-status guard. A proposed internal transaction-context bypass passed a staging dry-run: finance-table OUTSTANDING and CLEARED updates synchronized to the student row; the internal flag reset; direct non-finance updates remained rejected. The migration was rolled back and is proposed in the dependent follow-up PR.

**Unresolved policy decision:** the current guard permits the sole finance-authorized Admin to deactivate their own membership. The school must decide whether this is allowed and define a recovery or second-approver path before production acceptance.

# Finance Membership Guard — Security Test Matrix

**Status:** Required before applying the migration to the live LTC Supabase project.

The migration protects the finance capability at the database boundary. Run these tests against a disposable/staging database with synthetic users and memberships. Do not use real staff accounts or student data.

## Test identities

Create synthetic memberships in one test school:

- Admin A: active ADMIN, no finance access.
- Finance Admin B: active ADMIN, finance access.
- Admin C: active ADMIN, no finance access.
- Staff D: active non-ADMIN role, no finance access.

Use separate authenticated sessions for each identity. Do not simulate user identity by trusting client-supplied user IDs; the test session's JWT subject must be the identity under test.

## Required cases

| ID | Actor | Operation | Expected |
|---|---|---|---|
| FIN-01 | Admin A | Grant finance access to Admin C | Rejected |
| FIN-02 | Admin A | Grant finance access to own membership | Rejected by RLS/self-membership boundary |
| FIN-03 | Finance Admin B | Grant finance access to Admin C | Allowed |
| FIN-04 | Admin A | Change finance-enabled membership user_id | Rejected |
| FIN-05 | Admin A | Deactivate finance-enabled membership | Rejected |
| FIN-06 | Admin A | Change role or school of finance-enabled membership | Rejected |
| FIN-07 | Admin A | Revoke finance access from another Admin | Rejected |
| FIN-08 | Finance Admin B | Change user_id of a finance-enabled membership | Allowed only if RLS and foreign-key constraints also permit the target |
| FIN-09 | Finance Admin B | Deactivate another finance-enabled membership | Allowed, subject to the school's approved recovery policy |
| FIN-10 | Admin A | Update unrelated non-finance membership fields | Existing policy behaviour unchanged |
| FIN-11 | Trusted SQL administrator | Provision or recover a finance-enabled ADMIN | Allowed through the trusted administrative path |
| FIN-12 | Any authenticated role | Assign finance access to a non-ADMIN role | Rejected |

## Additional checks

- Confirm the trigger exists and fires on INSERT plus updates to has_finance_access, role, school_id, user_id, and is_active.
- Confirm the function has a fixed empty search path and execution is revoked from PUBLIC, anon and authenticated.
- Confirm ordinary Admins cannot bypass the trigger through direct PostgREST requests.
- Confirm at least one authorised finance Admin remains available after the test suite, or document the trusted recovery procedure.
- Confirm the application UI does not expose finance access as a self-service role toggle.
- Verify audit logs identify the actor and the attempted membership change.

## Release gate

Do not apply this migration to production until the exact migration chain is reproducible, a staging project has been tested, the migration has been reviewed, and a verified backup/restore path exists.
