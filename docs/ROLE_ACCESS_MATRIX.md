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
