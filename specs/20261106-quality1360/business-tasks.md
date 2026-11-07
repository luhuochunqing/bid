# P6任务
- [x] 原件哈希及线上报告复核、25项业务分级
- [x] 语义猜测撤回/真实样本回归RED-GREEN
- [ ] Mode B、原件回放/全量验证、独立审查
- [ ] commit/push/PR（不合并部署）

## fix 第2轮：来源与载体边界

- 修复审查两处Major，仅修改原授权文件；不提交、不push、不建PR、不部署。
- 保护测试：原PricingSubmissionPolicyTest、Quality1360RoleAndLocationTest通过。新增历史/参考来源与非系统载体8个反例，RED为135项中8失败、0错误。
- 评分：只确认直接指令或本次招标文件来源，未知来源转人工；直接必填对照保留FAIL，Engine覆盖模型PASS/FAIL。
- 报价：将载体限定与系统词绑定，非系统表/单不能生成SYS或覆盖独立材料失败。
- 最终验证结果由本轮阶段报告记录；监听端口测试交主执行器verify，不生成审查凭证。

- 扩大compliance全集验证遇到沙箱JVM自附加限制：Mockito inline Byte Buddy初始化失败（Could not self-attach to current VM using external process）。不得声明全量通过；交主执行器verify重跑。
- 全集实际结果：1267项，0断言失败、112环境错误、1跳过；除JVM附加限制外，SidecarStreamDocumentAdapterTest尝试本地socket被沙箱拒绝（Socket Operation not permitted），相关监听测试交主执行器verify，不调整测试或门禁。

## P7交付项

- 两源码对象最小修复、RED/GREEN和旧实现反证已记录于business-evidence。
- 完整门禁及独立审查结果以Git私有review-gate及正式session为准，所有内容变化须重新校验。
- PR以实际远端URL及head与本地HEAD一致为交付依据，不合并部署。

## P8限定两Major
- [x] 固定BA08/09及四策略/两测试写入清单，旧P7 BLOCKED保留。
- [x] 先执行新测试RED：79项6失败。
- [x] 只修改纸面义务与否定清单自身事实判断及必要直接调用者。
- [ ] 包含原有自身事实后置/明确纸面正例的受影响回归及原件重放。
- [ ] 新完整三技能门禁、最终HEAD验收、正常commit/push/PR（不合并部署）。

## P9 当前任务
- [x] 用户批准报告建议、旧证据备份、范围及BA10–12固定。
- [x] 新行为回归RED，另括注隔离RED。
- [x] 删除跨动作推断、同次解析复用、统一候选/最终校准及原因码。
- [x] 完整1305项验证；原件文字回放另存私有日志，待核对终态。
- [ ] 完整交付门禁：实际run第二轮ESCALATE中止，正式BLOCKED；本地1307绿不能替代。未最终HEAD验收或PR。

## 当前生效：用户裁定最小裁剪（2026-11-06）

用户已同意scope-reconciliation.md方案A：本次撤回自然文本评分留白自动FAIL，涉及所有项目此类检查，不做1360特判。评分留白仅召回人工核对及真实摘录；评分模型负结论也不单独判FAIL。无留白的评分保留原模型通道。资格缺陷、真实违规、负偏离、明确纸面提交义务的FAIL保护不变。

BA14取代旧BA05、BA10、BA13中评分直接必填自动FAIL及出现统计的承诺；保留这些输入作MANUAL回归，撤回ScoringRequirementRolePolicy，不重建统一字段解析器。其他BA条款仍有效。原25项6误判/8正确处理/11证据不足不变；不宣称全部25项准确或已线上生效。

历史三轮NEEDS_FIXES保留；人工裁定仅授权一次完整验收和独立审查窗口，再失败停止，不自动fix或重开。PR授权已有，不合并/部署/重算。health/codehealth不可用，本次显式以源码结构删减比较及三项既有架构测试替代，无工具健康评分声明。

## 最新提交PR指令下的结构收口（2026-11-06）
用户在上次BLOCKED后再次明确要求“提交PR”。执行器据此继续必要交付动作：仅移动PricingSubmissionPolicy.instruction内依赖当前channels.start的4行句界定位到渠道循环、动作循环之前，消除四层嵌套。此为执行器对必要且可逆结构修改的范围判断，不冒称用户逐文件批准；不改变报价匹配/否定/unknown/证据索引、不新增方法/词表/测试预期。旧三轮及上次唯一窗口失败保留，不清零。
本次只允许该单文件纯移动和必要记录；按characterization模式先跑既有报价与业务测试，再同样断言验证。之后ModeB→真实完整验证→独立只读审查→相同内容提交→最终HEAD验收→push/PR，任何新Major再次停止，不自动fix或追加范围，不合并部署。health替代仍只限已记录的结构比较及既有架构测试，不豁免审查。

## 2026-11-07 用户批准的纯结构收口
用户明确回复“允许这项纯结构收口后提交PR”。只应用私有pricing-return-count-proposal.patch：PricingSubmissionPolicy中纸面义务原块提为reconcileRequiredPaper私有方法，合并相邻同结果守卫，主方法return由7降至5。规则、文案、优先级、原文索引及全部测试预期不变，不扩报价/评分能力。旧三轮及后续阻断完整保留。
路线继续既有SpecKit、controller=none，proof=characterization，delivery=PR。现有74项报价及业务回归前后相同断言；ModeB→完整合规/架构/原件回放/组件/构建→独立只读审查→相同内容提交→干净HEAD验收→正常push/PR；本窗口禁止自动fix或追加范围，新Major停止。不合并部署重算。health替代沿用既有结构比较及架构测试，不冒称工具分数。

## 2026-11-07 用户继续执行守卫合并补丁
用户在响应性两守卫私有补丁及范围裁定问题之后明确回复“继续”。只应用responsiveness-guard-proposal.patch：ResponsivenessRequirementEvidencePolicy中其他检查项和非PASS两个同结果守卫合并为短路OR，空值优先顺序不变，6return降为5。无新方法、规则、文案、测试预期或业务能力；其他源冻结。既有报价补丁保留。
沿用SpecKit、controller=none、characterization、PR交付；先同组真实入口回归前后验证，ModeB→完整验证→独立审查→提交→干净HEAD验收→push/PR。所有旧失败保留，不清零；不自动fix或追加范围。health替代沿用记录，不冒称工具分数，不合并部署重算。
