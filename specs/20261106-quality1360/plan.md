# 质量核查修复实施计划
Goal：消除已复现误判与假通过，保留真实缺陷和未知状态。
Architecture：沿现有DocumentContent→requirements→规则/LLM→引擎出口，修正负责该行为的纯策略。PricingSubmissionPolicy只负责招标报价渠道与Word核查适用性；不建立框架。工作在既有隔离worktree，不启动服务，不提交。
Tech：Java21/JUnit5，Vue3/Vitest。
## 宪法检查
纯核心不依赖框架与IO；不新增Mock业务源；无数据迁移；角色与权限不变。
## 切片
1. 基线与25项审查：读取规则、转换、LLM契约及最后校准，保存问题矩阵；新鲜基线。
2. 占位RED→GREEN：评分跨页证据/合同示例/目录召回缺口；资格否决占位关联。证据直接携带触发位置，无法确认作用对象返回人工。
3. 违规与报价RED→GREEN：处罚提示及否定清单反例；系统折扣率提取、融合与最终输出约束；混合要求保守处理，明确缺陷对照不变。
4. 附件与输出RED→GREEN：ID号码/图片标记不得假通过；完整JSON输出示例；前端后端建议优先。
5. Mode B删除死分支和重复规则；完整合规/架构/构建/受影响前端测试；独立审查最多三轮；规格一致性与Wiki回填。
## 验证命令
mvn test -Dtest=Quality1360RegressionTest 先RED后GREEN；mvn test -Dtest=com.xiyu.bid.compliance.**,FPJavaArchitectureTest,ArchitectureTest,MaintainabilityArchitectureTest；npm run build；npx vitest run src/views/Project/stages/components/QualityCheckDetail.qa.spec.js；git diff --check。
## 风险与否决方案
拒绝删除所有下划线检查、对所有关键词静默忽略、只改Prompt、仅固定报告内容；这些不能证明证据与要求的关系。既有资格/偏离模型不重写。DOCX物理页码、完整视觉提取和外部报价事实不在本地可证范围，不借此自动通过。

## 当前生效：用户裁定最小裁剪（2026-11-06）

用户已同意scope-reconciliation.md方案A：本次撤回自然文本评分留白自动FAIL，涉及所有项目此类检查，不做1360特判。评分留白仅召回人工核对及真实摘录；评分模型负结论也不单独判FAIL。无留白的评分保留原模型通道。资格缺陷、真实违规、负偏离、明确纸面提交义务的FAIL保护不变。

BA14取代旧BA05、BA10、BA13中评分直接必填自动FAIL及出现统计的承诺；保留这些输入作MANUAL回归，撤回ScoringRequirementRolePolicy，不重建统一字段解析器。其他BA条款仍有效。原25项6误判/8正确处理/11证据不足不变；不宣称全部25项准确或已线上生效。

历史三轮NEEDS_FIXES保留；人工裁定仅授权一次完整验收和独立审查窗口，再失败停止，不自动fix或重开。PR授权已有，不合并/部署/重算。health/codehealth不可用，本次显式以源码结构删减比较及三项既有架构测试替代，无工具健康评分声明。
