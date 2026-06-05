-- ================================================================
-- V140: Dynamic Form Engine - 表单自定义适配器基础设施
-- 功能：新增 form_definition_registry / form_field_visibility /
--       form_field_condition 三张核心表，为全系统动态表单
--       自定义引擎提供数据存储基础。
-- 说明：向后兼容——新增表独立于现有 workflow_form_* 表，
--       不影响 OA 审批表单功能。
-- ================================================================

-- ----------------------------------------------------------
-- 表1：form_definition_registry
-- 表单定义注册表（业务域元注册，支持多租户 scope）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS form_definition_registry (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    scope           VARCHAR(64) NOT NULL COMMENT '业务域标识，如 tender.entry / project.basic',
    scope_label     VARCHAR(128) NOT NULL COMMENT '业务域中文显示名',
    version         INT NOT NULL DEFAULT 1 COMMENT '当前版本号',
    schema_json     JSON NOT NULL COMMENT '字段定义 JSON，结构见 FormSchema',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    org_id          BIGINT DEFAULT NULL COMMENT 'NULL=全局模板，非NULL=租户级模板',
    created_by      VARCHAR(64) NOT NULL COMMENT '创建人',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_scope_org (scope, org_id),
    INDEX idx_scope (scope),
    INDEX idx_org_id (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表单定义注册表';

-- ----------------------------------------------------------
-- 表2：form_field_visibility
-- 字段可见性规则（支持角色级/组织级字段控制）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS form_field_visibility (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    definition_id   BIGINT NOT NULL COMMENT '关联 form_definition_registry.id',
    field_key       VARCHAR(64) NOT NULL COMMENT '字段 key',
    role_pattern    VARCHAR(64) DEFAULT NULL COMMENT '角色匹配，支持通配符如 bid_specialist，NULL=所有角色',
    org_id          BIGINT DEFAULT NULL COMMENT 'NULL=所有组织',
    visible         BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否可见',
    readonly        BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否只读',
    hidden          BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否隐藏（比 visible 优先级更高）',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    CONSTRAINT fk_visibility_def FOREIGN KEY (definition_id) REFERENCES form_definition_registry(id) ON DELETE CASCADE,
    UNIQUE KEY uk_def_field_role (definition_id, field_key, role_pattern),
    INDEX idx_definition_id (definition_id),
    INDEX idx_role_pattern (role_pattern)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段可见性规则表';

-- ----------------------------------------------------------
-- 表3：form_field_condition
-- 字段条件逻辑（支持字段间依赖配置）
-- ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS form_field_condition (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    definition_id   BIGINT NOT NULL COMMENT '关联 form_definition_registry.id',
    source_field    VARCHAR(64) NOT NULL COMMENT '触发字段 key',
    operator        VARCHAR(32) NOT NULL COMMENT '操作符：eq / neq / in / not_in / contains / gt / gte / lt / lte',
    target_value    VARCHAR(255) DEFAULT NULL COMMENT '目标值，多值用逗号分隔',
    action          VARCHAR(32) NOT NULL COMMENT '动作：show / hide / require / skip / readonly',
    target_field    VARCHAR(64) DEFAULT NULL COMMENT '受影响字段 key，NULL=作用于 source_field 自身',
    display_order   INT NOT NULL DEFAULT 0 COMMENT '顺序，用于同源多条件场景',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    CONSTRAINT fk_condition_def FOREIGN KEY (definition_id) REFERENCES form_definition_registry(id) ON DELETE CASCADE,
    INDEX idx_definition_id (definition_id),
    INDEX idx_source_field (source_field)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段条件逻辑表';

-- ----------------------------------------------------------
-- 种子数据：预注册系统核心业务域（供管理员直接使用）
-- 使用 INSERT IGNORE 确保幂等（重复执行不报错），兼容 H2 test profile
-- ----------------------------------------------------------
INSERT IGNORE INTO form_definition_registry(id, scope, scope_label, version, schema_json, enabled, org_id, created_by)
VALUES
    (1, 'tender.entry', '标讯手工录入', 1,
     '{"fields":[{"key":"title","label":"标讯标题","type":"TEXT","required":true,"placeholder":"请输入标讯标题"},{"key":"source","label":"信息来源","type":"SELECT","required":false,"options":[{"label":"招标公告","value":"bidding"},{"label":"比选公告","value":"selection"},{"label":"竞争性谈判","value":"negotiation"},{"label":"单一来源","value":"single_source"},{"label":"其他","value":"other"}]},{"key":"budget","label":"预算金额","type":"CURRENCY","required":false,"validation":{"min":0,"precision":2}},{"key":"region","label":"项目地区","type":"ADDRESS","required":false},{"key":"publishDate","label":"发布日期","type":"DATE","required":false},{"key":"deadline","label":"截止日期","type":"DATE","required":true},{"key":"contactName","label":"联系人","type":"TEXT","required":false},{"key":"contactPhone","label":"联系电话","type":"PHONE","required":false},{"key":"description","label":"标讯描述","type":"TEXTAREA","required":false,"rows":4}]}',
     TRUE, NULL, 'system'),
    (2, 'project.basic', '项目基本信息', 1,
     '{"fields":[{"key":"name","label":"项目名称","type":"TEXT","required":true},{"key":"managerId","label":"项目经理","type":"PERSON","required":true},{"key":"teamMembers","label":"团队成员","type":"PERSON","required":false},{"key":"startDate","label":"开始日期","type":"DATE","required":false},{"key":"endDate","label":"结束日期","type":"DATE","required":false},{"key":"budget","label":"项目预算","type":"CURRENCY","required":false},{"key":"industry","label":"所属行业","type":"SELECT","required":false,"options":[{"label":"政府","value":"government"},{"label":"央企","value":"soe"},{"label":"民营","value":"private"}]},{"key":"description","label":"项目描述","type":"TEXTAREA","required":false,"rows":4}]}',
     TRUE, NULL, 'system'),
    (3, 'resource.expense', '费用申请', 1,
     '{"fields":[{"key":"projectId","label":"关联项目","type":"PROJECT","required":true},{"key":"category","label":"费用类别","type":"SELECT","required":true,"options":[{"label":"差旅费","value":"travel"},{"label":"办公费","value":"office"},{"label":"咨询费","value":"consulting"},{"label":"其他","value":"other"}]},{"key":"amount","label":"金额","type":"CURRENCY","required":true,"validation":{"min":0.01}},{"key":"expenseDate","label":"费用日期","type":"DATE","required":true},{"key":"description","label":"费用说明","type":"TEXTAREA","required":false,"rows":3}]}',
     TRUE, NULL, 'system'),
    (4, 'knowledge.case', '案例建档', 1,
     '{"fields":[{"key":"title","label":"案例标题","type":"TEXT","required":true},{"key":"industry","label":"所属行业","type":"SELECT","required":false,"options":[{"label":"政府","value":"government"},{"label":"央企","value":"soe"},{"label":"民营","value":"private"}]},{"key":"amount","label":"合同金额","type":"CURRENCY","required":false},{"key":"projectDate","label":"完成日期","type":"DATE","required":false},{"key":"description","label":"案例描述","type":"TEXTAREA","required":false,"rows":4},{"key":"tags","label":"标签","type":"TEXT","required":false,"placeholder":"多个标签用逗号分隔"}]}',
     TRUE, NULL, 'system');
