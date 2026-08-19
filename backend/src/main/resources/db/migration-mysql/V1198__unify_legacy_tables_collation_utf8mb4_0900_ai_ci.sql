-- V1198__unify_legacy_tables_collation_utf8mb4_0900_ai_ci.sql
--
-- 背景：数据分析「多维度趋势-区域统计」报 500（MySQL ERROR 1267: Illegal mix of
-- collations (utf8mb4_bin,NONE) and (utf8mb4_0900_ai_ci,COERCIBLE) for operation 'like'）。
-- 根因：commit d1cbe8400 将区域表达式改为跨表 fallback：
--   coalesce(nullif(pid.headquartersLocation, ''), t.region)
-- 其中 project_initiation_details（新表，utf8mb4_0900_ai_ci）与 tenders/projects
-- （历史表，utf8mb4_unicode_ci）collation 不一致。MySQL 对两个 collation 不同且
-- 无超集关系的列做 COALESCE，结果退化为 utf8mb4_bin + 无隐式 collation（NONE），
-- 再与连接默认 collation（utf8mb4_0900_ai_ci）的 LIKE 字面量比较即报 1267。
--
-- 方案：把库内所有 collation 不等于 utf8mb4_0900_ai_ci 的基础表统一 CONVERT 为
-- utf8mb4 / utf8mb4_0900_ai_ci（对齐库默认 collation）。
--
-- 风险评估（测试环境实测，2026-08-19）：
--   * 全库共 141 张 utf8mb4_unicode_ci 表，总数据量约 51MB，最大表
--     organization_sync_items 约 10.8 万行 / 36.6MB，单表重建秒级。
--   * 全部列均为 utf8mb4 字符集、无列级 collation 覆盖，同字符集转 collation 无损。
--   * CONVERT 需要重建表（期间该表写锁），总耗时预计 1 分钟内，部署窗口内可接受。
--   * 幂等：已是 utf8mb4_0900_ai_ci 的表不在游标结果集内，重复执行无副作用。
--
-- 回滚：见 db/rollback/migration-mysql/U1198__unify_legacy_tables_collation_utf8mb4_0900_ai_ci.sql

DELIMITER $$
CREATE PROCEDURE p_unify_table_collation()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE t_name VARCHAR(192);
  DECLARE cur CURSOR FOR
    SELECT TABLE_NAME
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_TYPE = 'BASE TABLE'
      AND TABLE_COLLATION IS NOT NULL
      AND TABLE_COLLATION <> 'utf8mb4_0900_ai_ci';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO t_name;
    IF done THEN
      LEAVE read_loop;
    END IF;
    SET @ddl = CONCAT('ALTER TABLE `', t_name,
                      '` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci');
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END LOOP;
  CLOSE cur;
END$$
DELIMITER ;

CALL p_unify_table_collation();

DROP PROCEDURE IF EXISTS p_unify_table_collation;
