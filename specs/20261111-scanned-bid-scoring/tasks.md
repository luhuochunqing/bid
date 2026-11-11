# Tasks
- [x] T001 固定诊断事实与spec.md/plan.md。
- [x] T002 [US1] document-converter-sidecar/tests/test_scanned_bid_ocr.py建立真实扫描与混合PDF RED。
- [x] T003 [US1] document-converter-sidecar/converter.py复用中文OCR提取完整页序列。
- [x] T004 [US1] document-converter-sidecar/app.py接入opt-in和安全失败分类；test_convert_stream.py契约验证。
- [x] T005 [US1] SidecarStreamTextExtractor.java发送opt-in并映射失败；ScoreScoringAppService.java保留准确原因；相邻JUnit回归。
- [x] T006 [US2] ScoreParseTable.vue/spec.js区分预估原因且保留0分和actual逻辑。
- [x] T007 清理后执行pytest/Vitest/Maven/build并记录RED/GREEN。
- [ ] T008 独立审查、SpecKit一致性检查、现有lessons/wiki回填和本地证据。
## Dependencies
T001→T002→T003→T004→T005；T006独立；T007依赖全部实现；T008依赖T007。采用单代理顺序实施。

## 当前验证状态
后端53、前端90、Sidecar目标14通过，npm run build通过。独立沙箱因Mockito自附加受限ESCALATE；用户已明确批准更换受信任适配器，从清理起完整重跑。1225页合成扫描卷回放仍在进行，不等于177MB原件验收。
