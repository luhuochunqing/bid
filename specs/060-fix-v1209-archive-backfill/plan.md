# Implementation Plan: 修复 V1209 Flyway 迁移失败

**Feature**: `specs/060-fix-v1209-archive-backfill`
**Branch**: `agent/trae/co606-fix-v1209-archive-backfill`

## Goal

新增 `V1210__fix_v1209_archive_backfill_updated_at.sql`，修复 V1209 step1 漏写 `updated_at` 字段导致的 Flyway 失败迁移阻塞后端，同时幂等地补全缺失的项目归档数据。不回改 V1209，保留历史失败记录供审计。

## Design

### 表结构与核心字段（已核实 SHOW CREATE TABLE）

| 表 | 字段 | 类型 | 约束 |
|---|---|---|---|
| project_archive | updated_at | datetime(6) | NOT NULL 无默认值 ← 本次失败根因 |
| project_archive | project_name | varchar(255) | NOT NULL |
| project_archive | archive_status | varchar | NOT NULL |
| archive_file | file_path | varchar(255) | NOT NULL |
| archive_file | upload_user_id | bigint | NOT NULL |
| archive_file | upload_user_name | varchar(255) | NOT NULL |
| projects | name | varchar(500) | NOT NULL（可能超 255） |
| project_documents | file_url | varchar(1000) | DEFAULT NULL |

### V1210 迁移逻辑（两条 INSERT，均幂等）

**Step 1 - 补全 project_archive 归档头（修复漏字段）**：
- INSERT (project_id, project_name, archive_status, created_at, updated_at)
- SELECT p.id, LEFT(p.name,255) [防御超长], 'ACTIVE', NOW(), NOW() [修复点]
- FROM projects p INNER JOIN project_documents pd ON pd.project_id=p.id
- LEFT JOIN project_archive pa ON pa.project_id=p.id WHERE pa.id IS NULL [幂等]

**Step 2 - 回填 archive_file（补 updated_at + 字段防御）**：
- INSERT (archive_id, file_name, document_category, file_path, file_size, upload_user_id, upload_user_name, created_at, updated_at)
- document_category 用 CASE 映射 TENDER/BID/OPEN_LIST/WIN_NOTICE/DEPOSIT_RECEIPT/OTHER
- file_path = LEFT(COALESCE(pd.file_url,''),255) [防御空值+超长]
- upload_user_id = COALESCE(pd.uploader_id,0), upload_user_name = COALESCE(pd.uploader_name,'系统')
- created_at = NOW(), updated_at = NOW()
- 去重：LEFT JOIN archive_file af WHERE af.id IS NULL [幂等]

### 为什么用 V1210 而非改 V1209
- Flyway 对已标记失败的迁移（success=0）不会自动重跑，即使改对 SQL。
- 改 V1209 文件内容会改变 checksum，Flyway 可能报 checksum mismatch。
- 新版本号 V1210 是标准修复模式（见仓库 rollback-recovery-playbook：V120X 修复历史 V11XX 漏字段先例）。

## 步骤

1. 版本号已预约（V1210 via next-migration-version.sh --reserve）。
2. 创建 `backend/src/main/resources/db/migration-mysql/V1210__fix_v1209_archive_backfill_updated_at.sql`。
3. 同步创建回滚占位 `backend/src/main/resources/db/rollback/migration-mysql/U1210__fix_v1209_archive_backfill_updated_at.sql`（CLAUDE.md 回滚目录规范）。
4. 本地验证：在开发宿主 trae 真实数据库事务执行 V1210 逻辑（START TRANSACTION + ROLLBACK 预览），确认无 1364 错误。
5. 后端架构验证：mvn test -Dtest=ArchitectureTest。
6. 提交 PR 到 origin/main，合并后清理 fail-state 重启 launchd，验证后端 UP。
