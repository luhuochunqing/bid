-- Input: migration-mysql/V133__project_list_initiation_alignment.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

ALTER TABLE project_initiation_details
    DROP COLUMN customer_grade,
    DROP COLUMN bid_status,
    DROP COLUMN bidding_leader_name,
    DROP COLUMN bidding_platform,
    DROP COLUMN bid_result_status,
    DROP COLUMN project_leader_name,
    DROP COLUMN leader_department;
