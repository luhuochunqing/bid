-- Input: migration-mysql/V1218__backfill_table_column_comments.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1218: 回滚 backfill_table_column_comments（清空本次补齐的全部注释，恢复为空注释状态）
-- 仅元数据回滚，无数据丢失。

ALTER TABLE `V1091_staff_users_backup`
COMMENT='',
  MODIFY COLUMN `original_role_id` bigint NULL COMMENT '',
  MODIFY COLUMN `original_role` enum('ADMIN','MANAGER','STAFF') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `original_enabled` bit(1) NOT NULL COMMENT '',
  MODIFY COLUMN `migrated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '';

ALTER TABLE `account_borrow_applications`
  MODIFY COLUMN `project_id` bigint NULL COMMENT '',
  MODIFY COLUMN `status` enum('PENDING_APPROVAL','BORROWED','REJECTED','RETURNED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `approval_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '';

ALTER TABLE `account_borrow_requests`
COMMENT='',
  MODIFY COLUMN `approved_at` datetime NULL COMMENT '',
  MODIFY COLUMN `returned_at` datetime NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `ai_analysis_jobs`
COMMENT='',
  MODIFY COLUMN `completed_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `requested_by` bigint NULL COMMENT '',
  MODIFY COLUMN `target_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `analysis_type` enum('TENDER_ANALYSIS','PROJECT_SCORE_PREVIEW') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` enum('PENDING','COMPLETED','FAILED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `target_type` enum('TENDER','PROJECT') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `ai_analysis_results`
COMMENT='',
  MODIFY COLUMN `score` int NOT NULL COMMENT '',
  MODIFY COLUMN `job_id` bigint NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '',
  MODIFY COLUMN `tender_id` bigint NULL COMMENT '',
  MODIFY COLUMN `risk_level` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `suggestion` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `payload_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `analysis_type` enum('TENDER_ANALYSIS','PROJECT_SCORE_PREVIEW') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `alert_history`
COMMENT='',
  MODIFY COLUMN `resolved` bit(1) NOT NULL COMMENT '',
  MODIFY COLUMN `acknowledged_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `resolved_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `rule_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `related_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `level` enum('LOW','MEDIUM','HIGH','CRITICAL') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `alert_rules`
COMMENT='',
  MODIFY COLUMN `enabled` bit(1) NOT NULL COMMENT '',
  MODIFY COLUMN `threshold` decimal(19,2) NOT NULL COMMENT '',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `condition` enum('GREATER_THAN','LESS_THAN','EQUALS','CONTAINS') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `type` enum('DEADLINE','BUDGET','RISK','DOCUMENT','QUALIFICATION_EXPIRY','DEPOSIT_RETURN','PERFORMANCE_EXPIRY','CA_EXPIRY','CA_BORROW_OVERDUE') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `created_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '';

ALTER TABLE `api_keys`
COMMENT='',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `key_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `scopes` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` enum('ACTIVE','DISABLED','EXPIRED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '',
  MODIFY COLUMN `expires_at` datetime NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `approval_actions`
COMMENT='',
  MODIFY COLUMN `action_time` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `actor_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `approval_request_id` binary(16) NOT NULL COMMENT '',
  MODIFY COLUMN `actor_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `action_type` enum('SUBMIT','APPROVE','REJECT','CANCEL') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `new_status` enum('PENDING','APPROVED','REJECTED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `previous_status` enum('PENDING','APPROVED','REJECTED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` binary(16) NOT NULL COMMENT '';

ALTER TABLE `approval_requests`
COMMENT='',
  MODIFY COLUMN `is_read` bit(1) NULL COMMENT '',
  MODIFY COLUMN `priority` int NOT NULL COMMENT '',
  MODIFY COLUMN `completed_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `current_approver_id` bigint NULL COMMENT '',
  MODIFY COLUMN `due_date` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `requester_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `submitted_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `approval_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `current_approver_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `requester_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `project_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `attachment_ids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `status` enum('PENDING','APPROVED','REJECTED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `updated_by` bigint NULL COMMENT '',
  MODIFY COLUMN `id` binary(16) NOT NULL COMMENT '';

ALTER TABLE `archive_file`
COMMENT='',
  MODIFY COLUMN `archive_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `document_category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `file_size` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `upload_user_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `upload_user_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `archive_log`
COMMENT='',
  MODIFY COLUMN `archive_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `operator_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `operator_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `action_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `action_content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `assembly_templates`
COMMENT='',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `category` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `template_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `variables` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `audit_logs`
COMMENT='',
  MODIFY COLUMN `success` bit(1) NOT NULL COMMENT '',
  MODIFY COLUMN `timestamp` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `action` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `ip_address` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `entity_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '',
  MODIFY COLUMN `entity_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `new_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `old_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `user_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `bar_assets`
COMMENT='',
  MODIFY COLUMN `acquire_date` date NOT NULL COMMENT '',
  MODIFY COLUMN `asset_value` decimal(19,2) NOT NULL COMMENT '',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` enum('AVAILABLE','IN_USE','MAINTENANCE','RETIRED','DISPOSED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `type` enum('EQUIPMENT','FACILITY','VEHICLE','INVENTORY','LICENSE','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '';

ALTER TABLE `bar_certificate_borrow_records`
COMMENT='',
  MODIFY COLUMN `expected_return_date` date NULL COMMENT '',
  MODIFY COLUMN `borrowed_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `certificate_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '',
  MODIFY COLUMN `returned_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `borrower` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `purpose` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `status` enum('BORROWED','RETURNED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '';

ALTER TABLE `bar_certificates`
COMMENT='',
  MODIFY COLUMN `expected_return_date` date NULL COMMENT '',
  MODIFY COLUMN `expiry_date` date NULL COMMENT '',
  MODIFY COLUMN `bar_asset_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `current_project_id` bigint NULL COMMENT '',
  MODIFY COLUMN `current_borrower` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `holder` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `provider` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `borrow_purpose` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `location` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `serial_no` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` enum('AVAILABLE','BORROWED','EXPIRED','DISABLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '';

ALTER TABLE `bar_site_accounts`
COMMENT='',
  MODIFY COLUMN `bar_asset_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `role` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `phone` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `owner` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `email` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '';

ALTER TABLE `bar_site_attachments`
COMMENT='',
  MODIFY COLUMN `bar_asset_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `uploaded_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `size` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `content_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `uploaded_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `bar_site_sops`
COMMENT='',
  MODIFY COLUMN `bar_asset_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `estimated_time` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `reset_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `unlock_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `contacts_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `faqs_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `history_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `required_docs_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '';

ALTER TABLE `bar_site_verifications`
COMMENT='',
  MODIFY COLUMN `bar_asset_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `verified_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `verified_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `bid_agent_artifacts`
COMMENT='',
  MODIFY COLUMN `run_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `artifact_type` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `handoff_target` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `status` enum('DRAFTED','READY_FOR_WRITER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DRAFTED' COMMENT '',
  MODIFY COLUMN `applied_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '';

ALTER TABLE `bid_agent_runs`
COMMENT='',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `tender_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `project_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `tender_title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` enum('DRAFTED','REVIEWED','READY_FOR_WRITER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DRAFTED' COMMENT '',
  MODIFY COLUMN `snapshot_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `requirement_classification_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `material_match_score_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `gap_check_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `manual_confirmation_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `write_coverage_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `draft_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `review_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `generator_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `reviewed_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `applied_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '';

ALTER TABLE `bid_case_slice`
COMMENT='',
  MODIFY COLUMN `project_dir` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `project_idx` int NOT NULL COMMENT '',
  MODIFY COLUMN `docx_file` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `docx_label` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `section_idx` int NOT NULL COMMENT '',
  MODIFY COLUMN `level` int NOT NULL COMMENT '',
  MODIFY COLUMN `title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `text_preview` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `text_length` int NOT NULL COMMENT '',
  MODIFY COLUMN `para_count` int NOT NULL COMMENT '',
  MODIFY COLUMN `embedding` mediumblob NULL COMMENT '',
  MODIFY COLUMN `embedding_model` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `embedding_dim` int NULL COMMENT '',
  MODIFY COLUMN `embedding_at` timestamp NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `bid_file`
COMMENT='',
  MODIFY COLUMN `upload_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `original_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `object_key` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `bucket` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `file_size` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `file_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `mime_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `creator_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `error_message` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `completed_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '';

ALTER TABLE `bid_match_model_versions`
COMMENT='',
  MODIFY COLUMN `model_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `version_no` int NOT NULL COMMENT '',
  MODIFY COLUMN `snapshot_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `active_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `activated_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `activated_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '';

ALTER TABLE `bid_match_score_evaluations`
COMMENT='',
  MODIFY COLUMN `tender_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `model_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `model_version_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `model_version_no` int NOT NULL COMMENT '',
  MODIFY COLUMN `total_score` decimal(7,2) NOT NULL COMMENT '',
  MODIFY COLUMN `dimension_scores_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `evidence_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `evidence_fingerprint` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `model_snapshot_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `evaluated_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `evaluated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '';

ALTER TABLE `bid_match_scoring_models`
COMMENT='',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `draft_revision` bigint NOT NULL DEFAULT 1 COMMENT '',
  MODIFY COLUMN `model_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `active_version_id` bigint NULL COMMENT '',
  MODIFY COLUMN `active_version_no` int NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '';

ALTER TABLE `bid_requirement_items`
COMMENT='',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `tender_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `project_document_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `category` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `source_excerpt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `mandatory` tinyint(1) NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `confidence` int NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '';

ALTER TABLE `bid_result_fetch_results`
COMMENT='',
  MODIFY COLUMN `amount` decimal(19,2) NULL COMMENT '',
  MODIFY COLUMN `contract_duration_months` int NULL COMMENT '',
  MODIFY COLUMN `contract_end_date` date NULL COMMENT '',
  MODIFY COLUMN `contract_start_date` date NULL COMMENT '',
  MODIFY COLUMN `sku_count` int NULL COMMENT '',
  MODIFY COLUMN `analysis_document_id` bigint NULL COMMENT '',
  MODIFY COLUMN `confirmed_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `confirmed_by` bigint NULL COMMENT '',
  MODIFY COLUMN `fetch_time` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `notice_document_id` bigint NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '',
  MODIFY COLUMN `tender_id` bigint NULL COMMENT '',
  MODIFY COLUMN `source` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `project_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `win_announce_doc_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `ignored_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `registration_type` enum('MANUAL','SYNC','FETCH') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `result` enum('WON','LOST') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` enum('PENDING','CONFIRMED','IGNORED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `remark` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '';

ALTER TABLE `bid_result_reminders`
COMMENT='',
  MODIFY COLUMN `attachment_document_id` bigint NULL COMMENT '',
  MODIFY COLUMN `last_result_id` bigint NULL COMMENT '',
  MODIFY COLUMN `owner_id` bigint NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `remind_time` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `uploaded_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `uploaded_by` bigint NULL COMMENT '',
  MODIFY COLUMN `last_reminder_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `project_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_by_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `owner_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `reminder_type` enum('NOTICE','REPORT') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` enum('PENDING','REMINDED','UPLOADED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '';

ALTER TABLE `bid_result_sync_logs`
COMMENT='',
  MODIFY COLUMN `affected_count` int NOT NULL COMMENT '',
  MODIFY COLUMN `operator_id` bigint NULL COMMENT '',
  MODIFY COLUMN `source` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `operator_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `operation_type` enum('SYNC','FETCH') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `bid_tender_document_snapshots`
COMMENT='',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `tender_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `project_document_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `file_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `content_type` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `file_url` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `storage_path` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `content_sha256` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `extracted_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `profile_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `extractor_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `analyzer_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '';

ALTER TABLE `brand_auth_alert_config`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `brand_auth_attachment`
  MODIFY COLUMN `attachment_type` enum('AUTH_DOC','SUPPLEMENTARY','AGENT_AUTH_1','AGENT_AUTH_2') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `file_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `brand_auth_operation_log`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `brand_authorization_deprecated`
  MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `business_qualifications`
COMMENT='',
  MODIFY COLUMN `expected_return_date` date NULL COMMENT '',
  MODIFY COLUMN `expiry_date` date NULL COMMENT '',
  MODIFY COLUMN `issue_date` date NULL COMMENT '',
  MODIFY COLUMN `reminder_days` int NOT NULL COMMENT '',
  MODIFY COLUMN `reminder_enabled` bit(1) NOT NULL COMMENT '',
  MODIFY COLUMN `last_reminded_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `current_project_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `certificate_no` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `current_borrower` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `current_department` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `holder_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `issuer` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `cert_review_note` date NULL COMMENT '',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `subject_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `file_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `borrow_purpose` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `category` enum('LICENSE','PRODUCT','PERSONNEL','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `current_borrow_status` enum('AVAILABLE','BORROWED','RETURNED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `subject_type` enum('COMPANY','SUBSIDIARY') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `audit_log_file_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `last_review_reminded_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '';

ALTER TABLE `ca_borrow_applications`
  MODIFY COLUMN `applicant_employee_number` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `ca_borrow_events`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `ca_certificate_import_task`
COMMENT='',
  MODIFY COLUMN `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING' COMMENT '',
  MODIFY COLUMN `total_rows` int NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `valid_rows` int NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `invalid_rows` int NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `imported_rows` int NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `updated_rows` int NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `error_details` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `source_filename` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `created_by_username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `completed_at` datetime NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `ca_certificate_platforms`
COMMENT='',
  MODIFY COLUMN `ca_certificate_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `platform_account_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `ca_certificates`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `calendar_events`
COMMENT='',
  MODIFY COLUMN `event_date` date NOT NULL COMMENT '',
  MODIFY COLUMN `is_urgent` bit(1) NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '',
  MODIFY COLUMN `title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `event_type` enum('DEADLINE','MEETING','MILESTONE','REMINDER','SUBMISSION','REVIEW') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '';

ALTER TABLE `case_attachment_names`
COMMENT='',
  MODIFY COLUMN `case_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `attachment_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '';

ALTER TABLE `case_highlights`
COMMENT='',
  MODIFY COLUMN `case_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `highlight` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '';

ALTER TABLE `case_lessons_learned`
COMMENT='',
  MODIFY COLUMN `case_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `lesson_learned` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '';

ALTER TABLE `case_reference_records`
COMMENT='',
  MODIFY COLUMN `case_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `referenced_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `referenced_by` bigint NULL COMMENT '',
  MODIFY COLUMN `reference_context` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `reference_target` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `referenced_by_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `case_share_records`
COMMENT='',
  MODIFY COLUMN `case_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `expires_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `created_by_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `token` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `case_success_factors`
COMMENT='',
  MODIFY COLUMN `case_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `success_factor` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '';

ALTER TABLE `case_tags`
COMMENT='',
  MODIFY COLUMN `case_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `tag` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '';

ALTER TABLE `case_technologies`
COMMENT='',
  MODIFY COLUMN `case_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `technology` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '';

ALTER TABLE `cases`
COMMENT='',
  MODIFY COLUMN `amount` decimal(38,2) NOT NULL COMMENT '',
  MODIFY COLUMN `project_date` date NULL COMMENT '',
  MODIFY COLUMN `published_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `source_project_id` bigint NULL COMMENT '',
  MODIFY COLUMN `use_count` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `view_count` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `visibility` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `archive_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `customer_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `document_snapshot_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `price_strategy` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `product_line` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `project_period` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `search_document` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `industry` enum('REAL_ESTATE','INFRASTRUCTURE','MANUFACTURING','ENERGY','TRANSPORTATION','ENVIRONMENTAL','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `outcome` enum('WON','LOST','IN_PROGRESS') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '';

ALTER TABLE `collaboration_threads`
COMMENT='',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` enum('OPEN','IN_PROGRESS','RESOLVED','CLOSED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '';

ALTER TABLE `comments`
COMMENT='',
  MODIFY COLUMN `parent_id` bigint NULL COMMENT '',
  MODIFY COLUMN `thread_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `mentions` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `is_deleted` bit(1) NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '';

ALTER TABLE `competition_analyses`
COMMENT='',
  MODIFY COLUMN `win_probability` decimal(5,2) NULL COMMENT '',
  MODIFY COLUMN `analysis_date` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `competitor_id` bigint NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `competitive_advantage` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `recommended_strategy` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `risk_factors` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `competitor_win_records`
COMMENT='',
  MODIFY COLUMN `amount` decimal(19,2) NULL COMMENT '',
  MODIFY COLUMN `sku_count` int NULL COMMENT '',
  MODIFY COLUMN `won_at` date NULL COMMENT '',
  MODIFY COLUMN `competitor_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '',
  MODIFY COLUMN `recorded_by` bigint NULL COMMENT '',
  MODIFY COLUMN `discount` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `recorded_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `category` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `competitor_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `payment_terms` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `project_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `notes` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `competitors`
COMMENT='',
  MODIFY COLUMN `market_share` decimal(5,2) NULL COMMENT '',
  MODIFY COLUMN `typical_bid_range_max` decimal(19,2) NULL COMMENT '',
  MODIFY COLUMN `typical_bid_range_min` decimal(19,2) NULL COMMENT '',
  MODIFY COLUMN `industry` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `strengths` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `weaknesses` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `compliance_check_results`
COMMENT='',
  MODIFY COLUMN `risk_score` int NOT NULL COMMENT '',
  MODIFY COLUMN `checked_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '',
  MODIFY COLUMN `tender_id` bigint NULL COMMENT '',
  MODIFY COLUMN `checked_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `overall_status` enum('COMPLIANT','NON_COMPLIANT','PARTIAL_COMPLIANT','WARNING') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '';

ALTER TABLE `compliance_rules`
COMMENT='',
  MODIFY COLUMN `enabled` bit(1) NOT NULL COMMENT '',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `rule_definition` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `rule_type` enum('QUALIFICATION','DOCUMENT','FINANCIAL','EXPERIENCE','DEADLINE') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '';

ALTER TABLE `contract_borrow_applications`
COMMENT='',
  MODIFY COLUMN `contract_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `contract_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `source_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `borrower_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `borrower_dept` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `customer_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `purpose` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `borrow_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `expected_return_date` date NULL COMMENT '',
  MODIFY COLUMN `submitted_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `approver_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `approved_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `rejection_reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `rejected_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `return_remark` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `returned_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `cancel_reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `cancelled_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `last_comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `status` enum('PENDING_APPROVAL','APPROVED','REJECTED','BORROWED','RETURNED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `version` bigint NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '';

ALTER TABLE `contract_borrow_events`
COMMENT='',
  MODIFY COLUMN `application_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `event_type` enum('SUBMITTED','APPROVED','REJECTED','RETURNED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status_after` enum('PENDING_APPROVAL','APPROVED','REJECTED','BORROWED','RETURNED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `actor_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '';

ALTER TABLE `crm_project_mapping`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `customer_predictions`
COMMENT='',
  MODIFY COLUMN `avg_budget` decimal(14,2) NULL COMMENT '',
  MODIFY COLUMN `confidence` decimal(3,2) NULL COMMENT '',
  MODIFY COLUMN `frequency` int NULL COMMENT '',
  MODIFY COLUMN `opportunity_score` int NULL COMMENT '',
  MODIFY COLUMN `predicted_budget_max` decimal(14,2) NULL COMMENT '',
  MODIFY COLUMN `predicted_budget_min` decimal(14,2) NULL COMMENT '',
  MODIFY COLUMN `converted_project_id` bigint NULL COMMENT '',
  MODIFY COLUMN `last_computed_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `predicted_window` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `cycle_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `industry` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `predicted_category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `purchaser_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `period_months` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `region` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `sales_rep` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `evidence_record_ids` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `main_categories` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `purchaser_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `reasoning_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `status` enum('WATCH','RECOMMEND','CONVERTED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '';

ALTER TABLE `deposit_tracking`
COMMENT='',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `payment_date` date NOT NULL COMMENT '',
  MODIFY COLUMN `expected_return_date` date NOT NULL COMMENT '',
  MODIFY COLUMN `amount` decimal(12,2) NOT NULL COMMENT '',
  MODIFY COLUMN `status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `payee` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `dimension_scores`
COMMENT='',
  MODIFY COLUMN `score` int NULL COMMENT '',
  MODIFY COLUMN `weight` decimal(38,2) NULL COMMENT '',
  MODIFY COLUMN `analysis_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `dimension_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `comments` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '';

ALTER TABLE `document_archive_records`
COMMENT='',
  MODIFY COLUMN `archived_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `archived_by` bigint NULL COMMENT '',
  MODIFY COLUMN `export_id` bigint NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `structure_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `archive_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `archived_by_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `document_assemblies`
COMMENT='',
  MODIFY COLUMN `assembled_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `assembled_by` bigint NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `template_id` bigint NULL COMMENT '',
  MODIFY COLUMN `assembled_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `variables` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `document_assignments`
COMMENT='',
  MODIFY COLUMN `due_date` date NULL COMMENT '',
  MODIFY COLUMN `assigned_by` bigint NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `section_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `owner` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '';

ALTER TABLE `document_export_files`
COMMENT='',
  MODIFY COLUMN `export_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `document_exports`
COMMENT='',
  MODIFY COLUMN `exported_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `exported_by` bigint NULL COMMENT '',
  MODIFY COLUMN `file_size` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `structure_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `content_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `exported_by_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `format` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `project_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `document_locks`
COMMENT='',
  MODIFY COLUMN `locked` bit(1) NOT NULL COMMENT '',
  MODIFY COLUMN `locked_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `locked_by` bigint NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `section_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '';

ALTER TABLE `document_reminders`
COMMENT='',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `reminded_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `reminded_by` bigint NULL COMMENT '',
  MODIFY COLUMN `section_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `recipient` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `document_sections`
COMMENT='',
  MODIFY COLUMN `order_index` int NULL COMMENT '',
  MODIFY COLUMN `parent_id` bigint NULL COMMENT '',
  MODIFY COLUMN `structure_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `metadata` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `section_type` enum('CHAPTER','SECTION','SUBSECTION','TABLE','IMAGE','ATTACHMENT') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '';

ALTER TABLE `document_structures`
COMMENT='',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `root_section_id` bigint NULL COMMENT '',
  MODIFY COLUMN `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '';

ALTER TABLE `document_versions`
COMMENT='',
  MODIFY COLUMN `is_current` bit(1) NULL COMMENT '',
  MODIFY COLUMN `version_number` int NOT NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `change_summary` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `document_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `file_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `email_verification_tokens`
COMMENT='',
  MODIFY COLUMN `expires_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `verified_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `token` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `expense_approval_records`
COMMENT='',
  MODIFY COLUMN `acted_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `expense_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `approver` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `result` enum('APPROVED','REJECTED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `expense_payment_records`
COMMENT='',
  MODIFY COLUMN `amount` decimal(19,2) NOT NULL COMMENT '',
  MODIFY COLUMN `expense_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `paid_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `payment_method` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `paid_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `payment_reference` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '';

ALTER TABLE `expenses`
COMMENT='',
  MODIFY COLUMN `amount` decimal(19,2) NOT NULL COMMENT '',
  MODIFY COLUMN `date` date NOT NULL COMMENT '',
  MODIFY COLUMN `expected_return_date` date NULL COMMENT '',
  MODIFY COLUMN `approved_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `last_return_reminder_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `return_confirmed_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `return_requested_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `approved_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `expense_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `approval_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `return_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `category` enum('MATERIAL','LABOR','EQUIPMENT','TRANSPORTATION','SUBCONTRACTING','OVERHEAD','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` enum('PENDING_APPROVAL','APPROVED','REJECTED','PAID','RETURN_REQUESTED','RETURNED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `created_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '';

ALTER TABLE `export_tasks`
COMMENT='',
  MODIFY COLUMN `progress` int NULL COMMENT '',
  MODIFY COLUMN `completed_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `expires_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `file_size` bigint NULL COMMENT '',
  MODIFY COLUMN `data_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `export_params` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `export_type` enum('EXCEL','PDF') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` enum('PENDING','PROCESSING','COMPLETED','FAILED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `created_by` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `fees`
COMMENT='',
  MODIFY COLUMN `amount` decimal(19,2) NOT NULL COMMENT '',
  MODIFY COLUMN `fee_date` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `payment_date` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `return_date` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `paid_by` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `return_to` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `remarks` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `fee_type` enum('BID_BOND','SERVICE_FEE','DOCUMENT_FEE','TRAVEL_FEE','NOTARY_FEE','OTHER_FEE') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` enum('PENDING','PAID','RETURNED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '';

ALTER TABLE `historical_project_snapshots`
COMMENT='',
  MODIFY COLUMN `archive_record_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `captured_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `export_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `project_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `archive_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `customer_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `document_snapshot_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `product_line` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `recommended_tags` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `knowledge_case`
COMMENT='',
  MODIFY COLUMN `source_project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `source_project_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `scoring_point_title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `requirement_raw` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `response_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `reuse_count` int NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `customer_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `project_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `local_users`
COMMENT='',
  MODIFY COLUMN `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `user_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `email` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `mobile` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `dept_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `position` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '',
  MODIFY COLUMN `source_updated_at` datetime NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `manufacturer_authorization`
  MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `mention`
COMMENT='',
  MODIFY COLUMN `notification_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `mentioner_user_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `mentioned_user_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `source_entity_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `source_entity_id` bigint NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `notification`
COMMENT='',
  MODIFY COLUMN `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `source_entity_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `source_entity_id` bigint NULL COMMENT '',
  MODIFY COLUMN `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `body` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `payload_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_by` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `notification_delivery_dlq`
COMMENT='',
  MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `notification_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `recipient_user_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `business_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `reason_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `notification_delivery_task`
COMMENT='',
  MODIFY COLUMN `notification_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `recipient_user_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `event_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `business_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `attempt_count` int NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `next_retry_at` timestamp NULL COMMENT '',
  MODIFY COLUMN `last_error_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `last_error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `notification_outbound_log`
COMMENT='',
  MODIFY COLUMN `notification_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `channel` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `skip_reason` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `wecom_errcode` int NULL COMMENT '',
  MODIFY COLUMN `wecom_errmsg` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `attempt_count` int NOT NULL DEFAULT 1 COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `oa_process_bindings`
COMMENT='',
  MODIFY COLUMN `template_code` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `provider` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `workflow_code` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `field_mapping_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '';

ALTER TABLE `oa_process_events`
COMMENT='',
  MODIFY COLUMN `form_instance_id` bigint NULL COMMENT '',
  MODIFY COLUMN `oa_instance_id` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `event_id` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `event_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `raw_payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `opportunity_prediction_reminder_log`
COMMENT='',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `predicted_bid_date` date NOT NULL COMMENT '',
  MODIFY COLUMN `purchaser_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '',
  MODIFY COLUMN `notified_at` datetime NOT NULL COMMENT '',
  MODIFY COLUMN `event_published_at` datetime NULL COMMENT '',
  MODIFY COLUMN `event_publish_claim_token` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '',
  MODIFY COLUMN `event_publish_claimed_at` datetime NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `organization_departments`
COMMENT='',
  MODIFY COLUMN `department_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `department_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `parent_department_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '',
  MODIFY COLUMN `external_dept_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `parent_external_dept_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `source_app` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `last_event_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `last_synced_at` timestamp NULL COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `organization_event_logs`
COMMENT='',
  MODIFY COLUMN `event_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `event_topic` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `source_app` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `trace_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `payload_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` enum('PROCESSING','PROCESSED','DUPLICATE','REJECTED','FAILED','PENDING_RETRY','DEAD_LETTER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `received_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `processed_at` timestamp NULL COMMENT '',
  MODIFY COLUMN `upstream_event_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `span_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `parent_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `event_time` timestamp NULL COMMENT '',
  MODIFY COLUMN `entity_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `external_user_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `external_dept_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `raw_payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `retry_count` int NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `next_retry_at` timestamp NULL COMMENT '',
  MODIFY COLUMN `last_error_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `organization_sync_items`
COMMENT='',
  MODIFY COLUMN `run_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `entity_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `external_user_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `external_dept_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `internal_user_id` bigint NULL COMMENT '',
  MODIFY COLUMN `department_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `action_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `event_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `error_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `raw_payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `processed_at` timestamp NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `organization_sync_runs`
COMMENT='',
  MODIFY COLUMN `run_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `run_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `source_app` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `triggered_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `started_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `finished_at` timestamp NULL COMMENT '',
  MODIFY COLUMN `total_count` int NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `success_count` int NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `failed_count` int NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `last_error_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `last_error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `password_reset_tokens`
COMMENT='',
  MODIFY COLUMN `expires_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `used_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `token` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `performance_alert_config`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `performance_attachment`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `performance_export_task`
  MODIFY COLUMN `status` enum('PENDING','PROCESSING','COMPLETED','FAILED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `completed_at` datetime NULL COMMENT '',
  MODIFY COLUMN `failure_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL COMMENT '';

ALTER TABLE `performance_record`
  MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `personnel`
  MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `birth_date` date NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '';

ALTER TABLE `personnel_alert_config`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `personnel_certificate`
  MODIFY COLUMN `title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `is_permanent` tinyint(1) NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '';

ALTER TABLE `personnel_education`
  MODIFY COLUMN `is_highest_education_school` tinyint(1) NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `personnel_import_task`
  MODIFY COLUMN `success_count` int NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `failure_count` int NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `completed_at` datetime NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `personnel_operation_log`
COMMENT='',
  MODIFY COLUMN `personnel_id` bigint NULL COMMENT '',
  MODIFY COLUMN `operator_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `operator_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `operation_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `change_details` json NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `platform_account_import_task`
COMMENT='',
  MODIFY COLUMN `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING' COMMENT '',
  MODIFY COLUMN `total_rows` int NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `valid_rows` int NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `invalid_rows` int NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `imported_rows` int NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `updated_rows` int NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `error_details` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `source_filename` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `created_by_username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `completed_at` datetime NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `platform_accounts`
COMMENT='',
  MODIFY COLUMN `return_count` int NULL COMMENT '',
  MODIFY COLUMN `borrowed_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `borrowed_by` bigint NULL COMMENT '',
  MODIFY COLUMN `due_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `account_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `status` enum('AVAILABLE','PENDING_APPROVAL','IN_USE','MAINTENANCE','DISABLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '';

ALTER TABLE `project_archive`
COMMENT='',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `project_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `archive_status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `project_closure`
COMMENT='',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `closed_at` timestamp NULL COMMENT '',
  MODIFY COLUMN `closed_by` bigint NULL COMMENT '',
  MODIFY COLUMN `deposit_returned` tinyint(1) NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `deposit_return_evidence_id` bigint NULL COMMENT '',
  MODIFY COLUMN `archive_location` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `stage_locked` tinyint(1) NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `notes` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `review_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DRAFT' COMMENT '',
  MODIFY COLUMN `reviewed_by` bigint NULL COMMENT '',
  MODIFY COLUMN `reviewed_at` datetime NULL COMMENT '',
  MODIFY COLUMN `project_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `deposit_return_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'NA' COMMENT '',
  MODIFY COLUMN `deposit_return_date` datetime NULL COMMENT '',
  MODIFY COLUMN `transfer_amount` decimal(18,2) NULL COMMENT '',
  MODIFY COLUMN `returned_amount` decimal(18,2) NULL COMMENT '',
  MODIFY COLUMN `rejection_reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `updated_by` bigint NULL COMMENT '';

ALTER TABLE `project_documents`
COMMENT='',
  MODIFY COLUMN `linked_entity_id` bigint NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `uploader_id` bigint NULL COMMENT '',
  MODIFY COLUMN `file_url` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `document_category` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `file_size` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `file_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `linked_entity_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `uploader_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `project_evaluation`
COMMENT='',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `sub_stage` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '',
  MODIFY COLUMN `evaluation_started_at` timestamp NULL COMMENT '',
  MODIFY COLUMN `board_received_at` timestamp NULL COMMENT '',
  MODIFY COLUMN `announced_at` timestamp NULL COMMENT '',
  MODIFY COLUMN `notes` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `evaluation_files_json` json NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `updated_by` bigint NULL COMMENT '';

ALTER TABLE `project_group_members`
COMMENT='',
  MODIFY COLUMN `project_group_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `user_id` bigint NULL COMMENT '';

ALTER TABLE `project_group_projects`
COMMENT='',
  MODIFY COLUMN `project_group_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '';

ALTER TABLE `project_group_role_access`
COMMENT='',
  MODIFY COLUMN `project_group_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `role_code` enum('ADMIN','MANAGER','STAFF') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '';

ALTER TABLE `project_groups`
COMMENT='',
  MODIFY COLUMN `manager_user_id` bigint NULL COMMENT '',
  MODIFY COLUMN `group_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `group_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `visibility` enum('ALL','MEMBERS','MANAGER','CUSTOM') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '';

ALTER TABLE `project_initiation_details`
COMMENT='',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `bid_open_time` timestamp NULL COMMENT '',
  MODIFY COLUMN `bid_month` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `expected_bidders` int NULL COMMENT '',
  MODIFY COLUMN `customer_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `contract_period_months` int NULL COMMENT '',
  MODIFY COLUMN `competitors` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `department_snapshot` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `deposit_amount` decimal(20,2) NULL COMMENT '',
  MODIFY COLUMN `deposit_payment_method` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `locked` tinyint(1) NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `owner_unit` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `project_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `owner_user_id` bigint NULL COMMENT '',
  MODIFY COLUMN `review_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DRAFT' COMMENT '',
  MODIFY COLUMN `rejection_reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `reviewed_by` bigint NULL COMMENT '',
  MODIFY COLUMN `reviewed_at` datetime NULL COMMENT '',
  MODIFY COLUMN `customer_info_json` json NULL COMMENT '',
  MODIFY COLUMN `tender_document_id` bigint NULL COMMENT '',
  MODIFY COLUMN `ai_risk_level` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `updated_by` bigint NULL COMMENT '';

ALTER TABLE `project_lead_assignment`
COMMENT='',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `primary_lead_user_id` bigint NULL COMMENT '',
  MODIFY COLUMN `secondary_lead_user_id` bigint NULL COMMENT '',
  MODIFY COLUMN `assigned_at` timestamp NULL COMMENT '',
  MODIFY COLUMN `assigned_by` bigint NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `project_quality_checks`
COMMENT='',
  MODIFY COLUMN `empty` bit(1) NOT NULL COMMENT '',
  MODIFY COLUMN `checked_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `document_id` bigint NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `document_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `status` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `project_quality_issues`
COMMENT='',
  MODIFY COLUMN `adopted` bit(1) NOT NULL COMMENT '',
  MODIFY COLUMN `ignored` bit(1) NOT NULL COMMENT '',
  MODIFY COLUMN `check_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `location_label` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `original_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `suggestion_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `project_reminders`
COMMENT='',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `remind_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `created_by_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `recipient` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `project_result`
COMMENT='',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `result_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `award_amount` decimal(20,2) NULL COMMENT '',
  MODIFY COLUMN `contract_start_date` date NULL COMMENT '',
  MODIFY COLUMN `contract_end_date` date NULL COMMENT '',
  MODIFY COLUMN `evidence_attachment_id` bigint NULL COMMENT '',
  MODIFY COLUMN `registered_at` timestamp NULL COMMENT '',
  MODIFY COLUMN `summary` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `evidence_doc_ids` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `evidence_file_ids` json NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `updated_by` bigint NULL COMMENT '';

ALTER TABLE `project_result_competitor`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `project_retrospective`
COMMENT='',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `win_factors` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `loss_reasons` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `competitor_notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `improvement_actions` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `reviewed_by` bigint NULL COMMENT '',
  MODIFY COLUMN `reviewed_at` timestamp NULL COMMENT '',
  MODIFY COLUMN `review_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT '',
  MODIFY COLUMN `result_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `review_comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `process_issues` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `report_attachment_id` bigint NULL COMMENT '',
  MODIFY COLUMN `meeting_time` timestamp NULL COMMENT '',
  MODIFY COLUMN `meeting_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `participants` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `attachment_id` bigint NULL COMMENT '',
  MODIFY COLUMN `meeting_format` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `meeting_participants` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `loss_reason_flags` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `post_win_improvements` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `process_problems` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `post_loss_measures` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `report_file_ids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `updated_by` bigint NULL COMMENT '';

ALTER TABLE `project_score_drafts`
COMMENT='',
  MODIFY COLUMN `source_page` int NULL COMMENT '',
  MODIFY COLUMN `source_row_index` int NOT NULL COMMENT '',
  MODIFY COLUMN `source_table_index` int NOT NULL COMMENT '',
  MODIFY COLUMN `assignee_id` bigint NULL COMMENT '',
  MODIFY COLUMN `due_date` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `generated_task_id` bigint NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `task_action` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `assignee_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `score_value_text` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `generated_task_description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `generated_task_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `score_item_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `score_rule_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `skip_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `source_file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `suggested_deliverables` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` enum('DRAFT','READY','SKIPPED','GENERATED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '';

ALTER TABLE `project_score_previews`
COMMENT='',
  MODIFY COLUMN `budget` decimal(19,2) NULL COMMENT '',
  MODIFY COLUMN `win_score` int NOT NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '',
  MODIFY COLUMN `tender_id` bigint NULL COMMENT '',
  MODIFY COLUMN `win_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `industry` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `payload_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `project_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `tags_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '';

ALTER TABLE `project_share_links`
COMMENT='',
  MODIFY COLUMN `expires_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `created_by_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `token` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `project_team_members`
COMMENT='',
  MODIFY COLUMN `member_id` bigint NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '';

ALTER TABLE `qualification_alert_config`
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `qualification_attachments`
COMMENT='',
  MODIFY COLUMN `qualification_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `uploaded_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `file_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `qualification_loan_records`
COMMENT='',
  MODIFY COLUMN `expected_return_date` date NULL COMMENT '',
  MODIFY COLUMN `borrowed_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `qualification_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `returned_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `project_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `borrower` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `department` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `return_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `purpose` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `status` enum('AVAILABLE','BORROWED','RETURNED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '';

ALTER TABLE `qualifications`
COMMENT='',
  MODIFY COLUMN `expiry_date` date NULL COMMENT '',
  MODIFY COLUMN `issue_date` date NULL COMMENT '',
  MODIFY COLUMN `file_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `type` enum('CONSTRUCTION','DESIGN','SERVICE','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '';

ALTER TABLE `refresh_sessions`
COMMENT='',
  MODIFY COLUMN `expires_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `last_seen_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `revoked_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `ip_address` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `token_hash` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `device_info` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '';

ALTER TABLE `roi_analyses`
COMMENT='',
  MODIFY COLUMN `estimated_cost` decimal(19,2) NULL COMMENT '',
  MODIFY COLUMN `estimated_profit` decimal(19,2) NULL COMMENT '',
  MODIFY COLUMN `estimated_revenue` decimal(19,2) NULL COMMENT '',
  MODIFY COLUMN `payback_period_months` int NULL COMMENT '',
  MODIFY COLUMN `roi_percentage` decimal(10,2) NULL COMMENT '',
  MODIFY COLUMN `analysis_date` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `assumptions` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `risk_factors` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `roles`
COMMENT='',
  MODIFY COLUMN `enabled` bit(1) NOT NULL COMMENT '',
  MODIFY COLUMN `is_system` bit(1) NOT NULL COMMENT '',
  MODIFY COLUMN `data_scope` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `allowed_depts` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `allowed_projects` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `menu_permissions` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '';

ALTER TABLE `score_analyses`
COMMENT='',
  MODIFY COLUMN `is_ai_generated` bit(1) NULL COMMENT '',
  MODIFY COLUMN `overall_score` int NULL COMMENT '',
  MODIFY COLUMN `analysis_date` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `analyst_id` bigint NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '',
  MODIFY COLUMN `tender_id` bigint NULL COMMENT '',
  MODIFY COLUMN `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `risk_level` enum('LOW','MEDIUM','HIGH') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '';

ALTER TABLE `score_item`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL COMMENT '',
  MODIFY COLUMN `updated_at` datetime NOT NULL COMMENT '';

ALTER TABLE `score_parse_evidence`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `score_parse_fact`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `score_parse_review`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `score_parse_task`
  MODIFY COLUMN `started_at` datetime NULL COMMENT '',
  MODIFY COLUMN `completed_at` datetime NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL COMMENT '',
  MODIFY COLUMN `updated_at` datetime NOT NULL COMMENT '';

ALTER TABLE `score_result`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL COMMENT '',
  MODIFY COLUMN `updated_at` datetime NOT NULL COMMENT '';

ALTER TABLE `subscription`
COMMENT='',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `target_entity_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `target_entity_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `sys_crm_customer_permission`
COMMENT='',
  MODIFY COLUMN `customer_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `permission_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `sys_dashboard_layout`
COMMENT='',
  MODIFY COLUMN `code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `role_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `layout_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `sys_dashboard_widget`
COMMENT='',
  MODIFY COLUMN `code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `default_props_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `sys_project_member`
COMMENT='',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `member_role` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `permission_level` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `is_inherited` tinyint(1) NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `system_settings`
COMMENT='',
  MODIFY COLUMN `config_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `payload_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '';

ALTER TABLE `task_comment`
COMMENT='',
  MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `author_user_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `task_deliverables`
COMMENT='',
  MODIFY COLUMN `version` int NOT NULL COMMENT '',
  MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `uploader_id` bigint NULL COMMENT '',
  MODIFY COLUMN `file_size` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `file_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `uploader_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `storage_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `storage_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `deliverable_type` enum('DOCUMENT','QUALIFICATION','TECHNICAL','QUOTATION','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `task_extended_field`
COMMENT='',
  MODIFY COLUMN `key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `label` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `field_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `required` tinyint(1) NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `placeholder` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `options_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `sort_order` int NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `task_history`
COMMENT='',
  MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `actor_user_id` bigint NULL COMMENT '',
  MODIFY COLUMN `action` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `snapshot_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `archived_at` timestamp NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `task_status_dict`
COMMENT='',
  MODIFY COLUMN `code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `category` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `color` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '#909399' COMMENT '',
  MODIFY COLUMN `sort_order` int NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `is_initial` tinyint(1) NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `is_terminal` tinyint(1) NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_by` bigint NULL COMMENT '';

ALTER TABLE `template_download_records`
COMMENT='',
  MODIFY COLUMN `downloaded_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `downloaded_by` bigint NULL COMMENT '',
  MODIFY COLUMN `template_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `template_tags`
COMMENT='',
  MODIFY COLUMN `template_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `tag` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '';

ALTER TABLE `template_use_records`
COMMENT='',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '',
  MODIFY COLUMN `template_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `used_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `used_by` bigint NULL COMMENT '',
  MODIFY COLUMN `applied_options` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `doc_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `document_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `template_versions`
COMMENT='',
  MODIFY COLUMN `template_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `snapshot_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `version` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `templates`
COMMENT='',
  MODIFY COLUMN `description` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `current_version` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `document_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `file_size` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `file_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `industry` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `product_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `category` enum('TECHNICAL','COMMERCIAL','LEGAL','QUALIFICATION','CONTRACT','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '';

ALTER TABLE `tender_assignment_records`
COMMENT='',
  MODIFY COLUMN `assigned_at` datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN `assigned_by_id` bigint NULL COMMENT '',
  MODIFY COLUMN `assignee_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `tender_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `assigned_by_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `assignee_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `remark` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '';

ALTER TABLE `tender_attachments`
COMMENT='',
  MODIFY COLUMN `tender_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `file_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `file_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `file_url` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `tender_evaluation_basics`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `tender_evaluation_customer_info`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `tender_evaluations`
COMMENT='',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL COMMENT '',
  MODIFY COLUMN `updated_at` datetime NOT NULL COMMENT '';

ALTER TABLE `tender_event_logs`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `tender_favorite`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `tender_file`
COMMENT='',
  MODIFY COLUMN `upload_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `file_path` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `file_sha256` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `file_size` bigint NULL COMMENT '',
  MODIFY COLUMN `page_count` int NULL COMMENT '',
  MODIFY COLUMN `upload_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'INITIATED' COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '';

ALTER TABLE `tender_import_task`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `tender_reminder_logs`
COMMENT='',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `tender_reminder_settings`
COMMENT='',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `tender_task`
COMMENT='',
  MODIFY COLUMN `file_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `priority` int NOT NULL DEFAULT 5 COMMENT '',
  MODIFY COLUMN `attempts` int NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `available_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '',
  MODIFY COLUMN `locked_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `locked_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `error_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `started_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `finished_at` datetime(6) NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '';

ALTER TABLE `tender_task_dlq`
COMMENT='',
  MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `file_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `failed_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '',
  MODIFY COLUMN `error_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '';

ALTER TABLE `tenders`
  MODIFY COLUMN `abandonment_reason` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '';

ALTER TABLE `user_notification`
COMMENT='',
  MODIFY COLUMN `notification_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `read_at` timestamp NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `warehouse`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `warehouse_attachment`
  MODIFY COLUMN `warehouse_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `original_filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `stored_filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `file_size` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `content_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `uploaded_by` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `uploaded_at` datetime NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `warehouse_export_task`
  MODIFY COLUMN `status` enum('PENDING','PROCESSING','COMPLETED','FAILED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `filter_snapshot` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `total_count` int NULL COMMENT '',
  MODIFY COLUMN `stored_file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `download_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `expires_at` datetime NULL COMMENT '',
  MODIFY COLUMN `completed_at` datetime NULL COMMENT '',
  MODIFY COLUMN `failure_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_by` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL COMMENT '';

ALTER TABLE `warehouse_import_task`
  MODIFY COLUMN `status` enum('PENDING','VALIDATING','VALIDATED','IMPORTING','COMPLETED','FAILED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `total_rows` int NULL COMMENT '',
  MODIFY COLUMN `valid_rows` int NULL COMMENT '',
  MODIFY COLUMN `invalid_rows` int NULL COMMENT '',
  MODIFY COLUMN `imported_rows` int NULL COMMENT '',
  MODIFY COLUMN `error_details` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `source_file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `source_filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `created_by_username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `completed_at` datetime NULL COMMENT '',
  MODIFY COLUMN `failure_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_by` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL COMMENT '';

ALTER TABLE `warehouse_operation_log`
  MODIFY COLUMN `warehouse_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `operator_id` bigint NULL COMMENT '',
  MODIFY COLUMN `operator_username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `action_type` enum('CREATE','EDIT','CLOSE','RESTORE','ATTACH_UPLOAD','ATTACH_DELETE') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `field_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `old_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `new_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL COMMENT '';

ALTER TABLE `webhook_delivery_dlq`
COMMENT='',
  MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `tender_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `business_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `reason_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `webhook_delivery_logs`
COMMENT='',
  MODIFY COLUMN `tender_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `target_url` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status_code` int NULL COMMENT '',
  MODIFY COLUMN `response_body` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `retry_count` int NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING' COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `webhook_delivery_tasks`
COMMENT='',
  MODIFY COLUMN `tender_id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `external_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `target_url` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `event_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `business_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING' COMMENT '',
  MODIFY COLUMN `attempt_count` int NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `next_retry_at` datetime NULL COMMENT '',
  MODIFY COLUMN `last_error_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `last_error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `wecom_integration`
COMMENT='',
  MODIFY COLUMN `corp_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `agent_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `encrypted_secret` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `sso_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `message_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `notify_user_ids` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '';

ALTER TABLE `workflow_form_instances`
COMMENT='',
  MODIFY COLUMN `business_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `template_code` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '',
  MODIFY COLUMN `applicant_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `form_data_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `oa_instance_id` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `business_applied` tinyint(1) NOT NULL DEFAULT 0 COMMENT '',
  MODIFY COLUMN `business_apply_error` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `oa_operator_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `oa_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `template_version` int NULL COMMENT '',
  MODIFY COLUMN `schema_snapshot_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `oa_binding_snapshot_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `oa_payload_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `workflow_form_template_drafts`
COMMENT='',
  MODIFY COLUMN `template_code` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `business_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `draft_schema_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '',
  MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DRAFT' COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '';

ALTER TABLE `workflow_form_template_versions`
COMMENT='',
  MODIFY COLUMN `template_code` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `business_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `version` int NOT NULL COMMENT '',
  MODIFY COLUMN `schema_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '',
  MODIFY COLUMN `published_by` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '',
  MODIFY COLUMN `published_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '';

ALTER TABLE `workflow_form_templates`
COMMENT='',
  MODIFY COLUMN `template_code` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `business_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `version` int NOT NULL COMMENT '',
  MODIFY COLUMN `schema_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '',
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '';
