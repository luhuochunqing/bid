-- V108: WS-B 标书编制 - 主/副投标负责人分配（PRD §3.2）
-- 独立 1:1 表，避免改动 project_initiation_details。

CREATE TABLE IF NOT EXISTS project_lead_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    primary_lead_user_id BIGINT,
    secondary_lead_user_id BIGINT,
    assigned_at TIMESTAMP,
    assigned_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_lead_assignment_project UNIQUE (project_id)
);
CREATE INDEX idx_lead_assignment_project ON project_lead_assignment(project_id);
CREATE INDEX idx_lead_assignment_primary ON project_lead_assignment(primary_lead_user_id);
