-- PR: 修复 organization_departments 主键冲突，使 oss/ehsy 等不同 source_app 的部门记录互不覆盖
-- Source: handoff docs/org-integration-handoff-2026-06-15.md issue #1

-- 1. 确保 source_app 无 NULL（复合主键要求）
UPDATE organization_departments
SET source_app = ''
WHERE source_app IS NULL;

-- 2. 删除旧主键
ALTER TABLE organization_departments DROP PRIMARY KEY;

-- 3. 添加复合主键 (source_app, department_code)
ALTER TABLE organization_departments
    ADD PRIMARY KEY (source_app, department_code);
