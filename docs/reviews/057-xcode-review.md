# xiyu-code-review · PR #2430 score-eval-and-guard

> 审查者：ZCode (xiyu-code-review) · 2026-08-27
> 范围：`agent/gemini/score-eval-guards` (7 commits, 32 files, +1656/-157)
> 依据：specs/057-score-eval-and-guard/{spec,plan,tasks}.md；ARCHITECTURE.md FP-Java 11 条；AGENTS.md 不可妥协底线

## 状态快照

| 项 | 值 |
|---|---|
| mergeable | true（与 origin/main 干净） |
| 变更统计 | 32 files +1656 / -157 |
| ArchitectureTest | PASS（`mvn -f backend/pom.xml test -Dtest=ArchitectureTest`，exit=0，无失败行） |
| 关键单测 | PASS（RunSummaryAssemblerTest / ScoreCandidatePostProcessorTest / ScoreGlobalMergeOrchestratorTest / ScoreMergeResultGuardTest / ScoreParseAppServiceTest / GoldenCasesReplayTest；金标回放落 `backend/target/scoreparse-golden-report.json`） |
| check-keyword-sync | PASS（Java/Python 报价参数关键词表 15+4 一致） |
| OpenAiScoreAnalyzer 行数 | 296 / 300（line-budget 内） |
| ScoreParseAppService 行数 | 238（治理链抽离后从 300 → 238，-21%） |
| V/U 迁移配对 | V1204 / U1204 配对完整，含 source/Pos 4 行头，U1204 头标注 V1204 完整路径 |
| 数据库 schema 是否同步 | 同步（V1204 ADD COLUMN run_summary TEXT，score_parse_task 实体新增 @Column runSummary；ScoreParseItemsDTO.Meta 新增 runSummary 字段） |
| 知识沉淀 | docs/implementation-notes/057-score-eval-and-guard.md 已落；.wiki/pages/score-parse-service.md §金标回放已加 |

## 改动摘要

spec 057 三件套落地的代码切片：

1. **裁决语料完整性（FR-01/02/03）**——`ScoreGlobalMergeOrchestrator.resolveSectionText` 评分章节定位失败时 `AdjudicationContext.located()=false`，禁止回退到"全文前 12k 字符"裁决（旧降级会拿错语料误杀真项并污染 `hallucinationDropped`）。`ScoreMergeResultGuard.isPoolIdentityMatched` 新增规则 2 旁路：语料外但与候选池身份互含的真实项→ `poolBackedKept` 计数（不再误判幻觉）。`SECTION_TEXT_LIMIT=12000` 触发 `truncated=true` 透出。
2. **run_summary 持久化（FR-04/05/06）**——`RecallOutcome` 新增 `laneCounts / alignedHit / alignedTotal / llmModel` 字段；`OpenAiScoreAnalyzer.recallLane4LlmFullText` 计算 alignedCount（统计函数 `ScoreCandidateMapper.countAligned` 自 Analyzer 拆出以守 300 行预算）；`RunSummaryAssembler` 纯函数拼装 JSON，含 `ALIGNED_RATIO_FLOOR=0.5` / `HALLUCINATION_RATIO_CEIL=0.3` 阈值告警；`ScoreParseTask.run_summary TEXT` 落库（V1204/U1204 配对迁移）。
3. **金标夹具回放（FR-08/09/10 + US3 SC-4）**——`backend/src/test/resources/scoreparse-golden/` 双案合成 md+expected.json（**SYNTHETIC FIXTURE 头声明**，无真实客户文档）；`GoldenCasesReplayTest` 驱动确定性六件套（Locator→Extractor→TableRecallLane→Merge→GhostFilter→WeightNormalize），P/R/F1 硬断言 + `@AfterAll` 落 `target/scoreparse-golden-report.json`；`scripts/eval-scoreparse/{report.mjs,nightly-full.sh}` 两层 SKIP-safe；`package.json` 加 `eval:scoreparse:replay` / `eval:scoreparse:report`；`GoldenCasesReplayTest#mirrorMatchesMapperContract_fixedVector` 漂移哨兵——Mapper 字段序/截断 500 改而未同步则该测试先红。
4. **治理链抽离（D5 预案）**——`ScoreCandidatePostProcessor`（145 行）抽出：合并去重→幽灵过滤→无效权重过滤→全局归并（fail-open）→完整性回补→超载剔除→归一化收敛→数量校验→run-summary 装配；`ScoreParseAppService` 缩为 238 行纯编排；`ScoreGlobalMergeOrchestrator` 暴露 `applyGlobalMergeDetailed` + `MergeStats` 记录。

## 三透镜评审

### 透镜 A · 行为正确性

| Finding | 严重度 | 位置 | 问题 | 建议 |
|---|---|---|---|---|
| A-1 | Minor | `ScoreGlobalMergeOrchestrator.resolveSectionText:124-125` | 双重截断语义：循环内 `brokeEarly` + sanitize 后 `tailTruncated` 再做 substring 截断；`brokeEarly=true` 时已 `break`，`sanitize` 不会改变长度（仅替换字符），所以 `tailTruncated` 几乎恒为 false；两路截断存在"理论冗余实际等价" | 删除 `tailTruncated` 或把 `brokeEarly` 改名 `hitLimit` 让读者一眼看出限位逻辑；任一保留即可 |
| A-2 | Minor | `OpenAiScoreAnalyzer.recallLane4LlmFullText:170-173` | 现有 `log.info` 已带 aligned 比率，run-summary 又存 `aligned.hit/total`，双写有冗余但仍 OK（不致命） | 可考虑在 trace 级别减日志 |
| A-3 | Info | `RunSummaryAssembler.quote:76-96` | 手工 JSON escape 与 `ObjectMapper` 选型合理性：候选全部为数字+少量 llmModel 字符串；当前实现覆盖了 `\n \r \t " \` 与 \uXXXX<0x20，**足够**；引入 ObjectMapper 会带来 1.5MB+ 依赖与 Jackson 反序列化扫描面 | 留手工拼装，但建议在类头 Javadoc 写明"可观测范围内字符串字段受控"以防后续误引入未净化输入 |
| A-4 | Info | `ScoreParseAppService.doExecuteParse:159-163` | 任务 FAILED 路径（`processed.failMessage() != null`）下 `setRunSummary` 与 `taskRepository.save` 不会执行——即零项失败时**丢遥测**。但 `setSkippedChunks` 与 `setFileName/FileUrl` 已先写库，对事故复盘仍能拼出大部分事实 | 若要更稳：把 `setRunSummary(processed.runSummaryJson())` 移到 failTask 之前一行；或在 FailTaskService 内统一附 lastRunSummary。当前做法可接受 |
| A-5 | Major（修复中） | `ScoreGlobalMergeOrchestrator` 旧 `apply_sectionLocatorMisses_fallsBackToFullTextPrefix` 已删，新 `apply_sectionLocatorFails_skipsAdjudication_noLlmCall` + `apply_truncatedCorpus_logsTruncatedFlag` 完整覆盖 | spec 057 FR-01 行为已正确锁定 | — |

**行为判定**：FR-01/02/03/04/05/06/08/09/10 全部有断言覆盖；关键失败路径（executor 不可用 / LLM 不可用 / 守卫全滤 / 截断 / 章节定位失败）均经测试断言；池背书与高置信回补的两计数互不污染。✅

### 透镜 B · 结构健康

| Finding | 严重度 | 位置 | 问题 | 建议 |
|---|---|---|---|---|
| B-1 | Info | `ScoreGlobalMergeOrchestrator.SECTION_TEXT_LIMIT=12000` 影响面 | 全代码库 4 处引用：line 31 声明、line 101 注释、line 114 拼接判定、line 124 sanitize 后判定、line 129 truncate 截断。无跨模块引用，无外部配置注入 | 维持 static final 即可；下一步可挪到 `scoreparse-constants.properties` 走 `@ConfigurationProperties`，但当前是单调用方不必 |
| B-2 | Info | `RunSummaryAssembler.ALIGNED_RATIO_FLOOR=0.5` / `HALLUCINATION_RATIO_CEIL=0.3` 影响面 | 仅本类使用 + 1 处 PostProcessor log 引用（line 114） | 留 |
| B-3 | Info | `PostProcessor.OVERLOAD_LIMIT=100.5` 与 `MAX_SINGLE_WEIGHT=100` 影响面 | `MAX_SINGLE_WEIGHT` 与 `ScoreMergeResultGuard.MAX_SINGLE_WEIGHT` **重复定义**（guard:95 vs postProcessor:37），但数值一致（=100）。任何一方变更需手工同步另一方 | 抽到 `ScoreParseConstants` 公共类，guard 引用同一常量；当前两处值一致可合并 |
| B-4 | Info | `ScoreGlobalMergeOrchestrator.MergeStats.skipped / fallback` 工厂 | `skipped("...")` 与 `fallback("...")` 当前实现完全相同（`new MergeStats(null, false, reason, false, 0, 0, 0)`）；语义上 skip=前置不足、fallback=执行后失败被降级 | 至少在 Javadoc 写明"两语义当前实现相同但调用方语义不同"，便于未来分叉 |
| B-5 | Info | `OpenAiScoreAnalyzer.recallLane4LlmFullText` 嵌套 4 层 for+try+filter+map+count | 行 150-169 嵌套 3 层（for/if-else/stream）；尚可接受（<5 分支点，<3 嵌套） | 维持 |
| B-6 | Info | `ScoreCandidatePostProcessor.process` 链长 60 行（62-121） | 8 个步骤串成一条管线（merge→ghost→weights→mergeDetailed→weightCheck→recheck→trim→normalize→countCheck→summary），分支点 4 处，全部 1-2 行 | 已采用纯领域对象链路；可读性达标 |
| B-7 | Info | 依赖方向：application → domain → (no dep)；infrastructure/openai → application? | `RunSummaryAssembler` 在 `application` 但引用 `infrastructure.openai.RecallOutcome` 与 `infrastructure.openai.ScoreGlobalMergeExecutor`——**反向依赖**：application 不应依赖 infrastructure | 建议把 `RecallOutcome` 移到 `domain` 包（值对象，无框架依赖），`ScoreGlobalMergeExecutor` 已在 `infrastructure.openai`，作为 application 引用的端口接口是允许的。`RecallOutcome` 应下沉 |
| B-8 | Info | 漂移哨兵 | `GoldenCasesReplayTest#mirrorMatchesMapperContract_fixedVector` 固定向量断言（line 391-400）已加；`ScoreCandidateMapper#fromCriterion` 漂移时该测试先红 | 维持 |

**结构判定**：依赖方向仅 B-7 一处反向（小毛病，可后置优化）；常量重复 B-3 是技术债（Minor，零行为差异）；其余模块边界清晰。✅

### 透镜 C · 交付就绪

| Finding | 严重度 | 位置 | 问题 | 建议 |
|---|---|---|---|---|
| C-1 | Info | 7 commits 命名 | `feat(eval)` / `feat(scoreparse)` / `fix(db)` / `refactor(eval)` / `docs(spec)` / `fix(scoreparse)` 风格统一且 reflog 可追溯；无 squash 风险 | 维持 |
| C-2 | Info | 仓库卫生 | `docs/implementation-notes/057-score-eval-and-guard.md`（48 行）；`.wiki/pages/score-parse-service.md` §金标回放已加；`specs/057-*/{spec,plan,tasks}.md` 三件套完整 | 维持 |
| C-3 | Info | 迁移规范 | V1204 头 4 行 source/Output/Pos/回滚 + 标准 4 行头注释（Input/Output/Pos/维护声明）；U1204 头 4 行 + 完整 V 文件名配对 | 通过 FlywayRollbackScriptCoverageTest |
| C-4 | Info | 反 slop 跟进 | `RunSummaryAssembler.wrap()` 死方法已删（commit 79cede169）；金标镜像补 Mapper 交叉引用+固定向量漂移哨兵（同一 commit） | 维持 |
| C-5 | Info | `scoreparse-golden-report.json` 当前产出 | renumber F1=0.833 ≥ 0.6 ✓；weight-gap F1=1.0 ≥ 0.5 ✓；合计 P=78.9% R=100% F1=88.2% | baseline 已立；后续调参可量化对比 |
| C-6 | Minor | `scripts/eval-scoreparse/nightly-full.sh` 仅打印 curl 模板（不真调 LLM） | 文档化为"TODO 状态，未真调 LLM"；缺语料目录时 SKIP 退出 0 | 这是有意的"两层 SKIP-safe"；不需修 |
| C-7 | Info | 知识沉淀归档 | `docs/implementation-notes/057-score-eval-and-guard.md` 已写，AI slop 修正与金标哨兵追加均在 git log 可追溯 | 维持 |

**交付判定**：commit 卫生、迁移规范、仓库卫生、知识沉淀全部达标；baseline 数据可量化。✅

## 自动化 vs 判断性结论分栏

### 自动化结论

| 项 | 状态 |
|---|---|
| ArchUnit 11 条 | PASS |
| 关键单测（7 个） | PASS |
| check-keyword-sync（cross-language contract） | PASS |
| OpenAiScoreAnalyzer line-budget（≤300） | PASS（296） |
| V/U 迁移配对（FlywayRollbackScriptCoverageTest） | PASS |
| 金标回放 F1 双案 | PASS（0.833 / 1.0） |

### 判断性结论

- **依赖方向**：仅 `RunSummaryAssembler → RecallOutcome` 跨 application→infrastructure 一处反向（B-7），值对象无框架依赖，建议下移到 domain 包；非阻塞。
- **影响面**：新增 `run_summary` TEXT 列对既有大表查询无索引影响（不参与 WHERE/ORDER BY）；`poolBackedKept` 仅在金标/夜间采样时观察，不影响运行时。
- **复杂度**：PostProcessor 60 行 8 步链是**该业务的标准形态**——spec 046/047/055/057 的 4 步治理+裁决+回补+权重+摘要串联，拆分反而要传 5+ 上下文；维持单方法。
- **设计意图**：spec 057 D5 预案（治理块抽离）已落地，但 AppService 仍保留 `setRunSummary` 与 `setOriginalTotalWeight` 单点写库——这是有意的"落库仍归 AppService"职责边界。
- **次要债**：B-3（MAX_SINGLE_WEIGHT 重复定义）、A-1（双重截断语义可读性）——可下个 PR 顺手收掉。

## 重新设计建议（不强制，可下个 PR 收口）

1. **B-7**：`RecallOutcome` 移到 `scoreparse.domain`（值对象，无 framework 依赖）；`RunSummaryAssembler` 跟着 application 包内不再依赖 infrastructure。
2. **B-3**：抽 `ScoreParseConstants`（`MAX_SINGLE_WEIGHT=100` / `OVERLOAD_LIMIT=100.5` / `SECTION_TEXT_LIMIT=12000` / `ALIGNED_RATIO_FLOOR=0.5` / `HALLUCINATION_RATIO_CEIL=0.3`）。
3. **A-1**：把 `brokeEarly` 改 `hitLimit`，删 `tailTruncated` 冗余分支；让 truncate 单点决定 truncated 标记。
4. **A-4**：失败路径下也持久化 `runSummary`（便于事故复盘），需评估"已知失败 + runSummary 双写"的回滚语义。

## 合入前必做清单

- [x] 7 commits 已 push 到 `agent/gemini/score-eval-guards`
- [x] PR #2430 已建（https://gitee.com/allinai888/bid/pulls/2430）
- [x] 迁移配对 V1204/U1204 满足 FlywayRollbackScriptCoverageTest
- [x] 金标回放 baseline 双案达标
- [x] 反 slop 修正已合入（commit 79cede169）
- [x] 知识沉淀（docs/implementation-notes/057 + .wiki/pages/score-parse-service.md §金标回放）
- [ ] 等用户拍板：是否在本 PR 一并消化 B-3 / B-7 / A-1 / A-4（建议下个 PR 收口，避免 PR 膨胀）

## 最终结论

**✅ 可合入**（附 4 个非阻塞性后续建议）

PR #2430 严格落地 spec 057 三件套：裁决语料完整性（FR-01/02/03）防止定位失败时拿错语料误杀；run_summary 持久化（FR-04/05/06）让 §7 qwen 空返回类事故可按模型归因；金标夹具回放（FR-08/09/10+SC-4）建立可量化基线（合计 P=78.9% R=100% F1=88.2%）。ArchUnit 11 条、关键单测 7 套、cross-language contract 全部 PASS，OpenAiScoreAnalyzer 守 300 行预算（296 行），ScoreParseAppService 缩 -21%（300→238）。依赖方向仅 `RunSummaryAssembler→RecallOutcome` 一处反向（非阻塞，可下 PR 收口），其余边界清晰；提交卫生、迁移规范、仓库卫生、知识沉淀全部达标。建议合入，下个 PR 再消化技术债 B-3 / B-7 / A-1 / A-4。
