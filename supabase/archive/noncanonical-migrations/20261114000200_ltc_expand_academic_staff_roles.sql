-- Applied to the LTC Supabase project as ltc_expand_academic_staff_roles.
ALTER TABLE public.staff_memberships DROP CONSTRAINT IF EXISTS staff_memberships_role_check;
ALTER TABLE public.staff_memberships ADD CONSTRAINT staff_memberships_role_check
CHECK (role = ANY (ARRAY['ADMIN','HEAD_TEACHER','TEACHER','DIRECTOR_OF_STUDIES','BURSAR','EXAM_STAFF',
  'GATE_OFFICER','SECURITY_HEAD','MEAL_STAFF','MATRON_PATRON']::text[]));

DROP POLICY IF EXISTS "admins can create staff memberships" ON public.staff_memberships;
CREATE POLICY "admins can create staff memberships" ON public.staff_memberships FOR INSERT TO authenticated
WITH CHECK (private.is_active_staff(school_id, ARRAY['ADMIN']::text[]) AND user_id <> (SELECT auth.uid())
  AND role = ANY (ARRAY['ADMIN','HEAD_TEACHER','TEACHER','DIRECTOR_OF_STUDIES','BURSAR','EXAM_STAFF',
    'GATE_OFFICER','SECURITY_HEAD','MEAL_STAFF','MATRON_PATRON']::text[]));

DROP POLICY IF EXISTS "admins can update other staff memberships" ON public.staff_memberships;
CREATE POLICY "admins can update other staff memberships" ON public.staff_memberships FOR UPDATE TO authenticated
USING (private.is_active_staff(school_id, ARRAY['ADMIN']::text[]) AND user_id <> (SELECT auth.uid()))
WITH CHECK (private.is_active_staff(school_id, ARRAY['ADMIN']::text[]) AND user_id <> (SELECT auth.uid())
  AND role = ANY (ARRAY['ADMIN','HEAD_TEACHER','TEACHER','DIRECTOR_OF_STUDIES','BURSAR','EXAM_STAFF',
    'GATE_OFFICER','SECURITY_HEAD','MEAL_STAFF','MATRON_PATRON']::text[]));

DROP POLICY IF EXISTS "attendance staff can read attendance" ON public.attendance_records;
CREATE POLICY "attendance staff can read attendance" ON public.attendance_records FOR SELECT TO authenticated
USING (private.is_active_staff(school_id, ARRAY['ADMIN','HEAD_TEACHER','TEACHER','DIRECTOR_OF_STUDIES']::text[]));
DROP POLICY IF EXISTS "teachers can create attendance" ON public.attendance_records;
CREATE POLICY "teachers can create attendance" ON public.attendance_records FOR INSERT TO authenticated
WITH CHECK (private.is_active_staff(school_id, ARRAY['ADMIN','HEAD_TEACHER','TEACHER','DIRECTOR_OF_STUDIES']::text[]));
DROP POLICY IF EXISTS "teachers can update attendance" ON public.attendance_records;
CREATE POLICY "teachers can update attendance" ON public.attendance_records FOR UPDATE TO authenticated
USING (private.is_active_staff(school_id, ARRAY['ADMIN','HEAD_TEACHER','TEACHER','DIRECTOR_OF_STUDIES']::text[]))
WITH CHECK (private.is_active_staff(school_id, ARRAY['ADMIN','HEAD_TEACHER','TEACHER','DIRECTOR_OF_STUDIES']::text[]));
DROP POLICY IF EXISTS "exam teams can read exceptions" ON public.exam_exceptions;
CREATE POLICY "exam teams can read exceptions" ON public.exam_exceptions FOR SELECT TO authenticated
USING (private.is_active_staff(school_id, ARRAY['ADMIN','HEAD_TEACHER','BURSAR','EXAM_STAFF']::text[]));
