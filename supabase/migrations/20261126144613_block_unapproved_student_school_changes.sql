-- Fail closed on silent cross-school reassignment.
-- This is an interim guard, not the final transfer workflow. A future approved
-- transfer operation must add source/destination authorization and immutable
-- transfer events before this guard is relaxed.

CREATE OR REPLACE FUNCTION private.prevent_unapproved_student_school_change()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $function$
BEGIN
  IF NEW.school_id IS DISTINCT FROM OLD.school_id THEN
    RAISE EXCEPTION
      'Direct student school reassignment is prohibited; use the approved school transfer workflow';
  END IF;
  RETURN NEW;
END;
$function$;

REVOKE ALL ON FUNCTION private.prevent_unapproved_student_school_change()
  FROM PUBLIC, anon, authenticated, service_role;

DROP TRIGGER IF EXISTS students_prevent_unapproved_school_change
  ON public.students;
CREATE TRIGGER students_prevent_unapproved_school_change
  BEFORE UPDATE OF school_id ON public.students
  FOR EACH ROW
  EXECUTE FUNCTION private.prevent_unapproved_student_school_change();
