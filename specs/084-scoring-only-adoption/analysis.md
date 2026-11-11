# Spec Kit consistency analysis

- AC01/FR001 → T004/T005/T008：内部来源归属保持，复核投影增加有效分值或可信扣分必要条件。
- AC02/FR002 → T003/T005/T007：默认、显式、旧客户端统一只采纳正分或可信零分扣分；纯策略为最终边界。
- AC03 → T004/T006：正分资格引用作为正常对照，不引入词黑名单。
- AC04/FR003/FR004 → T007/T008/T009/T014：旧资格投影与前端默认选择同时约束；审计完整保留。
- AC05 → T003/T009：人工明确补分保留；负分拒绝，原多方案与聚合测试复核。
- AC06/FR005 → T010：生产恢复runbook可审阅但未执行，不新增表或数据修复脚本。
- Constitution read-only；采用原有纯核心/外壳边界，无额外控制器、无业务Mock。Spec/plan/tasks无当前冲突；最终验证与审查在.omc记录。
- 前置脚本兼容性：本地旧check-prerequisites需SPECIFY_FEATURE=084-scoring-only-adoption；不修改工具绕过分支约束。mandatory git.feature复用既有任务分支已执行；可选自动commit钩子不执行，符合working_tree上限。

## Review reconciliation
首轮两处Major均修复：不再修改内部qualify；保留完整原文核验零分扣分。可信标记由Writer产出，Mapper和App绑定fact及未改写source；客户端不可自报。新增真实Artifact→原文恢复→Writer→Mapper→采纳回放44通过，缺来源/异方案/非扣分及编辑失效反例覆盖。
标题继承失败经干净基准单独复现，记录为明确基线限制，不以修改断言消除；完整领域其他测试继续验。
