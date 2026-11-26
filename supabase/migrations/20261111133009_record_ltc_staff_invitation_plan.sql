
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
-- Personal email seed records from the historical applied migration were
-- intentionally omitted from source control. Provision staff invitations
-- through the authorized school-server workflow instead.
