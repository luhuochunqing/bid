-- V109: 修正 project_retrospective.review_status 默认值
-- 与 ProjectRetrospective.ReviewStatus 枚举（PENDING_REVIEW / APPROVED / REJECTED）保持一致。
-- 历史 V99 写的是 'PENDING'（旧 enum），与代码不一致；本迁移把默认改为 'PENDING_REVIEW'。
-- 数据修复：把已有的 'PENDING' 行迁移到 'PENDING_REVIEW'。

UPDATE project_retrospective
SET review_status = 'PENDING_REVIEW'
WHERE review_status = 'PENDING';

ALTER TABLE project_retrospective
    MODIFY review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW';
