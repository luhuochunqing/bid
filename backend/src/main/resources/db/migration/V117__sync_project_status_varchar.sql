-- V117: 确保 projects.status 列类型与 Java 枚举对齐
--
-- 背景: V113 已有迁移逻辑，但部分数据库（如测试/生产）因 Flyway 版本号
-- 跳跃（直接升到 999）跳过了 V113~V116。本 migration 提供幂等的兼容修复。
--
-- 旧 enum: ('INITIATED','PREPARING','REVIEWING','SEALING','BIDDING','ARCHIVED')
-- 新类型: VARCHAR(32) — 对应 Java com.xiyu.bid.entity.Project.Status
--
-- Step 1: 如果列仍然是 enum 类型，迁移旧值
UPDATE projects SET status = 'BIDDING'    WHERE status = 'PREPARING';
UPDATE projects SET status = 'EVALUATING' WHERE status = 'REVIEWING';
UPDATE projects SET status = 'BIDDING'    WHERE status = 'SEALING';
UPDATE projects SET status = 'ABANDONED'  WHERE status = 'ARCHIVED';

-- Step 2: 如果列仍然是 enum 类型，改为 VARCHAR(32)
-- 使用 IF 判断避免重复执行时报错
SET @col_type = (SELECT DATA_TYPE FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'projects' AND COLUMN_NAME = 'status');

UPDATE projects SET status = 'INITIATED' WHERE status IS NULL OR status = '';
UPDATE projects SET status = 'INITIATED' WHERE status = '';

ALTER TABLE projects MODIFY COLUMN status VARCHAR(32) NOT NULL;
