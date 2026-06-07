-- Input: migration-mysql/V97__workflow_form_designer.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- Manual rollback required for statement: DROP PROCEDURE IF EXISTS p_add_col_if_missing
-- Manual rollback required for statement: CALL p_add_col_if_missing('workflow_form_instances', 'oa_payload_json', 'TEXT')
-- Manual rollback required for statement: CALL p_add_col_if_missing('workflow_form_instances', 'oa_binding_snapshot_json', 'TEXT')
-- Manual rollback required for statement: CALL p_add_col_if_missing('workflow_form_instances', 'schema_snapshot_json', 'TEXT')
-- Manual rollback required for statement: CALL p_add_col_if_missing('workflow_form_instances', 'template_version', 'INT')
-- Manual rollback required for statement: END$$ DELIMITER
-- Manual rollback required for statement: END IF
-- Manual rollback required for statement: DEALLOCATE PREPARE stmt
-- Manual rollback required for statement: EXECUTE stmt
-- Manual rollback required for statement: PREPARE stmt FROM @ddl
-- Manual rollback required for statement: DELIMITER $$ CREATE PROCEDURE p_add_col_if_missing( IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition VARCHAR(255) ) BEGIN IF NOT EXISTS ( SELECT 1 FROM information_
-- Manual rollback required for statement: DROP PROCEDURE IF EXISTS p_add_col_if_missing
-- Data rollback required for INSERT INTO workflow_form_template_versions; verify seed rows before deleting.
-- Data rollback required for INSERT INTO workflow_form_template_drafts; verify seed rows before deleting.
DROP TABLE IF EXISTS workflow_form_template_versions;
DROP TABLE IF EXISTS workflow_form_template_drafts;
