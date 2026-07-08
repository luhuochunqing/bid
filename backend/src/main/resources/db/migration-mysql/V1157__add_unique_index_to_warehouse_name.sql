-- V1157: 为 warehouse.name 增加唯一索引，配合应用层同名校验彻底消除并发重复
-- Blueprint: §4.4 仓库信息
-- 处理策略：若已存在同名记录，保留 id 最小的一条，其余追加 _{id} 后缀重命名，避免数据丢失

UPDATE warehouse AS w1
JOIN (
    SELECT name, MIN(id) AS min_id
    FROM warehouse
    GROUP BY name
    HAVING COUNT(*) > 1
) AS dup ON w1.name = dup.name AND w1.id > dup.min_id
SET w1.name = CONCAT(w1.name, '_', w1.id);

ALTER TABLE warehouse ADD UNIQUE INDEX uk_warehouse_name (name);
