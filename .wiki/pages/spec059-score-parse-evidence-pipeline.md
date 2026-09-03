---
title: spec 059 评分解析证据化 Phase 0-2 落地结论 + 合入前评审修复
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

## Phase 3-6 待办（see tasks.md）

- Phase 3：LLM 受限为可审计语义适配器——契约已就绪（LlmFactOp 五操作 + LlmDecisionValidator，含跨表拒绝），编排层 ScoreGlobalMergeOrchestrator 改造未动
- Phase 4：完整性阻断、人工复核、移除 WeightNormalizationPolicy 自动调用
- Phase 5：DB/API/前端迁移 + 双写 + 灰度（双轨回放脚本 T505）
- Phase 6：金标收口（跨页页码锚点挂账）+ 架构门禁 + 清理

## 索引

- 完整 spec：`specs/059-score-parse-evidence-pipeline/{proposal,plan,tasks,data-model}.md`
- 金标登记：`docs/lessons/scoreparse-golden-corpus/index.md`
- Push gate 拦截事项：lint 模块依赖 / Wiki 健康检查需要 .wiki/ 变更配套
- FP-Java 不变量：所有新增 record 的硬不变量走 Optional<validate>()，不抛业务异常