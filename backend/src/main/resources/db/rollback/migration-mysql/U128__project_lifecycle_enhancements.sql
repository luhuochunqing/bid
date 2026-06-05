-- Input: migration-mysql/V128__project_lifecycle_enhancements.sql
-- Output: rollback script removing V126 column additions (preserves shared columns from V108/V113).
-- Pos: Flyway down migration coverage for 西域数智化投标管理平台.

ALTER TABLE tasks DROP COLUMN review_comment;

DROP INDEX idx_project_closure_review_status ON project_closure;
ALTER TABLE project_closure
    DROP COLUMN project_summary,
    DROP COLUMN reviewed_at,
    DROP COLUMN reviewed_by,
    DROP COLUMN review_status;

ALTER TABLE project_retrospective
    DROP COLUMN report_attachment_id,
    DROP COLUMN process_issues,
    DROP COLUMN participants,
    DROP COLUMN meeting_type,
    DROP COLUMN meeting_time;

ALTER TABLE project_result
    DROP COLUMN evidence_file_ids;

ALTER TABLE project_evaluation
    MODIFY COLUMN notes VARCHAR(2048) NULL,
    DROP COLUMN evaluation_files_json;

DROP INDEX idx_project_initiation_review_status ON project_initiation_details;
ALTER TABLE project_initiation_details
    DROP COLUMN ai_risk_level,
    DROP COLUMN tender_document_id,
    DROP COLUMN customer_info_json,
    DROP COLUMN reviewed_at,
    DROP COLUMN reviewed_by,
    DROP COLUMN rejection_reason,
    DROP COLUMN review_status;
