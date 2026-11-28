-- Ensure the LTC tenant exists before any school-scoped data is provisioned.
-- This is safe to replay: an existing ID or school_code is never overwritten.
INSERT INTO public.schools (
  id,
  name,
  school_code,
  is_active,
  student_privacy_migration_complete
)
VALUES (
  'ltc_school_01',
  'Lira Town College',
  'LTC',
  true,
  true
)
ON CONFLICT DO NOTHING;

-- Fail closed if either unique key is already occupied by a different school,
-- or if the existing LTC row has drifted from the expected identity.
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM public.schools
    WHERE id = 'ltc_school_01'
      AND name = 'Lira Town College'
      AND school_code = 'LTC'
      AND is_active = true
  ) THEN
    RAISE EXCEPTION
      'LTC school seed conflict: expected ltc_school_01 / Lira Town College / LTC';
  END IF;

  IF (
    SELECT count(*)
    FROM public.schools
    WHERE id = 'ltc_school_01'
  ) <> 1 THEN
    RAISE EXCEPTION 'LTC school seed must produce exactly one row';
  END IF;
END;
$$;
