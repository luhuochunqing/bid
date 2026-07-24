-- V1177: 补全核心业务表字段 COMMENT 注释
-- 目的:为库内字段添加 COMMENT,便于排障、数据加工和二次开发
-- 范围:P0 阶段——5 张核心业务表(accounts、users、tenders、projects、tasks)中无 COMMENT 的列
-- 策略:
--   1. 同表所有 MODIFY COLUMN 合并为一条 ALTER TABLE,每张表只重建 1 次(减少锁表风险)
--   2. MODIFY COLUMN 严格保留"当前真相"的 TYPE/NOT NULL/DEFAULT 属性(B73 之后多次迁移已演进,不能回退到 B73 状态)
--   3. 只给 COLUMN_COMMENT = '' 的列添加 COMMENT,已有 COMMENT 的列不覆盖
--   4. 同时补全表级 COMMENT
-- 当前真相来源:xiyu_bid_main 数据库(已应用 B73 + V74~V1176 全部迁移)
-- 风险控制:
--   - tenders.status 当前为 7 值 ENUM(V117 扩展后),不可回退到 B73 的 4 值 ENUM
--   - projects.status 当前为 8 值 ENUM(V1052 对齐 Java 后),不可回退到 B73 的 6 值 ENUM
--   - users.role 当前为 2 值 ENUM(V1091 移除 STAFF 后),不可回退到 B73 的 3 值 ENUM
--   - tasks.status 当前为 varchar(32)(非 ENUM,业务允许自定义状态字符串)
-- 回滚:U1177__backfill_business_table_comments.sql 将本次新增的 COMMENT 清空
-- 后续:P1(B73 其他 84 张表)、P2(V74~V146 增量表 60 张)单独任务跟进
-- 版本号说明:原 V1169 因与 main 上 V1169__add_contract_info_to_project_result.sql 撞号,重命名为 V1177

-- ============================================================
-- 1. accounts 客户/供应商表 (10 列)
-- ============================================================
ALTER TABLE accounts COMMENT = '客户/供应商/合作伙伴账户表';

ALTER TABLE accounts
  MODIFY COLUMN id bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN created_at datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN updated_at datetime(6) NULL COMMENT '更新时间',
  MODIFY COLUMN name varchar(200) NOT NULL COMMENT '账户名称(唯一)',
  MODIFY COLUMN industry varchar(100) NULL COMMENT '所属行业',
  MODIFY COLUMN region varchar(100) NULL COMMENT '所属区域',
  MODIFY COLUMN contact_info varchar(500) NULL COMMENT '联系人信息',
  MODIFY COLUMN credit_level enum('A','B','C','D') NOT NULL COMMENT '信用等级:A/B/C/D',
  MODIFY COLUMN type enum('CLIENT','SUPPLIER','PARTNER','GOVERNMENT','OTHER') NOT NULL COMMENT '账户类型:CLIENT客户/SUPPLIER供应商/PARTNER合作伙伴/GOVERNMENT政府/OTHER其他';

-- ============================================================
-- 2. users 用户表 (18 列)
-- ============================================================
ALTER TABLE users COMMENT = '系统用户表(本地账户,OSS 同步用户也写入此表)';

ALTER TABLE users
  MODIFY COLUMN id bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN created_at datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN updated_at datetime(6) NULL COMMENT '更新时间',
  MODIFY COLUMN username varchar(255) NOT NULL COMMENT '登录用户名(唯一)',
  MODIFY COLUMN password varchar(255) NOT NULL COMMENT 'BCrypt加密密码',
  MODIFY COLUMN email varchar(255) NOT NULL COMMENT '邮箱地址(唯一)',
  MODIFY COLUMN phone varchar(32) NULL COMMENT '手机号码',
  MODIFY COLUMN full_name varchar(255) NOT NULL COMMENT '用户真实姓名',
  MODIFY COLUMN role enum('ADMIN','MANAGER') NOT NULL COMMENT '历史角色(已弃用,权限以 role_id 为准)',
  MODIFY COLUMN role_id bigint NULL COMMENT '关联角色ID(role_profile)',
  MODIFY COLUMN enabled bit(1) NOT NULL COMMENT '是否启用:1是 0否',
  MODIFY COLUMN email_verified bit(1) NOT NULL COMMENT '邮箱是否已验证:1是 0否',
  MODIFY COLUMN department_code varchar(100) NULL COMMENT '部门编码',
  MODIFY COLUMN department_name varchar(100) NULL COMMENT '部门名称',
  MODIFY COLUMN wecom_user_id varchar(64) NULL COMMENT '企微用户ID(唯一)',
  MODIFY COLUMN external_org_user_id varchar(128) NULL COMMENT '外部组织架构用户ID(OSS 同步)',
  MODIFY COLUMN external_org_source_app varchar(100) NULL COMMENT '外部组织架构来源应用',
  MODIFY COLUMN last_org_event_key varchar(128) NULL COMMENT '最近组织架构同步事件key',
  MODIFY COLUMN last_org_synced_at timestamp NULL COMMENT '最近组织架构同步时间',
  MODIFY COLUMN employee_number varchar(32) NULL COMMENT '工号';

-- ============================================================
-- 3. tenders 标讯表 (42 列)
-- 注:本表部分列已有 COMMENT(V74~V1168 增量迁移添加),此处只补无 COMMENT 的列
-- ============================================================
ALTER TABLE tenders COMMENT = '标讯信息表(来源:外部平台抓取/CRM商机推送/人工录入/批量导入)';

ALTER TABLE tenders
  MODIFY COLUMN id bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN created_at datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN updated_at datetime(6) NULL COMMENT '更新时间',
  MODIFY COLUMN title varchar(500) NOT NULL COMMENT '标讯标题',
  MODIFY COLUMN source varchar(200) NULL COMMENT '标讯来源平台(原始字符串)',
  MODIFY COLUMN external_id varchar(100) NULL COMMENT '外部标讯ID(用于去重)',
  MODIFY COLUMN original_url varchar(1000) NULL COMMENT '标讯原始链接',
  MODIFY COLUMN status enum('PENDING_ASSIGNMENT','TRACKING','EVALUATED','BIDDING','WON','LOST','ABANDONED') NOT NULL COMMENT '标讯状态:待分配/跟踪中/已评估/投标中/中标/未中标/已放弃',
  MODIFY COLUMN risk_level enum('LOW','MEDIUM','HIGH') NULL COMMENT '风险等级:LOW低/MEDIUM中/HIGH高',
  MODIFY COLUMN ai_score int NULL COMMENT 'AI评分(0-100)',
  MODIFY COLUMN budget decimal(19,2) NULL COMMENT '项目预算金额',
  MODIFY COLUMN deadline datetime(6) NULL COMMENT '投标截止时间',
  MODIFY COLUMN region varchar(100) NULL COMMENT '所属区域(原始字符串)',
  MODIFY COLUMN industry varchar(100) NULL COMMENT '所属行业(原始字符串)',
  MODIFY COLUMN purchaser_name varchar(255) NULL COMMENT '采购人名称',
  MODIFY COLUMN purchaser_hash varchar(64) NULL COMMENT '采购人名称哈希(用于去重和聚合)',
  MODIFY COLUMN publish_date date NULL COMMENT '公告发布日期',
  MODIFY COLUMN contact_name varchar(100) NULL COMMENT '采购联系人姓名',
  MODIFY COLUMN contact_phone varchar(50) NULL COMMENT '采购联系人手机号',
  MODIFY COLUMN description text NULL COMMENT '标讯详情描述',
  MODIFY COLUMN tags text NULL COMMENT '标签(逗号分隔)',
  MODIFY COLUMN source_normalized varchar(200) NULL COMMENT '来源平台归一化值',
  MODIFY COLUMN region_normalized varchar(100) NULL COMMENT '区域归一化值',
  MODIFY COLUMN industry_normalized varchar(100) NULL COMMENT '行业归一化值',
  MODIFY COLUMN purchaser_hash_normalized varchar(64) NULL COMMENT '采购人哈希归一化值',
  MODIFY COLUMN purchaser_name_normalized varchar(255) NULL COMMENT '采购人名称归一化值',
  MODIFY COLUMN search_text_normalized text NULL COMMENT '全文检索归一化文本',
  MODIFY COLUMN source_document_name varchar(255) NULL COMMENT '招标文件名称',
  MODIFY COLUMN source_document_file_type varchar(100) NULL COMMENT '招标文件类型',
  MODIFY COLUMN source_document_file_url varchar(1000) NULL COMMENT '招标文件URL',
  MODIFY COLUMN tender_agency varchar(255) NULL COMMENT '招标代理机构',
  MODIFY COLUMN bid_opening_time datetime(6) NULL COMMENT '开标时间',
  MODIFY COLUMN customer_type varchar(100) NULL COMMENT '客户类型',
  MODIFY COLUMN priority varchar(10) NULL COMMENT '优先级',
  MODIFY COLUMN source_type enum('EXTERNAL_PLATFORM','CRM_OPPORTUNITY','MANUAL_SINGLE','BULK_IMPORT') NOT NULL DEFAULT 'MANUAL_SINGLE' COMMENT '标讯来源类型:外部平台/CRM商机/人工录入/批量导入',
  MODIFY COLUMN crm_opportunity_name varchar(200) NULL COMMENT 'CRM商机名称',
  MODIFY COLUMN project_id bigint NULL COMMENT '关联投标项目ID';

-- ============================================================
-- 4. projects 投标项目表 (36 列)
-- 注:initiated_at/evaluating_at/closed_at 已有 COMMENT(V1044 添加),不覆盖
-- ============================================================
ALTER TABLE projects COMMENT = '投标项目表(标讯转化后的投标业务实体)';

ALTER TABLE projects
  MODIFY COLUMN id bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN created_at datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN updated_at datetime(6) NULL COMMENT '更新时间',
  MODIFY COLUMN name varchar(500) NOT NULL COMMENT '项目名称',
  MODIFY COLUMN status enum('PENDING_INITIATION','INITIATED','BIDDING','EVALUATING','WON','LOST','FAILED','ABANDONED') NOT NULL COMMENT '项目状态:待立项/已立项/投标中/评标中/中标/未中标/失败/已放弃',
  MODIFY COLUMN stage varchar(32) NOT NULL DEFAULT 'INITIATED' COMMENT '项目阶段(用于阶段时间戳触发)',
  MODIFY COLUMN tender_id bigint NOT NULL COMMENT '关联标讯ID',
  MODIFY COLUMN manager_id bigint NOT NULL COMMENT '项目经理ID(关联 users.id)',
  MODIFY COLUMN customer varchar(255) NULL COMMENT '客户名称',
  MODIFY COLUMN customer_type varchar(100) NULL COMMENT '客户类型',
  MODIFY COLUMN customer_manager varchar(100) NULL COMMENT '客户经理姓名',
  MODIFY COLUMN customer_manager_id varchar(100) NULL COMMENT '客户经理ID',
  MODIFY COLUMN region varchar(100) NULL COMMENT '所属区域',
  MODIFY COLUMN industry varchar(50) NULL COMMENT '所属行业',
  MODIFY COLUMN budget decimal(14,2) NULL COMMENT '项目预算金额',
  MODIFY COLUMN deadline date NULL COMMENT '项目截止日期',
  MODIFY COLUMN start_date datetime(6) NULL COMMENT '项目开始时间',
  MODIFY COLUMN end_date datetime(6) NULL COMMENT '项目结束时间',
  MODIFY COLUMN platform varchar(255) NULL COMMENT '投标平台',
  MODIFY COLUMN description text NULL COMMENT '项目描述',
  MODIFY COLUMN remark text NULL COMMENT '备注',
  MODIFY COLUMN tags_json varchar(1000) NULL COMMENT '项目标签JSON数组',
  MODIFY COLUMN ai_analysis_json text NULL COMMENT 'AI分析结果JSON',
  MODIFY COLUMN competitor_analysis_json text NULL COMMENT '竞争对手分析JSON',
  MODIFY COLUMN tasks_json text NULL COMMENT '任务列表JSON(快照)',
  MODIFY COLUMN source_module varchar(100) NULL COMMENT '来源模块',
  MODIFY COLUMN source_customer_id varchar(100) NULL COMMENT '来源客户ID',
  MODIFY COLUMN source_customer varchar(255) NULL COMMENT '来源客户名称',
  MODIFY COLUMN source_opportunity_id varchar(100) NULL COMMENT '来源商机ID',
  MODIFY COLUMN source_reasoning_summary text NULL COMMENT '来源推理摘要';

-- ============================================================
-- 5. tasks 任务表 (18 列)
-- 注:content/extended_fields_json/last_reminded_at/last_overdue_reminded_at 已有 COMMENT,不覆盖
-- ============================================================
ALTER TABLE tasks COMMENT = '项目任务表(投标项目下的具体任务)';

ALTER TABLE tasks
  MODIFY COLUMN id bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  MODIFY COLUMN created_at datetime(6) NOT NULL COMMENT '创建时间',
  MODIFY COLUMN updated_at datetime(6) NULL COMMENT '更新时间',
  MODIFY COLUMN title varchar(255) NOT NULL COMMENT '任务标题',
  MODIFY COLUMN description text NULL COMMENT '任务描述',
  MODIFY COLUMN project_id bigint NOT NULL COMMENT '关联项目ID',
  MODIFY COLUMN assignee_id bigint NULL COMMENT '指派人ID(关联 users.id)',
  MODIFY COLUMN assignee_role_code varchar(64) NULL COMMENT '指派人角色码',
  MODIFY COLUMN assignee_role_name varchar(100) NULL COMMENT '指派人角色名称',
  MODIFY COLUMN assignee_dept_code varchar(100) NULL COMMENT '指派人部门编码',
  MODIFY COLUMN assignee_dept_name varchar(100) NULL COMMENT '指派人部门名称',
  MODIFY COLUMN status varchar(32) NOT NULL COMMENT '任务状态(常见值:TODO/IN_PROGRESS/REVIEW/COMPLETED/CANCELLED)',
  MODIFY COLUMN priority enum('LOW','MEDIUM','HIGH','URGENT') NOT NULL COMMENT '优先级:LOW低/MEDIUM中/HIGH高/URGENT紧急',
  MODIFY COLUMN due_date datetime(6) NULL COMMENT '任务截止时间',
  MODIFY COLUMN review_comment text NULL COMMENT '评审意见',
  MODIFY COLUMN completion_notes text NULL COMMENT '完成备注',
  MODIFY COLUMN created_by varchar(255) NULL COMMENT '创建人(存用户名)';
