# Tasks

- [x] T001 复核诊断与隔离基线，建立 specs/20261116-quality-page-provenance/spec.md、plan.md。
- [x] T002 [US1] 在 BidQualityCheckAsyncExecutorTest.java 与 BidQualityCheckTaskAppServiceTest.java 添加实际出口页码失败断言。
- [x] T003 [US1] 修改 domain/DocumentContent.java、service/SidecarPayloadParser.java 与 ScanPageTextPolicy.java、SemanticPageRetriever.java 保留原件页来源，分离内容块。
- [x] T004 [US1] 新增 domain/policy/ReportEvidenceLocationPolicy.java，修改 QualityEvidence.java、QualificationDetail.java 并验证多册、文本与视觉证据投影。
- [x] T005 [US1] 修改 LlmVerdict.java、service/LlmVerdictParser.java、LlmAdjudicator.java 验证模型摘录来源，移除不相关首命中定位。
- [x] T006 [US2] 新增 application/QualityReportSnapshot.java；修改 BidQualityCheckAsyncExecutor.java、BidQualityCheckTaskAppService.java、dto/QualityCheckReportDTO.java，保护旧报告及缓存。
- [x] T007 [US1] 修改 QualityCheckDetailDialog.vue、composables/qualityCheckDetail.js 与对应Vitest；QualityCheckDrawer.vue提示历史报告。
- [x] T008 修改 scripts/check-pages-contract.mjs 接入现有根因行为测试；更新模块README及原有Wiki/教训。
- [x] T009 运行compliance回归、架构检查、前端验证与变异实验；在specs/20261116-quality-page-provenance/validation.md记录证据。
- [x] T010 已完成前轮Spec Kit一致性分析与独立双审查。本轮提交PR的刷新结果由Git私有review-gate/state.json及Gitee PR记录。

依赖：T002 RED → T003/T004 → T005/T006 → T007/T008 → T009 → T010。单控制器顺序执行，不派实现子代理。

- [x] T011 用户授权提交PR，更新spec.md与plan.md交付范围，并增加e2e/quality-location-render.spec.js及独立配置执行真实组件浏览器验证。最终执行结果见validation.md与私有门禁产物。
