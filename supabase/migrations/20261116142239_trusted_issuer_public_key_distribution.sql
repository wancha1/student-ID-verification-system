CREATE TABLE IF NOT EXISTS public.trusted_issuers (
    school_id text NOT NULL REFERENCES public.schools(id) ON DELETE CASCADE,
    key_id text NOT NULL,
    public_key_base64 text NOT NULL,
    label text NOT NULL DEFAULT 'LTC Authoritative Issuer',
    enrolled_at_ms bigint NOT NULL,
    is_revoked boolean NOT NULL DEFAULT false,
    PRIMARY KEY (school_id, key_id)
);

ALTER TABLE public.trusted_issuers ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE public.trusted_issuers FROM anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON TABLE public.trusted_issuers TO authenticated;

CREATE POLICY "active staff can read issuer public keys"
ON public.trusted_issuers
FOR SELECT
TO authenticated
USING (private.is_active_staff(
    school_id,
    ARRAY['ADMIN', 'HEAD_TEACHER', 'GATE_OFFICER', 'MEAL_STAFF']::text[]
));

CREATE POLICY "administrators can enroll issuer public keys"
ON public.trusted_issuers
FOR INSERT
TO authenticated
WITH CHECK (private.is_active_staff(school_id, ARRAY['ADMIN']::text[]));

CREATE POLICY "administrators can update issuer public keys"
ON public.trusted_issuers
FOR UPDATE
TO authenticated
USING (private.is_active_staff(school_id, ARRAY['ADMIN']::text[]))
WITH CHECK (private.is_active_staff(school_id, ARRAY['ADMIN']::text[]));
