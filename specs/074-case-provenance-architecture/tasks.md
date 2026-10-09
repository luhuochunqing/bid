# Tasks：案例来源回填架构修复

## US1 分层
- [x] T001 [US1] 在specs/074-case-provenance-architecture/spec.md、plan.md及contracts/backfill.md完成批准设计与规格。
- [x] T002 [US1] 运行backend/src/test/java/com/xiyu/bid/architecture/BidCaseSliceArchitectureTest.java记录原规则失败。
- [x] T003 [US1] 新建backend/src/main/java/com/xiyu/bid/casework/application/port/CaseProvenanceBackfillPort.java及backend/src/main/java/com/xiyu/bid/casework/infrastructure/CaseProvenanceBackfillAdapter.java。
- [x] T004 [US1] 修改backend/src/main/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillAppService.java使用端口与不可变更新。

## US2 语义回归
- [x] T005 [US2] 调整backend/src/test/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillAppServiceTest.java，并在backend/src/test/java/com/xiyu/bid/casework/application/service/CaseProvenanceBackfillBoundaryTest.java补孤立案例、写失败与重复执行覆盖。
- [x] T006 [US2] 运行回填服务、命令行、BidDocumentLoader测试及架构规则。

## US3 持久化与交付
- [x] T007 [US3] 新建backend/src/test/java/com/xiyu/bid/casework/infrastructure/CaseProvenanceBackfillAdapterMysqlTest.java验证五字段、严格筛选、其他字段与事务回滚。
- [x] T008 [US3] 串行执行后端全量与JaCoCo，记录真实结果到本规格quickstart.md。
- [x] T009 [US3] 更新casework目录README.md、docs/lessons/lessons-learned.md和.wiki/pages相关页；独立审查并保存源码通过、交付ESCALATE结论。
- [x] T010 [US3] 根据2026-10-09用户授权，将dd39315e的四个测试契约修订cherry-pick到本独立任务；验证FormDefinitionIntegrationTest、CompetitionIntelControllerIntegrationTest、CaCommitmentLetterUploadIntegrationTest和TenderPermissionIntegrationTest。
- [ ] T011 [US3] 重新执行完整测试、JaCoCo及独立复审，满足PR/push门禁。
- [x] T012 [US3] 合并主线并保留docs/lessons/lessons-learned.md双方内容，案例回填§422、测试契约§423，引用同步。

- [x] T013 [US3] 在backend/src/test/java/com/xiyu/bid/competitionintel/integration/CompetitionIntelControllerIntegrationTest.java补授权创建后的实际数据库记录数与名称断言，并纳入完整验证。

依赖：T001/T002先于实现；T005/T007测试设计先于T003/T004；T006先于T008；T009审查依据最终候选。无前端改动，后端验证覆盖本次内部边界修复。

- [x] T014 [US3] 为最新主线小计列回归新增唯一合计满分测试，最小修复ScoreSubtotalColumnPolicy，并让新增测试先RED再GREEN。
- [x] T015 [US3] 验证全部7项此前失败；评分修复后全量10554项0失败0错误17跳过，JaCoCo有效，独立源评审通过。PR门禁仍待最终提交验证。
