---
title: spec 059 评分解析证据化 Phase 0-4 落地结论 + 合入前评审与去噪重构
space: engineering
category: guide
updated: 2026-09-03
health_checked: 2026-09-03
created: 2026-09-02
tags: [score-parse, spec059, 评分解析, DocumentArtifact, CanonicalTableGrid, ScoreFact]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreParseDecision.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/grid/CanonicalTableGridFactory.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreFact.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/SidecarDocumentArtifactExtractor.java
  - document-converter-sidecar/table_extractor.py
  - docs/lessons/scoreparse-golden-corpus/index.md
backlinks:
  - _index
  - score-parse-service
  - architecture
  - lessons-learned
---

# spec 059 评分解析证据化 Phase 0-2 落地结论 + 合入前评审修复

## 背景

事故样本 1347（商务部分父项重复计分）、1358（LLM 复述丢 detail）、1362（双重合并 + 大类与子项混淆）反复复发，根源是「多路召回后 LLM 与归一化兜底」链路没有任何证据可审计。
spec 059 把链路切到「原始 Word 结构 + 确定性事实 + 原文证据 + 受限 LLM 语义标注 + 完整性阻断」，分 6 个 phase 推进。
本 wiki 记录 Phase 0（冻结 legacy + 运行档案）与 Phase 1（DocumentArtifact v2 + CanonicalTableGrid）落地后的状态、决策与下一步。

## 关键决策

| 决策 | 理由 |
|---|---|
| 旧链路打 LEGACY_FALLBACK 标签，禁止 AUTO_APPROVED | 让旧路径显式可识别，避免静默归一化继续伪装成功 |
| DocumentArtifact schemaVersion=2.0，要求 Sidecar 返回 cellId + XML 路径 | 单元格必须可回溯；X-Schema-Version: 1.x 保留旧路径用于过渡 |
| FP-Java 严格不抛业务异常：CanonicalCell / MergedRegion 的硬不变量走 Optional<validate>() | FPJavaArchitectureTest.pure_core_must_not_use_exceptions_for_business_flow 拦截 throws / try-catch / Throwable ctor |
| 校验失败统一收口到 StructureValidationResult.violations() + Status.STRUCTURE_INCOMPLETE | 调用方按状态决定是否落库、是否调 LLM、是否调归一化 |
| 旧 SidecarTableExtraction 保留，仅作为 legacy 路径，新版不可降级 | Phase 5 切换需要双轨回放；金标未覆盖前不允许自动通过 |
| Golden corpus（1347 / 1358 / 1362 + 5 类合成夹具）只写脱敏描述，不放真实文件 | 真实文件仅入 XIYU_EVAL_CORPUS_DIR 本地路径 |

## Phase 0：运行档案可追溯 + 决策状态机（commit 48ac7fc32）

- score_parse_task 新增三列：pipeline_version / decision_status / fallback_reason（V1207 + U1207 回滚）
- ScoreParseDecision 枚举：AUTO_APPROVED / REVIEW_REQUIRED / STRUCTURE_INCOMPLETE / PARSE_FAILED
- ScoreParsePipeline 常量：LEGACY_FALLBACK / EVIDENCE_PIPELINE_V1
- RunSummaryAssembler 9 参重载透出三键，旧键顺序保持
- ScoreCandidatePostProcessor.Processed 携带决策：归一化 → REVIEW_REQUIRED/RESORT_TO_NORMALIZATION；LLM 幻觉丢弃率超阈 → REVIEW_REQUIRED/RESORT_TO_LLM_MERGE；0 项 → PARSE_FAILED/ZERO_VALID_ITEMS
- ScoreParseItemsDTO.Meta 末尾追加三字段
- RunSummaryAssemblerEvidencePipelineTest 覆盖序列化与 schema 兼容
- 65/65 测试绿（RunSummary* + ScoreParseApp* + PostProcessor* + ArchitectureTest）

行为变化：现有解析仍成功落库；新增决策状态写入；前端 Meta 透出三键，未强制变更 UI。

## Phase 1：DocumentArtifact v2 契约 + CanonicalTableGrid 结构校验（commit a63daa166）

### Sidecar 端（Python）
- extract_tables_artifact() 返回 schemaVersion=2.0 契约：artifact header 携带 sha256/fileType/extractionVersion；每个 cell 携带 cellId + rawText/normalizedText + rowspan/colspan/isMergedContinue + sourceXmlPath（XPath-like Word XML 锚点）+ mergedRegions 列表
- 合并覆盖使用 vMerge 严格遍历（Phase 1 提交时该说法尚未完全成立——`_v_merge_span_from_tc` 仍是「发现任一 continuation 推断整行」的近似逻辑，2026-09-03 评审修复才真正实现严格遍历，见下文评审修复 P0-2）
- extract_tables() 作为 v1 兼容入口保留，内部委托 v2 投影回旧 shape
- app.py 新增 X-Schema-Version header：默认 v2，显式 1.x 走旧路径
- test_extract_tables_v2.py 14 例：cellId 唯一性、XML 路径、双文本、vMerge/gridSpan/combined-VH 合并、多表、v1 兼容、边缘案例

### 后端（Java）
- DocumentArtifact / CanonicalTable / MergedRegionDto / CanonicalCellDto 不可变 record，对应 Sidecar JSON schema
- CanonicalTableGrid / CanonicalCell / MergedRegion / StructureValidationResult 不可变 record（domain/grid，零框架依赖）
- CanonicalTableGridFactory.fromSidecarTable() 强制执行不变量：
  - cellId 唯一
  - (row, col) 满覆盖
  - mergedRegions.startCellId 存在
  - origin span 与 cell 一致
  - 合并区域内 cell 必须 isMergedContinue=true
  - rowspan/colspan 不越界
- 任何失败汇总到 StructureValidationResult.violations()，Status 置为 STRUCTURE_INCOMPLETE
- SidecarDocumentArtifactExtractor 走 v2 路径，失败抛 DocumentArtifactUnavailable，调用方必须按 STRUCTURE_INCOMPLETE 处理，**不得降级到 LLM / 归一化**
- CanonicalTableGridTest 31 例：record 不变量、合并校验、duplicate cellId、缺失 grid 槽位、越界 mergedRegion、缺少 continuation、空表/零维表、DocumentArtifact 元数据校验

### 回归

76/76 绿（RunSummary* + ScoreParseApp* + PostProcessor* + ArchitectureTest + FPJavaArchitectureTest + MaintainabilityArchitectureTest + CanonicalTableGridTest）。

## Phase 2：确定性 ScoreFact + 合入前评审修复（commit 1d880e5ac → 7b63f98de）

- 不可变领域对象：ScoreFact / ScoreEvidence / ScoreRule / ScoreFactValidator（domain，纯核心）
- DeterministicScoreFactExtractor：显式分值列 > 标题括号分值 优先级生成 weightEvidence；vMerge origin 行 ↔ 延续行自动挂父子
- ScoreFact → ScoreCandidate 适配器 `toCandidate()`（落库接线按计划属 Phase 4/5）
- 影子组装管道 ScoreFactShadowPipeline：Artifact → CanonicalTableGrid → ScoreFact 只读端到端验证器；SidecarDocumentArtifactExtractor 补 @Component 进容器（生产主链路切换仍属 Phase 3/4）

### 合入前评审修复（5 项，2026-09-03）

| # | 问题 | 修复 |
|---|---|---|
| P0-1 | `isLeaf()` 把 parentFactId 为空（根）当叶子，父项放行/子项被拦，单测反向断言钉死 | 叶子改为集合属性：`ScoreFact.isLeaf(fact, facts)` + `parentIds(facts)`，未被引用才是叶子；成对断言重写 |
| P0-2 | `_v_merge_span_from_tc` 按"任一 continuation"近似计数，多列错位合并互相污染跨度 → 正常表被误判 STRUCTURE_INCOMPLETE | 传入 (logical_col, colspan)，按 gridSpan 累加定位覆盖列，只认该列自己的 continuation；新增错位双列回归测试 |
| P1-3 | 金标语料登记表为空模板，Phase 0 验收未闭环 | 登记表补齐事故 1347/1358/1362 + 5+2 类夹具全部预期；GoldenCorpusBaselineTest 8 例为可执行基线；跨页页码锚点/nightly 评估诚实挂账 |
| P1-4 | Phase 1/2 新类零调用零测试（孤岛） | 影子管道 + Bean 接线 + 4 例端到端测试；生产切换仍按计划 Phase 3/4 |
| P2-5 | Phase 3 LLM 契约提前混入 Phase 2 提交且操作集与 Plan 不符 | 不重写历史（分支已推送 + PR 已开），就地对齐 Plan 五操作 KEEP/CLASSIFY/LINK_PARENT/MARK_DUPLICATE/REVIEW_REQUIRED；删 AddObservation；LinkParent 补跨表拒绝（提前实现 T302） |

核心语义（后续 Phase 必须知道）：**叶子是集合属性，父项=被子项引用**。合并大类 origin 行即使自带细则分值，也会被延续行引用成父项——保留在快照但被 validateScoringSet 拦截出自动评分集，宁可漏进人工不可静默错分；权重差额由 Phase 4 完整性策略兜底 REVIEW_REQUIRED。

回归：Java 定向 631 例 0 失败（T009 口径 + 全部新增）；Sidecar pytest 22 passed。

回归：Java 定向 631 例 0 失败（T009 口径 + 全部新增）；Sidecar pytest 22 passed。

## Phase 3：限制 LLM 为可审计语义适配器 + 去噪重构（落地结论）

- **契约重构 (T301)**：
  - 传输层 Wire DTO `RawFactOperation` 显式内置 `weight`、`detail`、`sourceText`、`newFact` 陷阱字段，任何造项或改分企图均被拦截。
  - `LlmFactOp` 密封接口统一要求 `factId()`、`confidence()`、`reasonCellIds()`，严格限制为 5 项受限操作：`KEEP`、`CLASSIFY`、`LINK_PARENT`、`MARK_DUPLICATE`、`REVIEW_REQUIRED`。
  - 统一映射：在 `RawFactOperation.toDecision` 中收拢全局映射，消除编排器与执行器间的映射样板代码。
- **安全门禁与不回写纪律 (T302)**：
  - 纯核心校验器 `LlmFactOperationValidator` 严格执行 6 道门禁：
    1. 拒绝未知 `factId`
    2. 拒绝非法动作（`CREATE_SCORE_FACT` / `UPDATE_WEIGHT` / `DELETE_FACT`）
    3. 拒绝受保护字段篡改
    4. 拒绝跨表 cell 引用（`cellIdBelongsToTable`）
    5. 拒绝跨表父子挂载（同表 `sourceTableId` 校验）
    6. 拒绝置信度不足（< 0.80）语义分类
  - 违规处理纪律：绝不将脏数据回写事实池，受影响事实一律打标为 `REVIEW_REQUIRED`。
- **编排器与守卫去假背书 (T303)**：
  - `ScoreMergeResultGuard`：彻底删除 `poolIdentityMatched` 与 `PoolEntry`，废止“池文本互含即放行”规则，无原文包含的候选直接作为幻觉丢弃。
  - `ScoreGlobalMergeOrchestrator`：新增 `adjudicateFacts(List<ScoreFact>, String fullText)`，仅在既有确定性事实池上运行；LLM 故障或不可用时 fail-open，确定性事实与 `AUTO_APPROVED` / `REVIEW_REQUIRED` 状态完全稳定可复现。
  - `ScoreCandidatePostProcessor`：明确限制旧 LLM 召回（`recheckGaps`）仅作为人工复核建议（标 `REVIEW_REQUIRED` + `RESORT_TO_GAP_RECHECK`），禁止直接填补确定性事实的权重缺口。
- **验收与质量证据 (T304)**：
  - `ScoreFactAdjudicationAcceptanceTest` 8 例端到端验收全部通过。
  - 全量评分相关测试集 499 例全部绿（0 失败 0 错误）。
  - FP-Java 8 项架构合规全部通过，所有类行预算均低于 300 行硬限。

## Phase 4：完整性阻断、人工复核与取消静默归一化（落地结论）

- **7 大维度完整性策略 (T401)**：
  - 新增纯核心领域策略 `ScoreCompletenessPolicy`，落库前执行 7 大硬不变量校验：
    1. 声明总分：从标书正文中精准提取显式分值声明（如“总分100分”、“满分120分”），默认 100
    2. 末级权重合计：以集合语义提取末级叶子事实（`ScoreFact.isLeaf`），其权重合计必须精确等于声明总分，否则阻断为 `WEIGHT_SUM_MISMATCH`
    3. 父子重复拦截：被引用的父项严禁作为末级项计分，防止 1347 重复计分事故
    4. 大类分值自洽：父项声明的分值必须精确等于其直接子项权重之和，否则阻断为 `CATEGORY_SUM_MISMATCH`
    5. 异常低候选数拦截：末级项数极少（<3 项）但正文字数较长时拦截为 `ABNORMALLY_LOW_COUNT`
    6. 缺失证据拦截：事实缺失单元格引用或权重证据无法在原文精确定位时阻断为 `MISSING_EVIDENCE`
    7. 文档表格结构破损拦截：网格破损直接阻断为 `STRUCTURE_INCOMPLETE` + `STRUCTURE_DEFECT`
- **彻底剥离静默自动归一化 (T402)**：
  - 从 `ScoreCandidatePostProcessor` 中彻底删除 `normalizationPolicy.normalize` 对候选集权重的篡改调用。
  - 权重不符预期时保留原始权重不修改，记录原始总分与告警，决策置为 `REVIEW_REQUIRED` + `WEIGHT_SUM_MISMATCH`。
  - `WeightNormalizationPolicy` 保留仅供历史结果展示和显式人工“调整权重”命令使用。
- **复核工作流数据快照 (T403)**：
  - 新增 `ScoreParseReviewSnapshot`，结构化沉淀待复核事实快照、失败项、原始总分与待确认差异，为 Phase 5 前端抽屉展开原文证据提供完整数据契约。
- **任务状态与决策生效分离 & 持久化保护 (T404 & T405)**：
  - 解析任务状态（`task.status`）与决策状态（`task.decisionStatus`）解耦：任务可正常标记 `COMPLETED`，但其决策为 `REVIEW_REQUIRED`。
  - **持久化门禁**：`ScoreParseAppService` 严格限制**只有 `decisionStatus == AUTO_APPROVED`** 才能调用 `persistItems` 覆盖当前生效 `score_item`。
  - 任何 `REVIEW_REQUIRED`、`STRUCTURE_INCOMPLETE` 或 `PARSE_FAILED` 状态下，已有 `score_item` 集合与评分结果原封不动保留，严禁先删后写。
- **验收与质量证据**：
  - `ScoreCompletenessPolicyTest` 8 例测试绿。
  - `ScoreCompletenessAcceptanceTest` 4 项核心不变量验收绿。
  - `ScoreCandidatePostProcessorTest` 6 例绿。
  - `ScoreParseAppServiceTest` 21 例绿。
  - 全量评分测试套件 429 例全绿，FP-Java 架构测试 8/8 绿，所有类行数严格低于 300 行上限。

## Phase 5-6 待办（see tasks.md）

- Phase 5：DB/API/前端迁移 + 双写 + 灰度（双轨回放脚本 T505）
- Phase 6：金标收口（跨页页码锚点挂账）+ 架构门禁 + 清理

## 索引

- 完整 spec：`specs/059-score-parse-evidence-pipeline/{proposal,plan,tasks,data-model}.md`
- 金标登记：`docs/lessons/scoreparse-golden-corpus/index.md`
- Push gate 拦截事项：lint 模块依赖 / Wiki 健康检查需要 .wiki/ 变更配套
- FP-Java 不变量：所有新增 record 的硬不变量走 Optional<validate>()，不抛业务异常