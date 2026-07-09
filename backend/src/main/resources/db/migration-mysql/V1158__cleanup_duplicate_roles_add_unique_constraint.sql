-- V1158: 清理重复角色码 + 给 roles.code 加唯一约束
-- 背景：B73 基线 roles 表缺少 code 唯一约束，V1074 的 INSERT ... ON DUPLICATE KEY UPDATE
--       因无 code 唯一约束而失效（仅主键触发），导致 V122 与 V1074 各插入一行 bid_specialist
--       和 admin_staff。V1092 重命名后产生 2 行 bid-Team + 2 行 bid-administration。
-- 影响：users.role_id 可能指向不同 id 的同名角色，权限不一致。
-- 修复：保留 id 最小的角色，迁移 users.role_id，删除重复行，加唯一约束。
-- 幂等：无重复时步骤 1-2 为 no-op；已有唯一约束时步骤 3 会报错（正常不会触发）。
-- 适用：新空库（V1074 产生的重复）+ 已部署环境（如有同样问题）。

-- 步骤 1: 将 users.role_id 从重复角色迁移到保留角色（保留 id 最小的）
-- 使用 JOIN 派生表绕过 MySQL ERROR 1093
UPDATE users u
JOIN roles r_dup ON r_dup.id = u.role_id
JOIN (
    SELECT code, MIN(id) AS keep_id
    FROM roles
    GROUP BY code
    HAVING COUNT(*) > 1
) dup ON dup.code = r_dup.code AND r_dup.id <> dup.keep_id
SET u.role_id = dup.keep_id;

-- 步骤 2: 删除重复角色行（保留 id 最小的）
DELETE r_dup
FROM roles r_dup
JOIN (
    SELECT code, MIN(id) AS keep_id
    FROM roles
    GROUP BY code
    HAVING COUNT(*) > 1
) dup ON dup.code = r_dup.code AND r_dup.id <> dup.keep_id;

-- 步骤 3: 给 roles.code 加唯一约束，防止未来再出现重复
ALTER TABLE roles ADD UNIQUE INDEX uk_roles_code (code);
