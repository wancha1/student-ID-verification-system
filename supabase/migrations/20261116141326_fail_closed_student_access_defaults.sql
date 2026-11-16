-- New student rows must not receive gate clearance implicitly.
-- The approved fee state must be assigned explicitly by authorized school staff.
ALTER TABLE public.students
    ALTER COLUMN access_status SET DEFAULT 'RESTRICTED_FEES';

ALTER TABLE public.students
    ALTER COLUMN fee_status SET DEFAULT 'OUTSTANDING';

ALTER TABLE public.students
    ALTER COLUMN is_day_scholar SET DEFAULT false;
