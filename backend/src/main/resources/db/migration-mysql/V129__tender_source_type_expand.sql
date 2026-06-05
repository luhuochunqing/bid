-- V129: 标讯来源类型扩展为4个枚举值
-- 旧值: MANUAL / EXTERNAL
-- 新值: EXTERNAL_PLATFORM(第三方平台) / CRM_OPPORTUNITY(CRM商机) / MANUAL_SINGLE(人工录入) / BULK_IMPORT(批量导入)
-- 对应 PRD §4.2.3 四种标讯来源

-- 1. 先扩展 ENUM 保留旧值，避免数据截断错误
ALTER TABLE tenders MODIFY COLUMN source_type ENUM(
  'MANUAL',
  'EXTERNAL',
  'EXTERNAL_PLATFORM',
  'CRM_OPPORTUNITY',
  'MANUAL_SINGLE',
  'BULK_IMPORT'
) NOT NULL DEFAULT 'MANUAL';

-- 2. 数据回填：根据 source 字段语义区分历史数据
-- CRM 来源：source 以 CRM 或 商机 开头
UPDATE tenders
SET source_type = 'CRM_OPPORTUNITY'
WHERE source_type IN ('MANUAL', 'EXTERNAL')
  AND (source LIKE 'CRM%' OR source LIKE '商机%');

-- 批量导入来源：source 包含 导入 或 批量 关键字
UPDATE tenders
SET source_type = 'BULK_IMPORT'
WHERE source_type IN ('MANUAL', 'EXTERNAL')
  AND (source LIKE '%导入%' OR source LIKE '%批量%');

-- 人工录入：其余 MANUAL 值
UPDATE tenders
SET source_type = 'MANUAL_SINGLE'
WHERE source_type = 'MANUAL';

-- 第三方平台：其余 EXTERNAL 值
UPDATE tenders
SET source_type = 'EXTERNAL_PLATFORM'
WHERE source_type = 'EXTERNAL';

-- 3. 移除旧 ENUM 值，收缩为目标定义
ALTER TABLE tenders MODIFY COLUMN source_type ENUM(
  'EXTERNAL_PLATFORM',
  'CRM_OPPORTUNITY',
  'MANUAL_SINGLE',
  'BULK_IMPORT'
) NOT NULL DEFAULT 'MANUAL_SINGLE';

-- 4. 添加乐观锁版本号列（对应 Tender.java @Version）
ALTER TABLE tenders ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号';
