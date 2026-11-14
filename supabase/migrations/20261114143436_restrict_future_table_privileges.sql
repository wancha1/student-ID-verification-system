-- Prevent future public-schema tables created by the postgres migration owner
-- from inheriting broad anon/authenticated privileges.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  REVOKE ALL ON TABLES FROM anon, authenticated;
