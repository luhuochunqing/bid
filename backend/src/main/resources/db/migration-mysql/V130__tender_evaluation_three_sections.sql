-- V130: 标讯评估表三段式重构
-- 对应 PRD §4.2.5 评估表三部分：基础信息(8字段) + 客户信息(13×14) + 投标负责人建议

-- 1. tender_evaluations 表增加审核相关字段
ALTER TABLE tender_evaluations
  ADD COLUMN requires_review   BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '是否需要重新审核',
  ADD COLUMN last_reviewed_by  BIGINT       DEFAULT NULL COMMENT '最后审核人ID',
  ADD COLUMN last_reviewed_at  DATETIME     DEFAULT NULL COMMENT '最后审核时间',
  ADD COLUMN evaluation_round  INT          NOT NULL DEFAULT 1 COMMENT '评估轮次';

-- 2. 基础信息表（8个字段）
CREATE TABLE tender_evaluation_basics (
  id                        BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
  evaluation_id             BIGINT          NOT NULL COMMENT '关联评估表ID',
  shortlisted_count         INT             DEFAULT NULL COMMENT '入围家数',
  annual_procurement_amount DECIMAL(15,2)   DEFAULT NULL COMMENT '年度电商采购金额(万元)',
  unfavorable_items         TEXT            DEFAULT NULL COMMENT '招标文件不利项',
  risk_assessment           TEXT            DEFAULT NULL COMMENT '风险预判(举例说明)',
  risk_mitigation_plan      TEXT            DEFAULT NULL COMMENT '针对风险的兜底方案',
  process_knowledge         TEXT            DEFAULT NULL COMMENT '项目经理是否了解评标全流程',
  support_notes             TEXT            DEFAULT NULL COMMENT '需要的支持及其他关键信息备注',
  project_plan_gap          TEXT            DEFAULT NULL COMMENT '项目计划GAP',
  UNIQUE KEY uk_evaluation (evaluation_id),
  CONSTRAINT fk_eval_basics FOREIGN KEY (evaluation_id) REFERENCES tender_evaluations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估表-基础信息';

-- 3. 客户信息表（13角色 × 14维度 EAV）
CREATE TABLE tender_evaluation_customer_info (
  id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  evaluation_id BIGINT        NOT NULL COMMENT '关联评估表ID',
  role_key      VARCHAR(32)   NOT NULL COMMENT '角色枚举键',
  info_key      VARCHAR(32)   NOT NULL COMMENT '信息维度枚举键',
  cell_value    VARCHAR(500)  DEFAULT NULL COMMENT '单元格值',
  value_type    ENUM('TEXT','DROPDOWN') NOT NULL DEFAULT 'TEXT' COMMENT '值类型',
  UNIQUE KEY uk_eval_role_info (evaluation_id, role_key, info_key),
  CONSTRAINT fk_eval_cust_info FOREIGN KEY (evaluation_id) REFERENCES tender_evaluations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估表-客户信息(EAV)';

-- 4. 投标负责人建议表
CREATE TABLE tender_evaluation_recommendation (
  evaluation_id BIGINT  NOT NULL PRIMARY KEY COMMENT '关联评估表ID',
  should_bid    BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否建议投标',
  reason        TEXT    DEFAULT NULL COMMENT '理由(不建议时必填)',
  CONSTRAINT fk_eval_recommend FOREIGN KEY (evaluation_id) REFERENCES tender_evaluations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估表-投标负责人建议';
