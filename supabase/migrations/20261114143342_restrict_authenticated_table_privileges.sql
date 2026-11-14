-- LTC V1: remove broad table privileges that bypass row-level security.
-- PostgreSQL TRUNCATE is not governed by RLS.
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM anon;
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM authenticated;

-- Grant only operations used by the application. RLS remains the row-level authority.
GRANT SELECT ON TABLE public.schools TO authenticated;
GRANT SELECT, INSERT, UPDATE ON TABLE public.staff_memberships TO authenticated;
GRANT SELECT, INSERT, UPDATE ON TABLE public.students TO authenticated;
GRANT SELECT, INSERT, UPDATE ON TABLE public.student_private TO authenticated;
GRANT SELECT, INSERT, UPDATE ON TABLE public.cards TO authenticated;
GRANT SELECT, INSERT ON TABLE public.gate_logs TO authenticated;
GRANT SELECT, INSERT ON TABLE public.meals TO authenticated;
GRANT SELECT, INSERT, UPDATE ON TABLE public.attendance_records TO authenticated;
GRANT SELECT, INSERT, UPDATE ON TABLE public.exam_exceptions TO authenticated;
