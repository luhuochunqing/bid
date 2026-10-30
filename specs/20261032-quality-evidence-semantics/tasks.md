# Tasks
## Setup
- [x] T001 核对077历史与最新主线，建立独立CoW沙盒。
- [x] T002 编写spec.md与plan.md。
- [x] T003 完成plan.md独立审查并处理阻断项。首轮NEEDS_FIXES，补齐data-model及接口契约后复审通过（仅计划阶段）。
## US2 偏离
- [x] T004 [US2] 在backend/src/test/java/com/xiyu/bid/compliance/domain/policy/ResponsivenessReviewPolicyTest.java复现评分条件原句及相邻真实自述。基线18/18；新增后20项中原评分句1项失败，其余通过。
- [ ] T005 [US2] 修复RuleCitationContext.java并验证否决策略共享影响。
- [ ] T006 [US2] 在ResponsivenessReviewPolicy.java及引擎接入招标要求适用性，缺失要求时不硬判缺表。
## US1 资格
- [x] T007 [US1] 在TenderRequirementsSanitizeTest.java复现非证书资格丢失。
- [ ] T008 [US1] 在TenderRequirements.java及提取器保留完整条件，证书匹配消费子集。
- [ ] T009 [US1] 在QualificationVetoPolicy.java及最终引擎防止证书子集代表全部资格通过。
## US3 状态
- [ ] T010 [US3] 在QualityCheckStatus.java及QualityCheckItemResult.java新增独立不适用状态，更新所有明确豁免生产点与引擎消费者。
- [ ] T011 [US3] 验证报告存取汇总及qualityCheckDetail.js展示，保持旧快照可读。
## US4 位置
- [ ] T012 [US4] 在DocumentContent.java和解析器保留文件内页及来源，合并保持映射。
- [ ] T013 [US4] 在QualityEvidence.java和证据组装链路传递定位，在qualityCheckDetail.js显示分页口径。
## Verification and review
- [ ] T014 Mode B清理，重跑关联测试、三架构、前端构建并记录.omc/verify。
- [ ] T015 独立审查与规格一致性检查，写.omc/review，Wiki回填。
## Dependencies
T003先于T004；各片先失败证据后实现；T014在全部行为完成后，T015在新验证后。
T006的明确豁免分支依赖T010状态基础；T011必须验证最终引擎输出的计数守恒。T013补充AI页号与摘录不匹配时保持UNKNOWN的反例，不能仅凭页号补物理来源。

## 2026-10-30 qualification checkpoint
以下为前周期历史，其后用户同意已开启结构化模型新周期。
Base updated to742d1d77a by project who-touches/session-gate automatic stash/rebase/pop; task changes preserved. US1 candidate keeps full conditions, exposes certificate subset, and guards automatic qualification PASS at final engine. 97 focused JUnit tests and frontend build pass, but third independent slice review is NEEDS_FIXES. Conditional-change scope and real table rows still lose requirements (primary-agent JShell reproduction). T008/T009 remain unclosed; no fourth repair loop. See .omc/review-quality1030-qualification.md and .omc/verify-quality1030-qualification.md. Await human direction on structured conditions/subrequirements redesign; remaining slices are pending.

## 人工裁决后的结构化周期
- [x] R2-T001 原阻断RED→实现，条件、证书子项与来源分开建模。
- [x] R2-T002 旧构造、JSON往返、with/merge、Source及报告证据测试。
- [x] R2-T003 UNKNOWN引擎/异步保护，保留已知EXPIRED及占位事实。
- [x] R2-T004 Mode B及906项合规/三架构检查、构建、diff检查。
- [ ] R2-T005 第3轮独立审查仍NEEDS_FIXES，禁止交付。

已知强制证书的UNMATCHED仍被旧明细校准硬判FAIL，主Agent与跨模型审查均复现。T008/T009不标完成；停在人工升级点，不进行第4轮源码叠补。建议下一周期只补材料事实状态/原因与结果映射，保留已完成模型。见.omc/review-quality1030-structured.md及.omc/verify-quality1030-structured.md。

## 人工再次确认后的材料事实周期 R3

- [x] R3-T001 已知强制证书未命中RED；新增Finding区分检索缺口与已证实缺陷，保守兼容旧JSON。
- [x] R3-T002 实际异步JSON断言、假等级标签、其他项不改写、真实过期/占位/等级缺陷对照。
- [x] R3-T003 独立审查发现独立level遗漏及分行定位回归，分别补反例与修复；round3有界行为审查MERGE_READY，标准轴无阻断。
- [x] R3-T004 最终合规/三架构验证exit0，915项0失败0错误1冻结回放跳过；结果单独记录，保留外部PDF缺失限制。
- [x] R3-T005 Mode B清理旧注释与死分支，构建、Wiki检查及根因回填；上限仍为working_tree。

R3不等同整个规格完成，T005/T006、完整资格解析验收、N/A及页码等上方未完成项保持待办。代码/审查/生产重算分别验收；本次未提交或部署。
