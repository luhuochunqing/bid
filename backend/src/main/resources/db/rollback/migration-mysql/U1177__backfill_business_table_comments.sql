-- Input: migration-mysql/V1177__backfill_business_table_comments.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway rollback coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- U1177: 回滚 V1177 —— 清空本次新增的 COMMENT
-- 范围:与 V1177 完全对应(5 张表 + 114 个列)
-- 策略:同表所有 MODIFY COLUMN 合并为一条 ALTER TABLE,每张表只重建 1 次(减少锁表风险)
--       MODIFY COLUMN 严格保留 V1177 时的 TYPE/NOT NULL/DEFAULT,COMMENT 设为 ''
-- 注意:回滚不影响 V74~V1176 中已存在的 COMMENT(那些不在 V1177 范围内)
--       本脚本只清空 V1177 新增的 COMMENT(即 V1177 文件中 MODIFY 过的列)
-- No-op rollback: COMMENT 清空不影响业务数据,仅丢失字段注释元数据(可重新执行 V1177 恢复)
-- 版本号说明:原 U1169 因 V1169 撞号重命名为 V1177,本回滚脚本同步重命名为 U1177

-- ============================================================
-- 1. accounts
-- ============================================================
ALTER TABLE accounts COMMENT = '';

ALTER TABLE accounts
  MODIFY COLUMN id bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN created_at datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN updated_at datetime(6) NULL COMMENT '',
  MODIFY COLUMN name varchar(200) NOT NULL COMMENT '',
  MODIFY COLUMN industry varchar(100) NULL COMMENT '',
  MODIFY COLUMN region varchar(100) NULL COMMENT '',
  MODIFY COLUMN contact_info varchar(500) NULL COMMENT '',
  MODIFY COLUMN credit_level enum('A','B','C','D') NOT NULL COMMENT '',
  MODIFY COLUMN type enum('CLIENT','SUPPLIER','PARTNER','GOVERNMENT','OTHER') NOT NULL COMMENT '';

-- ============================================================
-- 2. users
-- ============================================================
ALTER TABLE users COMMENT = '';

ALTER TABLE users
  MODIFY COLUMN id bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN created_at datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN updated_at datetime(6) NULL COMMENT '',
  MODIFY COLUMN username varchar(255) NOT NULL COMMENT '',
  MODIFY COLUMN password varchar(255) NOT NULL COMMENT '',
  MODIFY COLUMN email varchar(255) NOT NULL COMMENT '',
  MODIFY COLUMN phone varchar(32) NULL COMMENT '',
  MODIFY COLUMN full_name varchar(255) NOT NULL COMMENT '',
  MODIFY COLUMN role enum('ADMIN','MANAGER') NOT NULL COMMENT '',
  MODIFY COLUMN role_id bigint NULL COMMENT '',
  MODIFY COLUMN enabled bit(1) NOT NULL COMMENT '',
  MODIFY COLUMN email_verified bit(1) NOT NULL COMMENT '',
  MODIFY COLUMN department_code varchar(100) NULL COMMENT '',
  MODIFY COLUMN department_name varchar(100) NULL COMMENT '',
  MODIFY COLUMN wecom_user_id varchar(64) NULL COMMENT '',
  MODIFY COLUMN external_org_user_id varchar(128) NULL COMMENT '',
  MODIFY COLUMN external_org_source_app varchar(100) NULL COMMENT '',
  MODIFY COLUMN last_org_event_key varchar(128) NULL COMMENT '',
  MODIFY COLUMN last_org_synced_at timestamp NULL COMMENT '',
  MODIFY COLUMN employee_number varchar(32) NULL COMMENT '';

-- ============================================================
-- 3. tenders (只回滚 V1177 新增的,不动已有 COMMENT)
-- ============================================================
ALTER TABLE tenders COMMENT = '';

ALTER TABLE tenders
  MODIFY COLUMN id bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN created_at datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN updated_at datetime(6) NULL COMMENT '',
  MODIFY COLUMN title varchar(500) NOT NULL COMMENT '',
  MODIFY COLUMN source varchar(200) NULL COMMENT '',
  MODIFY COLUMN external_id varchar(100) NULL COMMENT '',
  MODIFY COLUMN original_url varchar(1000) NULL COMMENT '',
  MODIFY COLUMN status enum('PENDING_ASSIGNMENT','TRACKING','EVALUATED','BIDDING','WON','LOST','ABANDONED') NOT NULL COMMENT '',
  MODIFY COLUMN risk_level enum('LOW','MEDIUM','HIGH') NULL COMMENT '',
  MODIFY COLUMN ai_score int NULL COMMENT '',
  MODIFY COLUMN budget decimal(19,2) NULL COMMENT '',
  MODIFY COLUMN deadline datetime(6) NULL COMMENT '',
  MODIFY COLUMN region varchar(100) NULL COMMENT '',
  MODIFY COLUMN industry varchar(100) NULL COMMENT '',
  MODIFY COLUMN purchaser_name varchar(255) NULL COMMENT '',
  MODIFY COLUMN purchaser_hash varchar(64) NULL COMMENT '',
  MODIFY COLUMN publish_date date NULL COMMENT '',
  MODIFY COLUMN contact_name varchar(100) NULL COMMENT '',
  MODIFY COLUMN contact_phone varchar(50) NULL COMMENT '',
  MODIFY COLUMN description text NULL COMMENT '',
  MODIFY COLUMN tags text NULL COMMENT '',
  MODIFY COLUMN source_normalized varchar(200) NULL COMMENT '',
  MODIFY COLUMN region_normalized varchar(100) NULL COMMENT '',
  MODIFY COLUMN industry_normalized varchar(100) NULL COMMENT '',
  MODIFY COLUMN purchaser_hash_normalized varchar(64) NULL COMMENT '',
  MODIFY COLUMN purchaser_name_normalized varchar(255) NULL COMMENT '',
  MODIFY COLUMN search_text_normalized text NULL COMMENT '',
  MODIFY COLUMN source_document_name varchar(255) NULL COMMENT '',
  MODIFY COLUMN source_document_file_type varchar(100) NULL COMMENT '',
  MODIFY COLUMN source_document_file_url varchar(1000) NULL COMMENT '',
  MODIFY COLUMN tender_agency varchar(255) NULL COMMENT '',
  MODIFY COLUMN bid_opening_time datetime(6) NULL COMMENT '',
  MODIFY COLUMN customer_type varchar(100) NULL COMMENT '',
  MODIFY COLUMN priority varchar(10) NULL COMMENT '',
  MODIFY COLUMN source_type enum('EXTERNAL_PLATFORM','CRM_OPPORTUNITY','MANUAL_SINGLE','BULK_IMPORT') NOT NULL DEFAULT 'MANUAL_SINGLE' COMMENT '',
  MODIFY COLUMN crm_opportunity_name varchar(200) NULL COMMENT '',
  MODIFY COLUMN project_id bigint NULL COMMENT '';

-- ============================================================
-- 4. projects (只回滚 V1177 新增的,initiated_at/evaluating_at/closed_at 不动)
-- ============================================================
ALTER TABLE projects COMMENT = '';

ALTER TABLE projects
  MODIFY COLUMN id bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN created_at datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN updated_at datetime(6) NULL COMMENT '',
  MODIFY COLUMN name varchar(500) NOT NULL COMMENT '',
  MODIFY COLUMN status enum('PENDING_INITIATION','INITIATED','BIDDING','EVALUATING','WON','LOST','FAILED','ABANDONED') NOT NULL COMMENT '',
  MODIFY COLUMN stage varchar(32) NOT NULL DEFAULT 'INITIATED' COMMENT '',
  MODIFY COLUMN tender_id bigint NOT NULL COMMENT '',
  MODIFY COLUMN manager_id bigint NOT NULL COMMENT '',
  MODIFY COLUMN customer varchar(255) NULL COMMENT '',
  MODIFY COLUMN customer_type varchar(100) NULL COMMENT '',
  MODIFY COLUMN customer_manager varchar(100) NULL COMMENT '',
  MODIFY COLUMN customer_manager_id varchar(100) NULL COMMENT '',
  MODIFY COLUMN region varchar(100) NULL COMMENT '',
  MODIFY COLUMN industry varchar(50) NULL COMMENT '',
  MODIFY COLUMN budget decimal(14,2) NULL COMMENT '',
  MODIFY COLUMN deadline date NULL COMMENT '',
  MODIFY COLUMN start_date datetime(6) NULL COMMENT '',
  MODIFY COLUMN end_date datetime(6) NULL COMMENT '',
  MODIFY COLUMN platform varchar(255) NULL COMMENT '',
  MODIFY COLUMN description text NULL COMMENT '',
  MODIFY COLUMN remark text NULL COMMENT '',
  MODIFY COLUMN tags_json varchar(1000) NULL COMMENT '',
  MODIFY COLUMN ai_analysis_json text NULL COMMENT '',
  MODIFY COLUMN competitor_analysis_json text NULL COMMENT '',
  MODIFY COLUMN tasks_json text NULL COMMENT '',
  MODIFY COLUMN source_module varchar(100) NULL COMMENT '',
  MODIFY COLUMN source_customer_id varchar(100) NULL COMMENT '',
  MODIFY COLUMN source_customer varchar(255) NULL COMMENT '',
  MODIFY COLUMN source_opportunity_id varchar(100) NULL COMMENT '',
  MODIFY COLUMN source_reasoning_summary text NULL COMMENT '';

-- ============================================================
-- 5. tasks (只回滚 V1177 新增的,content/extended_fields_json/last_reminded_at/last_overdue_reminded_at 不动)
-- ============================================================
ALTER TABLE tasks COMMENT = '';

ALTER TABLE tasks
  MODIFY COLUMN id bigint NOT NULL AUTO_INCREMENT COMMENT '',
  MODIFY COLUMN created_at datetime(6) NOT NULL COMMENT '',
  MODIFY COLUMN updated_at datetime(6) NULL COMMENT '',
  MODIFY COLUMN title varchar(255) NOT NULL COMMENT '',
  MODIFY COLUMN description text NULL COMMENT '',
  MODIFY COLUMN project_id bigint NOT NULL COMMENT '',
  MODIFY COLUMN assignee_id bigint NULL COMMENT '',
  MODIFY COLUMN assignee_role_code varchar(64) NULL COMMENT '',
  MODIFY COLUMN assignee_role_name varchar(100) NULL COMMENT '',
  MODIFY COLUMN assignee_dept_code varchar(100) NULL COMMENT '',
  MODIFY COLUMN assignee_dept_name varchar(100) NULL COMMENT '',
  MODIFY COLUMN status varchar(32) NOT NULL COMMENT '',
  MODIFY COLUMN priority enum('LOW','MEDIUM','HIGH','URGENT') NOT NULL COMMENT '',
  MODIFY COLUMN due_date datetime(6) NULL COMMENT '',
  MODIFY COLUMN review_comment text NULL COMMENT '',
  MODIFY COLUMN completion_notes text NULL COMMENT '',
  MODIFY COLUMN created_by varchar(255) NULL COMMENT '';
