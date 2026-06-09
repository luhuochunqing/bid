-- V116: CA certificate password encryption migration marker
-- The actual encryption is performed by CaPasswordMigrationRunner (Spring ApplicationRunner).
-- This migration ensures the ca_password column is wide enough for AES-256-GCM Base64 output.

-- Column already has length=512 in the entity, which is sufficient.
-- No DDL change needed; this migration serves as a version marker.

SELECT 1;
