-- U1067: 回滚仓库表 updated_by 字段 (PR: #367)
ALTER TABLE warehouse DROP COLUMN IF EXISTS updated_by;
