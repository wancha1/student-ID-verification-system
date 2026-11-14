# LTC Student Access V1 — Role and Interface Matrix

V1 has four supported staff roles. Authentication is resolved from the active `staff_memberships` row for the authenticated Supabase user and the LTC tenant. Client-provided role values are not trusted.

| Staff role | Primary interface | Permitted operational scope |
|---|---|---|
| `ADMIN` | Admin Dashboard | Student registry, student details, card lifecycle, approved exports, system configuration and administrative oversight |
| `HEAD_TEACHER` | Head Teacher Dashboard | Read-only leadership overview, gate activity and meal oversight |
| `GATE_OFFICER` | Gate Dashboard | QR verification, gate decisions, gate history and sync status |
| `MEAL_STAFF` | Dining Services | Meal-session selection, QR verification, manual serving lookup, duplicate-serving prevention and meal history |

## Interface separation

- Admin and Head Teacher use different Compose screens: `AdminDashboardScreen` and `HeadTeacherDashboardScreen`.
- Gate Officers use `GuardDashboardScreen` and `GuardScannerScreen`.
- Meal Staff use `MealsMasterScreen`.
- The app's `MainViewModel.VERSION_ONE_ROLES` allowlist is the client-side usability boundary. Supabase Row Level Security is the authoritative data boundary.

## Reserved roles

The database schema and older UI source contain additional roles such as Bursar, Teacher, Director of Studies, and Examination Staff. These are **not enabled in V1**. The Supabase authenticator now rejects them and signs out the session rather than allowing an authenticated session that the V1 UI cannot accept.

Do not provision reserved roles for a live V1 deployment. Enabling any additional role requires all of the following:
1. A documented role-permission matrix approved by the school.
2. A dedicated interface and navigation review.
3. Matching Postgres RLS policies and column-level data boundaries.
4. End-to-end tests proving allowed actions and denied actions.
5. A release note and migration plan.

## Security boundary

Hiding a button or screen is not authorization. Every write and sensitive read must remain protected by Supabase RLS and explicit backend role checks. Physical-device acceptance must verify that one role cannot perform another role's operations.

## Finance permission (Admin capability, not a fifth role)

Fee-balance management is an optional capability on an active `ADMIN` membership. It is **not** a separate Bursar login in V1.

- Default for every membership: `has_finance_access = false`.
- Only an active `ADMIN` membership with `has_finance_access = true` receives the `MANAGE_FEES` permission.
- Monetary balances live in `public.student_finances`, which has RLS policies requiring the explicit finance capability.
- Guardian contacts and notes remain in `student_private`; the outstanding balance is no longer stored there.
- Gate Officers receive only the operational `fee_status` needed for access decisions. Gate scan messages do not display monetary balances.
- The database trigger prevents a client from changing a student's fee status without explicit finance permission, even if a request bypasses the Android UI.

After the finance migration is applied, an authorized project owner can grant the capability to a specific Admin by updating that Admin's membership row in the Supabase SQL Editor:

```sql
UPDATE public.staff_memberships
SET has_finance_access = true
WHERE school_id = 'ltc_school_01'
  AND user_id = '<AUTH_USER_UUID>'
  AND role = 'ADMIN'
  AND is_active = true;
```

Replace `<AUTH_USER_UUID>` with the exact Supabase Auth user UUID for the approved Admin. Do not grant the capability to every Admin by default. To revoke it, set the flag to `false`.
