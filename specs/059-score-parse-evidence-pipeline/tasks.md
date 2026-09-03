# Tasks: 059 评分解析证据化

> 进度口径（2026-09-03）：Phase 0-2 代码 + 可执行金标基线已落地并过定向回归（631 例绿）；
> 评审 P0/P1/P2 五项修复见 `docs/implementation-notes/059-score-parse-evidence-pipeline.md`。

## Phase 0：冻结 legacy 自动落库扩张 — 运行档案 + 金标基线

- [x] T001 新增不可变枚举/值对象 `ScoreParseDecision` 与常量 `LEGACY_FALLBACK`、`EVIDENCE_PIPELINE_V1`。
- [x] T002 `ScoreParseTask` 实体字段追加：`pipeline_version`、`decision_status`、`fallback_reason`；DTO 同步透出。
- [x] T003 `RunSummaryAssembler` 装配 JSON 增 `pipelineVersion`、`decisionStatus`、`fallbackReason` 三键；旧字段保持向后兼容。
- [x] T004 `ScoreCandidatePostProcessor.process` 在产物 `Processed` 中携带 `decisionStatus` 与 `fallbackReason`；调用方按 `decisionStatus` 写入任务字段（Phase 0 默认沿用现有行为，落库仍发生，但不再静默）。
- [x] T005 `ScoreParseAppService` 在 `markCompleted` 前落地 `pipelineVersion` 与 `decisionStatus`；`run_summary` 由 assembler 装配，仍为 JSON。
- [x] T006 MySQL 迁移：`V1207__add_score_parse_task_pipeline_version.sql` 新增三列；提供 `U1207` 回滚脚本。
- [x] T007 `docs/lessons/scoreparse-golden-corpus/index.md`：登记事故 1347 / 1358 / 1362 脱敏描述 + 5 类合成夹具清单（脱敏来源说明）。
- [x] T008 新增单测：`RunSummaryAssemblerEvidencePipelineTest`（覆盖 decision/fallback 序列化与 schema 兼容）。
- [x] T009 定向回归：`mvn test -Dtest='RunSummary*,ScoreParseApp*,*PostProcessor*,ArchitectureTest'` 绿；输出命令留证。

## Phase 1：结构契约 — CanonicalTableGrid（独立 Commit）

- [x] T101 升级 Sidecar `/extract-tables` 响应为版本化 `DocumentArtifact` 契约（schemaVersion=2.0）。
- [x] T102 Python 端 `DocumentArtifact` 装配器 + XML 路径、单元格 ID 唯一性、原始/规范化双文本补齐。
- [x] T103 新增领域模型 `CanonicalTableGrid`、`CanonicalCell`、`MergedRegion`、`StructureValidationResult`（纯核心，无框架依赖）。
- [x] T104 `CanonicalTableGridFactory` 结构校验：合并覆盖范围、行列闭合、ID 唯一。
- [x] T105 `STRUCTURE_INCOMPLETE` 返回路径：Sidecar HTTP 状态/解构失败时统一返回；不得降级到旧字符串表格继续自动评分。
- [x] T106 单测：`table_extractor_v2_test.py`（DOCX 合成夹具：纵/横/纵横合并、跨页、空合并续、嵌套段落、多评分表、表头跨列）+ Java `CanonicalTableGridTest`。

## Phase 2：确定性 ScoreFact（独立 Commit）

- [x] T201 不可变领域对象：`ScoreFact`、`ScoreEvidence`、`ScoreRule`、`ScoreParseDecision`；纯 record/Java。
- [x] T202 `DeterministicScoreFactExtractor`：按分值列/标题括号/满分/公式上限优先级生成 weightEvidence；区分父项/子项。
- [x] T203 `ScoreFactValidator`：事实完整性与评分计算完整性分开校验；自动化硬不变量单测。
- [x] T204 `ScoreFact → ScoreCandidate` 适配器；仅 `AUTO_APPROVED` 路径写入旧 `score_item`；新事实落 `score_parse_fact`（结构在 Phase 5 增加）。（适配器 `toCandidate()` 已就绪；落库接线按计划属 Phase 4/5）
- [x] T205 单测覆盖：1347 / 1358 / 1362 + 5 类合成夹具的末级事实数、权重、父子、source cell。（`GoldenCorpusBaselineTest` 8 例；跨页/嵌套段落缺口挂账见登记表）

## Phase 3：限制 LLM 为可审计语义适配器（独立 Commit）

- [x] T301 LLM 输出契约改为 FactId 操作集合（KEEP / CLASSIFY / LINK_PARENT / MARK_DUPLICATE / REVIEW_REQUIRED）。
- [x] T302 `LlmFactOperationValidator` 拒绝未知 factId、违规操作、跨文件 cell 引用、保护字段写入。
- [x] T303 `ScoreGlobalMergeOrchestrator` 改造：在 factId 池上运行；禁止 LLM 直接生成 score list；移除"池文本互含即放行"。
- [x] T304 LLM mock 单测：所有非法操作必须被拒绝并转 `REVIEW_REQUIRED`。

## Phase 4：完整性阻断、人工复核、取消静默归一化（独立 Commit）

- [x] T401 `ScoreCompletenessPolicy`：覆盖声明总分、末级权重、父子重复、大类声明、候选数、缺失证据、结构状态。
- [x] T402 删除新版链路对 `WeightNormalizationPolicy.normalize` 的自动调用；保留仅供显式人工调整。
- [x] T403 复核工作流：保存事实快照、校验失败项、原始总分；前端抽屉新增"自动通过 / 待复核 / 结构异常"状态与原文证据展开（雏形）。
- [x] T404 任务决策 ≠ 落库决策：`COMPLETED + REVIEW_REQUIRED` 不覆盖现有 `score_item`；只有 `AUTO_APPROVED` 可写。
- [x] T405 完整不变量单测：所有失败模式必须保留旧 `score_item`，不得先删后写。

## Phase 5：数据库 / 接口 / 前端迁移（独立 Commit）

- [ ] T501 迁移：`V{auto}__score_parse_task_evidence_pipeline_schema.sql` + 新表 `score_parse_fact` / `score_parse_evidence` / `score_parse_review`；`score_item` 追加单元格证据列（可空）。
- [ ] T502 双写：新链路写事实/证据/兼容 score_item；旧链路仅写 legacy run-summary，禁止 AUTO_APPROVED 决策。
- [ ] T503 API 增量：`ScoreParseItemsDTO.Meta` 增 `evidenceDecision` / `fallbackReason` / `pipelineVersion`；DTO 兼容性单测。
- [ ] T504 前端 `useScoreParseDrawer` / `ScoreParseDrawer.vue`：状态角标 + 原文证据展开；用户未确认的评分值不修改。
- [ ] T505 双轨回放工具：`scripts/scoreparse-dual-replay.sh`（脱敏输入，差异报告）。

## Phase 6：清理、门禁、交付（独立 Commit）

- [ ] T601 金标覆盖：合并语义/结构校验/fact 提取/LLM 操作/完整性/持久化保护/人工审计。
- [ ] T602 架构测试新增：`scoreparse.domain` 不依赖 Spring/HTTP/JPA；LLM/infrastructure 不得构造 `ScoreFact`。
- [ ] T603 CI 默认运行确定性金标；nightly 执行脱敏真实语料评估并出报告（lane/复核率/结构失败/证据覆盖率）。
- [ ] T604 删除自动归一化在生产链路调用；保留 legacy 只读适配器至存量任务迁移完成。
