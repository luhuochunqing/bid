# Tasks: 评分来源边界
## Setup
- [x] T001 核对诊断工作区、原文与版本，记录 .omc/entry-gate-1133.md
- [x] T002 完成 specs/20261117-scoreparse-source-boundaries/spec.md、plan.md及任务定义
## US1 方案与绑定
- [x] T003 [US1] 在 backend/src/test/java/com/xiyu/bid/scoreparse/domain/Project1133SourceBoundaryTest.java 建目录及方法说明RED
- [x] T004 [US1] 修复 domain/OriginalScoringTextScopePolicy.java 与 OriginalScoringSchemePolicy.java 并验证真实多方案
- [x] T005 [US1] 在 application/TextPrimaryEvidenceBinderTest.java 建材料清单误绑RED；修改 TextPrimaryEvidenceBinder.java，保留文本评分路径
## US2 规则与类型
- [x] T006 [US2] 补齐OCR来源取证，LogicalRuleSourceUnitTest.java 建汇总边界RED，修改 LogicalRuleSourceUnit.java
- [x] T007 [US2] ScoreTypeClassificationPolicyTest.java 建三项分档RED及客观反例，修改 ScoreTypeClassificationPolicy.java
## Acceptance
- [x] T008 合成 scoreparse-golden/case-source-boundaries.md 与 expected.json，同步 golden/GoldenCasesReplayTest.java
- [x] T009 真实输入回放、相邻定向测试、撤销修复反证，保存 docs/reviews/ 本地验证证据
- [x] T010 完成评分全域及架构执行、前端构建和基线归因；更新Wiki、计划和Spec一致性检查（全仓未完成，原因见plan）

依赖：T001→T002→各US RED/GREEN→T008→T009→T010。当前顺序执行，不并发修改共享文件。生产验收不在本轮授权内。

最终清理、验证、独立审查的执行/完成状态由仓库review-workflow在Git元数据review-gate/state.json中独立保存，不以本任务清单自批代替。

- [x] T011 PR独立审查：同步Python目录判据，两入口三例先RED，PDF表格与转换68项GREEN；新完整门禁覆盖Python回归。

- [x] T012 真实PDF经实际转换、Worker、MySQL与review接口贯通验收；逐项比对完整规则与数据库。
- [x] T013 贯通用例的修复撤销反证已失败于目标业务断言；最终门禁与PR更新结果由review-gate和远端PR记录确认，不部署。
