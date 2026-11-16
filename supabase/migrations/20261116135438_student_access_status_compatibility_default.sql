-- Preserve compatibility with existing app clients that omit access_status
-- during student registration. The gate decision still checks day-scholar and
-- fee status separately, so this default does not grant entry by itself.
ALTER TABLE public.students
  ALTER COLUMN access_status SET DEFAULT 'APPROVED';
