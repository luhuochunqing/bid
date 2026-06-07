-- V115: 新增标书审核记录表 bid_document_review
--
-- 背景：PRD §3.2.3-§3.2.4 标书审核流程持久化需求。
-- 投标负责人提交审核后，审核人可查看标书并做出通过/驳回决策。
--
-- 设计说明：
-- - 每项目一条记录（project_id 唯一约束），项目维度审核
-- - 状态流转：null → REVIEWING → APPROVED / REJECTED → REVIEWING（驳回后可重新提交）
-- - 驳回时记录 reject_reason，审核通过/驳回时记录 reviewed_at
--
-- PR 备注：#标书审核流程

CREATE TABLE IF NOT EXISTS bid_document_review (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    project_id      BIGINT       NOT NULL COMMENT '项目 ID（唯一约束）',
    reviewer_id     BIGINT       NOT NULL COMMENT '审核人用户 ID',
    submitted_by    BIGINT       NOT NULL COMMENT '提交审核的用户 ID（投标负责人或辅助人员）',
    status          VARCHAR(20)  NOT NULL DEFAULT 'REVIEWING' COMMENT '审核状态：REVIEWING / APPROVED / REJECTED',
    reject_reason   VARCHAR(1000)         DEFAULT NULL COMMENT '驳回原因',
    reviewed_at     DATETIME              DEFAULT NULL COMMENT '审核时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_project (project_id),
    INDEX idx_reviewer (reviewer_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标书审核记录表';
