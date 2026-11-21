-- Compatibility default for app versions that do not send access_status.
-- Superseded by the fail-closed defaults migration immediately after this one.
ALTER TABLE public.students
    ALTER COLUMN access_status SET DEFAULT 'APPROVED';
