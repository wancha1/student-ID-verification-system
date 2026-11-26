\set ON_ERROR_STOP on

DO $$
BEGIN
  IF (SELECT count(*) FROM public.trusted_issuer_events
      WHERE school_id = 'school-test' AND key_id = 'baseline-key'
        AND event_type = 'BASELINE' AND actor_user_id IS NULL) <> 1 THEN
    RAISE EXCEPTION 'Baseline issuer snapshot was not created exactly once';
  END IF;
END $$;

-- Trigger-generated enrollment audit event.
INSERT INTO public.trusted_issuers
  (school_id, key_id, public_key_base64, label, enrolled_at_ms, is_revoked)
VALUES
  ('school-test', 'new-key', 'c3ludGhldGljLWtleQ==', 'Synthetic new key', 1700000001000, false);

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.trusted_issuer_events
      WHERE school_id = 'school-test' AND key_id = 'new-key'
        AND event_type = 'ENROLLED') THEN
    RAISE EXCEPTION 'Issuer INSERT did not append ENROLLED event';
  END IF;
END $$;

-- An authorized Admin can revoke exactly once; actor attribution is captured.
SELECT set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000123', false);
SELECT set_config('test.staff_authorized', 'true', false);
SET ROLE authenticated;
UPDATE public.trusted_issuers
SET is_revoked = true
WHERE school_id = 'school-test' AND key_id = 'new-key';
RESET ROLE;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.trusted_issuer_events
      WHERE school_id = 'school-test' AND key_id = 'new-key'
        AND event_type = 'REVOKED'
        AND actor_user_id = '00000000-0000-0000-0000-000000000123') THEN
    RAISE EXCEPTION 'Revocation event or actor attribution missing';
  END IF;
END $$;

-- Re-enrollment/reactivation and mutation of a revoked key must fail.
DO $$
BEGIN
  BEGIN
    UPDATE public.trusted_issuers SET is_revoked = false
    WHERE school_id = 'school-test' AND key_id = 'new-key';
    RAISE EXCEPTION 'Expected revoked-key reactivation to fail';
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM = 'Expected revoked-key reactivation to fail' THEN RAISE; END IF;
    IF position('revoked issuer key' in lower(SQLERRM)) = 0 THEN RAISE; END IF;
  END;
END $$;

-- Lifecycle events are append-only even for the table owner.
DO $$
DECLARE event_id_value uuid;
BEGIN
  SELECT event_id INTO event_id_value FROM public.trusted_issuer_events
  WHERE key_id = 'new-key' AND event_type = 'ENROLLED' LIMIT 1;
  BEGIN
    UPDATE public.trusted_issuer_events SET metadata = '{}'::jsonb
    WHERE event_id = event_id_value;
    RAISE EXCEPTION 'Expected event UPDATE to fail';
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM = 'Expected event UPDATE to fail' THEN RAISE; END IF;
    IF position('append-only' in lower(SQLERRM)) = 0 THEN RAISE; END IF;
  END;
  BEGIN
    DELETE FROM public.trusted_issuer_events WHERE event_id = event_id_value;
    RAISE EXCEPTION 'Expected event DELETE to fail';
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM = 'Expected event DELETE to fail' THEN RAISE; END IF;
    IF position('append-only' in lower(SQLERRM)) = 0 THEN RAISE; END IF;
  END;
END $$;

-- Client-side issuer enrollment must be denied by SQL privileges.
SET ROLE authenticated;
DO $$
BEGIN
  BEGIN
    INSERT INTO public.trusted_issuers
      (school_id, key_id, public_key_base64, label, enrolled_at_ms, is_revoked)
    VALUES ('school-test', 'client-forbidden', 'eA==', 'forbidden', 1, false);
    RAISE EXCEPTION 'Expected client issuer INSERT to be denied';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;
END $$;
RESET ROLE;

-- Ordinary student updates remain possible; cross-school moves are blocked.
UPDATE public.students SET student_number = 'SYN-001-UPDATED'
WHERE id = 'student-test-1';
DO $$
BEGIN
  BEGIN
    UPDATE public.students SET school_id = 'other-school'
    WHERE id = 'student-test-1';
    RAISE EXCEPTION 'Expected cross-school reassignment to fail';
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM = 'Expected cross-school reassignment to fail' THEN RAISE; END IF;
    IF position('school reassignment is prohibited' in lower(SQLERRM)) = 0 THEN RAISE; END IF;
  END;
END $$;

-- DELETE/TRUNCATE rights on issuer trust records must be revoked.
DO $$
BEGIN
  IF has_table_privilege('authenticated', 'public.trusted_issuers', 'INSERT')
     OR has_table_privilege('authenticated', 'public.trusted_issuers', 'DELETE')
     OR has_table_privilege('authenticated', 'public.trusted_issuers', 'TRUNCATE') THEN
    RAISE EXCEPTION 'Authenticated issuer privileges are too broad';
  END IF;
END $$;

SELECT 'PASS: Blocker 1 migration integration assertions' AS result;


-- Fee-status sync must be allowed only through the trusted internal trigger context.
SELECT set_config('test.finance_authorized', 'true', false);
INSERT INTO public.student_finances
  (school_id, student_id, outstanding_amount, fee_status, updated_at_ms)
VALUES
  ('school-test', 'student-test-1', 50000, 'OUTSTANDING', 1700000002000);

DO $$
BEGIN
  IF (SELECT fee_status FROM public.students WHERE id='student-test-1') <> 'OUTSTANDING' THEN
    RAISE EXCEPTION 'Finance INSERT did not synchronize OUTSTANDING status to students';
  END IF;
END $$;

UPDATE public.student_finances
SET fee_status='CLEARED', updated_at_ms=1700000003000
WHERE school_id='school-test' AND student_id='student-test-1';

DO $$
BEGIN
  IF (SELECT fee_status FROM public.students WHERE id='student-test-1') <> 'CLEARED' THEN
    RAISE EXCEPTION 'Finance UPDATE did not synchronize CLEARED status to students';
  END IF;
  IF COALESCE(current_setting('app.finance_sync', true), '') = 'on' THEN
    RAISE EXCEPTION 'Internal finance sync flag was not reset';
  END IF;
END $$;

-- The internal bypass must not weaken direct client-side fee-status controls.
SELECT set_config('test.finance_authorized', 'false', false);
DO $$
BEGIN
  BEGIN
    UPDATE public.students SET fee_status='OUTSTANDING' WHERE id='student-test-1';
    RAISE EXCEPTION 'Expected direct non-finance fee-status update to fail';
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM = 'Expected direct non-finance fee-status update to fail' THEN RAISE; END IF;
    IF position('finance permission' in lower(SQLERRM)) = 0 THEN RAISE; END IF;
  END;
END $$;

-- Fee status is explicit: no default is allowed on student_finances.
DO $$
BEGIN
  BEGIN
    INSERT INTO public.student_finances (school_id, student_id, updated_at_ms)
    VALUES ('school-test', 'student-test-1', 1700000004000);
    RAISE EXCEPTION 'Expected student_finances.fee_status NOT NULL to reject omission';
  EXCEPTION WHEN not_null_violation THEN
    NULL;
  END;
END $$;


-- If the downstream student update fails, the finance row must roll back and
-- the internal context flag must still be restored.
CREATE FUNCTION pg_temp.fail_student_fee_update()
RETURNS trigger LANGUAGE plpgsql AS $fail$
BEGIN
  RAISE EXCEPTION 'CI forced student fee-status update failure';
END;
$fail$;
CREATE TRIGGER ci_fail_student_fee_update
BEFORE UPDATE OF fee_status ON public.students
FOR EACH ROW EXECUTE FUNCTION pg_temp.fail_student_fee_update();

DO $sync_failure$
BEGIN
  BEGIN
    UPDATE public.student_finances
    SET fee_status='OUTSTANDING', updated_at_ms=1700000005000
    WHERE school_id='school-test' AND student_id='student-test-1';
    RAISE EXCEPTION 'Expected downstream student update failure';
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM = 'Expected downstream student update failure' THEN RAISE; END IF;
    IF position('CI forced student fee-status update failure' in SQLERRM) = 0 THEN RAISE; END IF;
  END;
END $sync_failure$;

DO $sync_failure_assertions$
BEGIN
  IF (SELECT fee_status FROM public.student_finances
      WHERE school_id='school-test' AND student_id='student-test-1') <> 'CLEARED' THEN
    RAISE EXCEPTION 'Finance row was not rolled back after downstream sync failure';
  END IF;
  IF (SELECT fee_status FROM public.students WHERE id='student-test-1') <> 'CLEARED' THEN
    RAISE EXCEPTION 'Student fee status changed despite failed downstream update';
  END IF;
  IF COALESCE(current_setting('app.finance_sync', true), '') = 'on' THEN
    RAISE EXCEPTION 'Internal finance sync flag was not restored after exception';
  END IF;
END $sync_failure_assertions$;

SELECT 'PASS: Blocker 1 and finance sync migration integration assertions' AS result;
