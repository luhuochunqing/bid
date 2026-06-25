-- V1099: 恢复 users.full_name_pinyin 列，支持选人控件按姓名拼音模糊搜索。
-- 背景：历史上有 full_name_pinyin 列（由 PinyinUtils 在 @PrePersist/@PreUpdate 回填），
--   PR #1088 清理拼音逻辑时一并删除了该列与字段；随后 V1097 在 DB 侧 drop 了该列。
--   但选人控件的 placeholder 承诺"姓名/工号/拼音"搜索，且用户明确需要按拼音检索
--   （搜 "zhangsan" 命中 "张三"）。本迁移把列加回来，配合 User.java 的 fullNamePinyin
--   字段 + UserNamePinyinBackfillRunner 启动回填，恢复拼音搜索能力。
--   此决策逆转 !1101（删除拼音代码残留）——由产品需求驱动。
-- 幂等：MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 前置判断。
-- Backout: db/rollback/migration-mysql/U1099__add_users_full_name_pinyin.sql

-- 1. 添加 full_name_pinyin 列（置于 full_name 之后，语义聚合）
DROP PROCEDURE IF EXISTS p_v1099_add_full_name_pinyin_col;
DELIMITER $$
CREATE PROCEDURE p_v1099_add_full_name_pinyin_col()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME = 'full_name_pinyin'
  ) THEN
    ALTER TABLE users ADD COLUMN full_name_pinyin VARCHAR(255) NULL COMMENT '姓名拼音（小写无调，由 PinyinUtils 回填）' AFTER full_name;
  END IF;
END$$
DELIMITER ;

CALL p_v1099_add_full_name_pinyin_col();
DROP PROCEDURE IF EXISTS p_v1099_add_full_name_pinyin_col;

-- 2. 添加 full_name_pinyin 索引（加速 LIKE 前缀扫描；注意 LIKE '%xxx' 仍走全表，
--    但本表数据量小（同步的在职用户），索引主要服务于 'xxx%' 场景与未来前缀搜索优化）
DROP PROCEDURE IF EXISTS p_v1099_add_full_name_pinyin_idx;
DELIMITER $$
CREATE PROCEDURE p_v1099_add_full_name_pinyin_idx()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND INDEX_NAME = 'idx_users_full_name_pinyin'
  ) THEN
    CREATE INDEX idx_users_full_name_pinyin ON users(full_name_pinyin);
  END IF;
END$$
DELIMITER ;

CALL p_v1099_add_full_name_pinyin_idx();
DROP PROCEDURE IF EXISTS p_v1099_add_full_name_pinyin_idx;

-- 注：存量行的 full_name_pinyin 回填不在此 SQL 完成（MySQL 无法调用 pinyin4j 做汉字转拼音）。
--   由 UserNamePinyinBackfillRunner（Bootstrap 层 ApplicationRunner）在应用启动时，
--   对 full_name_pinyin IS NULL 且 full_name 非空的行用 PinyinUtils.toPinyin 计算回填，幂等。
