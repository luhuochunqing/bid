-- V131__tender_source_config.sql
-- 标讯源配置表（单例模式，id 始终为 1）
-- US3: 投标管理员统一管理标讯源配置，团队共享一份生效配置
-- FR-015 ~ FR-018

CREATE TABLE tender_source_configs (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '始终为 1（单例）',
    platforms_json JSON COMMENT '已选平台列表',
    api_endpoint VARCHAR(500) COMMENT 'API 端点',
    api_key_encrypted VARCHAR(512) COMMENT 'API 密钥（AES 加密存储）',
    keywords VARCHAR(500) COMMENT '关键字，逗号分隔',
    regions_json JSON COMMENT '地区列表',
    business_units_json JSON COMMENT '业务单元列表',
    budget_min DECIMAL(15,2) NOT NULL DEFAULT 0 COMMENT '预算下限（万元）',
    budget_max DECIMAL(15,2) NOT NULL DEFAULT 1000 COMMENT '预算上限（万元）',
    auto_sync BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否自动同步',
    sync_interval_minutes INT NOT NULL DEFAULT 1440 COMMENT '自动同步间隔（分钟）',
    auto_dedupe BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否自动去重',
    updated_by VARCHAR(32) COMMENT '最后修改人用户 ID',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标讯源配置表';

-- 插入默认配置（单例行）
INSERT INTO tender_source_configs (id, platforms_json, keywords, regions_json, budget_min, budget_max, auto_sync, auto_dedupe)
VALUES (1, '["中国政府采购网"]', '', '[]', 0, 1000, FALSE, TRUE);
