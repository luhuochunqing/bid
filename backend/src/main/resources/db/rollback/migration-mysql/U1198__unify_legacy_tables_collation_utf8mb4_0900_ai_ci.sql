-- Input: migration-mysql/V1198__unify_legacy_tables_collation_utf8mb4_0900_ai_ci.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- 回滚 V1198（统一历史表 collation 为 utf8mb4_0900_ai_ci）。
--
-- ⚠️ 回滚语义说明：
--   * 本脚本把库内所有 utf8mb4_0900_ai_ci 基础表转回 utf8mb4_unicode_ci，
--     恢复到「历史表 collation」的形态（V1198 引入的 bug 将复现）。
--   * 无法精确区分「V1198 转换的表」与「迁移前就是 0900_ai_ci 的新表」，
--     因此这是整体反转，不是精确还原。
--   * 若回滚原因是数据损坏而非 collation 本身，请改用 DB 备份恢复
--     （/opt/xiyu-bid/db-backups/），不要执行本脚本。

DELIMITER $$
CREATE PROCEDURE p_rollback_table_collation()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE t_name VARCHAR(192);
  DECLARE cur CURSOR FOR
    SELECT TABLE_NAME
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_TYPE = 'BASE TABLE'
      AND TABLE_COLLATION = 'utf8mb4_0900_ai_ci';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO t_name;
    IF done THEN
      LEAVE read_loop;
    END IF;
    SET @ddl = CONCAT('ALTER TABLE `', t_name,
                      '` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci');
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END LOOP;
  CLOSE cur;
END$$
DELIMITER ;

CALL p_rollback_table_collation();

DROP PROCEDURE IF EXISTS p_rollback_table_collation;
