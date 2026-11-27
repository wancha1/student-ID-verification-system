-- Scholink V1 data-integrity hardening.
-- Existing meal history is retained; new meal rows are limited to the two served meals.
-- Attendance and exam row mutations are captured as immutable before/after events.

ALTER TABLE public.meals
  ADD CONSTRAINT meals_v1_served_meal_type_check
  CHECK (meal_type IN ('LUNCH', 'DINNER')) NOT VALID;

CREATE TABLE public.attendance_record_events (
  event_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  school_id text NOT NULL,
  record_id text NOT NULL,
  event_type text NOT NULL CHECK (event_type IN ('RECORDED', 'CORRECTED', 'UPDATED')),
  actor_user_id uuid,
  occurred_at timestamptz NOT NULL DEFAULT now(),
  old_record jsonb,
  new_record jsonb NOT NULL
);
CREATE INDEX attendance_record_events_school_record_idx
  ON public.attendance_record_events (school_id, record_id, event_id);

CREATE TABLE public.exam_exception_events (
  event_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  school_id text NOT NULL,
  record_id text NOT NULL,
  event_type text NOT NULL CHECK (event_type IN ('REQUESTED', 'APPROVED', 'REJECTED', 'AMENDED')),
  actor_user_id uuid,
  occurred_at timestamptz NOT NULL DEFAULT now(),
  old_record jsonb,
  new_record jsonb NOT NULL
);
CREATE INDEX exam_exception_events_school_record_idx
  ON public.exam_exception_events (school_id, record_id, event_id);

ALTER TABLE public.attendance_record_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.exam_exception_events ENABLE ROW LEVEL SECURITY;
GRANT SELECT ON public.attendance_record_events, public.exam_exception_events TO authenticated;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON public.attendance_record_events, public.exam_exception_events FROM anon, authenticated;
CREATE POLICY attendance_event_read_policy ON public.attendance_record_events
  FOR SELECT TO authenticated
  USING (private.is_active_staff(school_id, ARRAY['ADMIN','HEAD_TEACHER','TEACHER','DIRECTOR_OF_STUDIES']::text[]));
CREATE POLICY exam_event_read_policy ON public.exam_exception_events
  FOR SELECT TO authenticated
  USING (private.is_active_staff(school_id, ARRAY['ADMIN','HEAD_TEACHER','BURSAR','EXAM_STAFF']::text[]));

CREATE OR REPLACE FUNCTION private.reject_scholink_audit_mutation()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $$
BEGIN
  RAISE EXCEPTION 'Scholink audit events are append-only';
END;
$$;

CREATE TRIGGER attendance_record_events_immutable
  BEFORE UPDATE OR DELETE ON public.attendance_record_events
  FOR EACH ROW EXECUTE FUNCTION private.reject_scholink_audit_mutation();
CREATE TRIGGER exam_exception_events_immutable
  BEFORE UPDATE OR DELETE ON public.exam_exception_events
  FOR EACH ROW EXECUTE FUNCTION private.reject_scholink_audit_mutation();

CREATE OR REPLACE FUNCTION private.audit_attendance_record_change()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  old_data jsonb;
  new_data jsonb;
  kind text;
BEGIN
  IF TG_OP = 'INSERT' THEN
    new_data := to_jsonb(NEW);
    kind := 'RECORDED';
    INSERT INTO public.attendance_record_events
      (school_id, record_id, event_type, actor_user_id, new_record)
    VALUES (NEW.school_id, NEW.id, kind, auth.uid(), new_data);
    RETURN NEW;
  END IF;

  old_data := to_jsonb(OLD);
  new_data := to_jsonb(NEW);
  IF old_data IS DISTINCT FROM new_data THEN
    IF NEW.is_present IS DISTINCT FROM OLD.is_present
       OR NEW.notes IS DISTINCT FROM OLD.notes
       OR NEW.is_correction IS DISTINCT FROM OLD.is_correction
       OR NEW.corrected_by IS DISTINCT FROM OLD.corrected_by THEN
      kind := 'CORRECTED';
    ELSE
      kind := 'UPDATED';
    END IF;
    INSERT INTO public.attendance_record_events
      (school_id, record_id, event_type, actor_user_id, old_record, new_record)
    VALUES (NEW.school_id, NEW.id, kind, auth.uid(), old_data, new_data);
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER attendance_record_audit_after_change
  AFTER INSERT OR UPDATE ON public.attendance_records
  FOR EACH ROW EXECUTE FUNCTION private.audit_attendance_record_change();

-- Extend the current boolean approval model with an explicit rejection state.
-- Existing approved rows are backfilled; pending rows remain pending.
ALTER TABLE public.exam_exceptions
  ADD COLUMN decision_status text NOT NULL DEFAULT 'PENDING',
  ADD COLUMN decision_reason text,\n  ADD COLUMN decision_by_user_id uuid;
UPDATE public.exam_exceptions
SET decision_status = CASE WHEN is_approved THEN 'APPROVED' ELSE 'PENDING' END;
ALTER TABLE public.exam_exceptions
  ADD CONSTRAINT exam_exceptions_decision_status_check
  CHECK (decision_status IN ('PENDING', 'APPROVED', 'REJECTED'));

CREATE OR REPLACE FUNCTION private.audit_exam_exception_change()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  old_data jsonb;
  new_data jsonb;
  kind text;
BEGIN
  IF TG_OP = 'INSERT' THEN
    new_data := to_jsonb(NEW);
    kind := 'REQUESTED';
    INSERT INTO public.exam_exception_events
      (school_id, record_id, event_type, actor_user_id, new_record)
    VALUES (NEW.school_id, NEW.id, kind, auth.uid(), new_data);
    RETURN NEW;
  END IF;

  old_data := to_jsonb(OLD);
  new_data := to_jsonb(NEW);
  IF old_data IS DISTINCT FROM new_data THEN
    kind := CASE
      WHEN OLD.decision_status IS DISTINCT FROM NEW.decision_status
           AND NEW.decision_status = 'APPROVED' THEN 'APPROVED'
      WHEN OLD.decision_status IS DISTINCT FROM NEW.decision_status
           AND NEW.decision_status = 'REJECTED' THEN 'REJECTED'
      ELSE 'AMENDED'
    END;
    INSERT INTO public.exam_exception_events
      (school_id, record_id, event_type, actor_user_id, old_record, new_record)
    VALUES (NEW.school_id, NEW.id, kind, auth.uid(), old_data, new_data);
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER exam_exception_audit_after_change
  AFTER INSERT OR UPDATE ON public.exam_exceptions
  FOR EACH ROW EXECUTE FUNCTION private.audit_exam_exception_change();

-- Leadership decision endpoint. RLS still applies because this is SECURITY INVOKER.
CREATE OR REPLACE FUNCTION public.scholink_decide_exam_exception(
  p_school_id text,
  p_record_id text,
  p_decision text,
  p_reason text DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = ''
AS $$
DECLARE
  now_ms bigint := (extract(epoch FROM clock_timestamp()) * 1000)::bigint;
  current_status text;
BEGIN
  IF p_decision NOT IN ('APPROVED', 'REJECTED') THEN
    RAISE EXCEPTION 'Decision must be APPROVED or REJECTED';
  END IF;
  IF NOT private.is_active_staff(p_school_id, ARRAY['ADMIN','HEAD_TEACHER']::text[]) THEN
    RAISE EXCEPTION 'Only school leadership can decide exam exceptions'
      USING ERRCODE = '42501';
  END IF;

  SELECT decision_status INTO current_status
  FROM public.exam_exceptions
  WHERE school_id = p_school_id AND id = p_record_id
  FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Exam exception not found';
  END IF;
  -- Retrying the same decision is a no-op; a different second decision is forbidden.
  IF current_status = p_decision THEN
    RETURN;
  END IF;
  IF current_status <> 'PENDING' THEN
    RAISE EXCEPTION 'Exam exception has already received a final decision';
  END IF;

  UPDATE public.exam_exceptions
  SET decision_status = p_decision,
      decision_reason = NULLIF(btrim(p_reason), ''),
      is_approved = (p_decision = 'APPROVED'),
      approved_by_head_teacher = CASE WHEN p_decision = 'APPROVED' THEN 'Authorized school leadership' ELSE NULL END,
      approved_at_ms = CASE WHEN p_decision = 'APPROVED' THEN now_ms ELSE NULL END,
      updated_at_ms = now_ms
  WHERE school_id = p_school_id
    AND id = p_record_id
    AND decision_status = 'PENDING';
END;
$$;

REVOKE ALL ON FUNCTION public.scholink_decide_exam_exception(text, text, text, text) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.scholink_decide_exam_exception(text, text, text, text) TO authenticated;
