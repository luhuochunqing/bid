-- V118__tender_evaluation_and_review.sql
-- 标讯评估与审核功能
-- 1. tenders 表添加 abandonment_reason 字段
-- 2. 创建 tender_evaluations 表

-- 1. tenders 表添加 abandonment_reason 字段
ALTER TABLE tenders
ADD COLUMN abandonment_reason VARCHAR(1000) AFTER status;

-- 2. 创建标讯评估表
CREATE TABLE tender_evaluations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tender_id BIGINT NOT NULL UNIQUE COMMENT '关联的标讯ID',
    evaluation_content TEXT COMMENT '评估内容',
    estimated_budget DECIMAL(19,2) COMMENT '预估预算',
    risk_assessment VARCHAR(500) COMMENT '风险评估',
    notes VARCHAR(2000) COMMENT '备注',
    evaluator_id BIGINT COMMENT '评估人ID',
    evaluator_name VARCHAR(100) COMMENT '评估人姓名',
    evaluated_at DATETIME COMMENT '评估时间',
    review_status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '审核状态: PENDING/APPROVED/REJECTED',
    reviewer_id BIGINT COMMENT '审核人ID',
    reviewer_name VARCHAR(100) COMMENT '审核人姓名',
    reviewed_at DATETIME COMMENT '审核时间',
    review_comment VARCHAR(500) COMMENT '审核意见',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_tender_eval_tender_id (tender_id),
    INDEX idx_tender_eval_evaluator_id (evaluator_id),
    INDEX idx_tender_eval_review_status (review_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
