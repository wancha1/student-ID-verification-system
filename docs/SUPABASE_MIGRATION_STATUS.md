# Supabase Migration — LTC V1

## Source of truth
- Android source branch: `fix/ltc-production-reliability`
- Migration work branch: `feat/supabase-backend-migration`
- Do not use `main` as the migration source until it is reconciled with the development branch.

## Supabase target
- Project: School access
- School row: `ltc_school_01` / Lira Town College / LTC
- Region: `eu-central-1`
- Keep Room as the local offline database. Supabase is the remote backend.
- Firebase remains the active Android backend until the replacement is implemented and verified.

## V1 data scope
- schools
- staff_memberships
- students
- student_private
- cards
- gate_logs
- meals
- private `student-photos` Storage bucket

Attendance and exam-exceptions are not included in the active V1 cloud-sync scope. Do not add them to this migration without a separate product decision.

## Staff onboarding
The four intended staff email/role assignments have been recorded in the restricted database table `private.staff_invitation_plan`. That table has no grants for anon or authenticated clients. The records are plans only: they do not create Supabase Auth users, send invitations, or create staff memberships.

Provisioning sequence:
1. Invite each address through Supabase Auth using the dashboard or a trusted server-side Admin API.
2. Require each staff member to complete account setup and sign in.
3. Resolve the resulting Auth user UUID by exact verified email.
4. Insert the matching row into `public.staff_memberships` with school_id `ltc_school_01`, full_name, email, role and is_active=true.
5. Verify each role against RLS with separate test accounts before enabling live use.

Never put a Supabase service-role key in the Android app or in GitHub. Never trust a role supplied by the Android client or user-editable JWT metadata.

## Cutover requirements
- Implement Supabase Auth and a trusted staff provisioning flow.
- Replace Firestore access behind the existing repository/cloud-backend boundary.
- Replace Firebase Storage photo upload/download with private Supabase Storage paths: `<school_id>/<student_id>/<filename>`.
- Preserve Room pending-change queue, retry semantics, conflict handling and offline scanning.
- Validate QR signatures and card lifecycle invariants server-side as well as locally.
- Verify school isolation and role-specific RLS using separate authenticated test accounts.
- Run Android unit tests, instrumented tests and a clean GitHub Actions build.
- Do not remove Firebase configuration or switch production endpoints until the full acceptance checklist passes.

## Status
- Supabase schema and core RLS migration: applied.
- RLS auto-enable function public execute grant: revoked.
- Security advisor: rechecked after the permission change.
- LTC school row: created.
- Staff invitation plan: recorded; Auth invitations and membership linking remain pending.
- Student/photo/log data transfer: not started.
- Android backend replacement: not yet implemented or built.
