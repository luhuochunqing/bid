# Implementation Plan: 多表评分标准自动拆分（062-score-multi-table-split）

**Branch**: `agent/trae/score-multi-table-split` | **Date**: 2026-09-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/062-score-multi-table-split/spec.md`

## Summary

修复 Evidence Pipeline 多表场景缺陷：多张独立评分表各有汇总行声明总分时，当前把全部表的
`DeclaredTotalEvidence` 合并后一次校验，不同总分触发"结构化声明总分冲突"→ 整份文档
`REVIEW_REQUIRED` → 正式评分项（`score_item`）为空。

方案 A：按 `ScoreFact.sourceTableId` 把评分事实拆分为多个评分标准，每张独立评分表按本表
声明总分独立完整性校验；校验通过的表自动落库为正式评分项，未通过的表保留人工复核，多表不再互相拖累。

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.2（后端）

**Primary Dependencies**: Spring Data JPA、Flyway（本任务不新增迁移）、无新增第三方库

**Storage**: MySQL 8.0 — `score_item`（正式评分项）、`score_parse_fact`/`score_parse_evidence`（事实与证据）、
`score_parse_task`（任务，`run_summary` JSON 字段承载拆分明细）。**无 schema 变更**（见 Assumptions）。

**Testing**: JUnit 5 + Mockito；纯核心 `ScoreTableSplitPolicyTest` + `ScoreFactShadowPipelineTest` 回归 +
`PdfEvidencePipelineTest` 多表场景；`ArchitectureTest`/`ScoreParseArchitectureTest` 保持全绿。

**Target Platform**: Linux 服务器（测试 172.16.38.78 / 生产 172.16.10.149）

**Project Type**: 后端 Web 服务（Spring Boot）内部功能增强

**Performance Goals**: 每张表独立校验为 O(facts per table)，总耗时与现状同量级（分组开销可忽略）

**Constraints**: 单文件硬上限 300 行（新策略 <100 行）；纯核心零框架依赖；不新增 DB 迁移；不修改前端

**Scale/Scope**: 影响 Evidence Pipeline 结构化表格通道（docx/pdf）；单次解析典型 1~5 张评分表

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 合规 | 说明 |
|------|------|------|
| I. FP-Java Architecture | PASS | 新策略 `ScoreTableSplitPolicy` 为纯核心 static 方法 + 不可变 record，无框架依赖 |
| II. Real-API Only | PASS | 纯后端解析逻辑，不涉及 Mock |
| III. TDD | PASS | Red→Green：先加多表失败用例（`PdfEvidencePipelineTest`），再实现拆分策略 |
| IV. Split-First & Simplicity | PASS | 新文件 <100 行；拆分逻辑收敛到单一策略，不膨胀既有 pipeline |
| V. OSS Integration | N/A | 不涉及 |
| VI. Authorization Unification | N/A | 无权限变更 |
| VII. Defensive Collection | PASS | 分组用 `Collectors.groupingBy`，禁止 2 参数 `toMap`；跨表 candidate 聚合用 ArrayList |
| VIII. Boring Proven Patterns | PASS | 复用 `ScoreCompletenessPolicy`/`ScoreTableSummaryPolicy`，不新建平行实现 |
| Code Quality Gates | PASS | 静态检查无新增 `toMap` 2 参版本；无新 DB 迁移；无前端改动 |

## Project Structure

### Documentation (this feature)

```text
specs/062-score-multi-table-split/
├── spec.md              # 需求规格（已生成）
├── plan.md              # 本文件
├── research.md          # Phase 0（本任务无 NEEDS CLARIFICATION，省略）
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/           # 内部功能，无外部接口变更，跳过
└── tasks.md             # Phase 2（speckit-tasks 生成）
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/scoreparse/
├── domain/
│   ├── ScoreTableSplitPolicy.java        [NEW] 按表分组 + 按表独立校验 + 文档级聚合（纯核心）
│   └── ScoreCompletenessPolicy.java       (不变，逐表复用)
└── application/
    ├── ScoreFactShadowPipeline.java       [MOD] ShadowResult 增加每表独立完整性结果
    └── StructuredTableExtractionHandler.java [MOD] 通过表聚合落库 + run_summary 拆分明细

backend/src/test/java/com/xiyu/bid/scoreparse/
├── domain/ScoreTableSplitPolicyTest.java  [NEW] 纯核心单测
├── application/ScoreFactShadowPipelineTest.java [MOD] 回归：单表行为不变
└── application/PdfEvidencePipelineTest.java [MOD] 多表端到端场景（Red 用例）
```

**Structure Decision**: 沿用 scoreparse 既有 domain/application 分层。拆分策略放 domain（纯核心可单测），
管道接线放 application。不新建包。

## Phase 0: Research

无 NEEDS CLARIFICATION。既有代码已确认：
- `ScoreFact.sourceTableId` 随每个事实记录，`factId` 含 tableId 前缀跨表唯一（分组键可用）。
- `ScoreTableSummaryPolicy.extractDeclaredTotals` 返回的 `DeclaredTotalEvidence` 含 `tableId`（按表过滤键可用）。
- `ScoreCompletenessPolicy.evaluateCompleteness(facts, text, structurallyValid, declaredTotals)` 已是纯函数，可逐表复用。
- `ScoreFact.toCandidate()` 仅对 `AUTO_APPROVED` 状态事实返回候选（落库安全边界已具备）。
- `run_summary` 为 `score_parse_task` 上的 JSON 字符串列，扩展拆分明细键无 schema 变更。

## Phase 1: Design

### 核心设计：ScoreTableSplitPolicy（domain，纯核心）

```text
Input:  List<TableOutcome>（每表 grid/mapping/facts/structurallyValid）
        + List<DeclaredTotalEvidence>（全表汇总）
        + String tenderSectionText
Output: TableSplitResult
        ├── List<TableCompleteness>  每张独立表一份完整性结果
        │     ├── tableId
        │     ├── ScoreCompletenessResult  completeness（独立校验结果）
        │     └── boolean  hasOwnDeclaredTotal（是否被识别为独立评分标准）
        └── ScoreCompletenessResult  documentLevel（文档级聚合，决策取最差）
```

**分组规则**：
1. 按 `sourceTableId` 将 facts 分组；按 `tableId` 将 declaredTotals 归属到对应表。
2. 有本表结构化总分证据的表 → 独立评分标准，单独调 `evaluateCompleteness`（传本表 facts + 本表 declaredTotals）。
3. 无本表结构化总分证据的表 → 合并为一个"无证据组"（合成 tableId=`NO_DECLARED_TOTAL`），
   传空结构化证据走正文/默认 100 兜底。单表无证据时该组即现状行为（回归零变化）。
4. 结构破损表（`structurallyValid=false`）：facts 为空，按 `STRUCTURE_INCOMPLETE` 标记该表，不产出落库候选。

**文档级聚合决策优先级（最差优先）**：
`PARSE_FAILED` > `STRUCTURE_INCOMPLETE` > `REVIEW_REQUIRED` > `AUTO_APPROVED`
（用于保留任务级 `decisionStatus` 字段语义，不阻塞按表落库）。

### 管道接线

**ScoreFactShadowPipeline**：
- `run()` 内部改用 `ScoreTableSplitPolicy` 计算：先按表分组校验，再聚合文档级结果。
- `ShadowResult` 新增 `tableCompletenesses`（每表独立结果），保留既有 `completeness` 字段
  = 文档级聚合结果（保持调用方兼容）。

**StructuredTableExtractionHandler.extractEvidencePipeline**：
- `leafCandidates` = 所有决策 `AUTO_APPROVED` 的表的 `AUTO_APPROVED` 叶子事实 `toCandidate()` 聚合
  （不再要求整份文档 AUTO_APPROVED）。
- 返回 `Result` 增加分表完整性列表透传。

**StructuredTableExtractionHandler.markEvidencePipelineCompleted**：
- 落库条件从 `decision == AUTO_APPROVED && !candidates.isEmpty()` 改为 `!candidates.isEmpty()`
  （任一表通过即落库通过表的项）。
- `run_summary` 增加 `tableSplits` 键：`[{tableId, declaredTotal, decision, fallbackReason}]`。
- `task.decisionStatus` / `fallbackReason` 保持文档级聚合值（最差决策语义不变）。
- 结构破损处理（FR-008）：破损表不产出候选；有通过表则照常落库通过表；全部表破损时保持现状
  `STRUCTURE_INCOMPLETE`。

### 关键行为矩阵

| 场景 | 表 A | 表 B | 文档级决策 | 落库 |
|------|------|------|-----------|------|
| 单表自洽 | AUTO_APPROVED | - | AUTO_APPROVED | 落库（现状不变） |
| 多表全自洽（各声明不同总分） | AUTO_APPROVED | AUTO_APPROVED | AUTO_APPROVED | 两表全落库（修复核心场景） |
| 表 A 自洽 / 表 B 不自洽 | AUTO_APPROVED | REVIEW_REQUIRED | REVIEW_REQUIRED | 落库表 A，表 B 保留复核 |
| 表 A 自洽 / 表 B 结构破损 | AUTO_APPROVED | STRUCTURE_INCOMPLETE | STRUCTURE_INCOMPLETE | 落库表 A，表 B 按破损处理 |
| 全表不自洽 | REVIEW_REQUIRED | REVIEW_REQUIRED | REVIEW_REQUIRED | 不落库（现状） |
| 全表无独立总分证据 | 无证据组（合并校验） | - | 同现状 | 同现状（回归零变化） |

## Complexity Tracking

无 Constitution 违规，无需豁免记录。

## 后续

- 风险评估（表间 code 重复）→ 已在 spec Assumptions 记录：多表场景指纹必然变化 → `persistItems`
  走"清旧打分"分支，不触发 code 匹配保留，串号风险不落地，无需加 score_item 字段。
- 交付顺序：Red 测试（PdfEvidencePipelineTest 多表用例）→ 纯核心 `ScoreTableSplitPolicy` → 管道接线 →
  全量回归 → ArchUnit/静态门禁。

## 2026-09-13 修复切片：合并起始行双事实建模

### 根因与边界

`DeterministicScoreFactExtractor` 当前以 `Map<Integer, ScoreFact>` 固定“一物理行一事实”。纵向合并
起始行被有效续行引用后成为父项，但该行最高分列实际属于第一个子项；由于 `resolveWeight` 优先显式列，
父项错误取得首个子项分值，同时首个子项没有独立事实。修复只在五个可判定条件同时满足时拆分：

1. 维度格是非 continuation 的合并起始格，且 `rowspan > 1`；
2. 维度标题中存在唯一、可定位的括号总分；
3. 本行细则非空；
4. 显式最高分列可解析为正数。
5. 合并范围内至少一个续行同时具有非空细则和正显式最高分，排除纯格式性空白合并。

### 类型与数据流设计

- 继续保持 `extract(CanonicalTableGrid, TableColumnMapping)` 公共签名不变。
- Pass 1 保留 `factByRow` 作为每行叶子/普通事实索引，并新增 `parentFactByOriginRow` 保存仅在上述五个条件下创建的父事实。
- 父事实：`factId(..., "parent")`、空 code、标题总分与 `DIMENSION_TITLE_MAX_SCORE` 证据、`REVIEW_REQUIRED`，避免进入自动计分集合。
- 首个叶子：`factId(..., "leaf")`、保留 code/细则/全行证据、显式最高分与 `EXPLICIT_WEIGHT_COLUMN` 证据、父 ID 指向新父事实。
- Pass 2 只索引映射后的维度列合并区域，continuation 优先链接 `parentFactByOriginRow`；编号列等其他纵向合并不得按行覆盖维度 origin，未拆分的历史形态仍链接原起始行事实。
- 输出顺序固定为同一行的 parent → first leaf → 后续物理行，保证快照可读且 factId 重放稳定。

### 拒绝方案

- 不颠倒全局权重优先级：会破坏普通行的显式最高分真相。
- 不放宽 `CATEGORY_SUM_MISMATCH`/`WEIGHT_SUM_MISMATCH`：会让错误层级自动落库。
- 不按 181 归一化：两张独立 100 分表不能合并伪装成一张 100 分表。
- 不修改 Sidecar：其 v2 Artifact 已完整表达 rowspan、continuation、标题和最高分，错误发生在后端语义投影。

### 验证与回滚

先用脱敏 v2 fixture 和纯核心单测锁定 RED，再做单文件生产修复；运行抽取、完整性、分表、PDF 兼容、
架构和 golden 回放。回滚只需撤销本切片的抽取器变更；数据库与外部契约均无迁移、无状态变更。
