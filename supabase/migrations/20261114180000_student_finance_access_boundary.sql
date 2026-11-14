-- Finance balances are separated from student contact/guardian records.
-- Only explicitly authorized active Admin memberships can read or change balances.
ALTER TABLE public.staff_memberships
  ADD COLUMN IF NOT EXISTS has_finance_access boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS public.student_finances (
  school_id text NOT NULL,
  student_id text NOT NULL,
  outstanding_amount numeric(12,2) NOT NULL DEFAULT 0 CHECK (outstanding_amount >= 0),
  fee_status text NOT NULL DEFAULT 'CLEARED' CHECK (fee_status IN ('CLEARED','OUTSTANDING')),
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
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.staff_memberships m
    WHERE m.school_id = p_school_id
      AND m.user_id = auth.uid()
      AND m.role = 'ADMIN'
      AND m.is_active = true
      AND m.has_finance_access = true
  );
$$;

REVOKE ALL ON FUNCTION private.has_finance_access(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION private.has_finance_access(text) TO authenticated;

DROP POLICY IF EXISTS "finance admins can read student balances" ON public.student_finances;
CREATE POLICY "finance admins can read student balances"
  ON public.student_finances FOR SELECT TO authenticated
  USING (private.has_finance_access(school_id));

DROP POLICY IF EXISTS "finance admins can create student balances" ON public.student_finances;
CREATE POLICY "finance admins can create student balances"
  ON public.student_finances FOR INSERT TO authenticated
  WITH CHECK (private.has_finance_access(school_id));

DROP POLICY IF EXISTS "finance admins can update student balances" ON public.student_finances;
CREATE POLICY "finance admins can update student balances"
  ON public.student_finances FOR UPDATE TO authenticated
  USING (private.has_finance_access(school_id))
  WITH CHECK (private.has_finance_access(school_id));

CREATE OR REPLACE FUNCTION private.sync_finance_fee_status_to_student()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, private, pg_temp
AS $
BEGIN
  UPDATE public.students
  SET fee_status = NEW.fee_status,
      updated_at_ms = NEW.updated_at_ms
  WHERE school_id = NEW.school_id
    AND id = NEW.student_id;
  RETURN NEW;
END;
$;

DROP TRIGGER IF EXISTS sync_finance_fee_status_to_student ON public.student_finances;
CREATE TRIGGER sync_finance_fee_status_to_student
  AFTER INSERT OR UPDATE OF fee_status ON public.student_finances
  FOR EACH ROW EXECUTE FUNCTION private.sync_finance_fee_status_to_student();

CREATE OR REPLACE FUNCTION private.guard_student_fee_status()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, private, pg_temp
AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.fee_status <> 'CLEARED'
       AND NOT private.has_finance_access(NEW.school_id) THEN
      RAISE EXCEPTION 'Explicit finance permission is required to set a non-cleared fee status';
    END IF;
  ELSIF NEW.fee_status IS DISTINCT FROM OLD.fee_status
        AND NOT private.has_finance_access(NEW.school_id) THEN
    RAISE EXCEPTION 'Explicit finance permission is required to change fee status';
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS enforce_student_fee_status_permission ON public.students;
CREATE TRIGGER enforce_student_fee_status_permission
  BEFORE INSERT OR UPDATE OF fee_status ON public.students
  FOR EACH ROW EXECUTE FUNCTION private.guard_student_fee_status();

-- Remove monetary balances from the leadership-readable private profile.
ALTER TABLE public.student_private
  DROP COLUMN IF EXISTS outstanding_amount;
