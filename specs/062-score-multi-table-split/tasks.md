---

description: "任务清单：多表评分标准自动拆分（062）"
---

# Tasks: 多表评分标准自动拆分（062-score-multi-table-split）

**Input**: Design documents from `/specs/062-score-multi-table-split/`

**Prerequisites**: plan.md（已生成）、spec.md（已生成）、data-model.md（已生成）、quickstart.md（已生成）

**Tests**: 本 feature 按 TDD（Red→Green）执行，测试任务为强制前置。

**Organization**: 按用户故事分组（US1 多表拆分自动落库 P1 / US2 任务元数据可追溯 P2）

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

- 后端纯核心：`backend/src/main/java/com/xiyu/bid/scoreparse/domain/`
- 后端应用层：`backend/src/main/java/com/xiyu/bid/scoreparse/application/`
- 后端测试：`backend/src/test/java/com/xiyu/bid/scoreparse/`
- 无前端改动、无 DB 迁移（见 spec Assumptions）

---

## Phase 1: Setup（基线确认）

**Purpose**: 确认改造基线可复现多表缺陷（Red 前置）

- [ ] T001 运行既有 scoreparse 测试确认基线全绿：`mvn test -Dtest=ScoreFactShadowPipelineTest,PdfEvidencePipelineTest,ScoreTableSummaryPolicyTest`

---

## Phase 2: Foundational（Red 测试——多表拆分失败用例）

**Purpose**: 先证明当前实现无法处理多表文档，作为拆分实现的验收基准

**⚠️ CRITICAL**: 以下测试必须先写、先 FAIL（Red），再进入 Phase 3 实现

- [ ] T002 [P] [US1] 在 `backend/src/test/java/com/xiyu/bid/scoreparse/application/PdfEvidencePipelineTest.java` 新增多表端到端失败用例：构造含两张独立评分表（技术表声明 60 分、商务表声明 40 分，各表叶子权重自洽）的 Sidecar 表格文档，断言改造前整份文档决策为 `REVIEW_REQUIRED` 且 `score_item` 落库为空（Red）
- [ ] T003 [P] [US1] 新增 `backend/src/test/java/com/xiyu/bid/scoreparse/domain/ScoreTableSplitPolicyTest.java` 纯核心单测骨架，覆盖：多表各声明不同总分且各自自洽 → 两表均 `AUTO_APPROVED` 且文档级 `AUTO_APPROVED`；表 A 自洽/表 B 不自洽 → A `AUTO_APPROVED`、B `REVIEW_REQUIRED`、文档级 `REVIEW_REQUIRED`；全表无独立总分证据 → 无证据组合并校验行为与现状一致；单表自洽 → 与 `ScoreCompletenessPolicy` 直接调用结果逐字段一致（Red，编译前先定义 `ScoreTableSplitPolicy` 签名）

**Checkpoint**: Red 成立——多表场景当前 FAIL，证明缺陷存在

---

## Phase 3: User Story 1 - 多表自动拆分并自动落库 (Priority: P1) 🎯 MVP

**Goal**: 每张独立评分表按本表声明总分独立校验，通过的表自动落库为正式评分项，多表不再互相拖累

**Independent Test**: `ScoreTableSplitPolicyTest` 纯核心单测 + `PdfEvidencePipelineTest` 多表端到端用例由 Red 转 Green；多表文档解析后 `score_item` 出现两表全部评分项

### Implementation for User Story 1

- [ ] T004 [US1] 新增 `backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreTableSplitPolicy.java`（纯核心，<100 行）：按 `ScoreFact.sourceTableId` 分组 facts；按 `DeclaredTotalEvidence.tableId` 归属本表证据；有本表结构化总分证据的表 → 独立调 `ScoreCompletenessPolicy.evaluateCompleteness`；无证据表合并为 `NO_DECLARED_TOTAL` 组走正文/默认 100 兜底；结构破损表标记 `STRUCTURE_INCOMPLETE`；输出 `TableCompleteness`（tableId/hasOwnDeclaredTotal/completeness）+ `TableSplitResult`（tables + documentLevel 聚合，决策取最差：PARSE_FAILED > STRUCTURE_INCOMPLETE > REVIEW_REQUIRED > AUTO_APPROVED）
- [ ] T005 [US1] 修改 `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreFactShadowPipeline.java`：`run()` 改用 `ScoreTableSplitPolicy` 计算每表独立结果；`ShadowResult` 新增 `tableCompletenesses` 字段（保留 `completeness` = 文档级聚合结果，保持既有调用方兼容）；同步更新两个构造路径与 `collectDeclaredTotals` 归表
- [ ] T006 [US1] 修改 `backend/src/main/java/com/xiyu/bid/scoreparse/application/StructuredTableExtractionHandler.java`：`extractEvidencePipeline` 的 `leafCandidates` 改为聚合所有决策 `AUTO_APPROVED` 表的 `AUTO_APPROVED` 叶子候选；`Result` 透传分表完整性列表；`markEvidencePipelineCompleted` 落库条件从 `decision == AUTO_APPROVED && !candidates.isEmpty()` 放宽为 `!candidates.isEmpty()`（任一表通过即落库通过表）；结构破损表不产出候选、有通过表则照常落库、全部表破损时保持 `STRUCTURE_INCOMPLETE` 现状

**Checkpoint**: US1 完成——T002/T003 转 Green，多表文档正式评分项不再为空

---

## Phase 4: User Story 2 - 任务元数据可追溯拆分结果 (Priority: P2)

**Goal**: `run_summary` 记录每张评分表的独立决策（tableId/声明总分/决策/fallbackReason），支撑人工复核定位

**Independent Test**: `RunSummaryAssemblerEvidencePipelineTest` 断言 run_summary 含 `tableSplits` 键且与 `score_parse_fact.source_table_id` 对应

### Tests for User Story 2 ⚠️

- [ ] T007 [US2] 修改 `backend/src/test/java/com/xiyu/bid/scoreparse/application/RunSummaryAssemblerEvidencePipelineTest.java`：新增断言 run_summary JSON 含 `tableSplits` 数组，每项含 tableId/declaredTotal/decision/fallbackReason（Red 先行）

### Implementation for User Story 2

- [ ] T008 [US2] 修改 `backend/src/main/java/com/xiyu/bid/scoreparse/application/StructuredTableExtractionHandler.java` 的 `markEvidencePipelineCompleted`：run_summary 增加 `tableSplits` 键（每张表 tableId、declaredTotalScore、decision、fallbackReason）；`task.setDecisionStatus`/`fallbackReason` 保持文档级聚合值；日志打印分表拆分明细

**Checkpoint**: US1 + US2 均完成且独立可测

---

## Phase 5: Polish & 回归门禁

**Purpose**: 回归安全、架构合规、质量门禁全绿

- [ ] T009 [P] 回归测试：`ScoreFactShadowPipelineTest` 单表行为不变（逐字段对比）；`ScoreCompletenessPolicyTest`/`ScoreTableSummaryPolicyTest` 全绿
- [ ] T010 [P] 架构门禁：`mvn test -Dtest=ArchitectureTest,ScoreParseArchitectureTest` 保持全绿；确认新增代码无 2 参数 `Collectors.toMap`
- [ ] T011 [P] 全量回归：`cd backend && mvn test`（scoreparse 相关）；`npm run build` + `npm run check:front-data-boundaries` + `npm run check:doc-governance` + `npm run check:line-budgets`（前端零改动验证）
- [ ] T012 按 `specs/062-score-multi-table-split/quickstart.md` 执行验证清单并记录结果

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1（基线）** → **Phase 2（Red）** → **Phase 3（US1 实现）** → **Phase 4（US2 实现）** → **Phase 5（回归）**

### Within Each User Story

- Red 测试（T002/T003）MUST 先写并 FAIL，再实现（T004/T005/T006）
- 纯核心（T004）→ 管道接线（T005）→ 命令式外壳（T006）
- US2（T007/T008）依赖 US1 的 T006（tableSplits 写入点在同一方法）

### Parallel Opportunities

- T002 与 T003 可并行（不同测试文件）
- T007 与 T006/T008 可并行准备，但 T008 实现需在 T006 之后
- T009/T010/T011 可并行（不同验证维度）

---

## Implementation Strategy

### MVP First（仅 US1）

1. T001 基线确认 → T002/T003 Red → T004 纯核心策略 → T005 管道 → T006 落库放宽
2. **STOP and VALIDATE**：T002/T003 转 Green，`PdfEvidencePipelineTest` 多表场景通过
3. 交付 US1（核心缺陷修复）

### Incremental Delivery

1. US1 完成 = 核心价值（正式评分项不再为空）
2. 再叠加 US2（审计可追溯性）
3. 最后全量回归门禁

---

## Notes

- [P] 任务 = 不同文件、无未完成依赖
- 严格 TDD：测试先 FAIL，再实现转 Green
- 提交按逻辑分组：文档（spec/plan/data-model/quickstart/tasks）一个 commit，Red 测试一个 commit，实现一个 commit
- 完成时须跑 `agent-finish-task.sh` 收尾门禁（wiki checkpoint + 三重合入检查）
