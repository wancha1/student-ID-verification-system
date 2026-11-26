
-- Keep invitation planning data inaccessible to client roles.
alter table private.staff_invitation_plan enable row level security;
revoke all on table private.staff_invitation_plan from anon, authenticated;

-- Ensure private storage buckets exist for student images and generated school exports.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('student-photos', 'student-photos', false, 5242880, array['image/jpeg','image/png','image/webp']),
  ('school-exports', 'school-exports', false, 20971520, array['text/csv','application/pdf','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'])
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

-- Replace the old mismatched photo path rules. Canonical object path:
-- <school_id>/<student_id>/photo.jpg
drop policy if exists "active school staff can read student photos" on storage.objects;
drop policy if exists "leadership can upload student photos" on storage.objects;
drop policy if exists "leadership can update student photos" on storage.objects;
drop policy if exists "leadership can delete student photos" on storage.objects;

create policy "active school staff can read student photos"
on storage.objects for select to authenticated
using (
  bucket_id = 'student-photos'
  and private.is_active_staff((storage.foldername(name))[1], null::text[])
  and (storage.foldername(name))[2] is not null
  and storage.filename(name) = 'photo.jpg'
);

create policy "leadership can upload student photos"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'student-photos'
  and private.is_active_staff((storage.foldername(name))[1], array['ADMIN','HEAD_TEACHER']::text[])
  and (storage.foldername(name))[2] is not null
  and storage.filename(name) = 'photo.jpg'
);

create policy "leadership can update student photos"
on storage.objects for update to authenticated
using (
  bucket_id = 'student-photos'
  and private.is_active_staff((storage.foldername(name))[1], array['ADMIN','HEAD_TEACHER']::text[])
  and (storage.foldername(name))[2] is not null
  and storage.filename(name) = 'photo.jpg'
)
with check (
  bucket_id = 'student-photos'
  and private.is_active_staff((storage.foldername(name))[1], array['ADMIN','HEAD_TEACHER']::text[])
  and (storage.foldername(name))[2] is not null
  and storage.filename(name) = 'photo.jpg'
);

create policy "leadership can delete student photos"
on storage.objects for delete to authenticated
using (
  bucket_id = 'student-photos'
  and private.is_active_staff((storage.foldername(name))[1], array['ADMIN','HEAD_TEACHER']::text[])
  and (storage.foldername(name))[2] is not null
  and storage.filename(name) = 'photo.jpg'
);

-- Exports are intentionally private. No client policy is added; use a narrowly
-- scoped server-side export workflow when that feature is implemented.
