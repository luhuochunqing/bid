# Tasks: 证书评分正确性
- [x] T001 核对诊断记录、隔离分支、项目协议与宪法，完成specs/081-certificate-score-correctness/spec.md。
- [x] T002 完成plan.md/research.md/data-model.md/contracts/api.md/quickstart.md并检查授权边界。
- [x] T003 [US1] 在backend/src/test/java/com/xiyu/bid/scoreparse/domain/CertificateScorePolicyTest.java补纯核心反例并确认RED。
- [x] T004 [US1] 实现domain/CertificateScoreRule.java与CertificateScorePolicy.java，覆盖三证及阶梯，未知规则待确认。
- [x] T005 [US1] 更新application/match/CertMatchService.java，真实JPA快照与输入指纹，并用CertificateScoreMysqlTest验证。
- [x] T006 [US2] 为entity/ScoreItem.java增加nullable元数据及V1225/U1225迁移，更新生成schema。
- [x] T007 [US2] 接入EstimatedScoreService与ScoreScoringVersionService，保留现有业绩穿透，CAS只更新估分列。
- [x] T008 [US2] 新增ScoreEstimateRefreshService/Controller，权限、空项、规则切换返回正确语义。
- [x] T009 [US3] 修改DTO和ScoreParseItemsReadSupport/MetaBuilder，陈旧证书预判从状态与汇总排除，来源时间指向实际任务。
- [x] T010 [US3] 新增真实MySQL刷新/失效/候选与阶段2不变/并发守卫测试ScoreEstimateRefreshMysqlTest。
- [x] T011 [US3] 修改scoreParse.js/useScoreParseDrawer.js/ScoreParseDrawer.vue及Vitest，提供独立更新交互。
- [x] T012 ai-slop-cleaner Mode B清理后重跑相关测试、架构、构建与真实API证据，写.omc/verify。
- [x] T013 Spec Kit analyze/converge、独立代码/规格审查与生产风险审查，写.omc/review。
- [x] T014 追加docs/lessons/lessons-learned.md与既有Wiki，保留本地工作区，不提交/推送/部署。

依赖：T003→T004→T005→T007；T006→T007→T008→T009→T010；T011可按固定API并行，最后T012→T013→T014。worker不提交或运行宿主启动脚本。

状态：T012现有测试证据已收集，但候选未获S6通过；T013三轮NEEDS_FIXES人工升级，开放P1见.omc/review。禁止宣称实现完成。

人工确认后恢复周期：已按独立维度路由修正并重新执行RED/GREEN、真实MySQL/实际Drawer验证和独立审查；历史三轮失败保留，最终状态详见.omc/confirmed-review.md。
