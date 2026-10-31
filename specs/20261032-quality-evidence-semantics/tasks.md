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

## R4 审查缺陷修复（2026-10-31用户立刻修复）

- [x] R4-T001 将多级编号丢项、长证书明细丢失及空明细放行固化RED；真实Async落库JSON验证。
- [x] R4-T002 统一分类与消费关键词入口，保留旧证书名称API边界；覆盖ISO/CMMI/ITSS/CCRC长条件。
- [x] R4-T003 第1/2轮NEEDS_FIXES保留历史；补紧凑/前导零编号、同级章节、日期金额、歧义范围和明确强制依据优先反例，采用编号路径及原文条目模型。
- [x] R4-T004 Mode B拆表格行和校准分支，删除重复长度过滤与Async跳过分支；最终合规及三架构940项0失败0错误1外部PDF回放跳过。
- [ ] R4-T005 第3轮独立审查、提交后门禁与PR2940远端更新核验。

已合并主线6eb9ccab1解决文档编号冲突，任务教训顺延480，保留双方内容及已发布提交历史。此前全量11290项的91失败已在8b基线同用例集合复现；本批不改无关评分模块，不宣称全仓门禁或生产验收完成。

R4第3轮仍NEEDS_FIXES：全可选ISO条件被排除查询后，空明细仍直接保留模型缺证FAIL。主代理与独立审查均经Engine→四参校准复现。940项关联绿不替代此反例；代码提交86b70d452已保留本地，未推送PR。三轮上限已触发，停止第四轮源码叠补，等待人工决定；验证见.omc/verify-quality1031-r4.md。

## R5 人工同意后的空明细材料事实修复

- [x] R5-T001 用Engine到四参校准及旧入口复现空明细保留模型FAIL、已有事实被空参数覆盖和其他检查项元数据丢失。
- [x] R5-T002 统一空明细归一化：保留原材料事实；无事实的模型FAIL降为待确认；可选条件不替代真实强制要求，保留已证实过期、等级缺陷及占位FAIL。
- [x] R5-T003 Mode B删除空明细绕过和重复校准；独立行为及规范轴第二轮复审无未解决源码问题。修复提交e32b14709，源码校验与验证命令记入本地审查证据。

本轮仅交付上述修复及此前获准改动到现有PR2940；R4失败历史和其他未完成规格保持可见。主线e69c187bd文档追加冲突保留双方内容，本任务教训顺延481。最终合并后测试、门禁和远端SHA以本地.omc/verify-quality1031-r5.md及PR记录为准；不代表合并、部署或线上重算。

## R6 结构化语义（2026-10-31 用户同意）

- [ ] R6-T001 固化R3全部反例与构造→查询→四参校准假通过 RED，保留三轮升级历史。
- [ ] R6-T002 资格子句的持证、承诺/处罚与未知对象分别建模并保守传递；证书库查询不吞强制证书。
- [ ] R6-T003 占位归属三态及实际页证据，未知阻止自动PASS；覆盖跨行、跨页、混合章节和正反例。
- [ ] R6-T004 统一Unicode门槛语境及偏离表状态/说明语义，覆盖全文与表格路径。
- [ ] R6-T005 全链路验证、旧契约回归、独立全diff审查及发布证据；测试环境真实重放成功后才评估生产部署。

## R7 证据角色与冲突状态（用户明确同意）

- [ ] R7-T001 在真实入口固定R6第三轮反例、反向事实及旧构造与快照兼容RED。
- [ ] R7-T002 建立资格对象判定及未知向最终状态的保守传播；纯处罚证明不伪装为证书，真实证书不得被静默删除。
- [ ] R7-T003 建立响应事实角色与冲突裁决；表格及全文复用，未知不能由AI自动升级为PASS。
- [ ] R7-T004 旧状态、材料事实、来源定位和规则引用消费者回归；独立全diff审查和真实Async持久化验证。
- [ ] R7-T005 测试环境同哈希重放、案例原件推荐及来源UAT、发布预检与生产决策；未完成则保持待办。

## R8 用户同意：证书绑定与全文语义
- [ ] R8-T001 固化 R7 第三轮证书绑定、多占位、全文自身不足和规则引用的端到端 RED。
- [ ] R8-T002 所有占位独立绑定证书，保留页面偏移，按适用性与已证实事实优先级裁决。
- [ ] R8-T003 全文、表格及最终引擎共享证据事实及未知保护。
- [ ] R8-T004 异步快照、关联合规、架构及独立完整差异复审。
- [ ] R8-T005 测试环境同哈希真实重放及其他功能 UAT，完成生产发布准备。

## R9 证书占位身份绑定（用户明确限域）
- [ ] R9-T001 固化R8第三轮两方向真实校准反例、身份冲突及章节切换RED。
- [ ] R9-T002 独立标识占位自身证书及其局部上下文，明确归属才匹配招标要求。
- [ ] R9-T003 不修改偏离规则；必要关联回归、异步快照及独立复审。
- [ ] R9-T004 可发布后回到原ego工作空间执行测试环境同样本验收和其他授权验证。

## R10 ISO 身份归一和多候选保护
- [x] R10-T001 真实四参入口 RED：常见 ISO 写法、完整编号边界和多证书候选。
- [x] R10-T002 最小修改资格占位证书身份绑定并保留未知与证据。
- [x] R10-T003 关联验证和限域独立审查。
- [ ] R10-T004 回到原 ego 工作空间，核验实际测试环境和生产发布准备。

R10 源码范围复核通过；完整合规/架构回归 0 失败、1 项外部 PDF 跳过，特殊 Unicode 空格保守转人工。未部署、未完成测试环境真实重算，R10-T004 保持待办。
