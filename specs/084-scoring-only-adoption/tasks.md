# Tasks: 评分项采纳边界修复

## Phase 1 Setup
- [x] T001 核对分支/基准/既有状态并建立隔离worktree，记录`.omc/entry-gate-scoring-only-adoption.md`。
- [x] T002 按Spec Kit写`specs/084-scoring-only-adoption/{spec,plan,research,data-model,quickstart}.md`与需求检查表。
## Phase 2 US1 只采纳计分项
- [x] T003 [US1] 在`backend/src/test/java/com/xiyu/bid/scoreparse/domain/ScoreReviewAdoptionPolicyTest.java`补显式零分/null与正分混合采纳、全零失败、补分及负分失败测试，记录RED。
- [x] T004 [US1] 在`backend/src/test/java/com/xiyu/bid/scoreparse/domain/OriginalScoreEvidencePolicyTest.java`复跑既有原文核验及可信零分扣分覆盖，并补同章节零分资格与正分引用对照金标，记录RED。
- [x] T005 [US1] 在`backend/src/main/java/com/xiyu/bid/scoreparse/domain/{OriginalScoreQualificationPolicy,ScoreReviewAdoptionPolicy}.java`保持来源核验原语义，实施正分/可信零分扣分必要条件及保留来源元数据。
- [x] T006 [US1] 在`backend/src/test/java/com/xiyu/bid/scoreparse/golden/ScoringOnlyAdoptionGoldenReplayTest.java`使用`backend/src/test/resources/scoreparse-golden/case-scoring-only-adoption.*`合成混表夹具回放至采纳，验证事实保留及正分完整。
## Phase 3 US2 旧候选与前端一致
- [x] T007 [US2] 在`backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewAppServiceTest.java`补旧eligible=true零分快照与默认/显式采纳测试，记录RED。
- [x] T008 [US2] 在`backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewFactMapper.java`重算历史非正分计分资格。
- [x] T009 [US2] 在`src/composables/projectDetail/manualScoreCandidate.spec.js`及相邻组件/提交测试验证默认排除和人工补分；修`manualScoreCandidate.js`及`ScoreCandidateReviewCard.vue`审计提示。
## Phase 4 US3 历史恢复
- [x] T010 [US3] 在`specs/084-scoring-only-adoption/quickstart.md`记录两项目恢复预检、备份、预览、采纳、评分失效及回退边界；不执行生产写入。
## Phase 5 Verify and review
- [ ] T011 运行Mode B清理，定向/金标/相邻回归、架构、前端构建，写`.omc/verify-scoring-only-adoption.md`。
- [ ] T012 独立只读审查并按三轮上限修复，写`.omc/review-scoring-only-adoption.md`；检查spec/plan/tasks一致性至`specs/084-scoring-only-adoption/analysis.md`。
- [ ] T013 追加`docs/lessons/lessons-learned.md`及`.wiki/pages/scoring-only-adoption.md`、索引和日志，交付本地路径及剩余生产恢复事项。

## Dependencies / implementation strategy
T001→T002→RED(T003,T004,T007,T009)→T005/T008/T009→T006→T011→T012→T013。无并行实现，先域策略再旧快照及前端一致性；审查独立只读。任务闭环与部署恢复分开。

- [x] T014 [US2] 在`SourceDeductionSelectionPolicy.java`公开既有verified；`ScoreParseFactEvidenceWriter.java`保存可信零分扣分标记，`ScoreParseReviewAppService.java`传递未改写证据，`ScoreZeroDeductionEvidenceTest.java`及前端/app测试覆盖完整/缺失来源与修改规则失效。

## Execution evidence
- 原始RED：5个后端目标断言及1个前端断言；补扣分例外RED：2个后端及1个前端断言。
- 原文资格内部改动撤回，历史提交证据不复用；可信扣分例外复用既有策略。
- 真实恢复/Writer/Mapper/采纳链44通过；前端49通过；完整领域及相邻/架构最终回归日志见.omc，待最终汇总。
- 标题继承基线失败已在干净57164cb98锚点复现，不删除/弱化断言；领域子集最终命令仅排除该单一已证实用例，其余10个同类用例仍执行。
