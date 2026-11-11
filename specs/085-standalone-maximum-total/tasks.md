# Tasks: 独立满分汇总行

## Phase 1 Setup
- [x] T001 核对根因和隔离工作区，创建spec.md/plan.md及.omc/entry-gate-score1123.md。
## Phase 2 US1 正确计分与总分
- [x] T002 [US1] 在backend/src/test/java/com/xiyu/bid/scoreparse/domain/StandaloneMaximumSummaryTest.java新增事实排除、原文总分、同方案多表回归，执行RED。
- [x] T003 [US1] 修改backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreTableSummaryPolicy.java与OriginalScoringContext.java共享独立满分标签，执行GREEN。
## Phase 3 US2 相邻规则兼容
- [x] T004 [US2] 在ScoreTableSummaryPolicyTest.java补单项满分不得当汇总的反例。
- [x] T005 [US2] 添加backend/src/test/resources/scoreparse-golden/case-standalone-maximum-total.md及.expected.json，注册golden/GoldenCasesReplayTest.java。
- [x] T006 [US2] src/composables/projectDetail/useScoreCandidateAdoption.spec.js验证明细40/60与来源声明100的候选权重100。
## Phase 4 Verification and Review
- [x] T007 对本diff执行ai-slop-cleaner Mode B，运行相邻单测、金标、Vitest、build，证据写.omc/verify-score1123.md。
- [x] T008 恢复缺陷确认T002再次失败，再还原GREEN，完成specs/085-standalone-maximum-total/analysis.md。
- [x] T009 回填.wiki/pages/scoreparse-1123-double-total.md与docs/lessons/lessons-learned.md；独立只读双审查并记录.omc/review-score1123.md。

Dependencies: T001→T002→T003；T004/5/6→T007→T008→T009。无需并行写入；reviewer仅只读。MVP是US1，但本次按全部任务交付。无提交/推送/PR/部署任务。

## 三技能审查 fix 第1轮

- [x] T010 [US2] 新增同方案明细40/60与无表头独立合计/总计/满分表的resolve回归，断言来源总分100及数值格；修复前7例中3例因总分为空失败，修复后相关四类119例全部通过，保留满分列表头反例。独立复审由主执行器另行调度，不在本写入阶段自批。
