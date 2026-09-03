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
