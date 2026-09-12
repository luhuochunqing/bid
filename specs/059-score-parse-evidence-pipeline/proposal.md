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

## 2026-09-12 增补：声明总分的结构化单一事实源

### Problem

项目 1381 的 PDF 已被 Sidecar 正确还原为 8 个评分项和表尾 `合计得分 / 满分（100分）`，但
`ScoreCompletenessPolicy` 仍只在转换后的 Markdown 中搜索声明总分。Markdown 把表尾拆成
`| 合计<br>得分 | 满分<br>（100分） |` 后，文本正则无法识别 100，反而把评分细则中的局部
`满分30分` 当成全表声明总分，导致 8 项合计 100 被误判为 `WEIGHT_SUM_MISMATCH`，正式
`score_item` 按安全策略保留旧值。

### Required behavior

- `isScoreTable=true` 且结构有效时，表尾汇总行必须直接从 `CanonicalTableGrid` 解析出带
  `tableId`、`row`、`cellId` 的声明总分证据。
- 同一批结构化汇总证据只有一个唯一分值时，该分值优先于 Markdown 文本启发式结果。
- 多个结构化汇总行声明不同分值时必须 `REVIEW_REQUIRED`，不得用最大值、默认 100 或文本
  正则静默裁决。
- 没有结构化汇总证据时，保留正文声明和默认 100 的兼容兜底。
- 汇总行识别由一个纯领域策略同时服务“跳过评分事实”和“提取声明总分”，禁止两套规则漂移。
- 完整性快照必须记录声明总分来源，便于从 `/review` 解释判定依据。

### Acceptance

- 以项目 1381 的真实转换形态构造回归：Markdown 包含局部 30/10 分，表尾为 `<br>` 分隔，
  Artifact 包含结构化 100 分汇总行；结果必须为 8 项、合计 100、`AUTO_APPROVED`，声明总分
  来源为结构化汇总行。
- 删除结构化汇总行后，同一 Markdown 必须继续复现 30 分误导并进入 `REVIEW_REQUIRED`，证明
  测试命中的是结构化优先级而非默认 100 偶然通过。
- 冲突的结构化汇总分值必须返回 `DECLARED_TOTAL_CONFLICT`。
