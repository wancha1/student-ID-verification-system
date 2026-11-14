-- Keep only the operational fee decision in the roster for gate checks.
-- Monetary balances remain in the leadership-only private profile.
alter table public.students
  add column if not exists fee_status text not null default 'CLEARED';
alter table public.student_private
  add column if not exists outstanding_amount numeric(12,2) not null default 0;

alter table public.students
  drop constraint if exists students_fee_status_check;
alter table public.students
  add constraint students_fee_status_check
  check (fee_status in ('CLEARED','OUTSTANDING'));

