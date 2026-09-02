# Change: 评分解析证据化 Phase 0—冻结 legacy 与运行档案可追溯

## Why
当前评分解析链路在多轮 prompt 与正则补丁后，仍会在事故样本上输出错误的 100 分（例如合并表格父项与子项重复计分、Markdown 路径漏掉 vMerge 与 gridSpan、归一化把"漏项"伪装成"已收敛"）。
为避免继续在 prompt/正则上修补，必须先冻结现有自动落库路径的可适用范围，并把每次解析的来源、决策与回退原因纳入运行档案，作为后续 Evidence Pipeline 的对照基线。

## What Changes
- `score_parse_task`：新增 `pipeline_version`、`decision_status`、`fallback_reason` 三列；现有 `run_summary` JSON 增加 `pipelineVersion`、`decisionStatus`、`fallbackReason` 三个键，旧调用方零改动。
- 旧链路（正则/结构化抽取/LLM 抽取/全局归并/归一化）统一标 `LEGACY_FALLBACK`；在 `ScoreCandidatePostProcessor` 写入 `fallback_reason` 解释为何降级，禁止旧路径产生 `AUTO_APPROVED` 任务决策。
- 决策状态枚举 `ScoreParseDecision`：`AUTO_APPROVED`、`REVIEW_REQUIRED`、`STRUCTURE_INCOMPLETE`、`PARSE_FAILED`。Phase 0 仍维持当前"链路运行成功即可落库"的实际行为，但记录该决策，不改变既有调用流程。
- 新增"金标语料登记表"目录 `docs/lessons/scoreparse-golden-corpus/index.md`，登记至少事故 1347 / 1358 / 1362 脱敏样本及 5 类合成夹具。

## Impact
- Affected code：
  - `backend/src/main/java/com/xiyu/bid/scoreparse/entity/ScoreParseTask.java`
  - `backend/src/main/java/com/xiyu/bid/scoreparse/application/RunSummaryAssembler.java`
  - `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreCandidatePostProcessor.java`
  - `backend/src/main/java/com/xiyu/bid/scoreparse/dto/ScoreParseItemsDTO.java`
- Affected migrations：`V{auto}_score_parse_task_evidence_pipeline_phase0.sql` + 同名回滚脚本。
- Affected docs：`docs/lessons/scoreparse-golden-corpus/index.md`（新增，最低限度登记，未涉及客户姓名）。
- 旧链路输入的项目仍可在 Phase 0 完成解析（行为不变），但任务决策记录为 `LEGACY_FALLBACK` + `REVIEW_REQUIRED` 等显式状态；前端不强制变更 UI，仅在 meta.runSummary 中透出。
- 不在 Phase 0 范围：替换抽取逻辑、引入新事实表、替换归一化、修改 spec 057 已有行为。

## Non-Goals
- 不替换 Sidecar、CanonicalTableGrid、ScoreFact（属 Phase 1/2）。
- 不删除现有 `WeightNormalizationPolicy`。
- 不修改前端抽屉展示（Phase 5）。
- 不引入 LLM 受限操作契约（属 Phase 3）。
