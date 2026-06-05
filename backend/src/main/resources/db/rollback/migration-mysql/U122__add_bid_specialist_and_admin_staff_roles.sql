-- Input: migration-mysql/V122__add_bid_specialist_and_admin_staff_roles.sql
-- Output: rollback script for mysql environments; removes bid_specialist and admin_staff roles.
-- Pos: Flyway down migration coverage for 西域数智化投标管理平台.
-- Note: Data rollback required - deleted roles cannot be recovered from migration.

DELETE FROM roles WHERE code IN ('bid_specialist', 'admin_staff');
