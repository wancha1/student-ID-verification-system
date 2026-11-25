-- A student may receive only one serving of a given meal type per school day.
-- This unique key makes offline retries and simultaneous scans from multiple devices idempotent.
CREATE UNIQUE INDEX IF NOT EXISTS meals_one_serving_per_student_type_day
ON public.meals (school_id, student_id, meal_type, meal_date);
