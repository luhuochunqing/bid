# Data Model: AI 评分标准解析 — 后端服务

**Date**: 2026-08-15 | 依据 spec.md Key Entities + research.md R7/R9

## 实体关系

```text
Project (现有) 1 ──── n ScoreParseTask（解析/打分任务）
Project (现有) 1 ──── n ScoreItem（评分项，阶段 1 产物）
ScoreItem       1 ──── 0..1 ScoreResult（打分结果，阶段 2 产物）
ScoreParseTask  1 ──── n ScoreItem（产生该批评分项的解析任务）

知识库侧（只读消费，不改结构）：
BusinessQualificationEntity / PersonnelEntity+PersonnelCertificateEntity /
PerformanceRecordEntity(+contract_amount) / WarehouseEntity / ManufacturerAuthorizationEntity
```

## 新表 1：score_parse_task（任务表）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | IDENTITY PK | |
| task_id | VARCHAR(36) | UK | UUID，对外标识 |
| project_id | BIGINT | INDEX, NOT NULL | 关联项目 |
| task_type | VARCHAR(20) | NOT NULL | `PARSE` / `SCORING` |
| status | VARCHAR(20) | NOT NULL | `PENDING` / `PROCESSING` / `COMPLETED` / `FAILED` |
| progress | INT | NOT NULL DEFAULT 0 | 0-100 |
| stage | VARCHAR(50) | NULL | 进度阶段描述（召回/提取/校验/匹配/打分） |
| file_name | VARCHAR(255) | NULL | 触发文件名 |
| file_url | VARCHAR(500) | NULL | doc-insight:// URL |
| error_message | TEXT | NULL | 失败原因 |
| timeout_marked | TINYINT(1) | NOT NULL DEFAULT 0 | 超时扫描 job 标记 |
| started_at | DATETIME | NULL | |
| completed_at | DATETIME | NULL | |
| created_at / updated_at | DATETIME | @PrePersist/@PreUpdate | 仓库惯例 |

**状态机**：`PENDING → PROCESSING → COMPLETED | FAILED`（终态不回退）；超时扫描将 `PROCESSING` 且 `updated_at < now-30min` 的任务置 `FAILED`（`timeout_marked=1`，`error_message='任务超时终止'`）。

**互斥约束**（应用层校验，SC 对应 FR-019）：同 `project_id + task_type` 仅允许一个非终态任务；重复触发返回进行中任务。

## 新表 2：score_item（评分项，阶段 1 产物）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | IDENTITY PK | |
| project_id | BIGINT | INDEX, NOT NULL | 冗余项目 ID 便于直查 |
| parse_task_id | BIGINT | INDEX, NOT NULL | 产生本批的解析任务 |
| item_index | INT | NOT NULL | 表内序号（编号可能重复，去重保留首次出现） |
| code | VARCHAR(50) | NOT NULL | 评分项编号（原文提取，如 A1/B2） |
| dim | VARCHAR(200) | NOT NULL | 评分项名称 |
| detail | TEXT | NOT NULL | 详细要素（完整保留原文，禁止摘要） |
| weight | DECIMAL(6,2) | NOT NULL | 权重绝对分值 |
| score_type | VARCHAR(20) | NOT NULL | `OBJECTIVE` / `SUBJECTIVE`（含报价类） |
| status_stage1 | VARCHAR(20) | NOT NULL | `OK` / `DANGER` / `PENDING` |
| est_score | DECIMAL(6,2) | NULL | 预计得分；主观项 NULL |
| est_basis | TEXT | NULL | 阶段 1 评分依据 |
| kb_hit | TINYINT(1) | NULL | 知识库命中标记（仅客观项可 true） |
| context_note | TEXT | NULL | 评分规则上下文（注/说明/备注） |
| source_text | TEXT | NULL | 原文依据 |
| location | VARCHAR(200) | NULL | 页码/位置 |
| created_at / updated_at | DATETIME | @PrePersist/@PreUpdate | |

**重新解析语义**（FR-021）：新批次写入前按 `project_id` 软清理旧行（DELETE WHERE project_id=?，旧打分结果随 score_result 级联删除并标记需重新打分）。

**校验规则**（domain 纯核心执行）：`weight > 0`；`est_score ∈ [0, weight]`；`score_type=SUBJECTIVE` 时 `est_score` MUST NULL、`kb_hit` MUST NULL。

## 新表 3：score_result（打分结果，阶段 2 产物）

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | IDENTITY PK | |
| score_item_id | BIGINT | UK, NOT NULL | 1:1 关联评分项 |
| scoring_task_id | BIGINT | INDEX, NOT NULL | 产生本结果的打分任务 |
| actual_score | DECIMAL(6,2) | NULL | 实际得分；主观项/异常项 NULL |
| status_stage2 | VARCHAR(20) | NOT NULL | `OK` / `DANGER` / `PENDING` |
| evidence | TEXT | NULL | 评分依据 |
| quote | TEXT | NULL | 标书引用原文（含章节页码）；无则 NULL（前端显示"标书引用：无"） |
| missed_reason | TEXT | NULL | 缺失说明 |
| suggestion | TEXT | NULL | 修改建议（主观项/待确认/不满足项） |
| match_ratio | INT | NULL | 匹配比例 0-100 |
| created_at / updated_at | DATETIME | @PrePersist/@PreUpdate | |

**重新打分语义**（FR-021）：同项目整批覆盖（事务内 DELETE + INSERT）；评分项变更后旧结果随 score_item 清理失效。

**校验规则**（domain 纯核心执行）：`actual_score ∈ [0, weight]`（超区间置 NULL + `status_stage2=PENDING` + 日志，FR-016）；`status_stage2` 判定：满分=OK、零分=DANGER、部分分或证书过期=PENDING（FR-015）。

## 存量表变更：performance_record

| 变更 | 类型 | 说明 |
|---|---|---|
| contract_amount | DECIMAL(15,2) NULL | 新增列（research R7）；存量行 NULL 视为"金额未知"，匹配时跳过金额门槛比对（不因 NULL 失配） |

## 汇总统计（不落表，查询时计算）

`totalEstScore` / `totalActualScore` / `okCount` / `dangerCount` / `pendingCount` / `objectiveWeight` / `subjectiveWeight` 按 PRD §2.1 口径在查询服务聚合；权重合计 ≠ 100 时响应携带 `weightWarning=true` + 实际总分（FR-022）。

## 迁移清单

| 版本 | 内容 | 回滚 |
|---|---|---|
| V1187（以 reserve 实际输出为准） | 建 score_parse_task / score_item / score_result | U1187 DROP 三表 |
| V1188 | performance_record 加 contract_amount | U1188 DROP COLUMN |

创建方式：`bash scripts/new-migration.sh <描述>`（自动 next-migration-version --reserve）；implement 前对 `db/migration-mysql/**` + `db/rollback/migration-mysql/**` acquire agent lock。
