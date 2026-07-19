-- Input: migration-mysql/V1170__unify_archive_file_category.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway rollback coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- 回滚 V1170：将 archive_file 表中由 V1170 归一化为 OTHER 的记录还原为原历史分类
-- 注意：V1170 是有损归一化，原分类值（CONTRACT/PROCESS/RETROSPECTIVE）在迁移后无法区分
-- 回滚脚本无法精确还原原值，生产环境回滚前请先备份 archive_file 表
-- 若确需回滚，请从 V1170 执行前的备份恢复 archive_file 表，而非依赖此脚本
-- 此脚本仅作为 Flyway rollback 形式占位，实际回滚需人工介入
-- Manual rollback required: 有损迁移，无法自动回滚，需人工从 V1170 执行前备份恢复
SELECT 1;  -- no-op: 有损迁移无法自动回滚，需人工从备份恢复
