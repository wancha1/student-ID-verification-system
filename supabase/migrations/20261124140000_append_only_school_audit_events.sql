-- Transactional, append-only audit events for school-critical database mutations.
-- Event payloads intentionally exclude student names, contact details, QR payloads,
-- public-key material, payment amounts and free-text notes/reasons.

CREATE TABLE IF NOT EXISTS public.audit_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  school_id text NOT NULL REFERENCES public.schools(id) ON DELETE RESTRICT,
  actor_user_id uuid,
  event_type text NOT NULL,
  entity_type text NOT NULL,
  entity_id text NOT NULL,
  occurred_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  source text NOT NULL DEFAULT 'database_trigger',
  transaction_id bigint NOT NULL DEFAULT txid_current(),
  correlation_id text,
  changed_fields text[] NOT NULL DEFAULT ARRAY[]::text[],
  before_state jsonb,
  after_state jsonb
);

CREATE INDEX IF NOT EXISTS audit_events_school_time_idx
  ON public.audit_events (school_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS audit_events_entity_idx
  ON public.audit_events (school_id, entity_type, entity_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS audit_events_actor_idx
  ON public.audit_events (school_id, actor_user_id, occurred_at DESC);

ALTER TABLE public.audit_events ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE public.audit_events FROM PUBLIC, anon, authenticated;
GRANT SELECT ON TABLE public.audit_events TO authenticated;

CREATE POLICY "school leadership can read audit events"
ON public.audit_events
FOR SELECT
TO authenticated
USING (
  private.is_active_staff(
    school_id,
    ARRAY['ADMIN', 'HEAD_TEACHER']::text[]
  )
);

CREATE OR REPLACE FUNCTION private.audit_safe_snapshot(
  p_table_name text,
  p_row jsonb
)
RETURNS jsonb
LANGUAGE sql
IMMUTABLE
SET search_path = ''
AS $fn$
  SELECT CASE p_table_name
    WHEN 'students' THEN jsonb_strip_nulls(jsonb_build_object(
      'access_status', p_row->'access_status',
      'fee_status', p_row->'fee_status',
      'grade_class', p_row->'grade_class',
      'is_day_scholar', p_row->'is_day_scholar',
      'is_active', p_row->'is_active',
      'is_deleted', p_row->'is_deleted'
    ))
    WHEN 'cards' THEN jsonb_strip_nulls(jsonb_build_object(
      'status', p_row->'status',
      'valid_from', p_row->'valid_from',
      'valid_until', p_row->'valid_until',
      'is_deleted', p_row->'is_deleted'
    ))
    WHEN 'staff_memberships' THEN jsonb_strip_nulls(jsonb_build_object(
      'user_id', p_row->'user_id',
      'role', p_row->'role',
      'is_active', p_row->'is_active',
      'has_finance_access', p_row->'has_finance_access'
    ))
    WHEN 'student_finances' THEN jsonb_strip_nulls(jsonb_build_object(
      'fee_status', p_row->'fee_status'
    ))
    WHEN 'gate_logs' THEN jsonb_strip_nulls(jsonb_build_object(
      'direction', p_row->'direction',
      'decision', p_row->'decision',
      'is_approved', p_row->'is_approved'
    ))
    WHEN 'meals' THEN jsonb_strip_nulls(jsonb_build_object(
      'meal_type', p_row->'meal_type',
      'meal_date', p_row->'meal_date',
      'status', p_row->'status'
    ))
    WHEN 'attendance_records' THEN jsonb_strip_nulls(jsonb_build_object(
      'lesson_date', p_row->'lesson_date',
      'session_name', p_row->'session_name',
      'is_present', p_row->'is_present',
      'is_correction', p_row->'is_correction'
    ))
    WHEN 'exam_exceptions' THEN jsonb_strip_nulls(jsonb_build_object(
      'exam_code', p_row->'exam_code',
      'is_approved', p_row->'is_approved'
    ))
    WHEN 'trusted_issuers' THEN jsonb_strip_nulls(jsonb_build_object(
      'key_id', p_row->'key_id',
      'is_revoked', p_row->'is_revoked'
    ))
    ELSE '{}'::jsonb
  END;
$fn$;

CREATE OR REPLACE FUNCTION private.capture_school_audit_event()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $fn$
DECLARE
  v_old jsonb;
  v_new jsonb;
  v_row jsonb;
  v_school_id text;
  v_entity_id text;
  v_before jsonb;
  v_after jsonb;
  v_changed text[];
BEGIN
  IF TG_OP IN ('UPDATE', 'DELETE') THEN
    v_old := to_jsonb(OLD);
  END IF;
  IF TG_OP IN ('INSERT', 'UPDATE') THEN
    v_new := to_jsonb(NEW);
  END IF;

  v_row := COALESCE(v_new, v_old);
  v_school_id := v_row->>'school_id';
  v_entity_id := COALESCE(
    v_row->>'id',
    v_row->>'student_id',
    v_row->>'card_id',
    v_row->>'key_id',
    v_row->>'user_id',
    'unknown'
  );

  IF v_school_id IS NULL THEN
    RAISE EXCEPTION 'Cannot audit %.% without school_id', TG_TABLE_NAME, TG_OP;
  END IF;

  IF v_old IS NOT NULL THEN
    v_before := private.audit_safe_snapshot(TG_TABLE_NAME, v_old);
  END IF;
  IF v_new IS NOT NULL THEN
    v_after := private.audit_safe_snapshot(TG_TABLE_NAME, v_new);
  END IF;

  SELECT COALESCE(array_agg(keys.key ORDER BY keys.key), ARRAY[]::text[])
    INTO v_changed
  FROM (
    SELECT key
    FROM jsonb_object_keys(COALESCE(v_before, '{}'::jsonb) || COALESCE(v_after, '{}'::jsonb)) AS key
    WHERE (COALESCE(v_before, '{}'::jsonb)->key)
      IS DISTINCT FROM (COALESCE(v_after, '{}'::jsonb)->key)
  ) AS keys;

  IF TG_OP = 'UPDATE' AND cardinality(v_changed) = 0 THEN
    RETURN NEW;
  END IF;

  INSERT INTO public.audit_events (
    school_id,
    actor_user_id,
    event_type,
    entity_type,
    entity_id,
    source,
    correlation_id,
    changed_fields,
    before_state,
    after_state
  ) VALUES (
    v_school_id,
    auth.uid(),
    TG_TABLE_NAME || '.' || lower(TG_OP),
    TG_TABLE_NAME,
    v_entity_id,
    'database_trigger',
    NULLIF(current_setting('app.audit_correlation_id', true), ''),
    v_changed,
    v_before,
    v_after
  );

  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  RETURN NEW;
END;
$fn$;

CREATE OR REPLACE FUNCTION private.prevent_audit_event_mutation()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $fn$
BEGIN
  RAISE EXCEPTION 'Audit events are append-only and cannot be updated or deleted';
END;
$fn$;

REVOKE ALL ON FUNCTION private.audit_safe_snapshot(text, jsonb)
  FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.capture_school_audit_event()
  FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION private.prevent_audit_event_mutation()
  FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS audit_events_prevent_mutation ON public.audit_events;
CREATE TRIGGER audit_events_prevent_mutation
  BEFORE UPDATE OR DELETE ON public.audit_events
  FOR EACH ROW EXECUTE FUNCTION private.prevent_audit_event_mutation();

-- Trigger writes are in the same database transaction as the business change:
-- if the audit insert fails, the underlying mutation fails as well.
DO $triggers$
DECLARE
  v_table text;
BEGIN
  FOREACH v_table IN ARRAY ARRAY[
    'students',
    'cards',
    'staff_memberships',
    'student_finances',
    'gate_logs',
    'meals',
    'attendance_records',
    'exam_exceptions',
    'trusted_issuers'
  ]
  LOOP
    IF to_regclass(format('public.%I', v_table)) IS NOT NULL THEN
      EXECUTE format('DROP TRIGGER IF EXISTS capture_school_audit_event ON public.%I', v_table);
      EXECUTE format(
        'CREATE TRIGGER capture_school_audit_event AFTER INSERT OR UPDATE OR DELETE ON public.%I FOR EACH ROW EXECUTE FUNCTION private.capture_school_audit_event()',
        v_table
      );
    END IF;
  END LOOP;
END;
$triggers$;
