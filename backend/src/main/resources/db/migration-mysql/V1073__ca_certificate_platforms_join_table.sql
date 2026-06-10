-- V1073__ca_certificate_platforms_join_table.sql
-- IJTHTV 修复：CA 关联投标平台由「逗号分隔字符串」改为「多对多关联表」
-- 蓝图 V1.0 第五部分「新增CA」要求：关联投标平台 → 支持多选

CREATE TABLE ca_certificate_platforms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ca_certificate_id BIGINT NOT NULL,
    platform_account_id BIGINT NOT NULL,
    CONSTRAINT fk_cap_ca_certificate
        FOREIGN KEY (ca_certificate_id) REFERENCES ca_certificates(id) ON DELETE CASCADE,
    CONSTRAINT fk_cap_platform_account
        FOREIGN KEY (platform_account_id) REFERENCES platform_accounts(id) ON DELETE CASCADE,
    CONSTRAINT uk_cap_ca_platform UNIQUE (ca_certificate_id, platform_account_id),
    INDEX idx_cap_ca_id (ca_certificate_id),
    INDEX idx_cap_platform_id (platform_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 历史数据回填：将 ca_certificates.platform_ids 的逗号分隔 ID 解析到关联表
-- 跳过无法解析为数字的脏数据，保留为新业务的人工修正
INSERT INTO ca_certificate_platforms (ca_certificate_id, platform_account_id)
SELECT
    cc.id AS ca_certificate_id,
    CAST(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(cc.platform_ids, ',', n.n), ',', -1)) AS UNSIGNED) AS platform_account_id
FROM ca_certificates cc
JOIN (
    SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8
    UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11 UNION ALL SELECT 12
    UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15 UNION ALL SELECT 16
) n
  ON CHAR_LENGTH(cc.platform_ids) - CHAR_LENGTH(REPLACE(cc.platform_ids, ',', '')) >= n.n - 1
WHERE cc.platform_ids IS NOT NULL AND cc.platform_ids <> ''
  AND TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(cc.platform_ids, ',', n.n), ',', -1)) REGEXP '^[0-9]+$'
  AND CAST(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(cc.platform_ids, ',', n.n), ',', -1)) AS UNSIGNED) > 0
ON DUPLICATE KEY UPDATE ca_certificate_id = ca_certificate_id;
