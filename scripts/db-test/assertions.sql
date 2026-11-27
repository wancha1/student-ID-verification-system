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
SELECT set_config('test.staff_role', 'ADMIN', false);
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


-- Head Teacher remains able to read private profiles and photos, but cannot write them.
SELECT set_config('test.staff_role', 'HEAD_TEACHER', false);
SET ROLE authenticated;
DO $$
DECLARE affected integer;
BEGIN
  IF (SELECT count(*) FROM public.student_private
      WHERE school_id = 'school-test' AND student_id = 'student-test-1') <> 1 THEN
    RAISE EXCEPTION 'Head Teacher must retain private-profile SELECT access';
  END IF;

  BEGIN
    INSERT INTO public.student_private (school_id, student_id, guardian_name)
    VALUES ('school-test', 'student-test-2', 'Must be denied');
    RAISE EXCEPTION 'Expected Head Teacher private-profile INSERT to be denied';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  UPDATE public.student_private
  SET guardian_name = 'Head Teacher write must be denied'
  WHERE school_id = 'school-test' AND student_id = 'student-test-1';
  GET DIAGNOSTICS affected = ROW_COUNT;
  IF affected <> 0 THEN
    RAISE EXCEPTION 'Head Teacher unexpectedly updated a private profile';
  END IF;

  IF (SELECT count(*) FROM storage.objects
      WHERE bucket_id = 'student-photos'
        AND name = 'school-test/student-test-1/photo.jpg') <> 1 THEN
    RAISE EXCEPTION 'Head Teacher must retain student-photo SELECT access';
  END IF;

  BEGIN
    INSERT INTO storage.objects (bucket_id, name)
    VALUES ('student-photos', 'school-test/student-test-2/photo.jpg');
    RAISE EXCEPTION 'Expected Head Teacher photo INSERT to be denied';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  UPDATE storage.objects
  SET name = 'school-test/student-test-1/changed.jpg'
  WHERE bucket_id = 'student-photos'
    AND name = 'school-test/student-test-1/photo.jpg';
  GET DIAGNOSTICS affected = ROW_COUNT;
  IF affected <> 0 THEN
    RAISE EXCEPTION 'Head Teacher unexpectedly updated a student photo';
  END IF;

  DELETE FROM storage.objects
  WHERE bucket_id = 'student-photos'
    AND name = 'school-test/student-test-1/photo.jpg';
  GET DIAGNOSTICS affected = ROW_COUNT;
  IF affected <> 0 THEN
    RAISE EXCEPTION 'Head Teacher unexpectedly deleted a student photo';
  END IF;
END $$;
RESET ROLE;

-- Admin retains the registry and photo write capabilities.
SELECT set_config('test.staff_role', 'ADMIN', false);
SET ROLE authenticated;
UPDATE public.student_private
SET guardian_name = 'Admin update allowed'
WHERE school_id = 'school-test' AND student_id = 'student-test-1';
INSERT INTO storage.objects (bucket_id, name)
VALUES ('student-photos', 'school-test/student-test-2/photo.jpg');
RESET ROLE;

SELECT 'PASS: Head Teacher read-only student profile and photo boundary' AS result;


-- Gate and Meal sync is append-only and idempotent under offline retries.
SELECT set_config('test.staff_authorized', 'true', false);
SELECT set_config('test.staff_role', 'GATE_OFFICER', false);
SET ROLE authenticated;
DO $$
DECLARE inserted boolean;
BEGIN
  inserted := public.scholink_insert_gate_log_once(
    '{"school_id":"school-test","id":"gate-event-1","student_number":"SYN-001","student_name":"Original name","decision":"APPROVED","gate_location":"Main gate","timestamp_ms":1700000000000}'::jsonb
  );
  IF NOT inserted THEN RAISE EXCEPTION 'Gate first insert should be accepted'; END IF;

  inserted := public.scholink_insert_gate_log_once(
    '{"school_id":"school-test","id":"gate-event-1","student_number":"SYN-001","student_name":"Overwritten name","decision":"DENIED","gate_location":"Other gate","timestamp_ms":1700000000001}'::jsonb
  );
  IF inserted THEN RAISE EXCEPTION 'Gate retry must be a no-op'; END IF;

  IF (SELECT student_name FROM public.gate_logs
      WHERE school_id='school-test' AND id='gate-event-1') <> 'Original name' THEN
    RAISE EXCEPTION 'Gate retry overwrote an existing event';
  END IF;

  BEGIN
    PERFORM public.scholink_insert_meal_once(
      '{"school_id":"school-test","id":"meal-forbidden","student_id":"student-test-1","student_number":"SYN-001","student_name":"Synthetic","grade_class":"S1","meal_type":"LUNCH","meal_date":"2026-11-27","status":"SERVED","serving_station":"Hall","timestamp_ms":1700000000000}'::jsonb
    );
    RAISE EXCEPTION 'Gate Officer must not insert meal records';
  EXCEPTION WHEN insufficient_privilege THEN NULL;
  END;
END $$;
RESET ROLE;

SELECT set_config('test.staff_role', 'MEAL_STAFF', false);
SET ROLE authenticated;
DO $$
DECLARE inserted boolean;
BEGIN
  inserted := public.scholink_insert_meal_once(
    '{"school_id":"school-test","id":"meal-event-1","student_id":"student-test-1","student_number":"SYN-001","student_name":"Synthetic","grade_class":"S1","meal_type":"LUNCH","meal_date":"2026-11-27","status":"SERVED","notes":"First device","serving_station":"Hall","timestamp_ms":1700000000000}'::jsonb
  );
  IF NOT inserted THEN RAISE EXCEPTION 'Meal first insert should be accepted'; END IF;

  inserted := public.scholink_insert_meal_once(
    '{"school_id":"school-test","id":"meal-event-2","student_id":"student-test-1","student_number":"SYN-001","student_name":"Changed","grade_class":"S1","meal_type":"LUNCH","meal_date":"2026-11-27","status":"SERVED","notes":"Second device","serving_station":"Other hall","timestamp_ms":1700000000001}'::jsonb
  );
  IF inserted THEN RAISE EXCEPTION 'Duplicate student meal on same date must be ignored'; END IF;

  IF (SELECT count(*) FROM public.meals
      WHERE school_id='school-test' AND student_id='student-test-1'
        AND meal_type='LUNCH' AND meal_date='2026-11-27') <> 1 THEN
    RAISE EXCEPTION 'Meal natural-key duplicate was not prevented';
  END IF;

  BEGIN
    PERFORM public.scholink_insert_gate_log_once(
      '{"school_id":"school-test","id":"gate-forbidden","student_number":"SYN-001","student_name":"Synthetic","decision":"APPROVED","gate_location":"Main gate","timestamp_ms":1700000000000}'::jsonb
    );
    RAISE EXCEPTION 'Meal Staff must not insert gate records';
  EXCEPTION WHEN insufficient_privilege THEN NULL;
  END;
END $$;
RESET ROLE;

DO $$
BEGIN
  IF has_table_privilege('authenticated', 'public.gate_logs', 'UPDATE')
     OR has_table_privilege('authenticated', 'public.meals', 'UPDATE') THEN
    RAISE EXCEPTION 'Gate and Meal clients must not have UPDATE table privileges';
  END IF;
END $$;

SELECT 'PASS: append-only idempotent Gate and Meal sync' AS result;


-- Meal validation is enforced by PostgreSQL, including callers that bypass Android.
SELECT set_config('test.staff_role', 'MEAL_STAFF', false);
SET ROLE authenticated;
DO $$
BEGIN
  BEGIN
    PERFORM public.scholink_insert_meal_once(
      '{"school_id":"school-test","id":"meal-breakfast-forbidden","student_id":"student-test-1","student_number":"SYN-001","student_name":"Synthetic","grade_class":"S1","meal_type":"BREAKFAST","meal_date":"2026-11-28","status":"SERVED","serving_station":"Hall","timestamp_ms":1700000001000}'::jsonb
    );
    RAISE EXCEPTION 'Breakfast must be rejected by the database';
  EXCEPTION WHEN check_violation THEN NULL;
  END;
  IF public.scholink_insert_meal_once(
      '{"school_id":"school-test","id":"meal-supper-allowed","student_id":"student-test-1","student_number":"SYN-001","student_name":"Synthetic","grade_class":"S1","meal_type":"DINNER","meal_date":"2026-11-28","status":"SERVED","serving_station":"Hall","timestamp_ms":1700000001001}'::jsonb
    ) IS DISTINCT FROM true THEN
    RAISE EXCEPTION 'Supper (DINNER) should be accepted';
  END IF;
END $$;
RESET ROLE;

-- Attendance corrections preserve the original row and append a before/after event.
SELECT set_config('test.staff_role', 'TEACHER', false);
SET ROLE authenticated;
INSERT INTO public.attendance_records
  (school_id,id,student_id,student_number,student_name,grade_class,subject,session_name,
   lesson_date,timestamp_ms,teacher_name,is_present,notes,is_correction,updated_at_ms)
VALUES
  ('school-test','attendance-1','student-test-1','SYN-001','Synthetic Student','S1',
   'Mathematics','Morning','2026-11-28',1700000000000,'Synthetic Teacher',true,'Initial mark',false,1700000000000);
UPDATE public.attendance_records
SET is_present = false, notes = 'Corrected after register review',
    is_correction = true, corrected_by = 'Synthetic Head Teacher', updated_at_ms = 1700000001000
WHERE school_id = 'school-test' AND id = 'attendance-1';
RESET ROLE;
DO $$
BEGIN
  IF (SELECT count(*) FROM public.attendance_record_events
      WHERE school_id='school-test' AND record_id='attendance-1') <> 2 THEN
    RAISE EXCEPTION 'Attendance record and correction must create two audit events';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM public.attendance_record_events
    WHERE record_id='attendance-1' AND event_type='CORRECTED'
      AND old_record->>'is_present'='true'
      AND new_record->>'is_present'='false'
  ) THEN
    RAISE EXCEPTION 'Attendance correction did not preserve before/after values';
  END IF;
END $$;

-- Bursar can submit exam requests but cannot approve or reject them.
SELECT set_config('test.staff_role', 'BURSAR', false);
SET ROLE authenticated;
INSERT INTO public.exam_exceptions
  (school_id,id,student_id,student_number,student_name,exam_code,requested_by_bursar,
   recommendation_reason,is_approved,requested_at_ms,updated_at_ms)
VALUES
  ('school-test','exam-1','student-test-1','SYN-001','Synthetic Student','UACE-MOCK',
   'Synthetic Bursar','Fee arrangement submitted',false,1700000000000,1700000000000);
DO $$
BEGIN
  BEGIN
    PERFORM public.scholink_decide_exam_exception('school-test','exam-1','APPROVED','not allowed');
    RAISE EXCEPTION 'Bursar must not decide an exam exception';
  EXCEPTION WHEN insufficient_privilege THEN NULL;
  END;
END $$;
RESET ROLE;

-- Head Teacher can make a one-time approval; the immutable log records it.
SELECT set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000456', false);
SELECT set_config('test.staff_role', 'HEAD_TEACHER', false);
SET ROLE authenticated;
SELECT public.scholink_decide_exam_exception('school-test','exam-1','APPROVED','Verified by leadership');
RESET ROLE;
DO $$
BEGIN
  IF (SELECT count(*) FROM public.exam_exception_events
      WHERE school_id='school-test' AND record_id='exam-1') <> 2 THEN
    RAISE EXCEPTION 'Exam request and approval must create two audit events';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.exam_exception_events
      WHERE record_id='exam-1' AND event_type='APPROVED'
        AND actor_user_id='00000000-0000-0000-0000-000000000456') THEN
    RAISE EXCEPTION 'Exam approval event or actor attribution missing';
  END IF;
END $$;

-- A second request demonstrates explicit rejection as a distinct decision event.
SELECT set_config('test.staff_role', 'BURSAR', false);
SET ROLE authenticated;
INSERT INTO public.exam_exceptions
  (school_id,id,student_id,student_number,student_name,exam_code,requested_by_bursar,
   recommendation_reason,is_approved,requested_at_ms,updated_at_ms)
VALUES
  ('school-test','exam-2','student-test-1','SYN-001','Synthetic Student','UCE-MOCK',
   'Synthetic Bursar','Insufficient supporting evidence',false,1700000000001,1700000000001);
RESET ROLE;
SELECT set_config('test.staff_role', 'ADMIN', false);
SET ROLE authenticated;
SELECT public.scholink_decide_exam_exception('school-test','exam-2','REJECTED','Evidence is incomplete');
RESET ROLE;
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.exam_exception_events
      WHERE record_id='exam-2' AND event_type='REJECTED') THEN
    RAISE EXCEPTION 'Exam rejection was not recorded as a separate event';
  END IF;
  BEGIN
    UPDATE public.attendance_record_events SET event_type='UPDATED'
    WHERE record_id='attendance-1';
    RAISE EXCEPTION 'Attendance audit events must reject mutation';
  EXCEPTION WHEN OTHERS THEN
    IF SQLERRM = 'Attendance audit events must reject mutation' THEN RAISE; END IF;
    IF position('append-only' in lower(SQLERRM)) = 0 THEN RAISE; END IF;
  END;
END $$;

SELECT 'PASS: meal validation, attendance corrections, and exam decision audit' AS result;
