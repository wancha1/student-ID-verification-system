
create table if not exists private.staff_invitation_plan (
  school_id text not null references public.schools(id) on delete restrict,
  email text not null,
  role text not null check (role in ('ADMIN','HEAD_TEACHER','GATE_OFFICER','MEAL_STAFF')),
  status text not null default 'PENDING_AUTH_INVITE'
    check (status in ('PENDING_AUTH_INVITE','INVITED','ACCOUNT_LINKED','CANCELLED')),
  created_at timestamptz not null default now(),
  primary key (school_id, email)
);
revoke all on private.staff_invitation_plan from public, anon, authenticated;
insert into private.staff_invitation_plan (school_id,email,role)
values
('ltc_school_01','wanchaaaron@gmail.com','ADMIN'),
('ltc_school_01','aaronwancha@gmail.com','HEAD_TEACHER'),
('ltc_school_01','introvertcorner85@gmail.com','GATE_OFFICER'),
('ltc_school_01','darklordlucian85@gmail.com','MEAL_STAFF')
on conflict (school_id,email) do update set role=excluded.role, status='PENDING_AUTH_INVITE';
