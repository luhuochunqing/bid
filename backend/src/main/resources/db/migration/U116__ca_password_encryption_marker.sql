-- U116: Rollback CA password encryption marker (PR #)
-- Note: Encrypted passwords cannot be automatically reverted to plain text via SQL.
-- The CaPasswordMigrationRunner.decryptPassword() method handles compatibility.

SELECT 1;
