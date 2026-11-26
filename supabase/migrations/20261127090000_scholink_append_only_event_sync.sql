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
  insert into public.gate_logs (
    school_id, id, student_id, student_number, student_name, card_id,
    card_identifier, qr_payload, decision, gate_location, timestamp_ms,
    fee_status, card_status, is_day_scholar, is_approved, reason,
    is_offline_decision, data_sync_timestamp_at_scan, guard_name, device_identifier
  )
  select
    x.school_id, x.id, x.student_id, x.student_number, x.student_name, x.card_id,
    x.card_identifier, x.qr_payload, x.decision, x.gate_location, x.timestamp_ms,
    x.fee_status, x.card_status, x.is_day_scholar, x.is_approved, x.reason,
    coalesce(x.is_offline_decision, false), x.data_sync_timestamp_at_scan,
    x.guard_name, x.device_identifier
  from jsonb_to_record(p_row) as x(
    school_id text, id text, student_id text, student_number text,
    student_name text, card_id text, card_identifier text, qr_payload text,
    decision text, gate_location text, timestamp_ms bigint, fee_status text,
    card_status text, is_day_scholar boolean, is_approved boolean, reason text,
    is_offline_decision boolean, data_sync_timestamp_at_scan bigint,
    guard_name text, device_identifier text
  )
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
  insert into public.meals (
    school_id, id, student_id, student_number, student_name, grade_class,
    meal_type, meal_date, status, notes, serving_station, timestamp_ms
  )
  select
    x.school_id, x.id, x.student_id, x.student_number, x.student_name, x.grade_class,
    x.meal_type, x.meal_date, x.status, coalesce(x.notes, ''),
    x.serving_station, x.timestamp_ms
  from jsonb_to_record(p_row) as x(
    school_id text, id text, student_id text, student_number text,
    student_name text, grade_class text, meal_type text, meal_date text,
    status text, notes text, serving_station text, timestamp_ms bigint
  )
  on conflict do nothing;

  get diagnostics inserted_rows = row_count;
  return inserted_rows = 1;
end;
$$;

revoke all on function public.scholink_insert_gate_log_once(jsonb) from public, anon;
revoke all on function public.scholink_insert_meal_once(jsonb) from public, anon;
grant execute on function public.scholink_insert_gate_log_once(jsonb) to authenticated;
grant execute on function public.scholink_insert_meal_once(jsonb) to authenticated;
