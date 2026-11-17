# Tasks: 案例内容相关性约束
## Setup
- [x] T001 固定源码机制、隔离worktree并完成 specs/089-case-topic-relevance/spec.md 与 plan.md。
## US1 内容适用性
- [x] T002 [US1] 新增 backend/src/test/java/com/xiyu/bid/casework/domain/policy/ScoreItemCaseRelevanceRegressionTest.java 并执行RED。
- [x] T003 [US1] 在既有 ScoreItemCaseRecommendAppServiceTest.java 验证同客户置顶证书被排除。
- [x] T004 [US1] 修改 ScoreItemCaseMatchPolicy.java，背景不再制造内容相关性；CaseSubTopicPolicy.java与 scripts/extract-historical-case-features.py 同步配送规则。
## US2 正常路径保护
- [x] T005 [US2] 在根因行为测试覆盖跨客户配送、同主题背景排序、明确证书要求及无评分项默认推荐。
- [x] T006 [US2] 在 scripts/pre-push-gate.sh 接入根因行为测试，执行受影响回归及关键修复撤销实验。
## Validation and Capture
- [x] T007 执行清理、全量回归、独立审查，证据写入 .omc/verify-case-topic-relevance.md 与 review-case-topic-relevance.md。
- [x] T008 更新 .wiki/pages/ai-case-recommend-data-source-fix.md、索引/log及 docs/lessons/lessons-learned.md；分析spec/plan/tasks一致性。
依赖：T001→T002/T003→T004→T005/T006→T007→T008。单Agent写入，审查只读。

## Final verification status
本地工作树修复与目标验收完成；110项聚焦、635项案例模块（1跳过）、真实349候选快照与独立缺陷恢复验证通过。全仓初跑12217项94失败，83依赖问题复用既有Sidecar Python后解决，11项在修复前策略基线同现。全仓未全绿；初次本地交付时自动审查因沙箱Mockito自附加限制ESCALATE停止，无自动通过凭证。用户后续已授权提交PR，正式交付必须重新取得三技能凭证后执行提交/推送/建PR；未部署。完成勾选表示步骤已执行且结果已如实记录，不表示全仓或发布门禁通过。
