-- Extend the finance-capability boundary to protect membership identity and lifecycle.
--
-- The earlier guard covered has_finance_access, role and school_id. However,
-- the staff-membership UPDATE policy also permits an Admin to update other
-- membership columns. Without this extension, a finance-enabled membership
-- could be transferred to another user by changing user_id, or disrupted by
-- changing is_active, without changing the protected finance flag.
--
-- Authenticated changes to a finance-enabled membership's identity, role,
-- school, active state or finance capability require an existing finance-
-- authorized Admin in both the old and new school contexts. Trusted SQL
-- administration (auth.uid() IS NULL) remains available for provisioning and
-- emergency recovery.

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
