-- The equivalent meals_school_student_meal_day_uq index already existed.
-- Remove the duplicate index introduced by the previous migration; retain the original unique index.
DROP INDEX IF EXISTS public.meals_one_serving_per_student_type_day;
