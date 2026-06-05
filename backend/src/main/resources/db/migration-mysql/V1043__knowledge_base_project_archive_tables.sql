-- Input: 迁移脚本、蓝图 4.1.1.1 表定义
-- Output: 创建 project_archive / archive_file / archive_log 表，支持即时归档
-- Pos: 蓝图驱动 4.1.1.1 项目档案实现（V1043）
-- 维护声明: 维护者按项目SOP；与 U1043 配对，含 header

-- V1043__knowledge_base_project_archive_tables.sql
-- 知识库 - 项目档案（方案管理 4.1.1.1）表结构
-- 按蓝图：项目立项后自动建档 + 上传即时归档（按文档分类）

CREATE TABLE IF NOT EXISTS project_archive (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT UNIQUE NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    archive_status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS archive_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    archive_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    document_category VARCHAR(50) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    upload_user_id BIGINT NOT NULL,
    upload_user_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_archive_file_archive_id FOREIGN KEY (archive_id) REFERENCES project_archive(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS archive_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    archive_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(100) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    action_content VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 注：knowledge_case 等其他知识库表若之前已存在于 V999 则此处仅补充 archive 相关；
-- 如需全套可并入或保留历史 V。
