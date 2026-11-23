-- Protect the finance capability from ordinary Admin membership management.
--
-- Existing staff-membership RLS policies intentionally let an active ADMIN
-- manage other staff. RLS cannot distinguish updates to individual columns,
-- so this trigger protects the finance capability at the database boundary.
--
-- An authenticated caller must already hold finance access in the affected
-- school to grant/revoke it or alter the role/school of an enabled finance
-- membership. Direct trusted SQL administration (auth.uid() IS NULL) remains
-- available for initial provisioning and emergency recovery.

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

  -- A finance-enabled membership must remain an ADMIN. Changes to its
  -- capability, role, or school are restricted to an already-authorized
  -- finance Admin in both the old and new school contexts.
  IF NEW.has_finance_access AND NEW.role <> 'ADMIN' THEN
    RAISE EXCEPTION 'Finance access can only belong to an ADMIN membership';
  END IF;

  IF NEW.has_finance_access IS DISTINCT FROM OLD.has_finance_access
     OR (
       OLD.has_finance_access
       AND (
         NEW.is_active IS DISTINCT FROM OLD.is_active
         OR NEW.role IS DISTINCT FROM OLD.role
         OR NEW.school_id IS DISTINCT FROM OLD.school_id
       )
     ) THEN
    IF auth.uid() IS NOT NULL
       AND (
         NOT private.has_finance_access(OLD.school_id)
         OR NOT private.has_finance_access(NEW.school_id)
       ) THEN
      RAISE EXCEPTION 'An existing finance-authorized Admin is required to change finance access';
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
  BEFORE INSERT OR UPDATE OF has_finance_access, role, school_id
  ON public.staff_memberships
  FOR EACH ROW
  EXECUTE FUNCTION private.guard_staff_membership_finance_access();
