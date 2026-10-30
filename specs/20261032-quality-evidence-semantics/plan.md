# Implementation Plan: AI核查语义与证据
## Technical Context
Java21/Spring Boot，Vue3/Vitest。纯核心在compliance/domain，外壳负责模型调用、解析与持久化。基线04ee25a34。
## Constitution Check
不新增Mock或纯核心I/O；逐片RED→GREEN；working_tree交付，不推送、不改宪法。
## Task slices
1. US2：原句与真实自述RED，修正局部评分文法；接入偏离表适用性。
2. US1：完整资格条件保留，证书消费子集；最终输出防止子集代表全部资格通过。
3. US3：独立不适用状态，生产点、引擎、存取、汇总和前端一致；旧报告兼容读取。
4. US4：解析页保留文件身份、原页与分页来源，合并保留映射；证据和展示消费映射。
## Verification
JUnit覆盖每片正反例；关联compliance与三架构测试；Vitest、前端构建、diff检查。Mode B清理后重新验证与独立审查。
## Alternatives rejected
只改标签无法修正模型；全文负向词不能证明投标人偏离；不把全部负偏离降级人工；不把虚拟页称物理页。
## Review checkpoints
实现前审查接口兼容、状态覆盖、资格下游过滤和页码重复编号；每片验证后再扩大范围。最终审查需完整diff与新测试证据。

## 人工裁决后的新周期
1. 固化强制证书附属变更条件、真实表格数据行两个RED。
2. 引入QualificationCondition及CertificateRequirementPolicy，作用对象、适用性、依据与来源显式建模；未知可见且保守核验。
3. TenderRequirements兼容旧构造/JSON，完整条件与证书查询投影均从结构化模型产生，所有with/merge保留来源。
4. 表格按单元格角色组装；确定性提取绑定原文件及实际引用页，LLM prompt消费结构化上下文；查询缺口不证明未提供。
5. Mode B删除整句可选关键词过滤，重跑关联/架构/构建与新周期独立审查。旧NEEDS_FIXES历史保持，新周期最多3轮。

## Mode B cleanup scope
117项关联测试保护当前行为。仅清理本次改动文件：删除无调用的isValidQualification兼容别名；删除prompt中重复的完整资格文本序列化，保留结构化条件与来源一次；复用章节正则，修正已过时的PASS/FAIL注释与测试命名。不调整其他业务规则，不新加依赖。清理后重跑关联与架构检查。

## R3 材料事实映射周期（用户再次确认）
1. 真实强制要求、有来源、无检索命中经四参校准误判FAIL的RED；同时保留过期、等级不足与占位正反例。
2. QualificationDetail增加Finding（LOOKUP_MISS/EVIDENCE_INCOMPLETE/MATCHED_REFERENCE/EXPIRED/GRADE_INSUFFICIENT/UNKNOWN），旧构造/JSON不从备注猜缺陷。生成器在取得相应日期/等级事实时显式赋值。
3. Reconciler按事实原因映射，未命中及证据不足人工；过期、等级不足与四参入口验证出的实际占位仍FAIL。去掉按结论文字猜材料事实的重复分支。
4. 验证真实Async JSON、旧格式、已知/未知混合、模型PASS/FAIL不得升级纯检索缺口；Mode B后重跑合规/架构/构建及新周期审查。working_tree交付，不推送。
