# Tasks: 业绩评分项案例推荐精准准入（品类 + 金额门槛 + 资格项回退）

**Feature**: `specs/064-perf-case-admission` | **Branch**: `agent/gemini/fix-perf-case-admission` | **Plan**: [plan.md](./plan.md)

**TDD**: 项目 Constitution 强制 RED→GREEN；每个实现任务先补失败用例。

## Phase 1: Setup

- [x] T001 确认任务 worktree 与基线：`git status`、`git log -1`，在 `backend/` 记录改动前定向测试基线输出（RED 证据），写入 `.omc/verify-<sessionID>.md` 草稿
- [x] T002 [P] 复核锁与范围：`.agent-locks/` 无冲突（本任务仅改 casework 策略/编排与测试）；确认未触碰 `db/migration-mysql`（herdr 锁）

## Phase 2: Foundational（US1/US2 共同前置）

- [x] T003 [P] 新增 `backend/src/main/java/com/xiyu/bid/casework/domain/policy/PerformanceThresholdExtractor.java`：`CategoryThreshold(category, amountWan, rawText)` record + `extract(String)` 分句配对与单位归一（亿×10000 / 万×1 / 千×0.1 / 元÷10000，千分位、可选“不低于/不少于”前缀）
- [x] T004 先写 RED 单测 `backend/src/test/java/com/xiyu/bid/casework/domain/policy/PerformanceThresholdExtractorTest.java`：双标包双门槛、单位混写、同句双品类、无金额仅品类、纯资质文本不产门槛；再实现 T003 至全绿
- [x] T005 扩展 `QualificationPerformanceRetriever`：`PerformanceRequirementResult` 增加 `thresholds`；`retrieve` 合并**全部**实质条款（列表 + 资格审查/初步评审章节，去重，上限 5 条/4000 字符），关键词/品类/门槛取并集；条款收集逻辑拆分至 NEW `QualificationClauseCollector`（满足 300 行棘轮）；保持 `isPerformanceScoreItem`/防泛化语义不变
- [x] T006 更新 RED 断言 `QualificationPerformanceRetrieverTest`：两条款分列时两个门槛均出现在结果中；合并文本同时含 2000 万与 4500 万；防泛化用例保持 empty

## Phase 3: User Story 1 - 业绩评分项只推荐符合条件的业绩案例 (P1)

- [x] T007 [US1] 新增 RED 单测 `PerformanceCaseAdmissionPolicyTest`：人员/团队文案拒绝；技术文案（无业绩证据）拒绝；品类+金额达门槛准入（Tier-1）；金额明确低于门槛一票否决；无金额但品类命中准入（Tier-2）；仅业绩信号准入（Tier-3；无门槛场景）
- [x] T008 [US1] 实现 `backend/src/main/java/com/xiyu/bid/casework/domain/policy/PerformanceCaseAdmissionPolicy.java`：证据提取（业绩信号词 + 品类族 + 品类分句金额最大值）、`evaluate(candidateText, requirements)` 返回 `AdmissionVerdict(admitted, tier, reasons)`，完成 T007 全绿
- [x] T009 [US1] `ScoreItemCaseRecommendAppService` 编排：业绩评分项时对 `findAllActive()` 候选执行分层准入（存在 Tier1/Tier2 时保留 Tier1+Tier2，否则降级 Tier3；全部无证据才返回空），记录过滤前后数量日志；准入结果不改变 `ScoreItemCaseMatchPolicy` 打分链路
- [x] T010 [US1] 更新 RED 集成断言 `ScoreItemCaseRecommendAppServiceTest`：业绩评分项 + 人员干扰案例 → 人员案例不出现在 `items`；达门槛业绩案例出现且 `matchScore ≥ 50`；低于门槛案例被排除；Tier2 无金额候选保留；资格项快照缺失时人员案例仍被排除（仅 Tier-3 业绩信号）

## Phase 4: User Story 2 - 未选评分项时回退资格项检索 (P2)

- [x] T011 [US2] 扩展 `QualificationPerformanceRetriever` 增加“无条件提取资格项业绩条款”能力（不依赖评分项文本），供回退场景使用；补对应单测
- [x] T012 [US2] `ScoreItemCaseRecommendAppService` 回退编排：`scoreItem == null` 且**无关键词检索**且资格项存在实质业绩条款时，以资格项作为 `qualificationRequirementText/Keywords` 上下文并执行准入；资格项无业绩条款时保持现状
- [x] T013 [US2] 更新 `ScoreItemCaseRecommendAppServiceTest`：无 scoreItemId + 资格项双门槛 → 结果按门槛准入且无人员案例；无 scoreItemId + 无资格业绩条款 → 与现状一致（回归防护）

## Phase 5: User Story 3 - 混合类别按业绩优先归类 (P2)

- [x] T014 [US3] `CaseCategoryMatchPolicy.classifyText`：PERFORMANCE 判定前移到 QUALIFICATION 之前（PERSONNEL 优先级保持最高）
- [x] T015 [US3] 更新 `ScoreItemCaseMatchPolicyTest`：`资质业绩`→PERFORMANCE；`资质等级证书`→QUALIFICATION；既有 PERSONNEL/TECHNICAL/COMMERCIAL 断言不回归

## Phase 6: Polish & Cross-Cutting

- [x] T016 S4 Exit：执行 `ai-slop-cleaner` Mode B（删除优先：死代码/冗余分支/重复常量），清理后重跑 T003-T015 相关测试
- [x] T017 运行门禁：`mvn test -Dtest=FPJavaArchitectureTest,ArchitectureTest,ProjectAccessGuardCoverageTest` + `mvn test -Dtest='com.xiyu.bid.casework.**'` + `node scripts/check-line-budgets.mjs`；结果写入 `.omc/verify-<sessionID>.md`
- [x] T018 `speckit-analyze` 一致性检查（spec/plan/tasks 与实现差异），修正偏差
- [x] T019 [P] 知识沉淀：`docs/lessons/lessons-learned.md` 追加根因与防复发纪律；`.wiki/pages/ai-case-recommend-data-source-fix.md` 回填本次机制（金额门槛准入 + 分层降级 + 资格项回退）
- [x] T020 [P] 技术债登记：`docs/exec-plans/tech-debt-tracker.md` 记录 `knowledge_case` 缺金额/业绩品类字段（未来结构化门禁）

## Dependencies

```text
T001 → T003 → T004 → T005 → T006
                 ↘
T007 → T008 → T009 → T010 → T011 → T012 → T013
T014 → T015（独立于 US1/US2）
T016 → T017 → T018 → T019/T020
```

- US1 依赖 Foundational（T003-T006）；US2 依赖 US1 的编排（T009）以复用准入；
- US3（T014/T015）与 US1/US2 无代码依赖，可并行；
- Polish 必须在所有实现与清理完成后执行。

## Parallel Execution

- T002 与 T003 可并行（不同关注点/不同文件）；
- T007（测试）与 T014（分类实现）可并行；
- T019 与 T020 可并行（不同文档）；
- 其余任务按依赖顺序串行。

## Independent Test Criteria

- **US1**：`mvn test -Dtest=PerformanceCaseAdmissionPolicyTest,ScoreItemCaseRecommendAppServiceTest` 中人员案例零出现、门槛排除/降级断言全绿。
- **US2**：`ScoreItemCaseRecommendAppServiceTest` 中无 scoreItemId 场景准入断言全绿且存量默认路径无回归。
- **US3**：`mvn test -Dtest=ScoreItemCaseMatchPolicyTest` 混合类别断言全绿。

## MVP Scope

US1（T001-T010）即最小可用：业绩评分项不再推荐人员案例并能执行品类/金额准入。US2/US3 为增量完善。
