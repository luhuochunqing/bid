# 实施计划：质量核查证据上下文纠偏

目标：按报告56的AC01–AC11修复可证实误判，保留真实缺项和人工确认边界。Java21/SpringBoot3.2，纯规则为主，不迁移。

## 宪法检查
- FP-Java纯核心不引入框架、时间、IO；副作用仍留现有查询编排层。
- 真实API唯一源，无新Mock业务路径；测试用领域输入及既有测试端口模拟确定输出，不伪造生产成功。
- 复杂任务采用Spec Kit规格→计划→任务，实施在受管隔离worktree。
- delivery_target: working_tree；不创建commit，不推送，不部署。

## 设计与文件边界
1. 主代理：SubItemAndAttachmentRules、ResponsivenessReviewPolicy、BasicInfoCheckPolicy及对应测试，必要时NEW纯上下文策略；禁止全局简单关键词白名单。
2. 资质/CA工作单元：QualificationQueryDelegate、QualificationMatchPolicy、QualificationDetailReconciler、DeterministicCaCertificateExtractor、RequirementSectionParser/CaCertificateCheckPolicy及测试；选择同标准最佳证书，未知平台人工确认，保持真实缺锁失败。
3. 语义证据工作单元：SemanticPageRetriever、EvidenceAssembler、LlmAdjudicator、BidDocumentQualityEngine、FrontTableResponsePolicy及测试；显式抽样边界、正文召回、LLM负结论证据校验。
4. 语义证据单元补根因依赖DeterministicFinancialExtractor：局部正向财务需求识别，显式否定/可选模板不被全文关键词升级；FinancialRequirement融合中确定性豁免不被LLM反转，换行真实强制要求仍识别，保留真正须提供审计三表反例。
5. 共用文件不得并发编辑，Maven由主代理串行运行。各工作单元先RED测试再实现，汇总后统一清理与最终验证。

## 被拒方案
- 将全部FAIL改MANUAL/PASS：掩盖真正缺项，拒绝。
- 从原PDF删除规则词：改变业务文件且不修系统，拒绝。
- 新建第二套检索或AI流程：复用现有策略与EvidenceAssembler，拒绝。

## 测试计划
以最小生产形态输入复现误判，同时验证自身违规、表格负偏离、相似ISO错配、真实平台无锁等反例；相关域JUnit与架构门禁、生产构建、独立只读审查与规格一致性。无可用health工具，采用行数基线/现有门禁及审查作明确替代，不声称工具分数通过。

## 风险与边界
语义抽样无法证明文档缺失；扫描件需视觉证据，证据不足人工确认。不能保证整份报告全通过。生产行为仍需合并部署后对同hash文件重跑验收。


## 人工升级后的保守兜底范围（2026-10-25）

用户明确要求“不要假通过”。保留此前三轮 NEEDS_FIXES 记录；财务解析器未完成条款级重构。新增安全出口：财务核查及复合资格性评审不得自动 PASS，统一待人工确认；有证据的 FAIL 保留，其他检查项不受影响。该兜底不代表财务解析准确性已修复，不修改生产报告。需反例 RED→GREEN 和独立复审。

当前交付上限更新：用户已明确授权创建PR。新安全范围定义见spec.md当前PR交付契约；原解析器重构与生产验收继续独立，不混为已完成。
