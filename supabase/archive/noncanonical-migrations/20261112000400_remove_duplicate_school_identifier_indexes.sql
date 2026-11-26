-- Remove redundant standalone unique indexes. The equivalent unique
-- constraints remain in place and continue enforcing the same keys.
drop index if exists public.cards_school_card_identifier_uq;
drop index if exists public.students_school_student_number_uq;
