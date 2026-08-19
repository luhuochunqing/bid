-- V1197: 修复 compliance_check_results.status 列类型
-- V1196 创建 status 为 VARCHAR(32)，但 JPA 实体 TaskStatus @Enumerated(EnumType.STRING) 需要 ENUM 类型，
-- 否则 Hibernate schema-validation 启动失败（wrong column type: found varchar, expecting enum）。
-- 参照 V1040→V1041 同类修复先例，仅修改列类型，不重建索引。

ALTER TABLE compliance_check_results
    MODIFY COLUMN status ENUM('INIT','PARSING','CHECKING','COMPLETED','FAILED') NOT NULL DEFAULT 'COMPLETED' COMMENT '任务状态: INIT, PARSING, CHECKING, COMPLETED, FAILED';
