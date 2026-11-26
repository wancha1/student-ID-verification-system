-- Capture the pre-migration RLS event-trigger prerequisite found on the
-- existing Supabase projects but absent from schema_migrations history.
-- This makes fresh local/preview databases replay the recorded chain safely.

CREATE OR REPLACE FUNCTION public.rls_auto_enable()
RETURNS event_trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'pg_catalog'
AS $function$
DECLARE
  cmd record;
  tbl name;
BEGIN
  FOR cmd IN
    SELECT *
    FROM pg_event_trigger_ddl_commands()
    WHERE command_tag IN ('CREATE TABLE', 'CREATE TABLE AS', 'SELECT INTO')
      AND object_type IN ('table', 'partitioned table')
  LOOP
    IF cmd.schema_name IS NOT NULL
       AND cmd.schema_name IN ('public')
       AND cmd.schema_name NOT IN ('pg_catalog', 'information_schema')
       AND cmd.schema_name NOT LIKE 'pg_toast%'
       AND cmd.schema_name NOT LIKE 'pg_temp%' THEN
      BEGIN
        SELECT c.relname INTO tbl
        FROM pg_catalog.pg_class c
        WHERE c.oid = cmd.objid;

        EXECUTE format(
          'ALTER TABLE IF EXISTS %I.%I ENABLE ROW LEVEL SECURITY',
          cmd.schema_name,
          tbl
        );
        RAISE LOG 'rls_auto_enable: enabled RLS on %', cmd.object_identity;
      EXCEPTION
        WHEN OTHERS THEN
          RAISE LOG 'rls_auto_enable: failed to enable RLS on %', cmd.object_identity;
      END;
    ELSE
      RAISE LOG 'rls_auto_enable: skip % (system schema or unenforced schema: %)',
        cmd.object_identity, cmd.schema_name;
    END IF;
  END LOOP;
END;
$function$;

DO $event_trigger$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM pg_event_trigger
    WHERE evtname = 'ensure_rls'
      AND evtfoid <> 'public.rls_auto_enable()'::regprocedure
  ) THEN
    RAISE EXCEPTION 'Event trigger ensure_rls already exists with an unexpected function';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_event_trigger WHERE evtname = 'ensure_rls'
  ) THEN
    EXECUTE 'CREATE EVENT TRIGGER ensure_rls ON ddl_command_end
             EXECUTE FUNCTION public.rls_auto_enable()';
  END IF;
END;
$event_trigger$;
