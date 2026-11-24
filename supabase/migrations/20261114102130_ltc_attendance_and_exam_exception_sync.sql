-- Extend LTC's clean Supabase backend to attendance and exam-exception workflows.
-- Existing Firebase data is not imported. All new tables remain empty until the app syncs records.

ALTER TABLE public.staff_memberships
  DROP CONSTRAINT IF EXISTS staff_memberships_role_check;
ALTER TABLE public.staff_memberships
  ADD CONSTRAINT staff_memberships_role_check
  CHECK (role = ANY (ARRAY[
    'ADMIN','HEAD_TEACHER','TEACHER','BURSAR','GATE_OFFICER','MEAL_STAFF'
  ]::text[]));

-- Keep the role allow-list in staff-management RLS aligned with the constraint.
DROP POLICY IF EXISTS "admins can create staff memberships" ON public.staff_memberships;
CREATE POLICY "admins can create staff memberships"
ON public.staff_memberships FOR INSERT TO authenticated
WITH CHECK (
  private.is_active_staff(school_id, ARRAY['ADMIN']::text[])
  AND user_id <> (SELECT auth.uid())
  AND role = ANY (ARRAY['ADMIN','HEAD_TEACHER','TEACHER','BURSAR','GATE_OFFICER','MEAL_STAFF']::text[])
);

DROP POLICY IF EXISTS "admins can update other staff memberships" ON public.staff_memberships;
CREATE POLICY "admins can update other staff memberships"
ON public.staff_memberships FOR UPDATE TO authenticated
USING (
  private.is_active_staff(school_id, ARRAY['ADMIN']::text[])
  AND user_id <> (SELECT auth.uid())
)
WITH CHECK (
  private.is_active_staff(school_id, ARRAY['ADMIN']::text[])
  AND user_id <> (SELECT auth.uid())
  AND role = ANY (ARRAY['ADMIN','HEAD_TEACHER','TEACHER','BURSAR','GATE_OFFICER','MEAL_STAFF']::text[])
);

CREATE TABLE IF NOT EXISTS public.attendance_records (
  school_id text NOT NULL REFERENCES public.schools(id) ON DELETE RESTRICT,
  id text NOT NULL,
  student_id text NOT NULL,
  student_number text NOT NULL,
  student_name text NOT NULL,
  grade_class text NOT NULL,
  subject text NOT NULL,
  session_name text NOT NULL,
  lesson_date text NOT NULL,
  timestamp_ms bigint NOT NULL,
  teacher_name text NOT NULL,
  is_present boolean NOT NULL,
  notes text NOT NULL DEFAULT '',
  is_correction boolean NOT NULL DEFAULT false,
  corrected_by text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at_ms bigint NOT NULL,
  CONSTRAINT attendance_records_pkey PRIMARY KEY (school_id,id),
  CONSTRAINT attendance_records_student_fkey
    FOREIGN KEY (school_id,student_id) REFERENCES public.students(school_id,id) ON DELETE RESTRICT,
  CONSTRAINT attendance_records_student_session_key
    UNIQUE (school_id,student_id,lesson_date,session_name)
);
CREATE INDEX IF NOT EXISTS attendance_records_school_date_idx
  ON public.attendance_records (school_id,lesson_date);
CREATE INDEX IF NOT EXISTS attendance_records_school_class_date_idx
  ON public.attendance_records (school_id,grade_class,lesson_date);

CREATE TABLE IF NOT EXISTS public.exam_exceptions (
  school_id text NOT NULL REFERENCES public.schools(id) ON DELETE RESTRICT,
  id text NOT NULL,
  student_id text NOT NULL,
  student_number text NOT NULL,
  student_name text NOT NULL,
  exam_code text NOT NULL,
  requested_by_bursar text NOT NULL,
  recommendation_reason text NOT NULL,
  is_approved boolean NOT NULL DEFAULT false,
  approved_by_head_teacher text,
  requested_at_ms bigint NOT NULL,
  approved_at_ms bigint,
  updated_at_ms bigint NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT exam_exceptions_pkey PRIMARY KEY (school_id,id),
  CONSTRAINT exam_exceptions_student_fkey
    FOREIGN KEY (school_id,student_id) REFERENCES public.students(school_id,id) ON DELETE RESTRICT,
  CONSTRAINT exam_exceptions_student_exam_key
    UNIQUE (school_id,student_id,exam_code),
  CONSTRAINT exam_exceptions_approval_consistency_check
    CHECK (
      (is_approved = false AND approved_at_ms IS NULL)
      OR (is_approved = true AND approved_at_ms IS NOT NULL AND approved_by_head_teacher IS NOT NULL)
    )
);
CREATE INDEX IF NOT EXISTS exam_exceptions_school_approval_idx
  ON public.exam_exceptions (school_id,is_approved,requested_at_ms DESC);

ALTER TABLE public.attendance_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.exam_exceptions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "attendance staff can read attendance" ON public.attendance_records;
CREATE POLICY "attendance staff can read attendance"
ON public.attendance_records FOR SELECT TO authenticated
USING (private.is_active_staff(school_id, ARRAY['ADMIN','HEAD_TEACHER','TEACHER']::text[]));

DROP POLICY IF EXISTS "teachers can create attendance" ON public.attendance_records;
CREATE POLICY "teachers can create attendance"
ON public.attendance_records FOR INSERT TO authenticated
WITH CHECK (private.is_active_staff(school_id, ARRAY['ADMIN','HEAD_TEACHER','TEACHER']::text[]));

DROP POLICY IF EXISTS "teachers can update attendance" ON public.attendance_records;
CREATE POLICY "teachers can update attendance"
ON public.attendance_records FOR UPDATE TO authenticated
USING (private.is_active_staff(school_id, ARRAY['ADMIN','HEAD_TEACHER','TEACHER']::text[]))
WITH CHECK (private.is_active_staff(school_id, ARRAY['ADMIN','HEAD_TEACHER','TEACHER']::text[]));

DROP POLICY IF EXISTS "exam teams can read exceptions" ON public.exam_exceptions;
CREATE POLICY "exam teams can read exceptions"
ON public.exam_exceptions FOR SELECT TO authenticated
USING (private.is_active_staff(school_id, ARRAY['ADMIN','HEAD_TEACHER','BURSAR']::text[]));

DROP POLICY IF EXISTS "bursars can create pending exceptions" ON public.exam_exceptions;
CREATE POLICY "bursars can create pending exceptions"
ON public.exam_exceptions FOR INSERT TO authenticated
WITH CHECK (
  is_approved = false
  AND approved_at_ms IS NULL
  AND approved_by_head_teacher IS NULL
  AND private.is_active_staff(school_id, ARRAY['ADMIN','BURSAR']::text[])
);

DROP POLICY IF EXISTS "bursars can update pending exceptions" ON public.exam_exceptions;
CREATE POLICY "bursars can update pending exceptions"
ON public.exam_exceptions FOR UPDATE TO authenticated
USING (
  is_approved = false
  AND private.is_active_staff(school_id, ARRAY['BURSAR']::text[])
)
WITH CHECK (
  is_approved = false
  AND approved_at_ms IS NULL
  AND approved_by_head_teacher IS NULL
  AND private.is_active_staff(school_id, ARRAY['BURSAR']::text[])
);

DROP POLICY IF EXISTS "leadership can approve exam exceptions" ON public.exam_exceptions;
CREATE POLICY "leadership can approve exam exceptions"
ON public.exam_exceptions FOR UPDATE TO authenticated
USING (private.is_active_staff(school_id, ARRAY['ADMIN','HEAD_TEACHER']::text[]))
WITH CHECK (private.is_active_staff(school_id, ARRAY['ADMIN','HEAD_TEACHER']::text[]));

GRANT SELECT, INSERT, UPDATE ON public.attendance_records TO authenticated;
GRANT SELECT, INSERT, UPDATE ON public.exam_exceptions TO authenticated;
REVOKE ALL ON public.attendance_records, public.exam_exceptions FROM anon;
