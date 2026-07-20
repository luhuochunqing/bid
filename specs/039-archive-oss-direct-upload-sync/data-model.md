# Data Model: OBS 直传项目文档同步归档到项目档案

**Date**: 2026-07-17

**Spec**: [spec.md](./spec.md)

## Schema 变更

**无 schema 变更**。本次修复不修改任何表结构，仅：
1. 修改 Java 代码归档触发点
2. 新增 Flyway V1168 数据修复迁移（INSERT 已存在的 OBS 直传文档到 archive_file）

## 涉及表

### project_documents（项目文档表，未修改）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 文档 ID |
| project_id | BIGINT NOT NULL | 关联项目 |
| name | VARCHAR(255) | 文档名 |
| document_category | VARCHAR(64) | 分类（可能为历史别名如 BID_DOCUMENT） |
| file_url | VARCHAR(1000) | 文件 URL（OBS 直传场景为 `obs-direct:{uploadId}`） |
| size | VARCHAR(50) | 文件大小字符串（如 "1MB"） |
| file_type | VARCHAR(50) | 文件类型 |
| uploader_id | BIGINT | 上传者 ID |
| uploader_name | VARCHAR(100) | 上传者姓名 |
| linked_entity_type | VARCHAR(64) | 关联实体类型（如 BID_RESULT） |
| linked_entity_id | BIGINT | 关联实体 ID |
| created_at | TIMESTAMP | 创建时间 |

### project_archive（项目档案表，未修改）

参见 `V1043__knowledge_base_project_archive_tables.sql`：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 档案 ID |
| project_id | BIGINT UNIQUE NOT NULL | 关联项目（唯一约束） |
| project_name | VARCHAR(200) NOT NULL | 项目名 |
| archive_status | VARCHAR(50) NOT NULL | 档案状态（ACTIVE/ARCHIVED） |
| created_at | TIMESTAMP NOT NULL | 创建时间 |
| updated_at | TIMESTAMP NOT NULL | 更新时间 |

### archive_file（归档文件表，未修改）

参见 `V1043__knowledge_base_project_archive_tables.sql`：

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT PK | AUTO_INCREMENT | 主键 |
| archive_id | BIGINT NOT NULL | FK → project_archive.id | 关联档案 |
| file_name | VARCHAR(255) NOT NULL | | 文件名 |
| document_category | VARCHAR(50) NOT NULL | | 标准枚举（TENDER/BID/OPEN_LIST/WIN_NOTICE/DEPOSIT_RECEIPT/OTHER） |
| file_path | VARCHAR(500) NOT NULL | | 文件路径（multipart 为本地路径，OBS 直传为 `obs-direct:{uploadId}`） |
| file_size | BIGINT NOT NULL | | 文件大小（字节） |
| upload_user_id | BIGINT NOT NULL | | 上传者 ID |
| upload_user_name | VARCHAR(100) NOT NULL | | 上传者姓名 |
| created_at | TIMESTAMP NOT NULL | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

**关键约束**：`file_path` 是 NOT NULL，OBS 直传场景必须传非空值。`file_url` 字段值 `obs-direct:{uploadId}` 长度远小于 500，无截断风险。

### archive_log（归档操作日志表，未修改）

本次修复不涉及 archive_log 写入（沿用现有 `recordLog` 调用点）。

## 数据修复迁移 V1168

### 目标

回填历史已通过 OBS 直传上传但未归档的 project_documents 记录到 archive_file 表。

### SQL 逻辑

```sql
-- V1168__backfill_archive_files_for_obs_direct_uploads.sql
-- 回填 OBS 直传历史文档到 archive_file 表（修复归档逻辑遗漏）

INSERT INTO archive_file (
    archive_id,
    file_name,
    document_category,
    file_path,
    file_size,
    upload_user_id,
    upload_user_name,
    created_at
)
SELECT
    pa.id AS archive_id,
    pd.name AS file_name,
    -- SQL 层归一化分类（与 DocumentCategoryNormalizer ALIAS 映射一致）
    CASE pd.document_category
        WHEN 'TENDER_DOCUMENT' THEN 'TENDER'
        WHEN 'BID_DOCUMENT' THEN 'BID'
        WHEN 'EVALUATION_EVIDENCE' THEN 'OPEN_LIST'
        WHEN 'RESULT_EVIDENCE' THEN 'WIN_NOTICE'
        WHEN 'CLOSURE_EVIDENCE' THEN 'DEPOSIT_RECEIPT'
        WHEN 'RETROSPECTIVE_REPORT' THEN 'OTHER'
        ELSE COALESCE(pd.document_category, 'OTHER')
    END AS document_category,
    pd.file_url AS file_path,
    0 AS file_size,  -- project_documents.size 是 VARCHAR，无法可靠解析为字节
    COALESCE(pd.uploader_id, 0) AS upload_user_id,
    COALESCE(pd.uploader_name, '系统') AS upload_user_name,
    NOW() AS created_at
FROM project_documents pd
INNER JOIN project_archive pa ON pa.project_id = pd.project_id
LEFT JOIN archive_file af ON af.archive_id = pa.id AND af.file_path = pd.file_url
WHERE pd.file_url LIKE 'obs-direct:%'
  AND af.id IS NULL;  -- 幂等：未归档的才插入
```

### 幂等保证

- LEFT JOIN archive_file af ON af.archive_id = pa.id AND af.file_path = pd.file_url
- WHERE af.id IS NULL：仅未归档的才插入
- 重复执行 V1168 不会产生重复记录

### 边界处理

- **project_archive 不存在**：INNER JOIN 自动跳过（不违反 archive_id 外键约束）
- **document_category 为 NULL**：CASE 的 ELSE 分支 COALESCE 到 'OTHER'
- **document_category 是标准枚举（如 'BID'）**：CASE 的 ELSE 分支保留原值
- **document_category 是未知别名**：CASE 的 ELSE 分支保留原值（与 DocumentCategoryNormalizer 行为一致，未知值原样返回）

## 回滚迁移 U1168

```sql
-- U1168__backfill_archive_files_for_obs_direct_uploads.sql
-- 回滚 V1168：删除由 V1168 插入的 archive_file 记录

-- 通过 file_path 前缀 + created_at 时间范围限定（避免误删历史 multipart 归档）
-- 注：multipart 归档的 file_path 不会以 obs-direct: 开头，但 created_at 限定提供双重保护
DELETE FROM archive_file
WHERE file_path LIKE 'obs-direct:%'
  AND created_at >= NOW() - INTERVAL 1 HOUR;  -- 假定回滚在 V1168 执行后 1 小时内
```

**注**：回滚脚本的 created_at 时间范围限定是启发式的，无法精确还原 V1168 的执行时间。生产环境回滚建议手动确认 V1168 执行时间后调整 INTERVAL。

## 数据流变更示意

### 修复前

```
multipart 上传:
  ProjectDocumentUploadWorkflowService.createUploadedProjectDocument
    → fileStorage.store → 写本地
    → projectDocumentWorkflowService.createProjectDocument → 写 project_documents
    → projectArchiveWorkflowService.attachFileToArchive → 写 archive_file ✓

JSON 上传（OBS 直传）:
  ProjectDocumentWorkflowService.createProjectDocument → 写 project_documents
  → BidResultProjectDocumentBindingGateway.onDocumentCreated（仅 BID_RESULT 处理）
  // 不写 archive_file ✗
```

### 修复后

```
multipart 上传:
  ProjectDocumentUploadWorkflowService.createUploadedProjectDocument
    → fileStorage.store → 写本地
    → projectDocumentWorkflowService.createProjectDocument
      → 写 project_documents
      → BidResultProjectDocumentBindingGateway.onDocumentCreated（仅 BID_RESULT）
      → projectArchiveWorkflowService.attachFileToArchive → 写 archive_file ✓

JSON 上传（OBS 直传）:
  ProjectDocumentWorkflowService.createProjectDocument
    → 写 project_documents
    → BidResultProjectDocumentBindingGateway.onDocumentCreated（仅 BID_RESULT）
    → projectArchiveWorkflowService.attachFileToArchive → 写 archive_file ✓
```

两条路径统一在 `createProjectDocument` 末尾归档，行为一致。
