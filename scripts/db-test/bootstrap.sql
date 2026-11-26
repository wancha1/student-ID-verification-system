-- Minimal Supabase-compatible fixture for the two Blocker 1 migrations.
-- All records are synthetic. This is not a production schema dump.
CREATE ROLE anon NOLOGIN;
CREATE ROLE authenticated NOLOGIN;
CREATE ROLE service_role NOLOGIN;

CREATE SCHEMA auth;
CREATE SCHEMA private;
GRANT USAGE ON SCHEMA private TO authenticated;

CREATE OR REPLACE FUNCTION auth.uid()
RETURNS uuid
LANGUAGE sql STABLE
AS $$
  SELECT NULLIF(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;

CREATE OR REPLACE FUNCTION private.is_active_staff(p_school_id text, p_roles text[])
RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = ''
AS $active_staff$
  SELECT current_setting('test.staff_authorized', true) = 'true'
$active_staff$;

CREATE OR REPLACE FUNCTION private.has_finance_access(p_school_id text)
RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = ''
AS $finance_access$
  SELECT current_setting('test.finance_authorized', true) = 'true'
$finance_access$;

CREATE TABLE public.trusted_issuers (
  school_id text NOT NULL,
  key_id text NOT NULL,
  public_key_base64 text NOT NULL,
  label text NOT NULL,
  enrolled_at_ms bigint NOT NULL,
  is_revoked boolean NOT NULL DEFAULT false,
  PRIMARY KEY (school_id, key_id)
);
ALTER TABLE public.trusted_issuers ENABLE ROW LEVEL SECURITY;
GRANT SELECT, UPDATE, INSERT, DELETE ON public.trusted_issuers TO authenticated;
CREATE POLICY "administrators can enroll issuer public keys"
  ON public.trusted_issuers FOR INSERT TO authenticated WITH CHECK (true);
CREATE POLICY "administrators can update issuer public keys"
  ON public.trusted_issuers FOR UPDATE TO authenticated
  USING (private.is_active_staff(school_id, ARRAY['ADMIN']::text[]))
  WITH CHECK (private.is_active_staff(school_id, ARRAY['ADMIN']::text[]));
CREATE POLICY "active staff can read issuer public keys"
  ON public.trusted_issuers FOR SELECT TO authenticated USING (true);

CREATE TABLE public.students (
  id text PRIMARY KEY,
  school_id text NOT NULL,
  student_number text NOT NULL,
  fee_status text NOT NULL DEFAULT 'CLEARED',
  updated_at_ms bigint NOT NULL DEFAULT 0
);
ALTER TABLE public.students ENABLE ROW LEVEL SECURITY;
GRANT SELECT, UPDATE ON public.students TO authenticated;

CREATE TABLE public.student_finances (
  school_id text NOT NULL,
  student_id text NOT NULL REFERENCES public.students(id),
  fee_status text NOT NULL,
  outstanding_amount bigint NOT NULL DEFAULT 0,
  updated_at_ms bigint NOT NULL,
  PRIMARY KEY (school_id, student_id)
);

-- Pre-fix versions: the finance sync trigger updates students.fee_status,
-- whose guard sees the caller context and rejects the trusted nested update.
CREATE OR REPLACE FUNCTION private.guard_student_fee_status()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $guard_student_fee_status$
BEGIN
  IF TG_OP='INSERT' THEN
    IF NEW.fee_status <> 'CLEARED' AND NOT private.has_finance_access(NEW.school_id) THEN
      RAISE EXCEPTION 'Explicit finance permission is required to set a non-cleared fee status';
    END IF;
  ELSIF NEW.fee_status IS DISTINCT FROM OLD.fee_status AND NOT private.has_finance_access(NEW.school_id) THEN
    RAISE EXCEPTION 'Explicit finance permission is required to change fee status';
  END IF;
  RETURN NEW;
END;
$guard_student_fee_status$;

CREATE TRIGGER trg_guard_student_fee_status
BEFORE INSERT OR UPDATE OF fee_status ON public.students
FOR EACH ROW EXECUTE FUNCTION private.guard_student_fee_status();

CREATE OR REPLACE FUNCTION private.sync_finance_fee_status_to_student()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = ''
AS $sync_finance_fee_status_to_student$
BEGIN
  UPDATE public.students
  SET fee_status = NEW.fee_status, updated_at_ms = NEW.updated_at_ms
  WHERE school_id = NEW.school_id AND id = NEW.student_id;
  RETURN NEW;
END;
$sync_finance_fee_status_to_student$;

CREATE TRIGGER trg_sync_finance_fee_status_to_student
AFTER INSERT OR UPDATE OF fee_status ON public.student_finances
FOR EACH ROW EXECUTE FUNCTION private.sync_finance_fee_status_to_student();

INSERT INTO public.trusted_issuers
  (school_id, key_id, public_key_base64, label, enrolled_at_ms, is_revoked)
VALUES
  ('school-test', 'baseline-key', 'c3ludGhldGlj', 'Synthetic baseline key', 1700000000000, false);

INSERT INTO public.students (id, school_id, student_number)
VALUES ('student-test-1', 'school-test', 'SYN-001');
