-- Additional Scholink RLS regression coverage.
-- Runs only in the disposable PostgreSQL CI fixture, after canonical audit migrations.
-- Synthetic identities/data only; this file must never be pointed at a live database.

\set ON_ERROR_STOP on

-- Model the production membership capability used by the finance boundary.
CREATE TABLE public.staff_memberships (
  school_id text NOT NULL,
  user_id uuid NOT NULL,
  role text NOT NULL,
  is_active boolean NOT NULL DEFAULT true,
  has_finance_access boolean NOT NULL DEFAULT false,
  PRIMARY KEY (school_id, user_id)
);

CREATE OR REPLACE FUNCTION private.is_active_staff(p_school_id text, p_roles text[])
RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = ''
AS $fn$
  SELECT CASE
    WHEN current_setting('test.use_membership_auth', true) = 'true' THEN EXISTS (
      SELECT 1
      FROM public.staff_memberships m
      WHERE m.school_id = p_school_id
        AND m.user_id = auth.uid()
        AND m.is_active = true
        AND (p_roles IS NULL OR m.role = ANY(p_roles))
    )
    ELSE current_setting('test.staff_authorized', true) = 'true'
      AND p_school_id = COALESCE(
        NULLIF(current_setting('test.staff_school_id', true), ''),
        'school-test'
      )
      AND (
        p_roles IS NULL
        OR current_setting('test.staff_role', true) = ANY(p_roles)
      )
  END
$fn$;

-- Finance schema/policies mirror the explicit active-Admin capability contract.
ALTER TABLE public.students
  ADD CONSTRAINT students_school_id_id_security_test_unique UNIQUE (school_id, id);
INSERT INTO public.students (id, school_id, student_number)
VALUES
  ('finance-student-local', 'school-test', 'FIN-LOCAL'),
  ('finance-student-foreign', 'school-other', 'FIN-FOREIGN');

CREATE TABLE public.student_finances (
  school_id text NOT NULL,
  student_id text NOT NULL,
  outstanding_amount numeric(12,2) NOT NULL DEFAULT 0 CHECK (outstanding_amount >= 0),
  fee_status text NOT NULL DEFAULT 'CLEARED'
    CHECK (fee_status IN ('CLEARED','OUTSTANDING')),
  updated_at_ms bigint NOT NULL,
  PRIMARY KEY (school_id, student_id),
  FOREIGN KEY (school_id, student_id)
    REFERENCES public.students (school_id, id) ON DELETE CASCADE
);
ALTER TABLE public.student_finances ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.student_finances FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON public.student_finances TO authenticated;

CREATE OR REPLACE FUNCTION private.has_finance_access(p_school_id text)
RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = ''
AS $fn$
  SELECT EXISTS (
    SELECT 1
    FROM public.staff_memberships m
    WHERE m.school_id = p_school_id
      AND m.user_id = auth.uid()
      AND m.role = 'ADMIN'
      AND m.is_active = true
      AND m.has_finance_access = true
  )
$fn$;
REVOKE ALL ON FUNCTION private.has_finance_access(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION private.has_finance_access(text) TO authenticated;

CREATE POLICY finance_test_select
  ON public.student_finances FOR SELECT TO authenticated
  USING (private.has_finance_access(school_id));
CREATE POLICY finance_test_insert
  ON public.student_finances FOR INSERT TO authenticated
  WITH CHECK (private.has_finance_access(school_id));
CREATE POLICY finance_test_update
  ON public.student_finances FOR UPDATE TO authenticated
  USING (private.has_finance_access(school_id))
  WITH CHECK (private.has_finance_access(school_id));

INSERT INTO public.staff_memberships
  (school_id, user_id, role, is_active, has_finance_access)
VALUES
  ('school-test', '00000000-0000-0000-0000-000000000701', 'ADMIN', true, true),
  ('school-test', '00000000-0000-0000-0000-000000000702', 'ADMIN', true, false),
  ('school-test', '00000000-0000-0000-0000-000000000703', 'GATE_OFFICER', true, false),
  ('school-test', '00000000-0000-0000-0000-000000000704', 'TEACHER', true, false),
  ('school-test', '00000000-0000-0000-0000-000000000705', 'HEAD_TEACHER', true, false),
  ('school-test', '00000000-0000-0000-0000-000000000706', 'MEAL_STAFF', true, false);

INSERT INTO public.student_finances
  (school_id, student_id, outstanding_amount, fee_status, updated_at_ms)
VALUES
  ('school-test', 'finance-student-local', 125000, 'OUTSTANDING', 1700000000000),
  ('school-other', 'finance-student-foreign', 99000, 'OUTSTANDING', 1700000000001);

-- Finance: only the active finance-enabled Admin may read balances, and only
-- within the school for which that capability is granted.
SELECT set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000701', false);
SET ROLE authenticated;
DO $finance_admin$
BEGIN
  IF (SELECT count(*) FROM public.student_finances WHERE school_id = 'school-test') <> 1 THEN
    RAISE EXCEPTION 'Finance-enabled Admin must read the own-school balance';
  END IF;
  IF (SELECT count(*) FROM public.student_finances WHERE school_id = 'school-other') <> 0 THEN
    RAISE EXCEPTION 'Finance-enabled Admin must not read another school balance';
  END IF;
END
$finance_admin$;
RESET ROLE;

SELECT set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000702', false);
SET ROLE authenticated;
DO $finance_admin_without_capability$
BEGIN
  IF (SELECT count(*) FROM public.student_finances WHERE school_id = 'school-test') <> 0 THEN
    RAISE EXCEPTION 'Admin without finance capability read monetary balances';
  END IF;
END
$finance_admin_without_capability$;
RESET ROLE;

SELECT set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000703', false);
SET ROLE authenticated;
DO $finance_gate$
BEGIN
  IF (SELECT count(*) FROM public.student_finances) <> 0 THEN
    RAISE EXCEPTION 'Gate Officer read monetary balances';
  END IF;
END
$finance_gate$;
RESET ROLE;

-- Cross-school audit isolation: seed foreign-school events as the fixture owner,
-- then prove an authorized local role cannot read them through either audit table.
INSERT INTO public.attendance_record_events
  (school_id, record_id, event_type, actor_user_id, new_record)
VALUES
  ('school-other', 'foreign-attendance-sentinel', 'RECORDED',
   '00000000-0000-0000-0000-000000000704', '{"synthetic":true}');
INSERT INTO public.exam_exception_events
  (school_id, record_id, event_type, actor_user_id, new_record)
VALUES
  ('school-other', 'foreign-exam-sentinel', 'REQUESTED',
   '00000000-0000-0000-0000-000000000704', '{"synthetic":true}');

SELECT set_config('test.use_membership_auth', 'true', false);
SELECT set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000701', false);
SET ROLE authenticated;
DO $cross_school_admin$
BEGIN
  IF (SELECT count(*) FROM public.attendance_record_events
      WHERE school_id = 'school-other') <> 0 THEN
    RAISE EXCEPTION 'Admin read another school attendance audit events';
  END IF;
  IF (SELECT count(*) FROM public.exam_exception_events
      WHERE school_id = 'school-other') <> 0 THEN
    RAISE EXCEPTION 'Admin read another school exam audit events';
  END IF;
END
$cross_school_admin$;
RESET ROLE;

-- Exercise the cross-school denial for each role and preserve the intended
-- within-school audit visibility matrix.
SELECT set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000705', false);
SET ROLE authenticated;
DO $cross_school_head_teacher$
BEGIN
  IF (SELECT count(*) FROM public.attendance_record_events WHERE school_id = 'school-other') <> 0
     OR (SELECT count(*) FROM public.exam_exception_events WHERE school_id = 'school-other') <> 0 THEN
    RAISE EXCEPTION 'Head Teacher read foreign-school audit events';
  END IF;
  IF (SELECT count(*) FROM public.attendance_record_events WHERE school_id = 'school-test') = 0
     OR (SELECT count(*) FROM public.exam_exception_events WHERE school_id = 'school-test') = 0 THEN
    RAISE EXCEPTION 'Head Teacher lost permitted own-school audit visibility';
  END IF;
END
$cross_school_head_teacher$;
RESET ROLE;

SELECT set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000704', false);
SET ROLE authenticated;
DO $cross_school_teacher$
BEGIN
  IF (SELECT count(*) FROM public.attendance_record_events WHERE school_id = 'school-other') <> 0
     OR (SELECT count(*) FROM public.exam_exception_events WHERE school_id = 'school-other') <> 0 THEN
    RAISE EXCEPTION 'Teacher read foreign-school audit events';
  END IF;
  IF (SELECT count(*) FROM public.attendance_record_events WHERE school_id = 'school-test') = 0
     OR (SELECT count(*) FROM public.exam_exception_events WHERE school_id = 'school-test') <> 0 THEN
    RAISE EXCEPTION 'Teacher audit visibility does not match the attendance-only contract';
  END IF;
END
$cross_school_teacher$;
RESET ROLE;

SELECT set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000703', false);
SET ROLE authenticated;
DO $cross_school_gate$
BEGIN
  IF (SELECT count(*) FROM public.attendance_record_events) <> 0
     OR (SELECT count(*) FROM public.exam_exception_events) <> 0 THEN
    RAISE EXCEPTION 'Gate Officer read academic audit events';
  END IF;
END
$cross_school_gate$;
RESET ROLE;

SELECT set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000706', false);
SET ROLE authenticated;
DO $cross_school_meal$
BEGIN
  IF (SELECT count(*) FROM public.attendance_record_events) <> 0
     OR (SELECT count(*) FROM public.exam_exception_events) <> 0 THEN
    RAISE EXCEPTION 'Meal Staff read academic audit events';
  END IF;
END
$cross_school_meal$;
RESET ROLE;

-- Actor attribution survives membership revocation. A revoked staff identity
-- cannot create another attendance event, while its earlier audit record remains.
SELECT set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000704', false);
SET ROLE authenticated;
UPDATE public.attendance_records
SET notes = 'Actor attribution regression probe',
    updated_at_ms = updated_at_ms + 1
WHERE school_id = 'school-test' AND id = 'attendance-1';
RESET ROLE;

DO $actor_before_revocation$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM public.attendance_record_events
    WHERE school_id = 'school-test'
      AND record_id = 'attendance-1'
      AND actor_user_id = '00000000-0000-0000-0000-000000000704'
      AND new_record->>'notes' = 'Actor attribution regression probe'
  ) THEN
    RAISE EXCEPTION 'Attendance audit event did not preserve the authenticated actor';
  END IF;
END
$actor_before_revocation$;

UPDATE public.staff_memberships
SET is_active = false
WHERE school_id = 'school-test'
  AND user_id = '00000000-0000-0000-0000-000000000704';

SELECT set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000704', false);
SET ROLE authenticated;
DO $actor_after_revocation$
DECLARE affected integer;
BEGIN
  UPDATE public.attendance_records
  SET notes = 'This update must be denied',
      updated_at_ms = updated_at_ms + 1
  WHERE school_id = 'school-test' AND id = 'attendance-1';
  GET DIAGNOSTICS affected = ROW_COUNT;
  IF affected <> 0 THEN
    RAISE EXCEPTION 'Inactive staff membership changed an attendance record';
  END IF;
END
$actor_after_revocation$;
RESET ROLE;

DO $actor_immutable$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM public.attendance_record_events
    WHERE school_id = 'school-test'
      AND record_id = 'attendance-1'
      AND actor_user_id = '00000000-0000-0000-0000-000000000704'
      AND new_record->>'notes' = 'Actor attribution regression probe'
  ) THEN
    RAISE EXCEPTION 'Deactivating membership erased or changed prior actor attribution';
  END IF;
END
$actor_immutable$;

SELECT 'PASS: finance read isolation, cross-school audit isolation, and membership actor attribution' AS result;
