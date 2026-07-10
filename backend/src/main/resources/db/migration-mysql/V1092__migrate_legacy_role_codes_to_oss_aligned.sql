-- V1092: 旧角色码迁移到 OSS 文档对齐的新角色码
-- 背景：P0-1 角色 code 统一，将系统内部角色码对齐 OSS 文档定义。
-- 旧 code（下划线风格）→ 新 code（OSS 规范）映射：
--   bidAdmin        → /bidAdmin          （V1074 中间态，camelCase）
--   bid_admin       → /bidAdmin          （注意：OSS 投标管理员带前导斜杠）
--   bid_lead        → bid-TeamLeader
--   sales           → bid-projectLeader
--   bid_specialist  → bid-Team
--   admin_staff     → bid-administration
--   bid_other_dept  → bid-otherDept
-- 注意：admin 保持不变，staff 已在 V1091 移除。
--
-- 安全策略（顺序至关重要，避免 users.role_id 被置 NULL）：
--   若新 code 已存在：将 users.role_id 从旧角色迁移到新角色，再删除旧角色
--   若新 code 不存在：直接 UPDATE roles.code（users.role_id 不变，仍指向同一行）
--   幂等：旧 code 不存在时 no-op
--
-- MySQL 兼容性：使用 JOIN 绕过 ERROR 1093，避免在 UPDATE/DELETE roles 时通过子查询引用 roles。
--   使用 tmp_role_mappings 临时表仅存放静态映射关系，不存放运行时状态。
--
-- 事务：使用显式事务保证原子性，中途失败回滚避免半迁移状态

START TRANSACTION;

-- 静态映射表：source_code = 旧角色码，target_code = 新角色码
-- 注意：移除 DROP TEMPORARY TABLE IF EXISTS — Flyway 9.22.3 + MySQL 8.0.43 对不存在的
-- 临时表返回 Error Code 1051，即使有 IF EXISTS 也会被 Flyway 当作迁移失败。
-- 临时表在会话结束时会自动删除，无需显式 DROP。用 IF NOT EXISTS 防止重复创建。
-- Collation：显式指定 utf8mb4_unicode_ci 与 roles/users 表对齐，
-- 否则临时表用数据库默认 utf8mb4_0900_ai_ci，JOIN 时 Error 1267 (Illegal mix of collations)。
CREATE TEMPORARY TABLE IF NOT EXISTS tmp_role_mappings (
    source_code VARCHAR(100) COLLATE utf8mb4_unicode_ci PRIMARY KEY,
    target_code VARCHAR(100) COLLATE utf8mb4_unicode_ci NOT NULL
);
INSERT INTO tmp_role_mappings (source_code, target_code) VALUES
    ('bidAdmin', '/bidAdmin'),
    ('bid_admin', '/bidAdmin'),
    ('bid_lead', 'bid-TeamLeader'),
    ('sales', 'bid-projectLeader'),
    ('bid_specialist', 'bid-Team'),
    ('admin_staff', 'bid-administration'),
    ('bid_other_dept', 'bid-otherDept');

-- 步骤 1: 新旧角色同时存在时，合并用户到新角色
UPDATE users u
JOIN roles r_source ON r_source.id = u.role_id
JOIN tmp_role_mappings m ON m.source_code = r_source.code
JOIN roles r_target ON r_target.code = m.target_code
SET u.role_id = r_target.id;

-- 步骤 2: 新旧角色同时存在时，删除旧角色
DELETE r_source
FROM roles r_source
JOIN tmp_role_mappings m ON m.source_code = r_source.code
JOIN roles r_target ON r_target.code = m.target_code;

-- 步骤 3: 新角色不存在时，直接改名旧角色
UPDATE roles r_source
JOIN tmp_role_mappings m ON m.source_code = r_source.code
LEFT JOIN roles r_target ON r_target.code = m.target_code
SET r_source.code = m.target_code,
    r_source.updated_at = NOW()
WHERE r_target.id IS NULL;

-- 不显式 DROP 临时表：会话结束时自动删除。
-- 显式 DROP TEMPORARY TABLE IF EXISTS 在 Flyway 9.22.3 + MySQL 8.0.43 可能触发 Error Code 1051
-- 被误判为迁移失败（即使表存在，某些 MySQL 配置下仍可能返回 WARNING）。
-- 临时表生命周期与会话绑定，无需手动清理。

COMMIT;
