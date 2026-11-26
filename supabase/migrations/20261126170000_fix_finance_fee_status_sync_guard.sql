-- Allow the trusted finance-to-student synchronization trigger to update
-- students.fee_status without weakening direct client-side finance controls.
-- The internal context flag is set only inside the SECURITY DEFINER sync trigger.

CREATE OR REPLACE FUNCTION private.guard_student_fee_status()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN
  IF pg_catalog.current_setting('app.finance_sync', true) = 'on' THEN
    RETURN NEW;
  END IF;

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
$function$;

CREATE OR REPLACE FUNCTION private.sync_finance_fee_status_to_student()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
  v_previous_finance_sync text;
BEGIN
  v_previous_finance_sync := pg_catalog.current_setting('app.finance_sync', true);
  PERFORM pg_catalog.set_config('app.finance_sync', 'on', true);

  BEGIN
    UPDATE public.students
    SET fee_status = NEW.fee_status,
        updated_at_ms = NEW.updated_at_ms
    WHERE school_id = NEW.school_id
      AND id = NEW.student_id;
  EXCEPTION WHEN OTHERS THEN
    PERFORM pg_catalog.set_config('app.finance_sync', COALESCE(v_previous_finance_sync, ''), true);
    RAISE;
  END;

  PERFORM pg_catalog.set_config('app.finance_sync', COALESCE(v_previous_finance_sync, ''), true);
  RETURN NEW;
END;
$function$;
