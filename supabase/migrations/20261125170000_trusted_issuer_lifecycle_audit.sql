-- Blocker 1 hardening: make issuer revocation irreversible and auditable.
-- Enrollment remains Admin-authorized and is NOT a two-person approval workflow.
-- This migration deliberately does not claim to solve device attestation or
-- online authorization of the private signing key.

CREATE TABLE IF NOT EXISTS public.trusted_issuer_events (
  event_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  school_id text NOT NULL,
  key_id text NOT NULL,
  event_type text NOT NULL CHECK (event_type IN ('ENROLLED', 'REVOKED')),
  actor_user_id uuid,
  occurred_at timestamptz NOT NULL DEFAULT now(),
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS trusted_issuer_events_school_time_idx
  ON public.trusted_issuer_events (school_id, occurred_at DESC);

ALTER TABLE public.trusted_issuer_events ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.trusted_issuer_events
  FROM PUBLIC, anon, authenticated, service_role;
GRANT SELECT ON TABLE public.trusted_issuer_events TO authenticated;

DROP POLICY IF EXISTS "school leadership can read issuer lifecycle events"
  ON public.trusted_issuer_events;
CREATE POLICY "school leadership can read issuer lifecycle events"
  ON public.trusted_issuer_events
  FOR SELECT TO authenticated
  USING (
    private.is_active_staff(
      school_id,
      ARRAY['ADMIN','HEAD_TEACHER']::text[]
    )
  );

CREATE OR REPLACE FUNCTION private.reject_trusted_issuer_event_mutation()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
BEGIN
  RAISE EXCEPTION 'Trusted issuer lifecycle events are append-only';
END;
$function$;

REVOKE ALL ON FUNCTION private.reject_trusted_issuer_event_mutation()
  FROM PUBLIC, anon, authenticated, service_role;

DROP TRIGGER IF EXISTS trusted_issuer_events_append_only
  ON public.trusted_issuer_events;
CREATE TRIGGER trusted_issuer_events_append_only
  BEFORE UPDATE OR DELETE ON public.trusted_issuer_events
  FOR EACH ROW
  EXECUTE FUNCTION private.reject_trusted_issuer_event_mutation();

CREATE OR REPLACE FUNCTION private.audit_trusted_issuer_lifecycle()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $function$
BEGIN
  IF TG_OP = 'INSERT' THEN
    INSERT INTO public.trusted_issuer_events (
      school_id, key_id, event_type, actor_user_id, metadata
    ) VALUES (
      NEW.school_id,
      NEW.key_id,
      'ENROLLED',
      auth.uid(),
      jsonb_build_object('label', NEW.label, 'enrolled_at_ms', NEW.enrolled_at_ms)
    );
    RETURN NEW;
  END IF;

  IF TG_OP = 'UPDATE' THEN
    IF OLD.is_revoked THEN
      RAISE EXCEPTION 'A revoked issuer key cannot be changed or reactivated';
    END IF;

    IF NEW.school_id IS DISTINCT FROM OLD.school_id
       OR NEW.key_id IS DISTINCT FROM OLD.key_id
       OR NEW.public_key_base64 IS DISTINCT FROM OLD.public_key_base64
       OR NEW.label IS DISTINCT FROM OLD.label
       OR NEW.enrolled_at_ms IS DISTINCT FROM OLD.enrolled_at_ms
       OR NEW.is_revoked IS DISTINCT FROM TRUE THEN
      RAISE EXCEPTION 'Issuer keys are immutable; the only permitted update is revocation';
    END IF;

    INSERT INTO public.trusted_issuer_events (
      school_id, key_id, event_type, actor_user_id, metadata
    ) VALUES (
      OLD.school_id,
      OLD.key_id,
      'REVOKED',
      auth.uid(),
      '{}'::jsonb
    );
    RETURN NEW;
  END IF;

  RAISE EXCEPTION 'Trusted issuer rows cannot be physically deleted';
END;
$function$;

REVOKE ALL ON FUNCTION private.audit_trusted_issuer_lifecycle()
  FROM PUBLIC, anon, authenticated, service_role;

DROP TRIGGER IF EXISTS trusted_issuers_lifecycle_audit
  ON public.trusted_issuers;
CREATE TRIGGER trusted_issuers_lifecycle_audit
  BEFORE INSERT OR UPDATE OR DELETE ON public.trusted_issuers
  FOR EACH ROW
  EXECUTE FUNCTION private.audit_trusted_issuer_lifecycle();

-- Keep public-key distribution readable by active school staff. Admins may
-- request/enroll keys; the only allowed update is a one-way revocation.
DROP POLICY IF EXISTS "administrators can update issuer public keys"
  ON public.trusted_issuers;
DROP POLICY IF EXISTS "administrators can revoke issuer public keys"
  ON public.trusted_issuers;
CREATE POLICY "administrators can revoke issuer public keys"
  ON public.trusted_issuers
  FOR UPDATE TO authenticated
  USING (
    is_revoked = false
    AND private.is_active_staff(school_id, ARRAY['ADMIN']::text[])
  )
  WITH CHECK (
    is_revoked = true
    AND private.is_active_staff(school_id, ARRAY['ADMIN']::text[])
  );

-- No client or service-role path may delete or truncate issuer trust records.
REVOKE DELETE, TRUNCATE ON TABLE public.trusted_issuers
  FROM PUBLIC, anon, authenticated, service_role;
