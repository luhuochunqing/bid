-- V1218: backfill_table_column_comments 补齐数据字典表注释与字段注释
-- 说明: 为 151 张缺表注释的表与 1636 个缺字段注释的字段补齐中文 COMMENT；
--       仅元数据变更，不改类型、默认值、可空性等任何结构属性（定义逐列取自迁移链构建库）。
--       数据字典 docs/generated/db-schema.md 由 npm run db:generate-schema 重新生成。

ALTER TABLE `V1091_staff_users_backup`
COMMENT='员工用户历史备份表',
  MODIFY COLUMN `original_role_id` bigint NULL COMMENT '原角色ID',
  MODIFY COLUMN `original_role` enum('ADMIN','MANAGER','STAFF') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '原角色（ADMIN/MANAGER/STAFF）',
  MODIFY COLUMN `original_enabled` bit(1) NOT NULL COMMENT '原启用状态',
  MODIFY COLUMN `migrated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '迁移时间',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户ID';

ALTER TABLE `account_borrow_applications`
  MODIFY COLUMN `project_id` bigint NULL COMMENT '关联项目ID',
  MODIFY COLUMN `status` enum('PENDING_APPROVAL','BORROWED','REJECTED','RETURNED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '申请状态（待审批/已借出/已驳回/已归还/已取消）',
  MODIFY COLUMN `approval_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '审批意见';

ALTER TABLE `account_borrow_requests`
COMMENT='账号借用请求表',
  MODIFY COLUMN `approved_at` datetime NULL COMMENT '审批通过时间',
  MODIFY COLUMN `returned_at` datetime NULL COMMENT '归还时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `ai_analysis_jobs`
COMMENT='AI分析任务表',
  MODIFY COLUMN `completed_at` datetime(6) NULL COMMENT '完成时间',
  MODIFY COLUMN `requested_by` bigint NULL COMMENT '发起人ID',
  MODIFY COLUMN `target_id` bigint NOT NULL COMMENT '目标对象ID',
  MODIFY COLUMN `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '失败原因',
  MODIFY COLUMN `analysis_type` enum('TENDER_ANALYSIS','PROJECT_SCORE_PREVIEW') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分析类型（标讯分析/项目评分预评）',
  MODIFY COLUMN `status` enum('PENDING','COMPLETED','FAILED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务状态（待处理/已完成/失败）',
  MODIFY COLUMN `target_type` enum('TENDER','PROJECT') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目标对象类型（标讯/项目）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `ai_analysis_results`
COMMENT='AI分析结果表',
  MODIFY COLUMN `score` int NOT NULL COMMENT '评分',
  MODIFY COLUMN `job_id` bigint NULL COMMENT '分析任务ID',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '项目ID',
  MODIFY COLUMN `tender_id` bigint NULL COMMENT '标讯ID',
  MODIFY COLUMN `risk_level` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '风险等级',
  MODIFY COLUMN `suggestion` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'AI建议',
  MODIFY COLUMN `payload_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分析结果详情（JSON）',
  MODIFY COLUMN `analysis_type` enum('TENDER_ANALYSIS','PROJECT_SCORE_PREVIEW') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分析类型（标讯分析/项目评分预评）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `alert_history`
COMMENT='告警历史表',
  MODIFY COLUMN `resolved` bit(1) NOT NULL COMMENT '是否已解决（1=是 0=否）',
  MODIFY COLUMN `acknowledged_at` datetime(6) NULL COMMENT '确认时间',
  MODIFY COLUMN `resolved_at` datetime(6) NULL COMMENT '解决时间',
  MODIFY COLUMN `rule_id` bigint NOT NULL COMMENT '告警规则ID',
  MODIFY COLUMN `related_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '关联对象标识',
  MODIFY COLUMN `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '告警内容',
  MODIFY COLUMN `level` enum('LOW','MEDIUM','HIGH','CRITICAL') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '告警级别（低/中/高/严重）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `alert_rules`
COMMENT='告警规则表',
  MODIFY COLUMN `enabled` bit(1) NOT NULL COMMENT '是否启用（1=是 0=否）',
  MODIFY COLUMN `threshold` decimal(19,2) NOT NULL COMMENT '触发阈值',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规则名称',
  MODIFY COLUMN `condition` enum('GREATER_THAN','LESS_THAN','EQUALS','CONTAINS') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '触发条件（大于/小于/等于/包含）',
  MODIFY COLUMN `type` enum('DEADLINE','BUDGET','RISK','DOCUMENT','QUALIFICATION_EXPIRY','DEPOSIT_RETURN','PERFORMANCE_EXPIRY','CA_EXPIRY','CA_BORROW_OVERDUE') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '告警类型（截止日期/预算/风险/文档/资质到期/保证金退回/业绩到期/CA到期/CA借用逾期）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间',
  MODIFY COLUMN `created_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人ID';

ALTER TABLE `api_keys`
COMMENT='API密钥表',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '密钥名称',
  MODIFY COLUMN `key_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '密钥哈希',
  MODIFY COLUMN `scopes` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '授权范围',
  MODIFY COLUMN `status` enum('ACTIVE','DISABLED','EXPIRED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态（启用/停用/已过期）',
  MODIFY COLUMN `expires_at` datetime NULL COMMENT '过期时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '创建人ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `approval_actions`
COMMENT='审批动作记录表',
  MODIFY COLUMN `action_time` datetime(6) NOT NULL COMMENT '操作时间',
  MODIFY COLUMN `actor_id` bigint NOT NULL COMMENT '操作人ID',
  MODIFY COLUMN `approval_request_id` binary(16) NOT NULL COMMENT '审批请求ID',
  MODIFY COLUMN `actor_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作人姓名',
  MODIFY COLUMN `comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '审批意见',
  MODIFY COLUMN `action_type` enum('SUBMIT','APPROVE','REJECT','CANCEL') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '动作类型（提交/通过/驳回/取消）',
  MODIFY COLUMN `new_status` enum('PENDING','APPROVED','REJECTED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '动作后状态（待审批/通过/驳回/已取消）',
  MODIFY COLUMN `previous_status` enum('PENDING','APPROVED','REJECTED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '动作前状态（待审批/通过/驳回/已取消）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` binary(16) NOT NULL COMMENT '主键ID';

ALTER TABLE `approval_requests`
COMMENT='审批请求表',
  MODIFY COLUMN `is_read` bit(1) NULL COMMENT '是否已读（1=是 0=否）',
  MODIFY COLUMN `priority` int NOT NULL COMMENT '优先级',
  MODIFY COLUMN `completed_at` datetime(6) NULL COMMENT '完成时间',
  MODIFY COLUMN `current_approver_id` bigint NULL COMMENT '当前审批人ID',
  MODIFY COLUMN `due_date` datetime(6) NULL COMMENT '审批截止时间',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `requester_id` bigint NOT NULL COMMENT '申请人ID',
  MODIFY COLUMN `submitted_at` datetime(6) NOT NULL COMMENT '提交时间',
  MODIFY COLUMN `approval_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '审批类型',
  MODIFY COLUMN `current_approver_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '当前审批人姓名',
  MODIFY COLUMN `requester_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '申请人姓名',
  MODIFY COLUMN `project_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目名称',
  MODIFY COLUMN `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '审批标题',
  MODIFY COLUMN `attachment_ids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '附件ID列表',
  MODIFY COLUMN `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '审批说明',
  MODIFY COLUMN `status` enum('PENDING','APPROVED','REJECTED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '审批状态（待审批/通过/驳回/已取消）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间',
  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID',
  MODIFY COLUMN `id` binary(16) NOT NULL COMMENT '主键ID';

ALTER TABLE `archive_file`
COMMENT='档案文件表',
  MODIFY COLUMN `archive_id` bigint NOT NULL COMMENT '归档ID',
  MODIFY COLUMN `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件名',
  MODIFY COLUMN `document_category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文档类别',
  MODIFY COLUMN `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件路径',
  MODIFY COLUMN `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  MODIFY COLUMN `upload_user_id` bigint NOT NULL COMMENT '上传人ID',
  MODIFY COLUMN `upload_user_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '上传人姓名',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `archive_log`
COMMENT='档案操作日志表',
  MODIFY COLUMN `archive_id` bigint NOT NULL COMMENT '归档ID',
  MODIFY COLUMN `operator_id` bigint NOT NULL COMMENT '操作人ID',
  MODIFY COLUMN `operator_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作人姓名',
  MODIFY COLUMN `action_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作类型',
  MODIFY COLUMN `action_content` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作内容',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `assembly_templates`
COMMENT='组装模板表',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模板名称',
  MODIFY COLUMN `category` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '模板类别',
  MODIFY COLUMN `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '模板描述',
  MODIFY COLUMN `template_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模板内容',
  MODIFY COLUMN `variables` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '模板变量（JSON）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `audit_logs`
COMMENT='审计日志表',
  MODIFY COLUMN `success` bit(1) NOT NULL COMMENT '是否成功（1=是 0=否）',
  MODIFY COLUMN `timestamp` datetime(6) NOT NULL COMMENT '操作时间',
  MODIFY COLUMN `action` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作动作',
  MODIFY COLUMN `ip_address` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '操作IP地址',
  MODIFY COLUMN `entity_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '操作对象ID',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '关联项目ID',
  MODIFY COLUMN `entity_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '操作对象类型',
  MODIFY COLUMN `username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '操作人用户名',
  MODIFY COLUMN `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '操作描述',
  MODIFY COLUMN `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '浏览器UA',
  MODIFY COLUMN `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '失败原因',
  MODIFY COLUMN `new_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '变更后值',
  MODIFY COLUMN `old_value` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '变更前值',
  MODIFY COLUMN `user_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作人ID',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `bar_assets`
COMMENT='BAR投标资产台账表',
  MODIFY COLUMN `acquire_date` date NOT NULL COMMENT '购置日期',
  MODIFY COLUMN `asset_value` decimal(19,2) NOT NULL COMMENT '资产价值（元）',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '资产名称',
  MODIFY COLUMN `status` enum('AVAILABLE','IN_USE','MAINTENANCE','RETIRED','DISPOSED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '资产状态（可用/使用中/维护中/退役/已处置）',
  MODIFY COLUMN `type` enum('EQUIPMENT','FACILITY','VEHICLE','INVENTORY','LICENSE','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '资产类型（设备/设施/车辆/存货/证照/其他）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '备注';

ALTER TABLE `bar_certificate_borrow_records`
COMMENT='BAR证书借用记录表',
  MODIFY COLUMN `expected_return_date` date NULL COMMENT '预计归还日期',
  MODIFY COLUMN `borrowed_at` datetime(6) NOT NULL COMMENT '借用时间',
  MODIFY COLUMN `certificate_id` bigint NOT NULL COMMENT '证书ID',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '借用项目ID',
  MODIFY COLUMN `returned_at` datetime(6) NULL COMMENT '归还时间',
  MODIFY COLUMN `borrower` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '借用人',
  MODIFY COLUMN `purpose` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '借用用途',
  MODIFY COLUMN `status` enum('BORROWED','RETURNED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '借用状态（借出/已归还）',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '备注';

ALTER TABLE `bar_certificates`
COMMENT='BAR投标证书表',
  MODIFY COLUMN `expected_return_date` date NULL COMMENT '预计归还日期',
  MODIFY COLUMN `expiry_date` date NULL COMMENT '有效期至',
  MODIFY COLUMN `bar_asset_id` bigint NOT NULL COMMENT '所属资产ID',
  MODIFY COLUMN `current_project_id` bigint NULL COMMENT '当前借用项目ID',
  MODIFY COLUMN `current_borrower` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '当前借用人',
  MODIFY COLUMN `holder` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '持有人',
  MODIFY COLUMN `provider` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '发证单位',
  MODIFY COLUMN `type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '证书类型',
  MODIFY COLUMN `borrow_purpose` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '借用用途',
  MODIFY COLUMN `location` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '存放位置',
  MODIFY COLUMN `serial_no` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '证书编号',
  MODIFY COLUMN `status` enum('AVAILABLE','BORROWED','EXPIRED','DISABLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '证书状态（可用/借出/已过期/已停用）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '备注';

ALTER TABLE `bar_site_accounts`
COMMENT='BAR站点账号表',
  MODIFY COLUMN `bar_asset_id` bigint NOT NULL COMMENT '所属资产ID',
  MODIFY COLUMN `role` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '账号角色',
  MODIFY COLUMN `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '账号状态',
  MODIFY COLUMN `phone` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '绑定手机号',
  MODIFY COLUMN `owner` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '账号归属人',
  MODIFY COLUMN `username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '登录用户名',
  MODIFY COLUMN `email` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '绑定邮箱',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间';

ALTER TABLE `bar_site_attachments`
COMMENT='BAR站点附件表',
  MODIFY COLUMN `bar_asset_id` bigint NOT NULL COMMENT '所属资产ID',
  MODIFY COLUMN `uploaded_at` datetime(6) NOT NULL COMMENT '上传时间',
  MODIFY COLUMN `size` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文件大小',
  MODIFY COLUMN `content_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文件类型（MIME）',
  MODIFY COLUMN `uploaded_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '上传人',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '附件名称',
  MODIFY COLUMN `url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '附件地址',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `bar_site_sops`
COMMENT='BAR站点操作指引表',
  MODIFY COLUMN `bar_asset_id` bigint NOT NULL COMMENT '所属资产ID',
  MODIFY COLUMN `estimated_time` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '预计耗时',
  MODIFY COLUMN `reset_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '密码重置地址',
  MODIFY COLUMN `unlock_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '解锁地址',
  MODIFY COLUMN `contacts_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '联系人列表（JSON）',
  MODIFY COLUMN `faqs_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '常见问题（JSON）',
  MODIFY COLUMN `history_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '操作历史（JSON）',
  MODIFY COLUMN `required_docs_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '所需材料（JSON）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间';

ALTER TABLE `bar_site_verifications`
COMMENT='BAR站点核验记录表',
  MODIFY COLUMN `bar_asset_id` bigint NOT NULL COMMENT '所属资产ID',
  MODIFY COLUMN `verified_at` datetime(6) NOT NULL COMMENT '核验时间',
  MODIFY COLUMN `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '核验结果',
  MODIFY COLUMN `verified_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '核验人',
  MODIFY COLUMN `message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '核验说明',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `bid_agent_artifacts`
COMMENT='标书Agent产物表',
  MODIFY COLUMN `run_id` bigint NOT NULL COMMENT '运行记录ID',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `artifact_type` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '产物类型',
  MODIFY COLUMN `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '产物标题',
  MODIFY COLUMN `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '产物内容',
  MODIFY COLUMN `handoff_target` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '移交目标（写作岗位）',
  MODIFY COLUMN `status` enum('DRAFTED','READY_FOR_WRITER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DRAFTED' COMMENT '产物状态（已草拟/待写作）',
  MODIFY COLUMN `applied_at` datetime(6) NULL COMMENT '应用时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间';

ALTER TABLE `bid_agent_runs`
COMMENT='标书Agent运行记录表',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `tender_id` bigint NOT NULL COMMENT '标讯ID',
  MODIFY COLUMN `project_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '项目名称',
  MODIFY COLUMN `tender_title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标讯标题',
  MODIFY COLUMN `status` enum('DRAFTED','REVIEWED','READY_FOR_WRITER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DRAFTED' COMMENT '运行状态（已草拟/已复核/待写作）',
  MODIFY COLUMN `snapshot_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '标讯快照（JSON）',
  MODIFY COLUMN `requirement_classification_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '需求分类结果（JSON）',
  MODIFY COLUMN `material_match_score_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '素材匹配得分（JSON）',
  MODIFY COLUMN `gap_check_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '差距检查结果（JSON）',
  MODIFY COLUMN `manual_confirmation_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '人工确认记录（JSON）',
  MODIFY COLUMN `write_coverage_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '写作覆盖度（JSON）',
  MODIFY COLUMN `draft_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '草稿全文',
  MODIFY COLUMN `review_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '复核意见全文',
  MODIFY COLUMN `generator_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '生成器标识',
  MODIFY COLUMN `reviewed_at` datetime(6) NULL COMMENT '复核时间',
  MODIFY COLUMN `applied_at` datetime(6) NULL COMMENT '应用时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间';

ALTER TABLE `bid_case_slice`
COMMENT='案例文档切片表',
  MODIFY COLUMN `project_dir` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '案例项目目录',
  MODIFY COLUMN `project_idx` int NOT NULL COMMENT '案例项目序号',
  MODIFY COLUMN `docx_file` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '源文档文件名',
  MODIFY COLUMN `docx_label` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '源文档标签',
  MODIFY COLUMN `section_idx` int NOT NULL COMMENT '章节序号',
  MODIFY COLUMN `level` int NOT NULL COMMENT '章节层级',
  MODIFY COLUMN `title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '章节标题',
  MODIFY COLUMN `text_preview` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文本预览',
  MODIFY COLUMN `text_length` int NOT NULL COMMENT '文本长度',
  MODIFY COLUMN `para_count` int NOT NULL COMMENT '段落数',
  MODIFY COLUMN `embedding` mediumblob NULL COMMENT '向量数据',
  MODIFY COLUMN `embedding_model` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '向量化模型',
  MODIFY COLUMN `embedding_dim` int NULL COMMENT '向量维度',
  MODIFY COLUMN `embedding_at` timestamp NULL COMMENT '向量化时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `bid_file`
COMMENT='标书文件表',
  MODIFY COLUMN `upload_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '上传批次ID',
  MODIFY COLUMN `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件状态（上传中/已完成/失败）',
  MODIFY COLUMN `original_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '原始文件名',
  MODIFY COLUMN `object_key` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '对象存储键',
  MODIFY COLUMN `bucket` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '存储桶',
  MODIFY COLUMN `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  MODIFY COLUMN `file_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文件哈希',
  MODIFY COLUMN `mime_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文件类型（MIME）',
  MODIFY COLUMN `creator_id` bigint NOT NULL COMMENT '上传人ID',
  MODIFY COLUMN `error_message` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '失败原因',
  MODIFY COLUMN `completed_at` datetime(6) NULL COMMENT '完成时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间';

ALTER TABLE `bid_match_model_versions`
COMMENT='匹配评分模型版本表',
  MODIFY COLUMN `model_id` bigint NOT NULL COMMENT '模型ID',
  MODIFY COLUMN `version_no` int NOT NULL COMMENT '版本号',
  MODIFY COLUMN `snapshot_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型快照（JSON）',
  MODIFY COLUMN `active_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否激活版本（1=是 0=否）',
  MODIFY COLUMN `activated_at` datetime(6) NOT NULL COMMENT '激活时间',
  MODIFY COLUMN `activated_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '激活人',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间';

ALTER TABLE `bid_match_score_evaluations`
COMMENT='匹配评分评估表',
  MODIFY COLUMN `tender_id` bigint NOT NULL COMMENT '标讯ID',
  MODIFY COLUMN `model_id` bigint NOT NULL COMMENT '评分模型ID',
  MODIFY COLUMN `model_version_id` bigint NOT NULL COMMENT '模型版本ID',
  MODIFY COLUMN `model_version_no` int NOT NULL COMMENT '模型版本号',
  MODIFY COLUMN `total_score` decimal(7,2) NOT NULL COMMENT '总分',
  MODIFY COLUMN `dimension_scores_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '维度得分（JSON）',
  MODIFY COLUMN `evidence_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '评分证据（JSON）',
  MODIFY COLUMN `evidence_fingerprint` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '证据指纹',
  MODIFY COLUMN `model_snapshot_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型快照（JSON）',
  MODIFY COLUMN `evaluated_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '评估人',
  MODIFY COLUMN `evaluated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '评估时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间';

ALTER TABLE `bid_match_scoring_models`
COMMENT='匹配评分模型表',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型名称',
  MODIFY COLUMN `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '模型描述',
  MODIFY COLUMN `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型状态',
  MODIFY COLUMN `draft_revision` bigint NOT NULL DEFAULT 1 COMMENT '草稿修订号',
  MODIFY COLUMN `model_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型定义（JSON）',
  MODIFY COLUMN `active_version_id` bigint NULL COMMENT '激活版本ID',
  MODIFY COLUMN `active_version_no` int NULL COMMENT '激活版本号',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间';

ALTER TABLE `bid_requirement_items`
COMMENT='投标要求项表',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `tender_id` bigint NOT NULL COMMENT '标讯ID',
  MODIFY COLUMN `project_document_id` bigint NOT NULL COMMENT '项目文档ID',
  MODIFY COLUMN `category` varchar(60) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '要求类别',
  MODIFY COLUMN `title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '要求标题',
  MODIFY COLUMN `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '要求内容',
  MODIFY COLUMN `source_excerpt` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '标讯原文摘录',
  MODIFY COLUMN `mandatory` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否强制条款（1=是 0=否）',
  MODIFY COLUMN `confidence` int NULL COMMENT '提取置信度',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间';

ALTER TABLE `bid_result_fetch_results`
COMMENT='中标结果抓取记录表',
  MODIFY COLUMN `amount` decimal(19,2) NULL COMMENT '中标金额（元）',
  MODIFY COLUMN `contract_duration_months` int NULL COMMENT '合同期限（月）',
  MODIFY COLUMN `contract_end_date` date NULL COMMENT '合同结束日期',
  MODIFY COLUMN `contract_start_date` date NULL COMMENT '合同开始日期',
  MODIFY COLUMN `sku_count` int NULL COMMENT 'SKU数量',
  MODIFY COLUMN `analysis_document_id` bigint NULL COMMENT '分析文档ID',
  MODIFY COLUMN `confirmed_at` datetime(6) NULL COMMENT '确认时间',
  MODIFY COLUMN `confirmed_by` bigint NULL COMMENT '确认人ID',
  MODIFY COLUMN `fetch_time` datetime(6) NOT NULL COMMENT '抓取时间',
  MODIFY COLUMN `notice_document_id` bigint NULL COMMENT '中标公告文档ID',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '项目ID',
  MODIFY COLUMN `tender_id` bigint NULL COMMENT '标讯ID',
  MODIFY COLUMN `source` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '数据来源',
  MODIFY COLUMN `project_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '项目名称',
  MODIFY COLUMN `win_announce_doc_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '中标公告地址',
  MODIFY COLUMN `ignored_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '忽略原因',
  MODIFY COLUMN `registration_type` enum('MANUAL','SYNC','FETCH') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '登记方式（手工/同步/抓取）',
  MODIFY COLUMN `result` enum('WON','LOST') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '中标结果（中标/未中标）',
  MODIFY COLUMN `status` enum('PENDING','CONFIRMED','IGNORED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '处理状态（待确认/已确认/已忽略）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '更新时间',
  MODIFY COLUMN `remark` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '备注';

ALTER TABLE `bid_result_reminders`
COMMENT='中标结果提醒表',
  MODIFY COLUMN `attachment_document_id` bigint NULL COMMENT '附件文档ID',
  MODIFY COLUMN `last_result_id` bigint NULL COMMENT '最近结果ID',
  MODIFY COLUMN `owner_id` bigint NULL COMMENT '负责人ID',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `remind_time` datetime(6) NOT NULL COMMENT '提醒时间',
  MODIFY COLUMN `uploaded_at` datetime(6) NULL COMMENT '附件上传时间',
  MODIFY COLUMN `uploaded_by` bigint NULL COMMENT '附件上传人ID',
  MODIFY COLUMN `last_reminder_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '最近提醒留言',
  MODIFY COLUMN `project_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '项目名称',
  MODIFY COLUMN `created_by_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人姓名',
  MODIFY COLUMN `owner_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '负责人姓名',
  MODIFY COLUMN `reminder_type` enum('NOTICE','REPORT') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提醒类型（中标通知书/中标结果报告）',
  MODIFY COLUMN `status` enum('PENDING','REMINDED','UPLOADED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提醒状态（待提醒/已提醒/已上传）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '更新时间';

ALTER TABLE `bid_result_sync_logs`
COMMENT='中标结果同步日志表',
  MODIFY COLUMN `affected_count` int NOT NULL COMMENT '影响条数',
  MODIFY COLUMN `operator_id` bigint NULL COMMENT '操作人ID',
  MODIFY COLUMN `source` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '数据来源',
  MODIFY COLUMN `message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '同步说明',
  MODIFY COLUMN `operator_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作人姓名',
  MODIFY COLUMN `operation_type` enum('SYNC','FETCH') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作类型（同步/抓取）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `bid_tender_document_snapshots`
COMMENT='标讯文档快照表',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `tender_id` bigint NOT NULL COMMENT '标讯ID',
  MODIFY COLUMN `project_document_id` bigint NOT NULL COMMENT '项目文档ID',
  MODIFY COLUMN `file_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件名',
  MODIFY COLUMN `content_type` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文件类型（MIME）',
  MODIFY COLUMN `file_url` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文件地址',
  MODIFY COLUMN `storage_path` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '存储路径',
  MODIFY COLUMN `content_sha256` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '内容SHA256',
  MODIFY COLUMN `extracted_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提取文本全文',
  MODIFY COLUMN `profile_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文档画像（JSON）',
  MODIFY COLUMN `extractor_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提取器标识',
  MODIFY COLUMN `analyzer_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分析器标识',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间';

ALTER TABLE `brand_auth_alert_config`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `brand_auth_attachment`
  MODIFY COLUMN `attachment_type` enum('AUTH_DOC','SUPPLEMENTARY','AGENT_AUTH_1','AGENT_AUTH_2') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '附件类型（授权书/补充材料/代理商授权一/代理商授权二）',
  MODIFY COLUMN `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件名',
  MODIFY COLUMN `file_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件地址',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `brand_auth_operation_log`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `brand_authorization_deprecated`
  MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `business_qualifications`
COMMENT='企业资质表',
  MODIFY COLUMN `expected_return_date` date NULL COMMENT '预计归还日期',
  MODIFY COLUMN `expiry_date` date NULL COMMENT '有效期至',
  MODIFY COLUMN `issue_date` date NULL COMMENT '发证日期',
  MODIFY COLUMN `reminder_days` int NOT NULL COMMENT '到期提醒提前天数',
  MODIFY COLUMN `reminder_enabled` bit(1) NOT NULL COMMENT '是否启用到期提醒（1=是 0=否）',
  MODIFY COLUMN `last_reminded_at` datetime(6) NULL COMMENT '最近提醒时间',
  MODIFY COLUMN `current_project_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '当前借用项目ID',
  MODIFY COLUMN `certificate_no` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '证书编号',
  MODIFY COLUMN `current_borrower` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '当前借用人',
  MODIFY COLUMN `current_department` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '当前借用部门',
  MODIFY COLUMN `holder_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '持有主体名称',
  MODIFY COLUMN `issuer` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '发证单位',
  MODIFY COLUMN `cert_review_note` date NULL COMMENT '复审日期',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '资质名称',
  MODIFY COLUMN `subject_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '主体名称',
  MODIFY COLUMN `file_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '证书文件地址',
  MODIFY COLUMN `borrow_purpose` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '借用用途',
  MODIFY COLUMN `category` enum('LICENSE','PRODUCT','PERSONNEL','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '资质类别（证照/产品/人员/其他）',
  MODIFY COLUMN `current_borrow_status` enum('AVAILABLE','BORROWED','RETURNED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '借用状态（可用/借出/已归还）',
  MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '资质状态',
  MODIFY COLUMN `subject_type` enum('COMPANY','SUBSIDIARY') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '主体类型（本公司/子公司）',
  MODIFY COLUMN `audit_log_file_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '审计记录文件地址',
  MODIFY COLUMN `last_review_reminded_at` datetime(6) NULL COMMENT '最近复审提醒时间',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '更新时间';

ALTER TABLE `ca_borrow_applications`
  MODIFY COLUMN `applicant_employee_number` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '申请人工号',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `ca_borrow_events`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `ca_certificate_import_task`
COMMENT='CA证书导入任务表',
  MODIFY COLUMN `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING' COMMENT '任务状态（待处理/处理中/已完成/失败）',
  MODIFY COLUMN `total_rows` int NULL DEFAULT 0 COMMENT '总行数',
  MODIFY COLUMN `valid_rows` int NULL DEFAULT 0 COMMENT '有效行数',
  MODIFY COLUMN `invalid_rows` int NULL DEFAULT 0 COMMENT '无效行数',
  MODIFY COLUMN `imported_rows` int NULL DEFAULT 0 COMMENT '新增导入行数',
  MODIFY COLUMN `updated_rows` int NULL DEFAULT 0 COMMENT '更新行数',
  MODIFY COLUMN `error_details` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误明细',
  MODIFY COLUMN `source_filename` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '来源文件名',
  MODIFY COLUMN `created_by_username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '创建人用户名',
  MODIFY COLUMN `completed_at` datetime NULL COMMENT '完成时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `ca_certificate_platforms`
COMMENT='CA证书平台账号关联表',
  MODIFY COLUMN `ca_certificate_id` bigint NOT NULL COMMENT 'CA证书ID',
  MODIFY COLUMN `platform_account_id` bigint NOT NULL COMMENT '平台账号ID',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `ca_certificates`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `calendar_events`
COMMENT='日历事件表',
  MODIFY COLUMN `event_date` date NOT NULL COMMENT '事件日期',
  MODIFY COLUMN `is_urgent` bit(1) NULL COMMENT '是否紧急（1=是 0=否）',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '关联项目ID',
  MODIFY COLUMN `title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '事件标题',
  MODIFY COLUMN `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '事件描述',
  MODIFY COLUMN `event_type` enum('DEADLINE','MEETING','MILESTONE','REMINDER','SUBMISSION','REVIEW') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '事件类型（截止日期/会议/里程碑/提醒/递交/评审）',
  MODIFY COLUMN `created_at` datetime(6) NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间';

ALTER TABLE `case_attachment_names`
COMMENT='案例附件名称表',
  MODIFY COLUMN `case_id` bigint NOT NULL COMMENT '案例ID',
  MODIFY COLUMN `attachment_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '附件名称';

ALTER TABLE `case_highlights`
COMMENT='案例亮点表',
  MODIFY COLUMN `case_id` bigint NOT NULL COMMENT '案例ID',
  MODIFY COLUMN `highlight` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '亮点描述';

ALTER TABLE `case_lessons_learned`
COMMENT='案例经验教训表',
  MODIFY COLUMN `case_id` bigint NOT NULL COMMENT '案例ID',
  MODIFY COLUMN `lesson_learned` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '经验教训描述';

ALTER TABLE `case_reference_records`
COMMENT='案例引用记录表',
  MODIFY COLUMN `case_id` bigint NOT NULL COMMENT '案例ID',
  MODIFY COLUMN `referenced_at` datetime(6) NOT NULL COMMENT '引用时间',
  MODIFY COLUMN `referenced_by` bigint NULL COMMENT '引用人ID',
  MODIFY COLUMN `reference_context` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '引用场景说明',
  MODIFY COLUMN `reference_target` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '引用目标',
  MODIFY COLUMN `referenced_by_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '引用人姓名',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `case_share_records`
COMMENT='案例分享记录表',
  MODIFY COLUMN `case_id` bigint NOT NULL COMMENT '案例ID',
  MODIFY COLUMN `expires_at` datetime(6) NULL COMMENT '链接过期时间',
  MODIFY COLUMN `created_by_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人姓名',
  MODIFY COLUMN `token` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分享令牌',
  MODIFY COLUMN `url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分享链接',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `case_success_factors`
COMMENT='案例成功因素表',
  MODIFY COLUMN `case_id` bigint NOT NULL COMMENT '案例ID',
  MODIFY COLUMN `success_factor` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '成功因素描述';

ALTER TABLE `case_tags`
COMMENT='案例标签表',
  MODIFY COLUMN `case_id` bigint NOT NULL COMMENT '案例ID',
  MODIFY COLUMN `tag` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '标签';

ALTER TABLE `case_technologies`
COMMENT='案例技术栈表',
  MODIFY COLUMN `case_id` bigint NOT NULL COMMENT '案例ID',
  MODIFY COLUMN `technology` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '技术/产品名称';

ALTER TABLE `cases`
COMMENT='案例库表',
  MODIFY COLUMN `amount` decimal(38,2) NOT NULL COMMENT '项目金额（元）',
  MODIFY COLUMN `project_date` date NULL COMMENT '项目日期',
  MODIFY COLUMN `published_at` datetime(6) NULL COMMENT '发布时间',
  MODIFY COLUMN `source_project_id` bigint NULL COMMENT '来源项目ID',
  MODIFY COLUMN `use_count` bigint NOT NULL COMMENT '被引用次数',
  MODIFY COLUMN `view_count` bigint NOT NULL COMMENT '浏览次数',
  MODIFY COLUMN `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '案例状态',
  MODIFY COLUMN `visibility` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '可见性',
  MODIFY COLUMN `archive_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '归档摘要',
  MODIFY COLUMN `customer_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '客户名称',
  MODIFY COLUMN `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '案例描述',
  MODIFY COLUMN `document_snapshot_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文档快照文本',
  MODIFY COLUMN `location_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目所在地',
  MODIFY COLUMN `price_strategy` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '报价策略',
  MODIFY COLUMN `product_line` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '产品线',
  MODIFY COLUMN `project_period` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目周期',
  MODIFY COLUMN `search_document` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '检索全文',
  MODIFY COLUMN `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '案例标题',
  MODIFY COLUMN `industry` enum('REAL_ESTATE','INFRASTRUCTURE','MANUFACTURING','ENERGY','TRANSPORTATION','ENVIRONMENTAL','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '行业（房地产/基建/制造/能源/交通/环保/其他）',
  MODIFY COLUMN `outcome` enum('WON','LOST','IN_PROGRESS') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '结果（中标/未中标/进行中）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间';

ALTER TABLE `collaboration_threads`
COMMENT='项目协作讨论表',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `title` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '讨论主题',
  MODIFY COLUMN `status` enum('OPEN','IN_PROGRESS','RESOLVED','CLOSED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态（打开/处理中/已解决/已关闭）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间';

ALTER TABLE `comments`
COMMENT='讨论评论表',
  MODIFY COLUMN `parent_id` bigint NULL COMMENT '父评论ID',
  MODIFY COLUMN `thread_id` bigint NOT NULL COMMENT '所属讨论ID',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '评论人ID',
  MODIFY COLUMN `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '评论内容',
  MODIFY COLUMN `mentions` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '@提及用户列表',
  MODIFY COLUMN `is_deleted` bit(1) NULL COMMENT '是否删除（1=是 0=否）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间';

ALTER TABLE `competition_analyses`
COMMENT='竞争分析表',
  MODIFY COLUMN `win_probability` decimal(5,2) NULL COMMENT '预估赢单概率（%）',
  MODIFY COLUMN `analysis_date` datetime(6) NULL COMMENT '分析时间',
  MODIFY COLUMN `competitor_id` bigint NULL COMMENT '竞争对手ID',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `competitive_advantage` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '竞争优势',
  MODIFY COLUMN `recommended_strategy` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '推荐策略',
  MODIFY COLUMN `risk_factors` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '风险因素',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `competitor_win_records`
COMMENT='竞对赢单记录表',
  MODIFY COLUMN `amount` decimal(19,2) NULL COMMENT '赢单金额（元）',
  MODIFY COLUMN `sku_count` int NULL COMMENT 'SKU数量',
  MODIFY COLUMN `won_at` date NULL COMMENT '赢单日期',
  MODIFY COLUMN `competitor_id` bigint NOT NULL COMMENT '竞争对手ID',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '关联项目ID',
  MODIFY COLUMN `recorded_by` bigint NULL COMMENT '记录人ID',
  MODIFY COLUMN `discount` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '折扣信息',
  MODIFY COLUMN `recorded_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '记录人姓名',
  MODIFY COLUMN `category` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '品类',
  MODIFY COLUMN `competitor_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '竞争对手名称',
  MODIFY COLUMN `payment_terms` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '付款条件',
  MODIFY COLUMN `project_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目名称',
  MODIFY COLUMN `notes` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '备注',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `competitors`
COMMENT='竞争对手表',
  MODIFY COLUMN `market_share` decimal(5,2) NULL COMMENT '市场份额（%）',
  MODIFY COLUMN `typical_bid_range_max` decimal(19,2) NULL COMMENT '常见报价上限（元）',
  MODIFY COLUMN `typical_bid_range_min` decimal(19,2) NULL COMMENT '常见报价下限（元）',
  MODIFY COLUMN `industry` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '所属行业',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '竞争对手名称',
  MODIFY COLUMN `strengths` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '优势',
  MODIFY COLUMN `weaknesses` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '劣势',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `compliance_check_results`
COMMENT='合规检查结果表',
  MODIFY COLUMN `risk_score` int NOT NULL COMMENT '风险分值',
  MODIFY COLUMN `checked_at` datetime(6) NOT NULL COMMENT '检查时间',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '项目ID',
  MODIFY COLUMN `tender_id` bigint NULL COMMENT '标讯ID',
  MODIFY COLUMN `checked_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '检查人',
  MODIFY COLUMN `overall_status` enum('COMPLIANT','NON_COMPLIANT','PARTIAL_COMPLIANT','WARNING') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '总体结论（合规/不合规/部分合规/警告）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间';

ALTER TABLE `compliance_rules`
COMMENT='合规检查规则表',
  MODIFY COLUMN `enabled` bit(1) NOT NULL COMMENT '是否启用（1=是 0=否）',
  MODIFY COLUMN `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规则名称',
  MODIFY COLUMN `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '规则描述',
  MODIFY COLUMN `rule_definition` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '规则定义',
  MODIFY COLUMN `rule_type` enum('QUALIFICATION','DOCUMENT','FINANCIAL','EXPERIENCE','DEADLINE') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规则类型（资质/文档/财务/经验/截止日期）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间';

ALTER TABLE `contract_borrow_applications`
COMMENT='合同借用申请表',
  MODIFY COLUMN `contract_no` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '合同编号',
  MODIFY COLUMN `contract_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '合同名称',
  MODIFY COLUMN `source_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '合同来源方',
  MODIFY COLUMN `borrower_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '借用人姓名',
  MODIFY COLUMN `borrower_dept` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '借用人部门',
  MODIFY COLUMN `customer_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '客户名称',
  MODIFY COLUMN `purpose` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '借用用途',
  MODIFY COLUMN `borrow_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '借用类型',
  MODIFY COLUMN `expected_return_date` date NULL COMMENT '预计归还日期',
  MODIFY COLUMN `submitted_at` datetime(6) NOT NULL COMMENT '提交时间',
  MODIFY COLUMN `approver_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '审批人姓名',
  MODIFY COLUMN `approved_at` datetime(6) NULL COMMENT '审批通过时间',
  MODIFY COLUMN `rejection_reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '驳回原因',
  MODIFY COLUMN `rejected_at` datetime(6) NULL COMMENT '驳回时间',
  MODIFY COLUMN `return_remark` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '归还备注',
  MODIFY COLUMN `returned_at` datetime(6) NULL COMMENT '归还时间',
  MODIFY COLUMN `cancel_reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '取消原因',
  MODIFY COLUMN `cancelled_at` datetime(6) NULL COMMENT '取消时间',
  MODIFY COLUMN `last_comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '最近留言',
  MODIFY COLUMN `status` enum('PENDING_APPROVAL','APPROVED','REJECTED','BORROWED','RETURNED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '申请状态（待审批/已批准/已驳回/已借出/已归还/已取消）',
  MODIFY COLUMN `version` bigint NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间';

ALTER TABLE `contract_borrow_events`
COMMENT='合同借用事件流转表',
  MODIFY COLUMN `application_id` bigint NOT NULL COMMENT '申请ID',
  MODIFY COLUMN `event_type` enum('SUBMITTED','APPROVED','REJECTED','RETURNED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '事件类型（提交/通过/驳回/归还/取消）',
  MODIFY COLUMN `status_after` enum('PENDING_APPROVAL','APPROVED','REJECTED','BORROWED','RETURNED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '事件后状态（待审批/已批准/已驳回/已借出/已归还/已取消）',
  MODIFY COLUMN `actor_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '操作人姓名',
  MODIFY COLUMN `comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '操作留言',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间';

ALTER TABLE `crm_project_mapping`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `customer_predictions`
COMMENT='客户商机预测表',
  MODIFY COLUMN `avg_budget` decimal(14,2) NULL COMMENT '平均预算（元）',
  MODIFY COLUMN `confidence` decimal(3,2) NULL COMMENT '预测置信度（0-1）',
  MODIFY COLUMN `frequency` int NULL COMMENT '历史招标频次',
  MODIFY COLUMN `opportunity_score` int NULL COMMENT '商机评分',
  MODIFY COLUMN `predicted_budget_max` decimal(14,2) NULL COMMENT '预测预算上限（元）',
  MODIFY COLUMN `predicted_budget_min` decimal(14,2) NULL COMMENT '预测预算下限（元）',
  MODIFY COLUMN `converted_project_id` bigint NULL COMMENT '转化项目ID',
  MODIFY COLUMN `last_computed_at` datetime(6) NULL COMMENT '最近计算时间',
  MODIFY COLUMN `predicted_window` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '预测时间窗口',
  MODIFY COLUMN `cycle_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '采购周期类型',
  MODIFY COLUMN `industry` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '所属行业',
  MODIFY COLUMN `predicted_category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '预测采购品类',
  MODIFY COLUMN `purchaser_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '采购人名称哈希',
  MODIFY COLUMN `period_months` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '采购周期月份',
  MODIFY COLUMN `region` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '地区',
  MODIFY COLUMN `sales_rep` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '对应销售',
  MODIFY COLUMN `evidence_record_ids` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '证据记录ID列表',
  MODIFY COLUMN `main_categories` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '主要品类列表',
  MODIFY COLUMN `purchaser_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '采购人名称',
  MODIFY COLUMN `reasoning_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '推理摘要',
  MODIFY COLUMN `status` enum('WATCH','RECOMMEND','CONVERTED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '跟进状态（观察/推荐/已转化/已取消）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间';

ALTER TABLE `deposit_tracking`
COMMENT='保证金跟踪表',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `payment_date` date NOT NULL COMMENT '缴纳日期',
  MODIFY COLUMN `expected_return_date` date NOT NULL COMMENT '预计退回日期',
  MODIFY COLUMN `amount` decimal(12,2) NOT NULL COMMENT '保证金金额（元）',
  MODIFY COLUMN `status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '保证金状态',
  MODIFY COLUMN `payee` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '收款方',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `dimension_scores`
COMMENT='评分维度得分表',
  MODIFY COLUMN `score` int NULL COMMENT '得分',
  MODIFY COLUMN `weight` decimal(38,2) NULL COMMENT '权重',
  MODIFY COLUMN `analysis_id` bigint NOT NULL COMMENT '评分分析ID',
  MODIFY COLUMN `dimension_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '维度名称',
  MODIFY COLUMN `comments` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '维度评语',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间';

ALTER TABLE `document_archive_records`
COMMENT='文档归档记录表',
  MODIFY COLUMN `archived_at` datetime(6) NOT NULL COMMENT '归档时间',
  MODIFY COLUMN `archived_by` bigint NULL COMMENT '归档人ID',
  MODIFY COLUMN `export_id` bigint NULL COMMENT '导出记录ID',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `structure_id` bigint NOT NULL COMMENT '文档结构ID',
  MODIFY COLUMN `archive_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归档原因',
  MODIFY COLUMN `archived_by_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归档人姓名',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `document_assemblies`
COMMENT='文档组装记录表',
  MODIFY COLUMN `assembled_at` datetime(6) NOT NULL COMMENT '组装时间',
  MODIFY COLUMN `assembled_by` bigint NULL COMMENT '组装人ID',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `template_id` bigint NULL COMMENT '组装模板ID',
  MODIFY COLUMN `assembled_content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '组装后内容',
  MODIFY COLUMN `variables` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '组装变量（JSON）',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `document_assignments`
COMMENT='文档章节写作分配表',
  MODIFY COLUMN `due_date` date NULL COMMENT '完成期限',
  MODIFY COLUMN `assigned_by` bigint NULL COMMENT '分配人ID',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `section_id` bigint NOT NULL COMMENT '章节ID',
  MODIFY COLUMN `owner` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '负责人',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '更新时间';

ALTER TABLE `document_export_files`
COMMENT='文档导出文件表',
  MODIFY COLUMN `export_id` bigint NOT NULL COMMENT '导出记录ID',
  MODIFY COLUMN `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件内容',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `document_exports`
COMMENT='文档导出记录表',
  MODIFY COLUMN `exported_at` datetime(6) NOT NULL COMMENT '导出时间',
  MODIFY COLUMN `exported_by` bigint NULL COMMENT '导出人ID',
  MODIFY COLUMN `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `structure_id` bigint NOT NULL COMMENT '文档结构ID',
  MODIFY COLUMN `content_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '内容类型（MIME）',
  MODIFY COLUMN `exported_by_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '导出人姓名',
  MODIFY COLUMN `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件名',
  MODIFY COLUMN `format` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '导出格式',
  MODIFY COLUMN `project_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '项目名称',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `document_locks`
COMMENT='文档章节编辑锁表',
  MODIFY COLUMN `locked` bit(1) NOT NULL COMMENT '是否锁定（1=是 0=否）',
  MODIFY COLUMN `locked_at` datetime(6) NULL COMMENT '锁定时间',
  MODIFY COLUMN `locked_by` bigint NULL COMMENT '锁定人ID',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `section_id` bigint NOT NULL COMMENT '章节ID',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '更新时间';

ALTER TABLE `document_reminders`
COMMENT='文档章节催办记录表',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `reminded_at` datetime(6) NOT NULL COMMENT '催办时间',
  MODIFY COLUMN `reminded_by` bigint NULL COMMENT '催办人ID',
  MODIFY COLUMN `section_id` bigint NOT NULL COMMENT '章节ID',
  MODIFY COLUMN `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '催办留言',
  MODIFY COLUMN `recipient` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '催办接收人',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `document_sections`
COMMENT='标书文档章节表',
  MODIFY COLUMN `order_index` int NULL COMMENT '排序序号',
  MODIFY COLUMN `parent_id` bigint NULL COMMENT '父章节ID',
  MODIFY COLUMN `structure_id` bigint NOT NULL COMMENT '所属文档结构ID',
  MODIFY COLUMN `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '章节内容',
  MODIFY COLUMN `metadata` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '章节元数据（JSON）',
  MODIFY COLUMN `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '章节标题',
  MODIFY COLUMN `section_type` enum('CHAPTER','SECTION','SUBSECTION','TABLE','IMAGE','ATTACHMENT') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '章节类型（章/节/小节/表格/图片/附件）',
  MODIFY COLUMN `created_at` datetime(6) NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间';

ALTER TABLE `document_structures`
COMMENT='标书文档结构表',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `root_section_id` bigint NULL COMMENT '根章节ID',
  MODIFY COLUMN `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '结构名称',
  MODIFY COLUMN `created_at` datetime(6) NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间';

ALTER TABLE `document_versions`
COMMENT='文档版本表',
  MODIFY COLUMN `is_current` bit(1) NULL COMMENT '是否当前版本（1=是 0=否）',
  MODIFY COLUMN `version_number` int NOT NULL COMMENT '版本号',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `change_summary` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '变更摘要',
  MODIFY COLUMN `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '版本内容',
  MODIFY COLUMN `document_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文档标识',
  MODIFY COLUMN `file_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文件路径',
  MODIFY COLUMN `created_at` datetime(6) NULL COMMENT '创建时间',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `email_verification_tokens`
COMMENT='邮箱验证令牌表',
  MODIFY COLUMN `expires_at` datetime(6) NOT NULL COMMENT '过期时间',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户ID',
  MODIFY COLUMN `verified_at` datetime(6) NULL COMMENT '验证时间',
  MODIFY COLUMN `token` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '验证令牌',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `expense_approval_records`
COMMENT='费用审批记录表',
  MODIFY COLUMN `acted_at` datetime(6) NOT NULL COMMENT '操作时间',
  MODIFY COLUMN `expense_id` bigint NOT NULL COMMENT '费用申请ID',
  MODIFY COLUMN `approver` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '审批人',
  MODIFY COLUMN `comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '审批意见',
  MODIFY COLUMN `result` enum('APPROVED','REJECTED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '审批结果（通过/驳回）',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `expense_payment_records`
COMMENT='费用支付记录表',
  MODIFY COLUMN `amount` decimal(19,2) NOT NULL COMMENT '支付金额（元）',
  MODIFY COLUMN `expense_id` bigint NOT NULL COMMENT '费用申请ID',
  MODIFY COLUMN `paid_at` datetime(6) NOT NULL COMMENT '支付时间',
  MODIFY COLUMN `payment_method` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '支付方式',
  MODIFY COLUMN `paid_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '支付人',
  MODIFY COLUMN `payment_reference` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '支付凭证号',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '备注';

ALTER TABLE `expenses`
COMMENT='投标费用申请表',
  MODIFY COLUMN `amount` decimal(19,2) NOT NULL COMMENT '金额（元）',
  MODIFY COLUMN `date` date NOT NULL COMMENT '费用发生日期',
  MODIFY COLUMN `expected_return_date` date NULL COMMENT '预计归还日期',
  MODIFY COLUMN `approved_at` datetime(6) NULL COMMENT '审批时间',
  MODIFY COLUMN `last_return_reminder_at` datetime(6) NULL COMMENT '最近归还提醒时间',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `return_confirmed_at` datetime(6) NULL COMMENT '归还确认时间',
  MODIFY COLUMN `return_requested_at` datetime(6) NULL COMMENT '归还申请时间',
  MODIFY COLUMN `approved_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '审批人',
  MODIFY COLUMN `expense_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '费用类型',
  MODIFY COLUMN `approval_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '审批意见',
  MODIFY COLUMN `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '费用说明',
  MODIFY COLUMN `return_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '归还说明',
  MODIFY COLUMN `category` enum('MATERIAL','LABOR','EQUIPMENT','TRANSPORTATION','SUBCONTRACTING','OVERHEAD','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '费用类别（材料/人工/设备/运输/分包/管理费/其他）',
  MODIFY COLUMN `status` enum('PENDING_APPROVAL','APPROVED','REJECTED','PAID','RETURN_REQUESTED','RETURNED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态（待审批/已批准/已驳回/已支付/归还申请中/已归还）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间',
  MODIFY COLUMN `created_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人ID';

ALTER TABLE `export_tasks`
COMMENT='通用导出任务表',
  MODIFY COLUMN `progress` int NULL COMMENT '导出进度（百分比）',
  MODIFY COLUMN `completed_at` datetime(6) NULL COMMENT '完成时间',
  MODIFY COLUMN `expires_at` datetime(6) NULL COMMENT '文件过期时间',
  MODIFY COLUMN `file_size` bigint NULL COMMENT '文件大小（字节）',
  MODIFY COLUMN `data_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '导出数据类型',
  MODIFY COLUMN `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文件存储路径',
  MODIFY COLUMN `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '失败原因',
  MODIFY COLUMN `export_params` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '导出参数（JSON）',
  MODIFY COLUMN `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文件名',
  MODIFY COLUMN `export_type` enum('EXCEL','PDF') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '导出格式（Excel/PDF）',
  MODIFY COLUMN `status` enum('PENDING','PROCESSING','COMPLETED','FAILED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务状态（待处理/处理中/已完成/失败）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `created_by` bigint NOT NULL COMMENT '创建人ID',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `fees`
COMMENT='投标费用台账表',
  MODIFY COLUMN `amount` decimal(19,2) NOT NULL COMMENT '金额（元）',
  MODIFY COLUMN `fee_date` datetime(6) NOT NULL COMMENT '费用发生时间',
  MODIFY COLUMN `payment_date` datetime(6) NULL COMMENT '支付时间',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `return_date` datetime(6) NULL COMMENT '退回时间',
  MODIFY COLUMN `paid_by` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '支付人',
  MODIFY COLUMN `return_to` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '退回对象',
  MODIFY COLUMN `remarks` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '备注',
  MODIFY COLUMN `fee_type` enum('BID_BOND','SERVICE_FEE','DOCUMENT_FEE','TRAVEL_FEE','NOTARY_FEE','OTHER_FEE') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '费用类型（投标保证金/服务费/标书费/差旅费/公证费/其他）',
  MODIFY COLUMN `status` enum('PENDING','PAID','RETURNED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态（待支付/已支付/已退回/已取消）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间';

ALTER TABLE `historical_project_snapshots`
COMMENT='历史项目快照表',
  MODIFY COLUMN `archive_record_id` bigint NOT NULL COMMENT '归档记录ID',
  MODIFY COLUMN `captured_at` datetime(6) NOT NULL COMMENT '快照时间',
  MODIFY COLUMN `export_id` bigint NOT NULL COMMENT '导出任务ID',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `project_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '项目名称',
  MODIFY COLUMN `archive_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归档摘要',
  MODIFY COLUMN `customer_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '客户名称',
  MODIFY COLUMN `document_snapshot_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文档快照文本',
  MODIFY COLUMN `product_line` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '产品线',
  MODIFY COLUMN `recommended_tags` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '推荐标签',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `knowledge_case`
COMMENT='评分点知识案例表',
  MODIFY COLUMN `source_project_id` bigint NOT NULL COMMENT '来源项目ID',
  MODIFY COLUMN `source_project_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源项目名称',
  MODIFY COLUMN `scoring_point_title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '评分点标题',
  MODIFY COLUMN `requirement_raw` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '评分点原文',
  MODIFY COLUMN `response_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '应答内容',
  MODIFY COLUMN `reuse_count` int NOT NULL DEFAULT 0 COMMENT '复用次数',
  MODIFY COLUMN `status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态',
  MODIFY COLUMN `customer_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '客户类型',
  MODIFY COLUMN `project_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目类型',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `local_users`
COMMENT='本地用户表',
  MODIFY COLUMN `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户标识',
  MODIFY COLUMN `user_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户姓名',
  MODIFY COLUMN `email` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '邮箱',
  MODIFY COLUMN `mobile` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '手机号',
  MODIFY COLUMN `dept_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '部门ID',
  MODIFY COLUMN `position` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '岗位',
  MODIFY COLUMN `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态（启用/停用）',
  MODIFY COLUMN `source_updated_at` datetime NULL COMMENT '源系统更新时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `manufacturer_authorization`
  MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `mention`
COMMENT='评论@提及记录表',
  MODIFY COLUMN `notification_id` bigint NOT NULL COMMENT '通知ID',
  MODIFY COLUMN `mentioner_user_id` bigint NOT NULL COMMENT '提及人ID',
  MODIFY COLUMN `mentioned_user_id` bigint NOT NULL COMMENT '被提及人ID',
  MODIFY COLUMN `source_entity_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '来源对象类型',
  MODIFY COLUMN `source_entity_id` bigint NULL COMMENT '来源对象ID',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `notification`
COMMENT='站内通知表',
  MODIFY COLUMN `type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '通知类型',
  MODIFY COLUMN `source_entity_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '来源对象类型',
  MODIFY COLUMN `source_entity_id` bigint NULL COMMENT '来源对象ID',
  MODIFY COLUMN `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '通知标题',
  MODIFY COLUMN `body` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '通知正文',
  MODIFY COLUMN `payload_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '附加数据（JSON）',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_by` bigint NOT NULL COMMENT '创建人ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `notification_delivery_dlq`
COMMENT='通知投递死信队列表',
  MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '原投递任务ID',
  MODIFY COLUMN `notification_id` bigint NOT NULL COMMENT '通知ID',
  MODIFY COLUMN `recipient_user_id` bigint NOT NULL COMMENT '接收人ID',
  MODIFY COLUMN `business_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '业务幂等键',
  MODIFY COLUMN `reason_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '进入死信原因码',
  MODIFY COLUMN `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误信息',
  MODIFY COLUMN `payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '投递报文（JSON）',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `notification_delivery_task`
COMMENT='通知投递任务表',
  MODIFY COLUMN `notification_id` bigint NOT NULL COMMENT '通知ID',
  MODIFY COLUMN `recipient_user_id` bigint NOT NULL COMMENT '接收人ID',
  MODIFY COLUMN `event_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '事件类型',
  MODIFY COLUMN `business_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '业务幂等键',
  MODIFY COLUMN `payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '投递报文（JSON）',
  MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '投递状态',
  MODIFY COLUMN `attempt_count` int NOT NULL DEFAULT 0 COMMENT '已尝试次数',
  MODIFY COLUMN `next_retry_at` timestamp NULL COMMENT '下次重试时间',
  MODIFY COLUMN `last_error_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '最近错误码',
  MODIFY COLUMN `last_error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '最近错误信息',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `notification_outbound_log`
COMMENT='通知外发日志表',
  MODIFY COLUMN `notification_id` bigint NOT NULL COMMENT '通知ID',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '接收人ID',
  MODIFY COLUMN `channel` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '发送渠道',
  MODIFY COLUMN `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '发送状态',
  MODIFY COLUMN `skip_reason` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '跳过原因',
  MODIFY COLUMN `wecom_errcode` int NULL COMMENT '企微返回错误码',
  MODIFY COLUMN `wecom_errmsg` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '企微返回错误信息',
  MODIFY COLUMN `attempt_count` int NOT NULL DEFAULT 1 COMMENT '发送尝试次数',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `oa_process_bindings`
COMMENT='OA流程绑定配置表',
  MODIFY COLUMN `template_code` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '表单模板编码',
  MODIFY COLUMN `provider` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'OA提供方',
  MODIFY COLUMN `workflow_code` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'OA流程编码',
  MODIFY COLUMN `field_mapping_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '字段映射（JSON）',
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用（1=是 0=否）';

ALTER TABLE `oa_process_events`
COMMENT='OA流程事件表',
  MODIFY COLUMN `form_instance_id` bigint NULL COMMENT '表单实例ID',
  MODIFY COLUMN `oa_instance_id` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'OA流程实例ID',
  MODIFY COLUMN `event_id` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '事件唯一ID',
  MODIFY COLUMN `event_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '事件类型',
  MODIFY COLUMN `raw_payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '原始报文（JSON）',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `opportunity_prediction_reminder_log`
COMMENT='商机预测提醒日志表',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `predicted_bid_date` date NOT NULL COMMENT '预测投标日期',
  MODIFY COLUMN `purchaser_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '采购人名称',
  MODIFY COLUMN `notified_at` datetime NOT NULL COMMENT '提醒发送时间',
  MODIFY COLUMN `event_published_at` datetime NULL COMMENT '事件发布时间',
  MODIFY COLUMN `event_publish_claim_token` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '事件发布认领令牌',
  MODIFY COLUMN `event_publish_claimed_at` datetime NULL COMMENT '事件发布认领时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `organization_departments`
COMMENT='组织部门同步表',
  MODIFY COLUMN `department_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '部门编码',
  MODIFY COLUMN `department_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '部门名称',
  MODIFY COLUMN `parent_department_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '上级部门编码',
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用（1=是 0=否）',
  MODIFY COLUMN `external_dept_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '外部系统部门ID',
  MODIFY COLUMN `parent_external_dept_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '外部系统上级部门ID',
  MODIFY COLUMN `source_app` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源应用',
  MODIFY COLUMN `last_event_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '最近同步事件键',
  MODIFY COLUMN `last_synced_at` timestamp NULL COMMENT '最近同步时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `organization_event_logs`
COMMENT='组织事件日志表',
  MODIFY COLUMN `event_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '事件唯一键',
  MODIFY COLUMN `event_topic` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '事件主题',
  MODIFY COLUMN `source_app` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源应用',
  MODIFY COLUMN `trace_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '链路追踪ID',
  MODIFY COLUMN `payload_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '报文哈希（幂等判重）',
  MODIFY COLUMN `status` enum('PROCESSING','PROCESSED','DUPLICATE','REJECTED','FAILED','PENDING_RETRY','DEAD_LETTER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '处理状态（处理中/已处理/重复/拒绝/失败/待重试/死信）',
  MODIFY COLUMN `message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '处理说明',
  MODIFY COLUMN `received_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '接收时间',
  MODIFY COLUMN `processed_at` timestamp NULL COMMENT '处理完成时间',
  MODIFY COLUMN `upstream_event_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '上游事件键',
  MODIFY COLUMN `span_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '链路段ID',
  MODIFY COLUMN `parent_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '父事件ID',
  MODIFY COLUMN `event_time` timestamp NULL COMMENT '业务发生时间',
  MODIFY COLUMN `entity_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '事件对象类型',
  MODIFY COLUMN `external_user_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '外部用户ID',
  MODIFY COLUMN `external_dept_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '外部部门ID',
  MODIFY COLUMN `raw_payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '原始报文（JSON）',
  MODIFY COLUMN `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  MODIFY COLUMN `next_retry_at` timestamp NULL COMMENT '下次重试时间',
  MODIFY COLUMN `last_error_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '最近错误码',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `organization_sync_items`
COMMENT='组织同步处理条目表',
  MODIFY COLUMN `run_id` bigint NOT NULL COMMENT '同步运行ID',
  MODIFY COLUMN `entity_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '同步对象类型',
  MODIFY COLUMN `external_user_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '外部用户ID',
  MODIFY COLUMN `external_dept_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '外部部门ID',
  MODIFY COLUMN `internal_user_id` bigint NULL COMMENT '内部用户ID',
  MODIFY COLUMN `department_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '部门编码',
  MODIFY COLUMN `action_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作类型（新增/更新/停用）',
  MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '处理状态',
  MODIFY COLUMN `event_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '关联事件键',
  MODIFY COLUMN `error_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误码',
  MODIFY COLUMN `error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误信息',
  MODIFY COLUMN `raw_payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '原始报文（JSON）',
  MODIFY COLUMN `processed_at` timestamp NULL COMMENT '处理时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `organization_sync_runs`
COMMENT='组织同步运行记录表',
  MODIFY COLUMN `run_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '运行唯一键',
  MODIFY COLUMN `run_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '运行类型（全量/增量）',
  MODIFY COLUMN `source_app` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源应用',
  MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '运行状态',
  MODIFY COLUMN `triggered_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '触发人',
  MODIFY COLUMN `started_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  MODIFY COLUMN `finished_at` timestamp NULL COMMENT '结束时间',
  MODIFY COLUMN `total_count` int NOT NULL DEFAULT 0 COMMENT '总条数',
  MODIFY COLUMN `success_count` int NOT NULL DEFAULT 0 COMMENT '成功条数',
  MODIFY COLUMN `failed_count` int NOT NULL DEFAULT 0 COMMENT '失败条数',
  MODIFY COLUMN `last_error_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '最近错误码',
  MODIFY COLUMN `last_error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '最近错误信息',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `password_reset_tokens`
COMMENT='密码重置令牌表',
  MODIFY COLUMN `expires_at` datetime(6) NOT NULL COMMENT '过期时间',
  MODIFY COLUMN `used_at` datetime(6) NULL COMMENT '使用时间',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户ID',
  MODIFY COLUMN `token` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '重置令牌',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `performance_alert_config`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `performance_attachment`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `performance_export_task`
  MODIFY COLUMN `status` enum('PENDING','PROCESSING','COMPLETED','FAILED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务状态（待处理/处理中/已完成/失败）',
  MODIFY COLUMN `completed_at` datetime NULL COMMENT '完成时间',
  MODIFY COLUMN `failure_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '失败原因',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL COMMENT '创建时间';

ALTER TABLE `performance_record`
  MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `personnel`
  MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  MODIFY COLUMN `birth_date` date NULL COMMENT '出生日期',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '备注';

ALTER TABLE `personnel_alert_config`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `personnel_certificate`
  MODIFY COLUMN `title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '职称/头衔',
  MODIFY COLUMN `is_permanent` tinyint(1) NULL DEFAULT 0 COMMENT '是否长期有效（1=是 0=否）',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '备注';

ALTER TABLE `personnel_education`
  MODIFY COLUMN `is_highest_education_school` tinyint(1) NULL DEFAULT 0 COMMENT '是否最高学历院校（1=是 0=否）',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `personnel_import_task`
  MODIFY COLUMN `success_count` int NULL DEFAULT 0 COMMENT '成功条数',
  MODIFY COLUMN `failure_count` int NULL DEFAULT 0 COMMENT '失败条数',
  MODIFY COLUMN `completed_at` datetime NULL COMMENT '完成时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `personnel_operation_log`
COMMENT='人员操作日志表',
  MODIFY COLUMN `personnel_id` bigint NULL COMMENT '人员ID',
  MODIFY COLUMN `operator_id` bigint NOT NULL COMMENT '操作人ID',
  MODIFY COLUMN `operator_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '操作人姓名',
  MODIFY COLUMN `operation_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作类型',
  MODIFY COLUMN `change_details` json NULL COMMENT '变更明细（JSON）',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `platform_account_import_task`
COMMENT='平台账号导入任务表',
  MODIFY COLUMN `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING' COMMENT '任务状态（待处理/处理中/已完成/失败）',
  MODIFY COLUMN `total_rows` int NULL DEFAULT 0 COMMENT '总行数',
  MODIFY COLUMN `valid_rows` int NULL DEFAULT 0 COMMENT '有效行数',
  MODIFY COLUMN `invalid_rows` int NULL DEFAULT 0 COMMENT '无效行数',
  MODIFY COLUMN `imported_rows` int NULL DEFAULT 0 COMMENT '新增导入行数',
  MODIFY COLUMN `updated_rows` int NULL DEFAULT 0 COMMENT '更新行数',
  MODIFY COLUMN `error_details` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误明细',
  MODIFY COLUMN `source_filename` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '来源文件名',
  MODIFY COLUMN `created_by_username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '创建人用户名',
  MODIFY COLUMN `completed_at` datetime NULL COMMENT '完成时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `platform_accounts`
COMMENT='投标平台账号表',
  MODIFY COLUMN `return_count` int NULL COMMENT '归还次数',
  MODIFY COLUMN `borrowed_at` datetime(6) NULL COMMENT '借出时间',
  MODIFY COLUMN `borrowed_by` bigint NULL COMMENT '借用人ID',
  MODIFY COLUMN `due_at` datetime(6) NULL COMMENT '应归还时间',
  MODIFY COLUMN `username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '登录用户名',
  MODIFY COLUMN `account_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '账号名称',
  MODIFY COLUMN `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '登录密码（加密存储）',
  MODIFY COLUMN `status` enum('AVAILABLE','PENDING_APPROVAL','IN_USE','MAINTENANCE','DISABLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '账号状态（可用/待审批/使用中/维护中/停用）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间';

ALTER TABLE `project_archive`
COMMENT='项目归档表',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `project_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '项目名称',
  MODIFY COLUMN `archive_status` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归档状态',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `project_closure`
COMMENT='项目结项表',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `closed_at` timestamp NULL COMMENT '结项时间',
  MODIFY COLUMN `closed_by` bigint NULL COMMENT '结项操作人ID',
  MODIFY COLUMN `deposit_returned` tinyint(1) NOT NULL DEFAULT 0 COMMENT '保证金是否已退回（1=是 0=否）',
  MODIFY COLUMN `deposit_return_evidence_id` bigint NULL COMMENT '保证金退回凭证ID',
  MODIFY COLUMN `archive_location` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '归档位置',
  MODIFY COLUMN `stage_locked` tinyint(1) NOT NULL DEFAULT 0 COMMENT '阶段是否锁定（1=是 0=否）',
  MODIFY COLUMN `notes` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '结项说明',
  MODIFY COLUMN `review_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DRAFT' COMMENT '结项审核状态',
  MODIFY COLUMN `reviewed_by` bigint NULL COMMENT '审核人ID',
  MODIFY COLUMN `reviewed_at` datetime NULL COMMENT '审核时间',
  MODIFY COLUMN `project_summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目总结',
  MODIFY COLUMN `deposit_return_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'NA' COMMENT '保证金退回状态',
  MODIFY COLUMN `deposit_return_date` datetime NULL COMMENT '保证金退回时间',
  MODIFY COLUMN `transfer_amount` decimal(18,2) NULL COMMENT '转出金额（元）',
  MODIFY COLUMN `returned_amount` decimal(18,2) NULL COMMENT '已退回金额（元）',
  MODIFY COLUMN `rejection_reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '驳回原因',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID';

ALTER TABLE `project_documents`
COMMENT='项目文档表',
  MODIFY COLUMN `linked_entity_id` bigint NULL COMMENT '关联业务对象ID',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `uploader_id` bigint NULL COMMENT '上传人ID',
  MODIFY COLUMN `file_url` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文件地址',
  MODIFY COLUMN `document_category` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文档类别',
  MODIFY COLUMN `file_size` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文件大小',
  MODIFY COLUMN `file_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文件类型',
  MODIFY COLUMN `linked_entity_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '关联业务对象类型',
  MODIFY COLUMN `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文档名称',
  MODIFY COLUMN `uploader_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '上传人姓名',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `project_evaluation`
COMMENT='项目评标阶段表',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `sub_stage` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '评标子阶段',
  MODIFY COLUMN `evaluation_started_at` timestamp NULL COMMENT '评标开始时间',
  MODIFY COLUMN `board_received_at` timestamp NULL COMMENT '评标委员会接收时间',
  MODIFY COLUMN `announced_at` timestamp NULL COMMENT '中标公示时间',
  MODIFY COLUMN `notes` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '评审说明',
  MODIFY COLUMN `evaluation_files_json` json NULL COMMENT '评审附件（JSON）',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID';

ALTER TABLE `project_group_members`
COMMENT='项目组成员表',
  MODIFY COLUMN `project_group_id` bigint NOT NULL COMMENT '项目组ID',
  MODIFY COLUMN `user_id` bigint NULL COMMENT '成员用户ID';

ALTER TABLE `project_group_projects`
COMMENT='项目组项目关联表',
  MODIFY COLUMN `project_group_id` bigint NOT NULL COMMENT '项目组ID',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '项目ID';

ALTER TABLE `project_group_role_access`
COMMENT='项目组角色权限表',
  MODIFY COLUMN `project_group_id` bigint NOT NULL COMMENT '项目组ID',
  MODIFY COLUMN `role_code` enum('ADMIN','MANAGER','STAFF') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '角色编码（ADMIN/MANAGER/STAFF）';

ALTER TABLE `project_groups`
COMMENT='项目组表',
  MODIFY COLUMN `manager_user_id` bigint NULL COMMENT '项目经理用户ID',
  MODIFY COLUMN `group_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '项目组编码',
  MODIFY COLUMN `group_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '项目组名称',
  MODIFY COLUMN `visibility` enum('ALL','MEMBERS','MANAGER','CUSTOM') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '可见范围（全部/成员/经理/自定义）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '更新时间';

ALTER TABLE `project_initiation_details`
COMMENT='项目立项明细表',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `bid_open_time` timestamp NULL COMMENT '开标时间',
  MODIFY COLUMN `bid_month` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '投标月份',
  MODIFY COLUMN `expected_bidders` int NULL COMMENT '预计投标人家数',
  MODIFY COLUMN `customer_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '客户类型',
  MODIFY COLUMN `contract_period_months` int NULL COMMENT '合同期限（月）',
  MODIFY COLUMN `competitors` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '已知竞争对手',
  MODIFY COLUMN `department_snapshot` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '立项部门快照',
  MODIFY COLUMN `deposit_amount` decimal(20,2) NULL COMMENT '保证金金额（元）',
  MODIFY COLUMN `deposit_payment_method` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '保证金缴纳方式',
  MODIFY COLUMN `locked` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否锁定（1=是 0=否）',
  MODIFY COLUMN `owner_unit` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '招标单位（业主单位）',
  MODIFY COLUMN `project_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目类型',
  MODIFY COLUMN `owner_user_id` bigint NULL COMMENT '立项负责人ID',
  MODIFY COLUMN `review_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DRAFT' COMMENT '立项审核状态',
  MODIFY COLUMN `rejection_reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '驳回原因',
  MODIFY COLUMN `reviewed_by` bigint NULL COMMENT '审核人ID',
  MODIFY COLUMN `reviewed_at` datetime NULL COMMENT '审核时间',
  MODIFY COLUMN `customer_info_json` json NULL COMMENT '客户信息（JSON）',
  MODIFY COLUMN `tender_document_id` bigint NULL COMMENT '标讯文档ID',
  MODIFY COLUMN `ai_risk_level` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'AI评估风险等级',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID';

ALTER TABLE `project_lead_assignment`
COMMENT='项目负责人分配表',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `primary_lead_user_id` bigint NULL COMMENT '主负责人用户ID',
  MODIFY COLUMN `secondary_lead_user_id` bigint NULL COMMENT '副负责人用户ID',
  MODIFY COLUMN `assigned_at` timestamp NULL COMMENT '分配时间',
  MODIFY COLUMN `assigned_by` bigint NULL COMMENT '分配人ID',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `project_quality_checks`
COMMENT='文档质量检查表',
  MODIFY COLUMN `empty` bit(1) NOT NULL COMMENT '文档是否为空（1=是 0=否）',
  MODIFY COLUMN `checked_at` datetime(6) NOT NULL COMMENT '检查时间',
  MODIFY COLUMN `document_id` bigint NULL COMMENT '检查文档ID',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `document_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文档名称',
  MODIFY COLUMN `status` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '检查结果状态',
  MODIFY COLUMN `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '检查结果摘要',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `project_quality_issues`
COMMENT='文档质量问题表',
  MODIFY COLUMN `adopted` bit(1) NOT NULL COMMENT '是否已采纳（1=是 0=否）',
  MODIFY COLUMN `ignored` bit(1) NOT NULL COMMENT '是否已忽略（1=是 0=否）',
  MODIFY COLUMN `check_id` bigint NOT NULL COMMENT '质量检查ID',
  MODIFY COLUMN `location_label` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '问题位置标识',
  MODIFY COLUMN `original_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '问题原文',
  MODIFY COLUMN `suggestion_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '修改建议',
  MODIFY COLUMN `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '问题类型',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `project_reminders`
COMMENT='项目提醒表',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `remind_at` datetime(6) NOT NULL COMMENT '提醒时间',
  MODIFY COLUMN `created_by_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人姓名',
  MODIFY COLUMN `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '提醒内容',
  MODIFY COLUMN `recipient` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提醒接收人',
  MODIFY COLUMN `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提醒标题',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `project_result`
COMMENT='项目中标结果表',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `result_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '结果类型（中标/未中标）',
  MODIFY COLUMN `award_amount` decimal(20,2) NULL COMMENT '中标金额（元）',
  MODIFY COLUMN `contract_start_date` date NULL COMMENT '合同开始日期',
  MODIFY COLUMN `contract_end_date` date NULL COMMENT '合同结束日期',
  MODIFY COLUMN `evidence_attachment_id` bigint NULL COMMENT '证据附件ID',
  MODIFY COLUMN `registered_at` timestamp NULL COMMENT '登记时间',
  MODIFY COLUMN `summary` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '结果摘要',
  MODIFY COLUMN `evidence_doc_ids` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '证据文档ID列表',
  MODIFY COLUMN `evidence_file_ids` json NULL COMMENT '证据文件ID列表（JSON）',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID';

ALTER TABLE `project_result_competitor`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `project_retrospective`
COMMENT='项目复盘表',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '复盘总结',
  MODIFY COLUMN `win_factors` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '赢单因素',
  MODIFY COLUMN `loss_reasons` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '失标原因',
  MODIFY COLUMN `competitor_notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '竞争对手分析备注',
  MODIFY COLUMN `improvement_actions` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '改进措施',
  MODIFY COLUMN `reviewed_by` bigint NULL COMMENT '复盘审核人ID',
  MODIFY COLUMN `reviewed_at` timestamp NULL COMMENT '审核时间',
  MODIFY COLUMN `review_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT '复盘审核状态',
  MODIFY COLUMN `result_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '结果类型（中标/未中标）',
  MODIFY COLUMN `review_comment` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '审核意见',
  MODIFY COLUMN `process_issues` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '流程问题',
  MODIFY COLUMN `report_attachment_id` bigint NULL COMMENT '复盘报告附件ID',
  MODIFY COLUMN `meeting_time` timestamp NULL COMMENT '复盘会议时间',
  MODIFY COLUMN `meeting_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '会议类型',
  MODIFY COLUMN `participants` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '参会人员',
  MODIFY COLUMN `attachment_id` bigint NULL COMMENT '附件ID',
  MODIFY COLUMN `meeting_format` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '会议形式（线上/线下）',
  MODIFY COLUMN `meeting_participants` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '会议参会人员',
  MODIFY COLUMN `loss_reason_flags` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '失标原因标签',
  MODIFY COLUMN `post_win_improvements` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '赢单后改进事项',
  MODIFY COLUMN `process_problems` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '流程存在问题',
  MODIFY COLUMN `post_loss_measures` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '失标后改进措施',
  MODIFY COLUMN `report_file_ids` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '复盘报告文件ID列表',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID';

ALTER TABLE `project_score_drafts`
COMMENT='评分项草稿表',
  MODIFY COLUMN `source_page` int NULL COMMENT '来源页码',
  MODIFY COLUMN `source_row_index` int NOT NULL COMMENT '来源表格行索引',
  MODIFY COLUMN `source_table_index` int NOT NULL COMMENT '来源表格索引',
  MODIFY COLUMN `assignee_id` bigint NULL COMMENT '认领人ID',
  MODIFY COLUMN `due_date` datetime(6) NULL COMMENT '完成期限',
  MODIFY COLUMN `generated_task_id` bigint NULL COMMENT '生成的任务ID',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '评分项类别',
  MODIFY COLUMN `task_action` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务动作',
  MODIFY COLUMN `assignee_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '认领人姓名',
  MODIFY COLUMN `score_value_text` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '分值文本',
  MODIFY COLUMN `generated_task_description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '生成任务描述',
  MODIFY COLUMN `generated_task_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '生成任务标题',
  MODIFY COLUMN `score_item_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '评分项标题',
  MODIFY COLUMN `score_rule_text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '评分规则文本',
  MODIFY COLUMN `skip_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '跳过原因',
  MODIFY COLUMN `source_file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '来源文件名',
  MODIFY COLUMN `suggested_deliverables` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '建议交付物',
  MODIFY COLUMN `status` enum('DRAFT','READY','SKIPPED','GENERATED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态（草稿/就绪/已跳过/已生成任务）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '更新时间';

ALTER TABLE `project_score_previews`
COMMENT='中标概率预评表',
  MODIFY COLUMN `budget` decimal(19,2) NULL COMMENT '项目预算（元）',
  MODIFY COLUMN `win_score` int NOT NULL COMMENT '中标评分',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '项目ID',
  MODIFY COLUMN `tender_id` bigint NULL COMMENT '标讯ID',
  MODIFY COLUMN `win_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '中标等级',
  MODIFY COLUMN `industry` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '行业',
  MODIFY COLUMN `payload_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '预评结果（JSON）',
  MODIFY COLUMN `project_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目名称',
  MODIFY COLUMN `tags_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '标签（JSON）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间';

ALTER TABLE `project_share_links`
COMMENT='项目分享链接表',
  MODIFY COLUMN `expires_at` datetime(6) NULL COMMENT '过期时间',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `created_by_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '创建人姓名',
  MODIFY COLUMN `token` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分享令牌',
  MODIFY COLUMN `url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '分享链接',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `project_team_members`
COMMENT='项目团队成员表',
  MODIFY COLUMN `member_id` bigint NULL COMMENT '成员用户ID',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID';

ALTER TABLE `qualification_alert_config`
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用（1=是 0=否）',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `qualification_attachments`
COMMENT='资质附件表',
  MODIFY COLUMN `qualification_id` bigint NOT NULL COMMENT '资质ID',
  MODIFY COLUMN `uploaded_at` datetime(6) NOT NULL COMMENT '上传时间',
  MODIFY COLUMN `file_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件地址',
  MODIFY COLUMN `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件名',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `qualification_loan_records`
COMMENT='资质借出记录表',
  MODIFY COLUMN `expected_return_date` date NULL COMMENT '预计归还日期',
  MODIFY COLUMN `borrowed_at` datetime(6) NOT NULL COMMENT '借出时间',
  MODIFY COLUMN `qualification_id` bigint NOT NULL COMMENT '资质ID',
  MODIFY COLUMN `returned_at` datetime(6) NULL COMMENT '归还时间',
  MODIFY COLUMN `project_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '借用项目ID',
  MODIFY COLUMN `borrower` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '借用人',
  MODIFY COLUMN `department` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '借用部门',
  MODIFY COLUMN `return_remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '归还备注',
  MODIFY COLUMN `purpose` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '借用用途',
  MODIFY COLUMN `status` enum('AVAILABLE','BORROWED','RETURNED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '借用状态（可用/借出/已归还）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '更新时间',
  MODIFY COLUMN `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '备注';

ALTER TABLE `qualifications`
COMMENT='资质证书表',
  MODIFY COLUMN `expiry_date` date NULL COMMENT '有效期至',
  MODIFY COLUMN `issue_date` date NULL COMMENT '发证日期',
  MODIFY COLUMN `file_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '证书文件地址',
  MODIFY COLUMN `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '资质名称',
  MODIFY COLUMN `type` enum('CONSTRUCTION','DESIGN','SERVICE','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '资质类型（施工/设计/服务/其他）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间';

ALTER TABLE `refresh_sessions`
COMMENT='刷新令牌会话表',
  MODIFY COLUMN `expires_at` datetime(6) NOT NULL COMMENT '过期时间',
  MODIFY COLUMN `last_seen_at` datetime(6) NULL COMMENT '最近活跃时间',
  MODIFY COLUMN `revoked_at` datetime(6) NULL COMMENT '吊销时间',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户ID',
  MODIFY COLUMN `ip_address` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '登录IP',
  MODIFY COLUMN `token_hash` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '令牌哈希',
  MODIFY COLUMN `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '浏览器UA',
  MODIFY COLUMN `device_info` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '设备信息',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '更新时间';

ALTER TABLE `roi_analyses`
COMMENT='投资回报分析表',
  MODIFY COLUMN `estimated_cost` decimal(19,2) NULL COMMENT '预估成本（元）',
  MODIFY COLUMN `estimated_profit` decimal(19,2) NULL COMMENT '预估利润（元）',
  MODIFY COLUMN `estimated_revenue` decimal(19,2) NULL COMMENT '预估收入（元）',
  MODIFY COLUMN `payback_period_months` int NULL COMMENT '回收周期（月）',
  MODIFY COLUMN `roi_percentage` decimal(10,2) NULL COMMENT 'ROI百分比',
  MODIFY COLUMN `analysis_date` datetime(6) NULL COMMENT '分析时间',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `assumptions` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '分析假设',
  MODIFY COLUMN `risk_factors` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '风险因素',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `roles`
COMMENT='角色表',
  MODIFY COLUMN `enabled` bit(1) NOT NULL COMMENT '是否启用（1=是 0=否）',
  MODIFY COLUMN `is_system` bit(1) NOT NULL COMMENT '是否系统内置（1=是 0=否）',
  MODIFY COLUMN `data_scope` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '数据权限范围',
  MODIFY COLUMN `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色编码',
  MODIFY COLUMN `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色名称',
  MODIFY COLUMN `allowed_depts` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '可见部门列表',
  MODIFY COLUMN `allowed_projects` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '可见项目列表',
  MODIFY COLUMN `menu_permissions` varchar(4000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '菜单权限列表',
  MODIFY COLUMN `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '角色描述',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间';

ALTER TABLE `score_analyses`
COMMENT='投标评分分析表',
  MODIFY COLUMN `is_ai_generated` bit(1) NULL COMMENT '是否AI生成（1=是 0=否）',
  MODIFY COLUMN `overall_score` int NULL COMMENT '总分',
  MODIFY COLUMN `analysis_date` datetime(6) NULL COMMENT '分析时间',
  MODIFY COLUMN `analyst_id` bigint NULL COMMENT '分析人ID',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '项目ID',
  MODIFY COLUMN `tender_id` bigint NULL COMMENT '标讯ID',
  MODIFY COLUMN `summary` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '分析摘要',
  MODIFY COLUMN `risk_level` enum('LOW','MEDIUM','HIGH') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '风险等级（低/中/高）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间';

ALTER TABLE `score_item`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NOT NULL COMMENT '更新时间';

ALTER TABLE `score_parse_evidence`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `score_parse_fact`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `score_parse_review`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `score_parse_task`
  MODIFY COLUMN `started_at` datetime NULL COMMENT '开始时间',
  MODIFY COLUMN `completed_at` datetime NULL COMMENT '完成时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NOT NULL COMMENT '更新时间';

ALTER TABLE `score_result`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NOT NULL COMMENT '更新时间';

ALTER TABLE `subscription`
COMMENT='订阅关系表',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '订阅人ID',
  MODIFY COLUMN `target_entity_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '订阅对象类型',
  MODIFY COLUMN `target_entity_id` bigint NOT NULL COMMENT '订阅对象ID',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `sys_crm_customer_permission`
COMMENT='CRM客户数据权限表',
  MODIFY COLUMN `customer_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'CRM客户ID',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '授权用户ID',
  MODIFY COLUMN `permission_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '权限类型',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `sys_dashboard_layout`
COMMENT='仪表盘布局表',
  MODIFY COLUMN `code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '布局编码',
  MODIFY COLUMN `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '布局名称',
  MODIFY COLUMN `role_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '适用角色编码',
  MODIFY COLUMN `layout_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '布局配置（JSON）',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `sys_dashboard_widget`
COMMENT='仪表盘组件定义表',
  MODIFY COLUMN `code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '组件编码',
  MODIFY COLUMN `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '组件名称',
  MODIFY COLUMN `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '组件描述',
  MODIFY COLUMN `default_props_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '默认属性（JSON）',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `sys_project_member`
COMMENT='项目成员权限表',
  MODIFY COLUMN `project_id` bigint NOT NULL COMMENT '项目ID',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '成员用户ID',
  MODIFY COLUMN `member_role` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '项目内角色',
  MODIFY COLUMN `permission_level` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '权限级别',
  MODIFY COLUMN `is_inherited` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否继承自项目组（1=是 0=否）',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `system_settings`
COMMENT='系统配置表',
  MODIFY COLUMN `config_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '配置键',
  MODIFY COLUMN `payload_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '配置值（JSON）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL COMMENT '更新时间';

ALTER TABLE `task_comment`
COMMENT='任务评论表',
  MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '任务ID',
  MODIFY COLUMN `author_user_id` bigint NOT NULL COMMENT '评论人ID',
  MODIFY COLUMN `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '评论内容',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `task_deliverables`
COMMENT='任务交付物表',
  MODIFY COLUMN `version` int NOT NULL COMMENT '版本号',
  MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '任务ID',
  MODIFY COLUMN `uploader_id` bigint NULL COMMENT '上传人ID',
  MODIFY COLUMN `file_size` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文件大小',
  MODIFY COLUMN `file_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文件类型',
  MODIFY COLUMN `uploader_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '上传人姓名',
  MODIFY COLUMN `storage_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '存储路径',
  MODIFY COLUMN `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '交付物名称',
  MODIFY COLUMN `storage_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '存储键',
  MODIFY COLUMN `deliverable_type` enum('DOCUMENT','QUALIFICATION','TECHNICAL','QUOTATION','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '交付物类型（文档/资质/技术/报价/其他）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `task_extended_field`
COMMENT='任务扩展字段定义表',
  MODIFY COLUMN `key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字段键',
  MODIFY COLUMN `label` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字段标签',
  MODIFY COLUMN `field_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '字段类型',
  MODIFY COLUMN `required` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否必填（1=是 0=否）',
  MODIFY COLUMN `placeholder` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '输入提示',
  MODIFY COLUMN `options_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '选项列表（JSON）',
  MODIFY COLUMN `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序序号',
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用（1=是 0=否）',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `task_history`
COMMENT='任务历史快照表',
  MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '任务ID',
  MODIFY COLUMN `actor_user_id` bigint NULL COMMENT '操作人ID',
  MODIFY COLUMN `action` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作动作',
  MODIFY COLUMN `snapshot_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务快照（JSON）',
  MODIFY COLUMN `archived_at` timestamp NULL COMMENT '归档时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `task_status_dict`
COMMENT='任务状态字典表',
  MODIFY COLUMN `code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态编码',
  MODIFY COLUMN `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态名称',
  MODIFY COLUMN `category` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '状态分类',
  MODIFY COLUMN `color` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '#909399' COMMENT '显示颜色',
  MODIFY COLUMN `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序序号',
  MODIFY COLUMN `is_initial` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否初始状态（1=是 0=否）',
  MODIFY COLUMN `is_terminal` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否终态（1=是 0=否）',
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用（1=是 0=否）',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  MODIFY COLUMN `updated_by` bigint NULL COMMENT '更新人ID';

ALTER TABLE `template_download_records`
COMMENT='模板下载记录表',
  MODIFY COLUMN `downloaded_at` datetime(6) NOT NULL COMMENT '下载时间',
  MODIFY COLUMN `downloaded_by` bigint NULL COMMENT '下载人ID',
  MODIFY COLUMN `template_id` bigint NOT NULL COMMENT '模板ID',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `template_tags`
COMMENT='模板标签表',
  MODIFY COLUMN `template_id` bigint NOT NULL COMMENT '模板ID',
  MODIFY COLUMN `tag` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '标签';

ALTER TABLE `template_use_records`
COMMENT='模板使用记录表',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '使用项目ID',
  MODIFY COLUMN `template_id` bigint NOT NULL COMMENT '模板ID',
  MODIFY COLUMN `used_at` datetime(6) NOT NULL COMMENT '使用时间',
  MODIFY COLUMN `used_by` bigint NULL COMMENT '使用人ID',
  MODIFY COLUMN `applied_options` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '应用选项',
  MODIFY COLUMN `doc_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文档类型',
  MODIFY COLUMN `document_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '生成文档名称',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `template_versions`
COMMENT='模板版本表',
  MODIFY COLUMN `template_id` bigint NOT NULL COMMENT '模板ID',
  MODIFY COLUMN `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '版本描述',
  MODIFY COLUMN `snapshot_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '快照名称',
  MODIFY COLUMN `version` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '版本号',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `templates`
COMMENT='文档模板表',
  MODIFY COLUMN `description` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '模板描述',
  MODIFY COLUMN `current_version` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '当前版本号',
  MODIFY COLUMN `document_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文档类型',
  MODIFY COLUMN `file_size` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文件大小',
  MODIFY COLUMN `file_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文件地址',
  MODIFY COLUMN `industry` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '适用行业',
  MODIFY COLUMN `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模板名称',
  MODIFY COLUMN `product_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '适用产品类型',
  MODIFY COLUMN `category` enum('TECHNICAL','COMMERCIAL','LEGAL','QUALIFICATION','CONTRACT','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模板类别（技术/商务/法务/资质/合同/其他）',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `updated_at` datetime(6) NULL COMMENT '更新时间';

ALTER TABLE `tender_assignment_records`
COMMENT='标讯分配记录表',
  MODIFY COLUMN `assigned_at` datetime(6) NOT NULL COMMENT '分配时间',
  MODIFY COLUMN `assigned_by_id` bigint NULL COMMENT '分配人ID',
  MODIFY COLUMN `assignee_id` bigint NOT NULL COMMENT '被分配人ID',
  MODIFY COLUMN `tender_id` bigint NOT NULL COMMENT '标讯ID',
  MODIFY COLUMN `assigned_by_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '分配人姓名',
  MODIFY COLUMN `assignee_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '被分配人姓名',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `remark` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '备注';

ALTER TABLE `tender_attachments`
COMMENT='标讯附件表',
  MODIFY COLUMN `tender_id` bigint NOT NULL COMMENT '标讯ID',
  MODIFY COLUMN `file_name` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件名',
  MODIFY COLUMN `file_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文件类型',
  MODIFY COLUMN `file_url` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件地址',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `tender_evaluation_basics`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `tender_evaluation_customer_info`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `tender_evaluations`
COMMENT='标讯评估表',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NOT NULL COMMENT '更新时间';

ALTER TABLE `tender_event_logs`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `tender_favorite`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `tender_file`
COMMENT='标讯文件表',
  MODIFY COLUMN `upload_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '上传批次ID',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '上传人ID',
  MODIFY COLUMN `file_path` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '存储路径',
  MODIFY COLUMN `file_sha256` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文件SHA256',
  MODIFY COLUMN `file_size` bigint NULL COMMENT '文件大小（字节）',
  MODIFY COLUMN `page_count` int NULL COMMENT '页数',
  MODIFY COLUMN `upload_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'INITIATED' COMMENT '上传处理状态',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间';

ALTER TABLE `tender_import_task`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `tender_reminder_logs`
COMMENT='标讯提醒日志表',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `tender_reminder_settings`
COMMENT='标讯提醒配置表',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `tender_task`
COMMENT='标讯文件处理任务表',
  MODIFY COLUMN `file_id` bigint NOT NULL COMMENT '标讯文件ID',
  MODIFY COLUMN `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务状态（待处理/处理中/已完成/失败）',
  MODIFY COLUMN `priority` int NOT NULL DEFAULT 5 COMMENT '优先级',
  MODIFY COLUMN `attempts` int NOT NULL DEFAULT 0 COMMENT '已尝试次数',
  MODIFY COLUMN `available_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '可执行时间',
  MODIFY COLUMN `locked_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '锁定者（执行节点标识）',
  MODIFY COLUMN `locked_at` datetime(6) NULL COMMENT '锁定时间',
  MODIFY COLUMN `error_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误码',
  MODIFY COLUMN `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误信息',
  MODIFY COLUMN `started_at` datetime(6) NULL COMMENT '开始时间',
  MODIFY COLUMN `finished_at` datetime(6) NULL COMMENT '完成时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间';

ALTER TABLE `tender_task_dlq`
COMMENT='标讯处理死信队列表',
  MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '原任务ID',
  MODIFY COLUMN `file_id` bigint NOT NULL COMMENT '标讯文件ID',
  MODIFY COLUMN `failed_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '失败时间',
  MODIFY COLUMN `error_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误码',
  MODIFY COLUMN `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误信息',
  MODIFY COLUMN `payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '任务报文（JSON）',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间';

ALTER TABLE `tenders`
  MODIFY COLUMN `abandonment_reason` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '废标/放弃原因';

ALTER TABLE `user_notification`
COMMENT='用户通知已读表',
  MODIFY COLUMN `notification_id` bigint NOT NULL COMMENT '通知ID',
  MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '接收人ID',
  MODIFY COLUMN `read_at` timestamp NULL COMMENT '已读时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `warehouse`
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_by` bigint NULL COMMENT '创建人ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `warehouse_attachment`
  MODIFY COLUMN `warehouse_id` bigint NOT NULL COMMENT '素材ID',
  MODIFY COLUMN `original_filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '原始文件名',
  MODIFY COLUMN `stored_filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '存储文件名',
  MODIFY COLUMN `file_size` bigint NOT NULL COMMENT '文件大小（字节）',
  MODIFY COLUMN `content_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件类型（MIME）',
  MODIFY COLUMN `uploaded_by` bigint NOT NULL COMMENT '上传人ID',
  MODIFY COLUMN `uploaded_at` datetime NOT NULL COMMENT '上传时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `warehouse_export_task`
  MODIFY COLUMN `status` enum('PENDING','PROCESSING','COMPLETED','FAILED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务状态（待处理/处理中/已完成/失败）',
  MODIFY COLUMN `filter_snapshot` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '筛选条件快照（JSON）',
  MODIFY COLUMN `total_count` int NULL COMMENT '导出总条数',
  MODIFY COLUMN `stored_file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '文件存储路径',
  MODIFY COLUMN `download_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '下载地址',
  MODIFY COLUMN `expires_at` datetime NULL COMMENT '下载链接过期时间',
  MODIFY COLUMN `completed_at` datetime NULL COMMENT '完成时间',
  MODIFY COLUMN `failure_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '失败原因',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_by` bigint NOT NULL COMMENT '创建人ID',
  MODIFY COLUMN `created_at` datetime NOT NULL COMMENT '创建时间';

ALTER TABLE `warehouse_import_task`
  MODIFY COLUMN `status` enum('PENDING','VALIDATING','VALIDATED','IMPORTING','COMPLETED','FAILED') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '任务状态（待处理/校验中/已校验/导入中/已完成/失败）',
  MODIFY COLUMN `total_rows` int NULL COMMENT '总行数',
  MODIFY COLUMN `valid_rows` int NULL COMMENT '有效行数',
  MODIFY COLUMN `invalid_rows` int NULL COMMENT '无效行数',
  MODIFY COLUMN `imported_rows` int NULL COMMENT '导入行数',
  MODIFY COLUMN `error_details` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误明细',
  MODIFY COLUMN `source_file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '来源文件路径',
  MODIFY COLUMN `source_filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '来源文件名',
  MODIFY COLUMN `created_by_username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '创建人用户名',
  MODIFY COLUMN `completed_at` datetime NULL COMMENT '完成时间',
  MODIFY COLUMN `failure_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '失败原因',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_by` bigint NOT NULL COMMENT '创建人ID',
  MODIFY COLUMN `created_at` datetime NOT NULL COMMENT '创建时间';

ALTER TABLE `warehouse_operation_log`
  MODIFY COLUMN `warehouse_id` bigint NOT NULL COMMENT '素材ID',
  MODIFY COLUMN `operator_id` bigint NULL COMMENT '操作人ID',
  MODIFY COLUMN `operator_username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '操作人用户名',
  MODIFY COLUMN `action_type` enum('CREATE','EDIT','CLOSE','RESTORE','ATTACH_UPLOAD','ATTACH_DELETE') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '操作类型（新建/编辑/关闭/恢复/附件上传/附件删除）',
  MODIFY COLUMN `field_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '变更字段名',
  MODIFY COLUMN `old_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '变更前值',
  MODIFY COLUMN `new_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '变更后值',
  MODIFY COLUMN `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '操作描述',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL COMMENT '创建时间';

ALTER TABLE `webhook_delivery_dlq`
COMMENT='Webhook投递死信队列表',
  MODIFY COLUMN `task_id` bigint NOT NULL COMMENT '原投递任务ID',
  MODIFY COLUMN `tender_id` bigint NOT NULL COMMENT '标讯ID',
  MODIFY COLUMN `business_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '业务幂等键',
  MODIFY COLUMN `reason_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '进入死信原因码',
  MODIFY COLUMN `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误信息',
  MODIFY COLUMN `payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '投递报文（JSON）',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `webhook_delivery_logs`
COMMENT='Webhook投递日志表',
  MODIFY COLUMN `tender_id` bigint NOT NULL COMMENT '标讯ID',
  MODIFY COLUMN `target_url` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目标地址',
  MODIFY COLUMN `status_code` int NULL COMMENT 'HTTP状态码',
  MODIFY COLUMN `response_body` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '响应内容',
  MODIFY COLUMN `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING' COMMENT '投递状态（待投递/成功/失败）',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

ALTER TABLE `webhook_delivery_tasks`
COMMENT='Webhook投递任务表',
  MODIFY COLUMN `tender_id` bigint NOT NULL COMMENT '标讯ID',
  MODIFY COLUMN `external_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '外部系统事件ID',
  MODIFY COLUMN `target_url` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '目标地址',
  MODIFY COLUMN `event_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '事件类型',
  MODIFY COLUMN `business_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '业务幂等键',
  MODIFY COLUMN `payload` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '投递报文（JSON）',
  MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING' COMMENT '投递状态（待投递/成功/失败）',
  MODIFY COLUMN `attempt_count` int NOT NULL DEFAULT 0 COMMENT '已尝试次数',
  MODIFY COLUMN `next_retry_at` datetime NULL COMMENT '下次重试时间',
  MODIFY COLUMN `last_error_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '最近错误码',
  MODIFY COLUMN `last_error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '最近错误信息',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `wecom_integration`
COMMENT='企业微信集成配置表',
  MODIFY COLUMN `corp_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '企业ID',
  MODIFY COLUMN `agent_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '应用AgentId',
  MODIFY COLUMN `encrypted_secret` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '加密的应用密钥',
  MODIFY COLUMN `sso_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用扫码登录（1=是 0=否）',
  MODIFY COLUMN `message_enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用消息推送（1=是 0=否）',
  MODIFY COLUMN `notify_user_ids` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '通知接收人列表',
  MODIFY COLUMN `id` bigint NOT NULL COMMENT '主键ID',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  MODIFY COLUMN `updated_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '更新人ID';

ALTER TABLE `workflow_form_instances`
COMMENT='工作流表单实例表',
  MODIFY COLUMN `business_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '业务类型',
  MODIFY COLUMN `template_code` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '表单模板编码',
  MODIFY COLUMN `project_id` bigint NULL COMMENT '项目ID',
  MODIFY COLUMN `applicant_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '申请人姓名',
  MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '实例状态',
  MODIFY COLUMN `form_data_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '表单数据（JSON）',
  MODIFY COLUMN `oa_instance_id` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'OA流程实例ID',
  MODIFY COLUMN `business_applied` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已回写业务（1=是 0=否）',
  MODIFY COLUMN `business_apply_error` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '业务回写失败原因',
  MODIFY COLUMN `oa_operator_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'OA操作人姓名',
  MODIFY COLUMN `oa_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'OA审批意见',
  MODIFY COLUMN `template_version` int NULL COMMENT '模板版本号',
  MODIFY COLUMN `schema_snapshot_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '表单结构快照（JSON）',
  MODIFY COLUMN `oa_binding_snapshot_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'OA绑定配置快照（JSON）',
  MODIFY COLUMN `oa_payload_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT 'OA提交报文（JSON）',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `workflow_form_template_drafts`
COMMENT='工作流表单模板草稿表',
  MODIFY COLUMN `template_code` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '表单模板编码',
  MODIFY COLUMN `name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模板名称',
  MODIFY COLUMN `business_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '业务类型',
  MODIFY COLUMN `draft_schema_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '草稿表单结构（JSON）',
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用（1=是 0=否）',
  MODIFY COLUMN `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DRAFT' COMMENT '草稿状态',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  MODIFY COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

ALTER TABLE `workflow_form_template_versions`
COMMENT='工作流表单模板版本表',
  MODIFY COLUMN `template_code` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '表单模板编码',
  MODIFY COLUMN `name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模板名称',
  MODIFY COLUMN `business_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '业务类型',
  MODIFY COLUMN `version` int NOT NULL COMMENT '版本号',
  MODIFY COLUMN `schema_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '表单结构（JSON）',
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用（1=是 0=否）',
  MODIFY COLUMN `published_by` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '发布人',
  MODIFY COLUMN `published_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID';

ALTER TABLE `workflow_form_templates`
COMMENT='工作流表单模板表',
  MODIFY COLUMN `template_code` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '表单模板编码',
  MODIFY COLUMN `name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模板名称',
  MODIFY COLUMN `business_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '业务类型',
  MODIFY COLUMN `version` int NOT NULL COMMENT '当前版本号',
  MODIFY COLUMN `schema_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '表单结构（JSON）',
  MODIFY COLUMN `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用（1=是 0=否）';
