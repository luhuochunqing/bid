-- Input: V1076__organization_departments_composite_pk.sql
-- Rollback for V1076__organization_departments_composite_pk.sql
-- §organization integration

-- 前置检查：source_app 为 NULL 时先兜底，避免后续比较失真
UPDATE organization_departments
SET source_app = ''
WHERE source_app IS NULL;

-- 若同一 department_code 存在多 source_app 记录，优先保留 ehsy，删除其余
-- （回滚到单主键前必须消除冲突）
DELETE od FROM organization_departments od
INNER JOIN (
    SELECT department_code
    FROM organization_departments
    GROUP BY department_code
    HAVING COUNT(*) > 1
) dup ON od.department_code = dup.department_code
WHERE od.source_app <> 'ehsy';

-- 恢复为 department_code 单主键
ALTER TABLE organization_departments DROP PRIMARY KEY;
ALTER TABLE organization_departments ADD PRIMARY KEY (department_code);
