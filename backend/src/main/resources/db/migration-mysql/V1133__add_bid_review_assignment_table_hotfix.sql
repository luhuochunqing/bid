-- V1133: 新增标书审核人分配表 bid_review_assignment（P0 热修复）
--
-- 背景：CO-483 + CO-484 标书审核多人化 + 驳回后审核人清空（PR !1637）。
-- 该 PR 把建表迁移误放在历史目录 db/migration/V123__add_bid_review_assignment.sql，
-- 且版本号 V123 已被历史基线 "tender reminder settings" 占用，导致迁移从未被执行，
-- 运行时查询 bid_review_assignment 表报 "Table doesn't exist" → /api/projects/{id}/stage 500。
--
-- 修复策略：
-- 1. 本迁移作为 V1133 放在活跃目录 migration-mysql/，作为 P0 热修复
-- 2. 原始 migration/V123__add_bid_review_assignment.sql 在同 PR 中删除（避免后续混淆）
-- 3. SQL 内容幂等（CREATE TABLE IF NOT EXISTS + INSERT WHERE NOT EXISTS），可安全重复执行
--
-- 设计说明：
-- - 每个审核人的独立决策记录（一对多：一条 bid_document_review 对应多条 assignment）
-- - bid_document_review 表保留作审核记录主表（存整体 status/submittedBy）
-- - 同一审核记录同一审核人不重复（uk_review_reviewer）
-- - decision: APPROVED / REJECTED / NULL(未决)
-- - comment: 驳回原因（驳回时填）
-- - decided_at: 决策时间
--
-- 历史数据迁移：将现有单审核人记录（bid_document_review.reviewer_id）补一条 assignment 记录
-- 保留 reviewer_id 字段作"主审核人"冗余，便于兼容查询；真正的多人决策走 assignment 表

CREATE TABLE IF NOT EXISTS bid_review_assignment (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    review_id   BIGINT       NOT NULL COMMENT '关联 bid_document_review.id',
    reviewer_id BIGINT       NOT NULL COMMENT '审核人用户 ID',
    decision    VARCHAR(20)          DEFAULT NULL COMMENT '决策：APPROVED / REJECTED / NULL(未决)',
    comment     VARCHAR(1000)        DEFAULT NULL COMMENT '驳回原因（驳回时填）',
    decided_at  DATETIME             DEFAULT NULL COMMENT '决策时间',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_review_reviewer (review_id, reviewer_id),
    INDEX idx_review_id (review_id),
    INDEX idx_reviewer_id (reviewer_id),
    INDEX idx_decision (decision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标书审核人分配表（多人审核）';

-- 历史数据迁移：将现有单审核人记录补一条 assignment 记录
-- WHERE NOT EXISTS 保证幂等，重复执行不会插入重复记录
INSERT INTO bid_review_assignment (review_id, reviewer_id, decision, comment, decided_at, created_at)
SELECT r.id, r.reviewer_id,
       CASE WHEN r.status = 'APPROVED' THEN 'APPROVED'
            WHEN r.status = 'REJECTED' THEN 'REJECTED'
            ELSE NULL END AS decision,
       r.reject_reason AS comment,
       r.reviewed_at AS decided_at,
       r.created_at
FROM bid_document_review r
WHERE NOT EXISTS (
    SELECT 1 FROM bid_review_assignment a WHERE a.review_id = r.id AND a.reviewer_id = r.reviewer_id
);
