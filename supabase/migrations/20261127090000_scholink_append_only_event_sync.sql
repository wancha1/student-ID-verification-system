-- V1 gate and meal events are append-only. These invoker RPCs make sync
-- retries idempotent without granting UPDATE or allowing clients to overwrite
-- an existing event. RLS policies still authorize each INSERT.
create or replace function public.scholink_insert_gate_log_once(p_row jsonb)
returns boolean
language plpgsql
security invoker
set search_path = ''
as $$
declare
  inserted_rows integer;
begin
  insert into public.gate_logs
  select (jsonb_populate_record(null::public.gate_logs, p_row)).*
  on conflict do nothing;

  get diagnostics inserted_rows = row_count;
  return inserted_rows = 1;
end;
$$;

create or replace function public.scholink_insert_meal_once(p_row jsonb)
returns boolean
language plpgsql
security invoker
set search_path = ''
as $$
declare
  inserted_rows integer;
begin
  insert into public.meals
  select (jsonb_populate_record(null::public.meals, p_row)).*
  on conflict do nothing;

  get diagnostics inserted_rows = row_count;
  return inserted_rows = 1;
end;
$$;

revoke all on function public.scholink_insert_gate_log_once(jsonb) from public, anon;
revoke all on function public.scholink_insert_meal_once(jsonb) from public, anon;
grant execute on function public.scholink_insert_gate_log_once(jsonb) to authenticated;
grant execute on function public.scholink_insert_meal_once(jsonb) to authenticated;
