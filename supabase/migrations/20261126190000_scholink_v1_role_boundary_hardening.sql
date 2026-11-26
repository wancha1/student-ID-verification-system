-- Scholink V1: the Head Teacher is read-only for student profiles and photos.
-- Keep the historical migrations immutable; this migration narrows the final policy set.

DROP POLICY IF EXISTS "leadership can create private student records"
  ON public.student_private;
DROP POLICY IF EXISTS "leadership can update private student records"
  ON public.student_private;
DROP POLICY IF EXISTS "administrators can create private student records"
  ON public.student_private;
DROP POLICY IF EXISTS "administrators can update private student records"
  ON public.student_private;

CREATE POLICY "administrators can create private student records"
  ON public.student_private
  FOR INSERT TO authenticated
  WITH CHECK (private.is_active_staff(school_id, ARRAY['ADMIN']::text[]));

CREATE POLICY "administrators can update private student records"
  ON public.student_private
  FOR UPDATE TO authenticated
  USING (private.is_active_staff(school_id, ARRAY['ADMIN']::text[]))
  WITH CHECK (private.is_active_staff(school_id, ARRAY['ADMIN']::text[]));

-- Head Teachers retain SELECT access through the existing leadership read policy.
-- Photo writes are restricted to Admin because photos are part of student registry
-- maintenance, not a read-only management capability.
DROP POLICY IF EXISTS "leadership can upload student photos" ON storage.objects;
DROP POLICY IF EXISTS "leadership can update student photos" ON storage.objects;
DROP POLICY IF EXISTS "leadership can delete student photos" ON storage.objects;
DROP POLICY IF EXISTS "administrators can upload student photos" ON storage.objects;
DROP POLICY IF EXISTS "administrators can update student photos" ON storage.objects;
DROP POLICY IF EXISTS "administrators can delete student photos" ON storage.objects;

CREATE POLICY "administrators can upload student photos"
  ON storage.objects
  FOR INSERT TO authenticated
  WITH CHECK (
    bucket_id = 'student-photos'
    AND private.is_active_staff(
      (storage.foldername(name))[1],
      ARRAY['ADMIN']::text[]
    )
    AND (storage.foldername(name))[2] IS NOT NULL
    AND storage.filename(name) = 'photo.jpg'
  );

CREATE POLICY "administrators can update student photos"
  ON storage.objects
  FOR UPDATE TO authenticated
  USING (
    bucket_id = 'student-photos'
    AND private.is_active_staff(
      (storage.foldername(name))[1],
      ARRAY['ADMIN']::text[]
    )
    AND (storage.foldername(name))[2] IS NOT NULL
    AND storage.filename(name) = 'photo.jpg'
  )
  WITH CHECK (
    bucket_id = 'student-photos'
    AND private.is_active_staff(
      (storage.foldername(name))[1],
      ARRAY['ADMIN']::text[]
    )
    AND (storage.foldername(name))[2] IS NOT NULL
    AND storage.filename(name) = 'photo.jpg'
  );

CREATE POLICY "administrators can delete student photos"
  ON storage.objects
  FOR DELETE TO authenticated
  USING (
    bucket_id = 'student-photos'
    AND private.is_active_staff(
      (storage.foldername(name))[1],
      ARRAY['ADMIN']::text[]
    )
    AND (storage.foldername(name))[2] IS NOT NULL
    AND storage.filename(name) = 'photo.jpg'
  );
