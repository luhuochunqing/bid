# Plan: 059 评分解析证据化 Phase 0—运行档案可追溯

## 代码结构

```text
backend/src/main/java/com/xiyu/bid/scoreparse/
├── domain/
│   ├── ScoreParseDecision.java            [NEW] pipeline 决策枚举
│   ├── ScoreParsePipeline.java            [NEW] pipeline 版本常量
│   └── RecallOutcome.java                 (不变)
├── application/
│   ├── RunSummaryAssembler.java           [MOD] 新增 pipelineVersion/decisionStatus/fallbackReason 键
│   └── ScoreCandidatePostProcessor.java   [MOD] Processed 增 decisionStatus/fallbackReason
├── entity/
│   └── ScoreParseTask.java                [MOD] 新增三列
├── dto/
│   └── ScoreParseItemsDTO.java            [MOD] Meta 增 evidenceDecision/fallbackReason/pipelineVersion
└── service/
    └── ScoreParseAppService.java          [MOD] 落库前写入 pipelineVersion/decisionStatus/fallbackReason
```

## 数据库

```sql
-- V{auto}__score_parse_task_evidence_pipeline_phase0.sql
ALTER TABLE score_parse_task
  ADD COLUMN pipeline_version VARCHAR(32) NULL AFTER completed_at,
  ADD COLUMN decision_status  VARCHAR(32) NULL AFTER pipeline_version,
  ADD COLUMN fallback_reason  VARCHAR(128) NULL AFTER decision_status;

CREATE INDEX idx_score_parse_task_decision ON score_parse_task (decision_status);
```

回滚脚本同名 `U{auto}__score_parse_task_evidence_pipeline_phase0.sql`：删除三列 + 索引。

迁移规范使用 `scripts/next-migration-version.sh --reserve` 预约版本号。

## 行为变化

- Phase 0 不替换解析链路；现有解析依然成功落库。
- 新增决策状态写入：
  - 现有运行成功且未走 `WEIGHT_NORMALIZED` 路径：决策 = `AUTO_APPROVED`（与历史一致）。
  - 现有运行触发 `WeightNormalizationPolicy`：决策 = `REVIEW_REQUIRED`，`fallbackReason = "RESORT_TO_NORMALIZATION"`。
  - Sidecar 不可达且 Markdown 回退：决策 = `REVIEW_REQUIRED`，`fallbackReason = "RESORT_TO_MARKDOWN"`。
  - 命中 0 项数失败：决策 = `PARSE_FAILED`。
  - 后处理器 `failMessage` ≠ null：决策 = `PARSE_FAILED`。
- 前端零强制变化；Meta 透出 `evidenceDecision` / `fallbackReason` / `pipelineVersion`，前端可在后续 Phase 5 接入 UI。

## 风险与对策

| 风险 | 对策 |
|---|---|
| 旧字段读取方在新 run-summary 序列化中抛错 | 在 `RunSummaryAssembler` 字符串构造后置，新增 3 个键，旧键顺序保持 |
| 新列默认值与历史数据冲突 | 全部新增列允许 NULL；Phase 0 不重写历史任务，仅新任务填值 |
| 前端 Meta 字段缺失导致 layout 异常 | 仅追加可选字段，前端可选读取 |
| 迁移文件版本号撞号 | `next-migration-version.sh --reserve` 预约；commit 前再校验 |

## Phase 7：项目 1381 声明总分结构化收敛（2026-09-12）

### 纯核心

- 新增 `ScoreTableSummaryPolicy`：对 `CanonicalTableGrid + TableColumnMapping` 识别汇总行并提取
  不可变声明总分证据；同时提供汇总行判定，供 `DeterministicScoreFactExtractor` 复用。
- `ScoreCompletenessPolicy` 接收结构化总分证据：唯一值优先，冲突值硬阻断；无证据时才使用
  Markdown 文本启发式/默认 100。`ScoreCompletenessResult` 增加 `declaredTotalSource`。

### 命令式外壳

- `ScoreFactShadowPipeline` 在每张评分表生成事实的同时收集汇总证据，并一次性传给完整性策略。
- `StructuredTableExtractionHandler` 的持久化安全边界不变：仅 `AUTO_APPROVED` 覆盖 `score_item`；
  `REVIEW_REQUIRED` 仍只保存事实和复核快照。

### Red → Green

1. 先把 `PdfEvidencePipelineTest` 的 1381 正文改成 Sidecar `/convert` 的真实 `<br>` 表格形态，
   证明当前实现误取局部 30 分并失败。
2. 新增 `ScoreTableSummaryPolicyTest`，覆盖 100 分结构化提取、汇总行不生成事实、冲突阻断。
3. 实现纯核心和管道接线，只运行定向测试直至 Green。
4. 运行 scoreparse 相关回归、ArchUnit/静态门禁与 Sidecar PDF 表格契约测试。

### 风险与对策

| 风险 | 对策 |
|---|---|
| 多张评分表各有小计，被误当成唯一总分 | 不同结构化分值视为冲突并转人工复核 |
| 汇总行与事实过滤规则再次漂移 | 两处统一复用 `ScoreTableSummaryPolicy.isSummaryRow` |
| 无表尾总分的既有文档行为改变 | 结构化证据为空时保持正文/默认 100 兼容路径 |
| 为通过 1381 放宽字符串正则 | 回归输入保留 `<br>`，修复只允许来自 CanonicalTableGrid |
