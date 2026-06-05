-- V1018__personnel_education.sql
-- 人员教育经历表（支持多条记录，对应蓝图 4.3 "新增证书" 子节 Tab 2 需求）
-- Flyway migration MySQL 8.0

CREATE TABLE personnel_education (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    personnel_id BIGINT NOT NULL COMMENT '关联人员ID',
    school_name VARCHAR(200) NOT NULL COMMENT '毕业学校名称',
    start_date DATE NOT NULL COMMENT '入学时间（年-月）',
    end_date DATE NOT NULL COMMENT '毕业时间（年-月）',
    highest_education VARCHAR(50) NOT NULL COMMENT '最高学历（初中/高中/中专/大专/本科/硕士/博士）',
    study_form VARCHAR(50) NOT NULL COMMENT '学习形式（全日制/非全日制/网络教育/自学考试/其他）',
    major VARCHAR(100) COMMENT '所学专业',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (personnel_id) REFERENCES personnel(id) ON DELETE CASCADE,
    INDEX idx_edu_personnel (personnel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人员教育经历（多条，支持蓝图 4.3 新增人员 Tab 2）';
