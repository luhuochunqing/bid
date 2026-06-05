-- CRM 项目映射表：存储业主单位到 CRM 项目负责人的映射关系
-- 用于标讯创建后自动匹配项目负责人

CREATE TABLE crm_project_mapping (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    purchaser_name VARCHAR(500) NOT NULL COMMENT '业主单位名称',
    crm_project_id VARCHAR(100) COMMENT 'CRM项目ID',
    project_manager_id VARCHAR(100) COMMENT 'CRM项目负责人ID',
    project_manager_name VARCHAR(100) COMMENT 'CRM项目负责人姓名',
    department_id VARCHAR(100) COMMENT '部门ID',
    department_name VARCHAR(200) COMMENT '部门名称',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_purchaser (purchaser_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CRM项目映射表';

-- 索引：按项目负责人查询
CREATE INDEX idx_crm_project_manager ON crm_project_mapping(project_manager_id);

-- 索引：按部门查询
CREATE INDEX idx_department ON crm_project_mapping(department_id);
