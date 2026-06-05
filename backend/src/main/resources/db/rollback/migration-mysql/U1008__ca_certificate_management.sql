-- Input: migration-mysql/V1008__ca_certificate_management.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U1008: 回滚 CA 信息管理 (Blueprint 5.3)
-- 删除 ca_borrow_events → ca_borrow_applications → ca_certificates (按外键依赖逆序)

DROP TABLE IF EXISTS ca_borrow_events;
DROP TABLE IF EXISTS ca_borrow_applications;
DROP TABLE IF EXISTS ca_certificates;
