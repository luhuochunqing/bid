# 059 评分解析证据化 — 实现笔记

> 运行记录：spec 未写明而实际做出的决策、偏离与权衡。收尾时随 PR 合入。

## 2026-09-03 合入前评审修复（5 项）

### P0-1 ScoreFact 叶子判定颠倒（致命）

- **问题**：实例方法 `isLeaf()` 把 `parentFactId` 为空（根节点）判成叶子；校验器因此放行父项、拦截真正的细则子项，且单测把反向断言钉死。
- **决策**：叶子是**集合属性**，单项自身无法判定。改为 `ScoreFact.isLeaf(fact, facts)` 静态方法 + `ScoreFact.parentIds(facts)`：未被任何 fact 引用为父项的才是叶子。删除了不可能正确的实例方法。
- **语义影响（非直觉点，后续 Phase 必须知道）**：vMerge 合并类的 origin 行即使自带细则文本和分值，也会被子行引用成"父项"——它保留在事实快照里但被 `validateScoringSet` 拦截出自动评分集。其分值因此不计入总分，与子项合计的差额由 Phase 4 完整性策略兜底转 REVIEW_REQUIRED。这是"宁可漏进人工，不可静默错分"的取舍。

### P0-2 Sidecar vMerge 纵向跨度近似推断

- **问题**：`_v_merge_span_from_tc` 承认无法恢复列坐标，按"该行存在任一 continuation"计数。两列错位独立合并时（列 0 在 r12、列 1 在 r24），列 0 的跨度被列 1 的 continuation 拉长，Java 端 PASS 4 覆盖校验随即把正常表判成 STRUCTURE_INCOMPLETE。
- **决策**：调用点移到光标解析出真实逻辑列之后，把 `(logical_col, colspan)` 传入；函数内部按 gridSpan 累加定位覆盖重启列的 tc，只认它自己的 continuation。新增回归测试 `test_independent_column_merges_do_not_cross_contaminate`（旧实现下该用例失败：跨度被污染成 4）。
- **偏离说明**：未采用 tblGrid 列宽累加对齐方案——gridSpan 累加已满足 Plan 的"显式列坐标"要求且不依赖列宽一致性；真实脏文档里 tblGrid 与实际 tc 分布不一致的情况由结构校验兜底。

### P1-3 金标语料登记表空模板

- **决策**：登记表补齐事故 1347/1358/1362 与 5+2 类合成夹具的预期（末级事实数、权重、父子、source cell、预期决定、不可接受误识别），并新增 `GoldenCorpusBaselineTest`（8 例）作为**可执行基线**——"只读回放可复现"在双轨回放脚本（T505）落地前由单测代行。
- **诚实挂账（未假装完成）**：SYN-CROSS-PAGE 的页码锚点（Sidecar `sourcePage` 恒为 None）与真实语料 nightly 评估（T603）不在本轮范围，登记表"已知缺口"节显式列出。
- **附带发现**：表头文本推断列映射时（`HeuristicTableColumnMapper`），评分因素列出现"（N分）"括号会把 weightCol 劫持到括号列——影子管道测试踩到，夹具按显式分值列口径规避；列映射选择本身是 Phase 3 的事。

### P1-4 孤岛类接线

- **决策**：`SidecarDocumentArtifactExtractor` 补 `@Component`（配置键与 `SidecarTableExtractor` 完全一致）；新增纯静态 `ScoreFactShadowPipeline`（Artifact → Grid → Fact → 全池校验）+ 4 例端到端测试。
- **边界声明**：生产主链路（`ScoreParseAppService`）仍走旧 `tryStructuredTableExtract`，切换属 Phase 3/4 计划内动作，本轮不做。影子管道定位是只读验证器 + 迁移前对照口径，不落库、不调 LLM、不调归一化。

### P2-5 Phase 3 契约提前混入 Phase 2 提交

- **问题**：`LlmFactOp/LlmDecision/LlmDecisionValidator` 随 Phase 2 提交入库，且操作集（SelectFact/MergeDuplicates/MarkReviewRequired/AddObservation）与 Plan 契约（KEEP/CLASSIFY/LINK_PARENT/MARK_DUPLICATE/REVIEW_REQUIRED）不一致。
- **决策**：分支已推送且 PR 已开，**不做历史重写**（仓库纪律禁止 force-push；cherry-pick 可追溯性优先）。选择保留并就地对齐：重命名 + 补齐五操作，`AddObservation` 删除（Plan 无此操作）。顺带把 T302 的"跨文件 cell 引用拒绝"提前实现为 LinkParent 的 `sourceTableId` 一致性校验（确定性、零成本）。
- **流程教训**：Phase 2 commit 混入 Phase 3 文件是拆分提交时的纪律失误，后续按 Phase 边界 `git add` 指定文件。

## 2026-09-03 Phase 3 实现笔记：限制 LLM 为可审计语义适配器

### 1. 契约重构 (T301)
- 引入 Wire 传输层 DTO `RawFactOperation`，内置 `weight`、`detail`、`sourceText`、`newFact` 等陷阱字段，用于识别并拦截非法回写与幻觉发明。
- `LlmFactOp` 密封接口统一暴露 `factId()`、`confidence()`、`reasonCellIds()`，规范支持 5 种合法操作：`KEEP`、`CLASSIFY`、`LINK_PARENT`、`MARK_DUPLICATE`、`REVIEW_REQUIRED`。

### 2. 安全门禁与不回写纪律 (T302)
- 增加 `LlmFactOperationValidator`：
  - 拦截未知 `factId`
  - 拦截非法动作（如 `CREATE_SCORE_FACT`、`UPDATE_WEIGHT`、`DELETE_FACT` 等）
  - 拦截受保护字段篡改（`weight`、`detail`、`sourceText`、`newFact`）
  - 拦截跨表 cell 引用与跨表父子挂载（`cellIdBelongsToTable` 与 `sourceTableId` 一致性检查）
  - 拦截低置信度（< 0.80）语义分类
  - 违规处理纪律：绝不将脏数据回写事实池，受影响项打标为 `REVIEW_REQUIRED`。

### 3. 编排器与守卫改造 (T303)
- `ScoreMergeResultGuard`：彻底移除 `poolIdentityMatched` 与 `PoolEntry`，取消历史“池文本互含即放行”规则，未在原文背书的候选项一律作为幻觉丢弃。
- `ScoreGlobalMergeOrchestrator`：
  - 增加 `adjudicateFacts(List<ScoreFact>, String fullText)`，仅在既有确定性事实池上运行。
  - LLM 故障/不可用时 fail-open，确定性链路可稳定复现 `AUTO_APPROVED` 或 `REVIEW_REQUIRED`。
- `ScoreCandidatePostProcessor`：
  - 明确限制旧 LLM 召回（`recheckGaps`）仅作为人工复核建议（标记 `REVIEW_REQUIRED` + `RESORT_TO_GAP_RECHECK`），禁止直接合入 candidates 填补确定性事实权重缺口。

### 4. 验收与架构合规 (T304)
- 新增 `ScoreFactAdjudicationAcceptanceTest`（8 例端到端验收）：
  1. 发明新事实拦截
  2. 篡改权重拦截
  3. 篡改细则与原文拦截
  4. 跨表单元格引用拦截
  5. 跨表父子层级挂载拦截
  6. 低置信度分类拦截
  7. LLM 不可用时 fail-open 确定性复现
  8. 合法操作平滑应用层级与去重
- 所有新增与修改文件严格遵守 FP-Java 规范（纯核心、不抛受检业务异常、返回不可变数据、行预算硬限 300 / 软限 200 行以内）。

## 环境问题（本轮不修，挂账）

- `GITEE_TOKEN` 过期（Gitee API/MCP 均 401），PR CI 状态暂时查不了；需更新 `~/.zshenv`。
- `scripts/gitee-pr-helper.sh list` 解析 PR 列表崩溃（把字符串元素当 dict），值得单独修。
- 本 worktree Python 环境缺 `fastapi`，`test_extract_tables.py` 的 3 个 endpoint 测试报错为**既有现象**（基线 stash 对比确认），与本次改动无关。

## 测试证据

- Phase 3 验收与定向回归：
  - `ScoreFactAdjudicationAcceptanceTest`: 8/8 通过
  - `ScoreGlobalMergeOrchestratorTest`: 10/10 通过
  - `ScoreCandidatePostProcessorTest`: 6/6 通过
  - `LlmFactOperationValidatorTest`: 10/10 通过
  - `LlmDecisionValidatorTest`: 12/12 通过
  - `ScoreMergeResultGuardTest`: 16/16 通过
  - `FPJavaArchitectureTest`: 8/8 通过
  - 全量评分相关测试集 `mvn test -Dtest='*Score*,*Llm*,*Fact*'`: **499 例全部绿，0 失败 0 错误**。
