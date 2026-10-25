# 规格一致性与验收映射

## 输入
- spec.md：AC01–AC11、FR01–FR04。
- plan.md：纯规则/查询、语义证据及财务融合三条根因链。
- tasks.md：RED→GREEN、清理后验证、独立审查、部署边界。
- 证据来源：生产报告56，原始JSON/PDF信息保留在仓库外；测试使用最小脱敏特征。

## 覆盖映射
| 验收 | 可执行验证 |
|---|---|
| AC01/02 | Project1117RuleContextTest：规则引用与真实声明并存、否定语境跨句、命中证据准确定位 |
| AC03 | DeterministicCaCertificateExtractorTest、TenderRequirementsParserTest、CaCertificateCheckPolicyResourceTest：泛称、具体平台、地域CA、豁免/强制冲突 |
| AC04/05 | QualificationMatchPolicyTest、QualificationQueryDelegateTest、QualificationDetailReconcilerTest：不同标准、候选顺序、有效期、汇总冲突 |
| AC06/07 | QualityEvidenceBoundaryTest、SemanticPageRetrieverTest：抽样/无分页截断、授权身份证正文与缺失推断、明确留白仍FAIL |
| AC08 | DeterministicFinancialExtractorTest、FinancialRequirementTest、QualityEvidenceBoundaryTest：局部义务与字段scope、否定/可选/融合/unknown、无关摘录不能支撑缺财务 |
| AC09/10 | Project1117RuleContextTest、BasicInfoAndAttachmentRulesTest：跨页/同页索引不遮蔽实际联系方式，承诺正文/落款模板边界 |
| AC11 | BasicInfoAndAttachmentRulesTest、SealVisionFusionPolicyTest、DeterministicCheckPolicyTest：签章不明人工确认；保证金豁免/退款人工确认既有契约 |

## 一致性结论
- 旧077的封面日期已完成，本次不重复实现。
- 未把所有12项强改PASS；未知财务、图片或无定位证据保留人工确认。
- 主线合入、生产部署、清缓存/强制重算均不属于working_tree交付。
- 第一轮独立审查发现的财务scope、无关证据、承诺假通过与同页索引问题已进入RED用例与同一任务单；未用审查意见直接替代测试。

## 当前验证状态
以最终S5/S6工件为准；尚在NEEDS_FIXES回路时不据此宣称通过或生产已修复。

## 第二轮审查回路
条件主体豁免、CPA字段否定、须提交动词及“正向标题不能证明缺失”5个反例已执行RED（48项/5失败/0错误），保留原文自认缺失和明确留白FAIL控制。修复后必须重跑S5并接受第三轮独立审查；若第三轮仍NEEDS_FIXES，按路由升级而不继续堆叠补丁。

## 原目标历史状态：BLOCKED（第三轮独立审查）

- Spec轴认为原AC覆盖；Standards/生产风险轴仍为NEEDS_FIXES。不能合并为MERGE_READY。
- 可复现假PASS：`本项目无需提供财务审计报告但须提供2025年资产负债表。` 提取器把整句判为required=false，空投标材料经FinancialStatementCheckPolicy被判免除PASS。证据guard只处理FAIL，无法兜住错误PASS。
- 804项测试、构建和4项报告摘录回放均通过，但未覆盖此反例，不能作为功能完成或生产安全证据。
- 按unified-dev-flow三轮上限停止补丁，不提交/推送/部署。当前修改仅供审查，不可作为可发布候选。

## 待人工决策的恢复方案

建议把财务义务改为条款级事实：分别保存适用主体/条件、义务对象（报表/审计/印章/年份）、来源片段和否定/转折作用域；聚合到既有FinancialRequirement时，只有所有适用条款均明确豁免才允许notRequired，冲突或条件未知一律人工确认。保留既有API/数据库形状，不再以单句任意命中豁免直接清空整组要求。

替代方案是暂将财务自动结论降为人工核查，先阻止错误PASS，再独立设计上述条款事实层。两方案均需用户决策后重入Entry Gate，不在本轮直接实施。

## 用户升级后的新范围审查（当前PR）

用户要求“不要假通过”后，选择资格/财务自动PASS统一人工兜底；用户随后明确授权创建PR。源码文件摘要3314baa2d934325822e1298acb9736ca70918993fd2918b1a241189d73d74225（33个源/测试/模块README文件）。新安全范围Standards独立审查MERGE_READY，Spec复核确认原AC08仍未完整修复，不能将当前限定结论替代原目标。

当前验证：809测试/0失败/0错误/0跳过、构建exit0、4项生产报告摘录本地确定性回放、同句豁免/义务空标书MANUAL。无生产操作。代价是完整财务材料及真实豁免也必须人工确认，自动通过率下降。知识沉淀与Wiki均包含未解决根因。
