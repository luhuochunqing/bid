---
title: spec 059 评分解析证据化 Phase 0-4 落地结论 + 合入前评审与去噪重构
space: engineering
category: guide
tags: [score-parse, spec059, 评分解析, DocumentArtifact, CanonicalTableGrid, ScoreFact]
created: 2026-09-02
updated: 2026-09-12
health_checked: 2026-09-12
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreParseDecision.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseAppService.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/WeightSumCheck.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/grid/CanonicalTableGridFactory.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreFact.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/SidecarDocumentArtifactExtractor.java
  - document-converter-sidecar/table_extractor.py
  - docs/lessons/scoreparse-golden-corpus/index.md
  - backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreCompletenessAcceptanceTest.java
  - backend/src/test/java/com/xiyu/bid/scoreparse/application/Incident1358RealCorpusGoldenTest.java
  - src/composables/projectDetail/useScoreParseDrawer.js
  - src/views/Project/stages/components/ScoreParseDrawer.vue
backlinks:
  - _index
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
  - **持久化门禁**：`ScoreParseAppService` 严格限制**只有 `decisionStatus == AUTO_APPROVED`** 才能调用 `persistItems` 覆盖当前生效 `score_item`。〔2026-09-03 更正：此旧契约已被 1358 热修复 Fix A 修订——`REVIEW_REQUIRED` 现在也落库展示并保留复核标记，仅 `STRUCTURE_INCOMPLETE` 保留现有项不覆盖，见下文「1358 漏识别热修复」〕
  - 任何 `REVIEW_REQUIRED`、`STRUCTURE_INCOMPLETE` 或 `PARSE_FAILED` 状态下，已有 `score_item` 集合与评分结果原封不动保留，严禁先删后写。〔同上更正：`REVIEW_REQUIRED` 不再整批静默丢弃；`PARSE_FAILED` 在更早的 failMessage 分支即终止，不进入落库门禁〕
- **验收与质量证据**：
  - `ScoreCompletenessPolicyTest` 8 例测试绿。
  - `ScoreCompletenessAcceptanceTest` 4 项核心不变量验收绿。
  - `ScoreCandidatePostProcessorTest` 6 例绿。
  - `ScoreParseAppServiceTest` 21 例绿。
  - 全量评分测试套件 429 例全绿，FP-Java 架构测试 8/8 绿，所有类行数严格低于 300 行上限。

## Phase 5：数据库、接口和前端迁移（落地结论）

- **Flyway 迁移与回滚 (T501)**：
  - `V1208__score_parse_evidence_pipeline_phase5.sql`：
    - `score_parse_task` 追加 `artifact_sha256` 与 `review_reason`
    - `score_item` 追加 `source_cell_ids`、`weight_evidence`、`extraction_method`、`confidence`、`validation_status`、`parent_score_item_id`（全部允许 NULL）
    - 新增不可变评分事实快照表 `score_parse_fact`、单元格证据表 `score_parse_evidence`、人工复核审计表 `score_parse_review`
  - `U1208__score_parse_evidence_pipeline_phase5.sql`：配套可逆回滚脚本
  - 通过 `EntityTableMigrationCoverageTest` 与 `FlywayRollbackScriptCoverageTest` 校验
- **应用层双写与兼容机制 (T502)**：
  - `ScoreItemPersistenceService`：支持 `persistFactsAndEvidence` 与 `persistItems` 双写模式，落库事实与证据，并将证据元数据（`sourceCellIds`、`confidence` 等）写入兼容的 `ScoreItem`。
  - 旧链路仅标为 `LEGACY_FALLBACK`，禁止作为 `AUTO_APPROVED` 决策来源。
- **版本化元数据与 API 向后兼容 (T503)**：
  - `ScoreParseItemsDTO.Meta` 追加 `artifactSha256` 与 `reviewReason`，提供重载构造器与历史完全向后兼容。
  - `ScoreItemDTO` 追加证据字段并保留 11 参数兼容构造器。
  - `ScoreParseDualWriteTest` 覆盖双写与元数据读取契约。
- **前端抽屉与原文证据展开 (T504)**：
  - `useScoreParseDrawer.js`：解析 `meta.decisionStatus`、`meta.reviewReason`，计算 `decisionBadgeType` 与 `decisionBadgeText`。
  - `ScoreParseDrawer.vue`：在第一部分工具栏中展示决策状态徽章（绿色自动通过 / 橙色待人工复核 / 红色结构异常）与复核原因，严格遵循不修改用户未确认评分值的不变量。
  - `ScoreItemDetailModal.vue`：详情弹窗支持展示表格单元格编号、提取方式与权重证据。
- **双轨回放评估 (T505)**：
  - 后端回放套件 `ScoreParseDualRunTest.java` + Node.js 评估器 `scripts/eval-scoreparse/dual-run.mjs`。
  - 对比 Legacy 与 Evidence 链路在合成金标夹具上的表现，验证零 P0 缺陷（静默归一化篡改分值被 100% 拦截并转 `REVIEW_REQUIRED`），产出 Markdown 报告 `backend/target/scoreparse-dual-run-report.md`。

## Phase 6：清理、门禁与交付（落地结论）

- **全链路金标回放套件 (T601)**：
  - 新增 `ScoreParseEvidencePipelineGoldenReplayTest.java`，端到端覆盖 5 大核心闭环：
    1. Sidecar 合并语义解析与 `CanonicalTableGrid` 结构校验
    2. 确定性 `ScoreFact` 提取与单元格证据绑定（包含 vMerge 延续行继承 origin 父项关系）
    3. LLM 操作白名单安全门禁（严格约束为 `KEEP`/`CLASSIFY`/`LINK_PARENT`/`MARK_DUPLICATE`/`REVIEW_REQUIRED`，拦截伪造 ID、低置信度及越权写）
    4. 7 维度完整性策略校验（精准拦截权重缺口与父子重复，阻断为 `REVIEW_REQUIRED`）
    5. 持久化不覆盖保护（非 `AUTO_APPROVED` 严格禁止覆盖已有评分项）〔2026-09-03 更正：契约已改为仅 `STRUCTURE_INCOMPLETE` 不覆盖；对应金标环 `testPersistenceProtection_onlyAutoApprovedCanOverwrite` 是空断言循环验证，已删除，真实门禁证据移交 `ScoreCompletenessAcceptanceTest#invariant3`，见下文「1358 漏识别热修复」〕
    6. 人工调整审计实体（`ScoreParseReview`）记录操作人、原始分值、调整后分值与原因
- **硬性架构门禁测试 (T602)**：
  - 新增 `ScoreParseArchitectureTest.java`，固化三道工程红线：
    1. `scoreparse.domain` 为纯领域核心，零 Spring/HTTP/JPA 依赖
    2. `LLM` 与 `infrastructure` 严禁直接构造 `ScoreFact`（必须由确定性提取器产出）
    3. `ScoreItemPersistenceService` 拒绝无单元格证据且无分值引用的 `AUTO_APPROVED` 项落库
- **CI 与 Nightly 指标看板 (T603)**：
  - 在 `.github/workflows/ci.yml` 与 `scripts/pre-push-gate.sh` 中挂载架构与金标回放测试。
  - 增强 `GoldenCasesReplayTest` 与 `scripts/eval-scoreparse/report.mjs`，生成包含 4 大关键维度的质量与安全看板：
    - **Lane 分布**：正则候选 vs 表格候选统计
    - **复核率 (Review Rate)**：准确统计转人工复核的任务占比
    - **结构失败率 (Structural Failure Rate)**：统计 Word 表格破损阻断率
    - **证据覆盖率 (Evidence Coverage Rate)**：统计具备原文单元格/文本锚点的比例
  - 更新 `scripts/eval-scoreparse/nightly-full.sh`，在跑批完成后自动渲染最新 KPI 看板。
- **清理与正式交付 (T604)**：
  - 自动归一化在新管线中彻底绝迹；
  - 字符串 `TableGrid` 退居启发式映射辅助，新版主入口全量统一到 `CanonicalTableGrid`；
  - 评分相关全量测试集 511 例全绿，FP-Java 架构契约与代码行预算 100% 达标。

## 1358 漏识别热修复（2026-09-03，Fix A + Fix B，commit ce57e505b）

### 现象
项目 1358 评分解析实际成功（11 项 / 100 分闭环），前端却表现为"评分项漏识别 / AI 越改越错"。

### 根因（两层缺陷叠加）
1. **Fix B 触发面 — WeightSumCheck 假阳性**：大类声明值（如"商务…56分"）只出现在子项 `contextNote`，子项 `dim` 不含大类关键字 → `familySums` 无"商务"成员。旧逻辑 `getOrDefault(商务, 0)` 与声明 56 比对必然不等 → 假阳性 `needRecheck` → 决策被推成 `REVIEW_REQUIRED` + `WEIGHT_SUM_MISMATCH`，尽管总分已闭环 100。
2. **Fix A 放大面 — all-or-nothing 落库门禁**：Phase 4 T405 的持久化门禁规定"只有 `AUTO_APPROVED` 才落库"，于是上一步误判的 `REVIEW_REQUIRED` 让整批 11 项成功解析被**静默丢弃**，任务仍标记 `COMPLETED` → 前端拿到空结果。

### 修复
- **Fix A（`ScoreParseAppService`）**：落库门禁由「仅 `AUTO_APPROVED`」改为「有有效项且非 `STRUCTURE_INCOMPLETE`」（`!valid.isEmpty() && decisionStatus != STRUCTURE_INCOMPLETE`）。`REVIEW_REQUIRED` 也落库展示，复核标记保留在 `task.decisionStatus` / `fallbackReason` 上供前端徽章提示；仅 `STRUCTURE_INCOMPLETE`（结构损坏）保留现有项不覆盖。**这修订了 Phase 4 T404/T405 与 Phase 6 T601 第 5 条的旧契约**（见上文对应小节的更正标注）。
- **Fix B（`WeightSumCheck.checkCandidates`）**：遍历 `declaredFamilyWeights` 时，若该大类在 `familySums` 中**无成员**（`!containsKey`），跳过交叉校验——声明值来自子项 `contextNote`、分桶口径不一致时比对无意义。有成员的大类仍正常校验，守卫不废掉合法的"分布不符"复核（对照用例 `checkCandidates_declaredFamilyWithMembers_stillRechecks`）。

### 反模式教训（详见 `docs/lessons/lessons-learned.md §217`）
- **失败要响、失败要安全**：成功解析绝不能被静默丢弃；阻断落库必须同时阻断"任务完成"假象，或显式落库 + 标记复核。
- **循环验证**：原 GoldenReplay 第 4 环 `testPersistenceProtection_onlyAutoApprovedCanOverwrite` 是空断言（局部常量 + 永真 `if` + 对从未触碰的 mock `verify(never())`），既没跑生产代码、DisplayName 还钉死旧契约 → 已删除。真实门禁证据由 `ScoreCompletenessAcceptanceTest#invariant3_reviewRequired_stillPersistsWithReviewFlag`（改写为新契约）承载。
- **合成夹具自证**：1358 回归锚点是 `WeightSumCheckTest#checkCandidates_declaredFamilyWithoutMembers_noFalseRecheck`，用真实事故的分桶形态（`dim` 不含大类词、声明值在 `contextNote`）构造，而非对齐测试自身假设。

### 回归证据
`mvn -o test -Dtest='com.xiyu.bid.scoreparse.**,!StreamingBidFileFetcherTest'`（排除预存在网络挂起用例 `StreamingBidFileFetcherTest`，该用例与本修复无关）：**627 tests, 0 failures, 0 errors**。其中 `ScoreParseAppServiceTest` 22、`ScoreCompletenessAcceptanceTest` 4、`WeightSumCheckTest` 11、`GoldenReplayTest` 4 全绿；`ScoreParseAppService.java` 行数收敛到 300（行预算硬限）。

### Fix C 已落地（2026-09-03，闭环"循环验证"挂账）
Fix A+B 的回归锚点仍是单元/验收级合成夹具，无法证明真实 1358 形态跑通生产链。Fix C 补两层可执行端到端金标，直接驱动 `executeParseAsync → ScoreCandidatePostProcessor → WeightSumCheck → persistItems` 真实生产链：
- **脱敏金标（CI 常驻）**：`ScoreCompletenessAcceptanceTest#incident1358_closedLoopWithDeclaredFamilyLackingMembers_persistsAllItems_autoApproved`。11 项满分闭环（商务组 56 分声明在 `contextNote`、`dim` 不含大类词 → `familySums` 无"商务"成员；技术组 44 分自洽），断言 `AUTO_APPROVED` + `fallbackReason==null` + `persistItems` 捕获 11 项无一被静默丢弃。还原 Fix B（去掉 `familySums.containsKey` 守卫）会使本测试转红 = 1358 漏识别症状 → 真回归守卫，非循环验证。
- **真实数据金标（语料门控，CI 跳过）**：`Incident1358RealCorpusGoldenTest`，`@EnabledIfEnvironmentVariable(named="XIYU_EVAL_CORPUS_DIR")`；从 `${XIYU_EVAL_CORPUS_DIR}/incident-1358/candidates.json` 读取离线录制的真实 1358 候选快照跑同一生产链。真实客户文档禁止入仓，代码只出现"事故编号"约定路径。
- **登记 / 证据**：金标登记见 `docs/lessons/scoreparse-golden-corpus/index.md`（1358 新增"权重闭环/落库"行，与既有"证据可定位性"框架并列）。回归切片 `com.xiyu.bid.scoreparse.**`（排除 `StreamingBidFileFetcherTest`）：**629 tests, 0 failures, 1 skipped**（Part B 跳过）。

## 索引

- 完整 spec：`specs/059-score-parse-evidence-pipeline/{proposal,plan,tasks,data-model}.md`
- 金标登记：`docs/lessons/scoreparse-golden-corpus/index.md`
- Push gate 拦截事项：lint 模块依赖 / Wiki 健康检查需要 .wiki/ 变更配套
- FP-Java 不变量：所有新增 record 的硬不变量走 Optional<validate>()，不抛业务异常

## 人工复核「保留」按钮防重复提交（2026-09-12）

- **背景**：`ScoreParseReviewPanel` 的「记录已复核，保留现有项」点击后只向 `score_parse_review` 落一条审计（`action=RETAIN_ORIGINAL`），`task.decisionStatus` 仍为 `REVIEW_REQUIRED` → 面板与"待人工复核"徽章持续显示、按钮可无限重复点击，向审计表插入重复记录（实测项目出现"审计 2 条"= 点击两次）。
- **语义（非 bug）**：保留按钮只做**审计留痕，不改变 decisionStatus**；闭环证据是 `score_parse_review` 记录数，面板持续显示属设计如此。
- **修复**：`useScoreParseDrawer.js` 新增 `reviewSubmitted` 状态：
  - `submitReviewRetention` 成功后置 `true` → 按钮 disabled
  - `loadReviewSnapshotIfNeeded` 加载快照时若 `audits` 已含 `RETAIN_ORIGINAL` 同样置灰（刷新 / 重开抽屉不复活按钮）
  - `ScoreParseDrawer.vue` 分离 `:submitting="reviewSubmitting"` 与 `:submitted="reviewSubmitted"`
  - `ScoreParseReviewPanel.vue` 已提交显示“已保留”，不再把终态误显示为“保存中”
  - composable 入口同时拦截 `reviewSubmitting` 和 `reviewSubmitted`，避免并发或重复调用绕过按钮状态
- **验证**：`useScoreParseDrawer.spec.js` 与 `ScoreParseDrawer.spec.js` 共 35 例全绿；覆盖提交后终态文案、刷新后已有审计置灰、重复调用只发一次请求。
