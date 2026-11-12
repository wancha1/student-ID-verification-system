-- LTC V1 backend hardening and Supabase-native operational schema.
-- No Firebase data is imported; this establishes the clean Supabase source of truth.

alter table private.staff_invitation_plan enable row level security;
revoke all on table private.staff_invitation_plan from anon, authenticated;

alter table public.gate_logs
  add column if not exists student_id text,
  add column if not exists card_id text,
  add column if not exists card_identifier text,
  add column if not exists qr_payload text,
  add column if not exists fee_status text,
  add column if not exists card_status text,
  add column if not exists is_day_scholar boolean,
  add column if not exists is_approved boolean,
  add column if not exists reason text,
  add column if not exists is_offline_decision boolean not null default false,
  add column if not exists data_sync_timestamp_at_scan bigint,
  add column if not exists guard_name text,
  add column if not exists device_identifier text;

create unique index if not exists students_school_student_number_uq
  on public.students(school_id, student_number);
create unique index if not exists cards_school_card_identifier_uq
  on public.cards(school_id, card_identifier);
create unique index if not exists meals_school_student_meal_day_uq
  on public.meals(school_id, student_id, meal_type, meal_date);

-- A revoked card can never be reactivated by a stale device or client update.
create or replace function private.prevent_card_reactivation()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if old.status in ('LOST','REPLACED','DEACTIVATED') and new.status = 'ACTIVE' then
    raise exception 'A revoked card cannot be reactivated; issue a replacement card.';
  end if;
  if old.is_deleted and not new.is_deleted then
    raise exception 'A deleted card cannot be restored.';
  end if;
  return new;
end;
$$;

drop trigger if exists cards_prevent_reactivation on public.cards;
create trigger cards_prevent_reactivation
before update on public.cards
for each row execute function private.prevent_card_reactivation();

-- Correct the card update policy: allow normal admin updates, while the trigger
-- above enforces the non-reversible revocation invariant.
drop policy if exists "admins can update cards without restoring revoked cards" on public.cards;
create policy "admins can update cards"
on public.cards for update to authenticated
using (private.is_active_staff(school_id, array['ADMIN']::text[]))
with check (private.is_active_staff(school_id, array['ADMIN']::text[]));

-- Canonical private object path: <school_id>/<student_id>/photo.jpg
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('student-photos', 'student-photos', false, 5242880, array['image/jpeg','image/png','image/webp']),
  ('school-exports', 'school-exports', false, 20971520, array['text/csv','application/pdf','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'])
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

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

-- school-exports remains private and intentionally has no direct client policy.
