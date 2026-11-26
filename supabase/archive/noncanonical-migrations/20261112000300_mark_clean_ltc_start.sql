-- This is a clean Supabase deployment, not an import from Firebase.
-- The LTC project was verified empty before this flag was enabled.
update public.schools
set student_privacy_migration_complete = true,
    updated_at = now()
where id = 'ltc_school_01'
  and not exists (
    select 1 from public.students where school_id = 'ltc_school_01'
  );
