-- Security and data-integrity guardrails for the Scholink integration branch.
-- This migration is source-controlled only. It has NOT been applied to any database.

-- 1. Trusted issuer public keys are immutable after enrollment.
-- The only permitted update is one-way revocation (false -> true).
CREATE OR REPLACE FUNCTION private.guard_trusted_issuer_key_immutability()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $fn$
BEGIN
  IF NEW.school_id IS DISTINCT FROM OLD.school_id
     OR NEW.key_id IS DISTINCT FROM OLD.key_id
     OR NEW.public_key_base64 IS DISTINCT FROM OLD.public_key_base64
     OR NEW.label IS DISTINCT FROM OLD.label
     OR NEW.enrolled_at_ms IS DISTINCT FROM OLD.enrolled_at_ms THEN
    RAISE EXCEPTION 'Trusted issuer identity and public key material are immutable; enroll a new key_id for rotation';
  END IF;

  IF OLD.is_revoked THEN
    RAISE EXCEPTION 'A revoked issuer key cannot be modified or reactivated';
  END IF;

  IF OLD.is_revoked THEN
    RAISE EXCEPTION 'A revoked issuer key cannot be modified or reactivated';
  END IF;

  IF NEW.is_revoked IS DISTINCT FROM OLD.is_revoked
     AND NOT (OLD.is_revoked = false AND NEW.is_revoked = true) THEN
    RAISE EXCEPTION 'Issuer revocation is one-way';
  END IF;

  -- Permit an idempotent upsert retry that leaves an active row unchanged.
  -- Any attempt to change identity or key material was rejected above.
  RETURN NEW;
END;
$fn$;

REVOKE ALL ON FUNCTION private.guard_trusted_issuer_key_immutability()
  FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS guard_trusted_issuer_key_immutability
  ON public.trusted_issuers;

CREATE TRIGGER guard_trusted_issuer_key_immutability
  BEFORE UPDATE ON public.trusted_issuers
  FOR EACH ROW
  EXECUTE FUNCTION private.guard_trusted_issuer_key_immutability();

DROP POLICY IF EXISTS "administrators can update issuer public keys"
  ON public.trusted_issuers;

CREATE POLICY "administrators can revoke issuer public keys"
ON public.trusted_issuers
FOR UPDATE
TO authenticated
USING (
  is_revoked = false
  AND private.is_active_staff(school_id, ARRAY['ADMIN']::text[])
)
WITH CHECK (
  private.is_active_staff(school_id, ARRAY['ADMIN']::text[])
);

-- 2. Complete finance-membership protection.
-- This extends the earlier guard to cover identity transfer and activation state.
CREATE OR REPLACE FUNCTION private.guard_staff_membership_finance_access()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $fn$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.has_finance_access THEN
      IF NEW.role <> 'ADMIN' THEN
        RAISE EXCEPTION 'Finance access can only be assigned to an ADMIN membership';
      END IF;

      IF auth.uid() IS NOT NULL
         AND NOT private.has_finance_access(NEW.school_id) THEN
        RAISE EXCEPTION 'An existing finance-authorized Admin is required to grant finance access';
      END IF;
    END IF;

    RETURN NEW;
  END IF;

  IF NEW.has_finance_access AND NEW.role <> 'ADMIN' THEN
    RAISE EXCEPTION 'Finance access can only belong to an ADMIN membership';
  END IF;

  IF (OLD.has_finance_access OR NEW.has_finance_access)
     AND (
       NEW.has_finance_access IS DISTINCT FROM OLD.has_finance_access
       OR NEW.role IS DISTINCT FROM OLD.role
       OR NEW.school_id IS DISTINCT FROM OLD.school_id
       OR NEW.user_id IS DISTINCT FROM OLD.user_id
       OR NEW.is_active IS DISTINCT FROM OLD.is_active
     ) THEN
    IF auth.uid() IS NOT NULL
       AND (
         NOT private.has_finance_access(OLD.school_id)
         OR NOT private.has_finance_access(NEW.school_id)
       ) THEN
      RAISE EXCEPTION 'An existing finance-authorized Admin is required to change a finance-enabled membership';
    END IF;
  END IF;

  RETURN NEW;
END;
$fn$;

REVOKE ALL ON FUNCTION private.guard_staff_membership_finance_access()
  FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS guard_staff_membership_finance_access
  ON public.staff_memberships;

CREATE TRIGGER guard_staff_membership_finance_access
  BEFORE INSERT OR UPDATE OF has_finance_access, role, school_id, user_id, is_active
  ON public.staff_memberships
  FOR EACH ROW
  EXECUTE FUNCTION private.guard_staff_membership_finance_access();

-- 3. Remove the unsafe implicit CLEARED default. The application already sends
-- an explicit status on the finance write path; new callers must do the same.
ALTER TABLE public.student_finances
  ALTER COLUMN fee_status DROP DEFAULT;
