create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create table public.schools (
  id text primary key,
  name text not null,
  school_code text not null unique,
  is_active boolean not null default true,
  student_privacy_migration_complete boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.staff_memberships (
  school_id text not null references public.schools(id) on delete restrict,
  user_id uuid not null references auth.users(id) on delete restrict,
  full_name text not null,
  email text not null,
  role text not null check (role in ('ADMIN','HEAD_TEACHER','GATE_OFFICER','MEAL_STAFF')),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (school_id, user_id)
);
create index staff_memberships_user_idx on public.staff_memberships(user_id);

create table public.students (
  school_id text not null references public.schools(id) on delete restrict,
  id text not null,
  student_number text not null,
  name text not null,
  first_name text not null,
  last_name text not null,
  photo_path text,
  access_status text not null,
  grade_class text not null,
  class_name text,
  stream text,
  is_day_scholar boolean not null default false,
  day_scholar_type text,
  transport_route text,
  gender text,
  avatar_color_seed bigint,
  homeroom_teacher text,
  academic_year text,
  qr_token text,
  updated_at_ms bigint not null,
  is_deleted boolean not null default false,
  created_at timestamptz not null default now(),
  primary key (school_id, id),
  unique (school_id, student_number)
);
create index students_class_stream_idx on public.students(school_id, class_name, stream);
create index students_updated_idx on public.students(school_id, updated_at_ms);
create index students_active_idx on public.students(school_id, is_deleted);

create table public.student_private (
  school_id text not null,
  student_id text not null,
  guardian_name text,
  guardian_phone text,
  emergency_contact text,
  notes text,
  updated_at_ms bigint not null,
  primary key (school_id, student_id),
  foreign key (school_id, student_id)
    references public.students(school_id, id) on delete restrict
);

create table public.cards (
  school_id text not null,
  id text not null,
  card_identifier text not null,
  student_id text not null,
  student_number text not null,
  qr_payload text not null,
  status text not null check (status in ('ACTIVE','LOST','REPLACED','DEACTIVATED')),
  issue_date_ms bigint not null,
  activation_date_ms bigint,
  deactivation_date_ms bigint,
  replaced_by_card_id text,
  reason text,
  notes text,
  updated_at_ms bigint not null,
  is_deleted boolean not null default false,
  primary key (school_id, id),
  unique (school_id, card_identifier),
  foreign key (school_id, student_id)
    references public.students(school_id, id) on delete restrict
);
create index cards_student_idx on public.cards(school_id, student_id);
create index cards_updated_idx on public.cards(school_id, updated_at_ms);

create table public.gate_logs (
  school_id text not null references public.schools(id) on delete restrict,
  id text not null,
  student_number text not null,
  student_name text not null,
  decision text not null,
  gate_location text not null,
  timestamp_ms bigint not null,
  created_at timestamptz not null default now(),
  primary key (school_id, id)
);
create index gate_logs_time_idx on public.gate_logs(school_id, timestamp_ms desc);
create index gate_logs_student_idx on public.gate_logs(school_id, student_number);

create table public.meals (
  school_id text not null references public.schools(id) on delete restrict,
  id text not null,
  student_id text not null,
  student_number text not null,
  student_name text not null,
  grade_class text not null,
  meal_type text not null check (meal_type in ('BREAKFAST','LUNCH','DINNER','SPECIAL_SNACK')),
  meal_date text not null,
  status text not null,
  notes text not null default '',
  serving_station text not null,
  timestamp_ms bigint not null,
  created_at timestamptz not null default now(),
  primary key (school_id, id),
  foreign key (school_id, student_id)
    references public.students(school_id, id) on delete restrict
);
create index meals_student_date_idx on public.meals(school_id, student_id, meal_date);
create index meals_time_idx on public.meals(school_id, timestamp_ms desc);

create or replace function private.is_active_staff(target_school_id text, allowed_roles text[] default null)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.staff_memberships sm
    join public.schools s on s.id = sm.school_id
    where sm.school_id = target_school_id
      and sm.user_id = (select auth.uid())
      and sm.is_active = true
      and s.is_active = true
      and (allowed_roles is null or sm.role = any(allowed_roles))
  );
$$;

revoke all on function private.is_active_staff(text, text[]) from public, anon;
grant usage on schema private to authenticated;
grant execute on function private.is_active_staff(text, text[]) to authenticated;

alter table public.schools enable row level security;
alter table public.staff_memberships enable row level security;
alter table public.students enable row level security;
alter table public.student_private enable row level security;
alter table public.cards enable row level security;
alter table public.gate_logs enable row level security;
alter table public.meals enable row level security;

create policy "school members can read own school"
on public.schools for select to authenticated
using (private.is_active_staff(id, null));

create policy "staff can read own profile or admin can list staff"
on public.staff_memberships for select to authenticated
using (
  user_id = (select auth.uid())
  or private.is_active_staff(school_id, array['ADMIN'])
);

create policy "admins can create staff memberships"
on public.staff_memberships for insert to authenticated
with check (
  private.is_active_staff(school_id, array['ADMIN'])
  and user_id <> (select auth.uid())
  and role in ('ADMIN','HEAD_TEACHER','GATE_OFFICER','MEAL_STAFF')
);

create policy "admins can update other staff memberships"
on public.staff_memberships for update to authenticated
using (
  private.is_active_staff(school_id, array['ADMIN'])
  and user_id <> (select auth.uid())
)
with check (
  private.is_active_staff(school_id, array['ADMIN'])
  and user_id <> (select auth.uid())
  and role in ('ADMIN','HEAD_TEACHER','GATE_OFFICER','MEAL_STAFF')
);

create policy "active staff can read permitted student roster"
on public.students for select to authenticated
using (
  private.is_active_staff(school_id, null)
  and (
    private.is_active_staff(school_id, array['ADMIN','HEAD_TEACHER'])
    or exists (
      select 1 from public.schools s
      where s.id = students.school_id
        and s.student_privacy_migration_complete = true
    )
  )
);

create policy "admins can insert students"
on public.students for insert to authenticated
with check (private.is_active_staff(school_id, array['ADMIN']));

create policy "admins can update students"
on public.students for update to authenticated
using (private.is_active_staff(school_id, array['ADMIN']))
with check (private.is_active_staff(school_id, array['ADMIN']));

create policy "leadership can read private student records"
on public.student_private for select to authenticated
using (private.is_active_staff(school_id, array['ADMIN','HEAD_TEACHER']));

create policy "leadership can create private student records"
on public.student_private for insert to authenticated
with check (private.is_active_staff(school_id, array['ADMIN','HEAD_TEACHER']));

create policy "leadership can update private student records"
on public.student_private for update to authenticated
using (private.is_active_staff(school_id, array['ADMIN','HEAD_TEACHER']))
with check (private.is_active_staff(school_id, array['ADMIN','HEAD_TEACHER']));

create policy "admins can delete private student records"
on public.student_private for delete to authenticated
using (private.is_active_staff(school_id, array['ADMIN']));

create policy "active staff can read cards"
on public.cards for select to authenticated
using (private.is_active_staff(school_id, null));

create policy "admins can create cards"
on public.cards for insert to authenticated
with check (private.is_active_staff(school_id, array['ADMIN']));

create policy "admins can update cards without restoring revoked cards"
on public.cards for update to authenticated
using (private.is_active_staff(school_id, array['ADMIN']))
with check (
  private.is_active_staff(school_id, array['ADMIN'])
  and status <> 'ACTIVE'
);

create policy "gate team can read gate logs"
on public.gate_logs for select to authenticated
using (private.is_active_staff(school_id, array['ADMIN','HEAD_TEACHER','GATE_OFFICER']));

create policy "gate operators can create gate logs"
on public.gate_logs for insert to authenticated
with check (private.is_active_staff(school_id, array['ADMIN','GATE_OFFICER']));

create policy "meal team can read meal records"
on public.meals for select to authenticated
using (private.is_active_staff(school_id, array['ADMIN','HEAD_TEACHER','MEAL_STAFF']));

create policy "meal operators can create meal records"
on public.meals for insert to authenticated
with check (private.is_active_staff(school_id, array['ADMIN','MEAL_STAFF']));

revoke all on public.schools, public.staff_memberships, public.students,
  public.student_private, public.cards, public.gate_logs, public.meals
from anon, public;
grant select on public.schools to authenticated;
grant select, insert, update on public.staff_memberships to authenticated;
grant select, insert, update on public.students to authenticated;
grant select, insert, update, delete on public.student_private to authenticated;
grant select, insert, update on public.cards to authenticated;
grant select, insert on public.gate_logs to authenticated;
grant select, insert on public.meals to authenticated;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('student-photos', 'student-photos', false, 5242880, array['image/jpeg','image/png','image/webp'])
on conflict (id) do update
set public = false, file_size_limit = 5242880,
    allowed_mime_types = array['image/jpeg','image/png','image/webp'];

create policy "active school staff can read student photos"
on storage.objects for select to authenticated
using (
  bucket_id = 'student-photos'
  and private.is_active_staff((storage.foldername(name))[1], null)
  and storage.filename(name) = 'photo.jpg'
);

create policy "leadership can upload student photos"
on storage.objects for insert to authenticated
with check (
  bucket_id = 'student-photos'
  and private.is_active_staff((storage.foldername(name))[1], array['ADMIN','HEAD_TEACHER'])
  and storage.filename(name) = 'photo.jpg'
);

create policy "leadership can update student photos"
on storage.objects for update to authenticated
using (
  bucket_id = 'student-photos'
  and private.is_active_staff((storage.foldername(name))[1], array['ADMIN','HEAD_TEACHER'])
  and storage.filename(name) = 'photo.jpg'
)
with check (
  bucket_id = 'student-photos'
  and private.is_active_staff((storage.foldername(name))[1], array['ADMIN','HEAD_TEACHER'])
  and storage.filename(name) = 'photo.jpg'
);

create policy "leadership can delete student photos"
on storage.objects for delete to authenticated
using (
  bucket_id = 'student-photos'
  and private.is_active_staff((storage.foldername(name))[1], array['ADMIN','HEAD_TEACHER'])
  and storage.filename(name) = 'photo.jpg'
);
