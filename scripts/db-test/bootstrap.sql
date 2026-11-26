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
AS $$
  SELECT current_setting('test.staff_authorized', true) = 'true'
$$;

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
  student_number text NOT NULL
);
ALTER TABLE public.students ENABLE ROW LEVEL SECURITY;
GRANT SELECT, UPDATE ON public.students TO authenticated;

INSERT INTO public.trusted_issuers
  (school_id, key_id, public_key_base64, label, enrolled_at_ms, is_revoked)
VALUES
  ('school-test', 'baseline-key', 'c3ludGhldGlj', 'Synthetic baseline key', 1700000000000, false);

INSERT INTO public.students (id, school_id, student_number)
VALUES ('student-test-1', 'school-test', 'SYN-001');
