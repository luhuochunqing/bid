# Feature Specification: 修复 V1209 Flyway 迁移失败（project_archive 漏写 updated_at）

**Feature Branch**: `agent/trae/co606-fix-v1209-archive-backfill`

**Created**: 2026-09-03

**Status**: Draft

**Issue**: CO-606

**Input**: 后端启动被 `Schema contains a failed migration to version 1209` 阻塞。诊断确认根因是 `V1209__backfill_missing_project_archive_files.sql` 的 step1 INSERT `project_archive` 时**漏写 `updated_at` 字段**，而该字段 `datetime(6) NOT NULL` 无默认值；MySQL 8.0 `STRICT_TRANS_TABLES` 模式直接报 `1364 Field updated_at has no default value`，Flyway 单个迁移在事务内执行，任一条失败整体回滚 → `flyway_schema_history` 记录 `success=0`。此后每次启动 Flyway validate/migrate 都被该失败记录挡住，`entityManagerFactory` Bean 无法创建，Tomcat 启动失败。

## 背景与根因

- **失败迁移**：`V1209__backfill_missing_project_archive_files.sql`（数据回填，幂等设计）
- **真实报错**：`ERROR 1364 (HY000): Field 'updated_at' doesn't have a default value`（已用直接 MySQL 事务 `START TRANSACTION; INSERT ... ROLLBACK` 复现）
- **表结构**：`project_archive.updated_at datetime(6) NOT NULL`（无默认值）；`archive_file` 各 NOT NULL 字段
- **失败语义**：Flyway 默认不重跑失败迁移 → 必须新增更高版本迁移修复（V1210）
- **Flyway 配置**：`spring.flyway.outOfOrder: true`（允许乱序，但本方案用顺序版本 V1210，更稳妥）

## 影响数据规模（已实测）

| 指标 | 值 | 说明 |
|---|---|---|
| projects | 779 | 项目总数 |
| project_documents | 15 | 文档总数 |
| project_archive | 543 | 已有归档头（max created_at = 2026-08-04，早于 V1209 执行时间，证明 V1209 step1 未写入） |
| archive_file | 13 | 已有归档文件（证明 V1209 step2 未写入） |
| pd_without_pa | 2 | project_id 10、11 有文档但缺归档头（V1210 需补） |
| 预计新增 project_archive | ≈2 条 | step1 幂等补全 |
| 预计新增 archive_file | 至多 6 条 | step2 幂等回填 |

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 后端启动不再被失败迁移阻塞 (Priority: P1)

**Why this priority**: 后端当前无法启动，全系统不可用，是 P0 级阻塞。

**Independent Test**: 在 flyway_schema_history 已有 `V1209 success=0` 记录的前提下，执行 `mvn spring-boot:run`（或 Flyway migrate），断言 `FlywayMigrateException` 不再抛出，V1210 执行成功且 `success=1`。

**Acceptance Scenarios**:

1. **Given** `flyway_schema_history` 中 `V1209 success=0` 已存在，**When** 启动后端，**Then** Flyway V1210 成功执行（`success=1`），后端正常监听 18089。
2. **Given** 数据库中 project_id 10、11 有 `project_documents` 但缺 `project_archive` 头，**When** V1210 执行成功，**Then** 这两条 `project_archive` 被创建且 `updated_at` 不为空。
3. **Given** 重复执行 V1210 场景（幂等性），**When** 再次执行相同 INSERT 逻辑，**Then** 不产生重复行（LEFT JOIN + IS NULL 去重生效）。

### User Story 2 - project_archive 数据回填正确 (Priority: P1)

**Why this priority**: 修复目标不仅是解阻塞，还要补全缺失的归档数据，恢复 V1209 本该完成的业务动作。

**Independent Test**: 执行 V1210 后，`SELECT COUNT(*) FROM project_archive pa JOIN project_documents pd ON pd.project_id=pa.project_id` 与 `SELECT COUNT(*) FROM projects p JOIN project_documents pd ON pd.project_id=p.id` 结果一致（每个有文档的项目都有归档头）。

**Acceptance Scenarios**:

1. **Given** project_id 10、11，**When** 执行 V1210，**Then** 都有对应的 `project_archive` 行，`archive_status='ACTIVE'`，`created_at`/`updated_at` 均为当前时间。
2. **Given** project_documents 中 `file_url` 不为空，**When** 执行 V1210 step2，**Then** `archive_file` 记录 `file_path` 被安全截断到 ≤255 字符。

### Edge Cases

- `project_documents.uploader_id` 为 NULL → 用 `COALESCE(..., 0)` 兜底，`archive_file.upload_user_id bigint NOT NULL` 不报错。
- `project_documents.uploader_name` 为 NULL → 用 `COALESCE(..., '系统')` 兜底。
- `project_documents.file_url` 为 NULL → 用 `COALESCE(..., '')` 兜底；若超 255 字符 → `LEFT(..., 255)` 截断。
- `projects.name` 超 255 字符 → `LEFT(..., 255)` 截断，避免 `project_archive.project_name varchar(255) NOT NULL` 报数据过长错误。
- `project_documents.document_category` 出现预期外的值 → `CASE` 的 `ELSE` 分支用 `COALESCE(..., 'OTHER')` 兜底。
- 已存在归档头的项目（543 条）→ `LEFT JOIN ... WHERE pa.id IS NULL` 不重复插入。
- 已存在 archive_file 的项目 → step2 去重条件 `af.id IS NULL` 不重复插入。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 新增迁移 `V1210__fix_v1209_archive_backfill_updated_at.sql`，在 step1 的 `project_archive` INSERT 中补全 `updated_at = NOW()` 字段。
- **FR-002**: V1210 step1 复用 V1209 的幂等逻辑（`project_documents INNER JOIN projects LEFT JOIN project_archive WHERE pa.id IS NULL`），只补缺失的归档头。
- **FR-003**: V1210 step2 复用 V1209 的 `archive_file` 回填逻辑，并补 `updated_at = NOW()`，保持去重条件不变。
- **FR-004**: V1210 对所有可能超长或为 NULL 的字段做防御性处理（`LEFT` 截断 + `COALESCE` 兜底），保证在 STRICT_TRANS_TABLES 模式下不报错。
- **FR-005**: V1210 必须幂等：重复执行（或后续重新 migrate）不产生重复行。
- **FR-006**: 不回改 `V1209.sql` 本身（已发布迁移不可变，遵守 `check-flyway-immutable` 门禁）；但 V1209 在 `flyway_schema_history` 的失败记录（`success=0`）**必须 repair 置为 `success=1`**（`UPDATE flyway_schema_history SET success=1 WHERE version='1209'`），否则 Flyway 永远被该失败记录阻塞。
- **FR-007**: `archive_status` 枚举值使用 V1209 原值 `'ACTIVE'`，与既有 543 条归档头的 status 语意一致。
- **FR-008**: V1210 **单靠自身无法解锁** Flyway 的 failed-migration 硬性阻塞。Flyway 9.x 对 `flyway_schema_history` 中任意 `success=0` 迁移**无条件**抛 `Schema contains a failed migration to version 1209 !`（即便存在更高的 V1210）。必须先手动将 V1209 失败记录 `UPDATE ... SET success=1`（V1209 从未写入任何数据——迁移整事务回滚，数据回填完全由 V1210 承载），重启后 Flyway 才跳过 1209 并执行 V1210。

### Key Entities *(include if feature involves data)*

- **project_archive**: 项目归档头（`project_id`、`project_name`、`archive_status`、`created_at`、`updated_at`）。`updated_at datetime(6) NOT NULL` 无默认值——本次修复核心。
- **archive_file**: 归档文件记录（`archive_id`、`file_name`、`document_category`、`file_path`、`file_size`、`upload_user_id`、`upload_user_name`、`created_at`、`updated_at`）。
- **project_documents**: 项目文档（数据源：`name`、`document_category`、`file_url`、`uploader_id`、`uploader_name`）。
- **projects**: 项目主表（`id`、`name`，`name varchar(500)`，可能超 `project_archive.project_name varchar(255)`）。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `flyway_schema_history` 中 `V1209` 失败记录被 repair 为 `success=1`（见 FR-008），`V1210` 新增 `success=1`。V1209 的 `success=0` 必须被标记成功才能解锁 Flyway 启动（否则后端永远无法启动）。
- **SC-002**: 后端 `mvn spring-boot:run` 启动成功，`/actuator/health` 返回 UP，无 Flyway 异常。
- **SC-003**: project_id 10、11 各有 1 条 `project_archive`（归档头），`updated_at` 非空。
- **SC-004**: 每个拥有 `project_documents` 的项目都有 `project_archive`，无孤儿归档头/文档。
- **SC-005**: 既有 543 条 `project_archive` 与 13 条 `archive_file` 数据完全不变（幂等，未受影响）。
- **SC-006**: `mvn test -Dtest=ArchitectureTest` 通过（后端架构边界未破坏）。

## Assumptions

- 不做任何业务 Java 代码改动，仅新增一个 Flyway SQL 迁移文件。
- 数据库为 MySQL 8.0（STRICT_TRANS_TABLES 为默认/已开启）。
- Flyway 已配置 `outOfOrder: true`，但本方案采用连续版本号 V1210，不依赖 outOfOrder。
- 暂不处理本次排查沿途发现的 dev-services 隐患（backend.log 被覆盖、launchd REDIS_PORT=16379、watch-run -Xmx512m、8 个不规范迁移文件名）——这些另开独立任务，避免本 PR 范围膨胀（符合"最小化改动"原则）。
