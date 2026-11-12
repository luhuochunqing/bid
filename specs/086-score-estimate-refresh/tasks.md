# Tasks
- [x] T001 [US1] backend/src/test/java/com/xiyu/bid/scoreparse/application/EstimatedScoreServiceTest.java 无证书刷新RED
- [x] T002 [US1] backend/src/main/java/com/xiyu/bid/scoreparse/application/EstimatedScoreService.java 全量刷新
- [x] T003 [US2] backend/src/main/java/com/xiyu/bid/scoreparse/dto/ScoreEstimateRefreshDTO.java 结果契约及控制器
- [x] T004 [US3] src/views/Project/stages/components/ScoreParseDrawer.vue 折叠外按钮
- [x] T005 [US2] src/composables/projectDetail/useScoreEstimateRefresh.js 摘要及原因、回读失败/竞态
- [x] T006 [US4] backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreEstimateRefreshMysqlTest.java 真实保存与不变性
- [x] T007 specs/086-score-estimate-refresh/analysis.md 清理后验证与独立审查（round2 PASS，源码/测试摘要固定于.omc/review-agent_claude_score-estimate-refresh.md）
- [ ] T008 [US4] specs/086-score-estimate-refresh/quickstart.md 授权发布后生产1117/1119完整验收
Dependencies:T001→T002→T003→T006；契约固定后T004/T005可并行；T007最后。working_tree禁止自动提交。
