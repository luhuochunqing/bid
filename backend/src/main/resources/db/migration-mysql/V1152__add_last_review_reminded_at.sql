-- V1152: CO-532 资质证书审核提醒 - 新增 last_review_reminded_at 字段
-- 用于证书审核提醒的 24h 去重，与到期提醒的 last_reminded_at 独立，避免互相影响。
-- 精度对齐 last_reminded_at（datetime(6) 微秒精度）。
ALTER TABLE business_qualifications ADD COLUMN last_review_reminded_at DATETIME(6) NULL;
