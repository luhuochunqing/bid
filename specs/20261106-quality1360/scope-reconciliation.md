# 项目1360范围冲突裁定与最小交付提案（S1/S3，未实施）

状态：CONFLICTING_DRIFT；既有S6=BLOCKED；等待一次业务范围裁定。禁止进入S4、开启新审查循环、commit/push/PR。本文不是新的实现轮次，不重命名为P10，不清零历史。

## 理解和假设

原目标：以同SHA原件和报告144为依据，纠正6项已确认误判，保持8项已确认正确处理，11项证据不足不宣称已合规。不追求通用中文语义解析，也不把后来新增的合成断言自动升级为用户需求。8项处理正确不等于8份材料最终合规；11项未验收不等于已经全部自动改为MANUAL。

环境：隔离工作区xiyu-zcode-diagnose-quality1360，分支agent/zcode/diagnose-quality1360，非开发宿主。HEAD/origin main均af77669aa6564d88c6b97cf18614c56850b0b647；origin为Gitee allinai888/bid-xiyu。没有任务提交/推送/PR。33源与最后候选5f55d045…逐字一致。当前无.codegraph，不初始化；仅.specify存在，继续既有SpecKit；不新建统一运行时或其他规格后端。

## 既有审查轮次（不可重置）

| 累计S6次数 | 实际run | 结论与主要问题 |
|---|---|---|
| 1 | 44dc9f03dea946e4aeafa3be7da4add2 / native round1 | NEEDS_FIXES；同名字段用途未绑定、复杂否定后缀误放行 |
| — | 同run round2；0fa11ab7913541f1a803f45d91ca4e5a | S5失败/ESCALATE；分别为旧承诺回归和未改动性能时限波动，不冒充新的S6轮次 |
| 2 | 1658ec099380432c9f84dfa495059ea5 / round1 | NEEDS_FIXES；标题距离补丁仍误判合同样例；随后主执行器有意中断接管 |
| 3 | 3703942447c84509bea74106fc73c6bf / round1 | NEEDS_FIXES；冒号计数与无冒号留白识别语法不同；预算守卫拒绝继续fix |

实际报告逐份读取并记录SHA；索引保存在私有strict-resume-review-ledger.json。历史P1–P9、失败测试、所有中止记录保留。1312项测试绿不能覆盖开放Major。

## 最小方案 + 文件范围

**方案A（建议，需业务裁定）**：撤回这次新增的自然文本评分留白自动FAIL能力；遇到评分留白疑点，只给MANUAL及原位证据。无留白时评分语义仍可按既有模型通道处理；没有可靠事实的模型负结论也不单独定罪。其他检查项已确认的真实违规、资格缺陷、负偏离和明确纸面义务保护不变。不按projectId=1360特判。

这会影响所有项目的这一类评分留白自动硬判，不能假装只是格式修复。原件六误判中没有“正确的评分留白FAIL”验收样本；但历史合成用例对该能力有承诺，必须公开收回承诺后才能改预期。不可偷偷放宽测试、删除反例或宣称能力不变。

| 拟处置 | 精确文件/逻辑 | 业务依据与限制 |
|---|---|---|
| 保留 | EvidenceTextSlice.java；QualificationVetoPolicy.java的原位切片/声明范围 | 延保购买日期不能成为资格缺陷；不能编造物理页号 |
| 保留 | RuleCitationContext.java；SubItemAndAttachmentRules.java的处罚提示/否定清单保护 | 原件警示和编号承诺不当作违规；既有直接违规及直接承诺保护保留 |
| 保留 | PricingSubmissionPolicy.java；DeterministicPriceExtractor.java；PriceRequirement.java | 原件系统折扣率不等于缺Word金额表；不凭Word证明系统已提交；明确纸面义务仍核验 |
| 保留 | IdAuthLetterCheckPolicy.java；组成/版式证据边界；LLM JSON契约；详情具体建议 | 有明确证据不足和契约错误依据，不扩视觉识别或界面重做 |
| 拟裁剪 | ScoringResponsePolicy.java中的角色推断、同名计数、样例范围及自动FAIL分支 | 回到原件评分误判：文本用途无法证实时人工，保留当前实际留白摘录 |
| 拟移除（仅无生产调用后） | ScoringRequirementRolePolicy.java | 新增的通用评分指令解释器不是六项真实纠错所必需；输入回归保留，不以删除测试掩盖 |
| 必要依赖调整 | SemanticEvidenceBoundary.java；BidDocumentQualityEngine.java；LlmAdjudicator.java | 仅清理被撤回评分硬判留下的重复检查/无用参数；不重构其他检查项或新增层 |
| 同步行为契约 | 下面列出的8个评分相关测试文件 | 保留所有旧输入及其他检查项断言；只将已公开收回的评分裸文本硬判预期改人工 |

限定的评分相关测试文件：
- `backend/src/test/java/com/xiyu/bid/compliance/domain/policy/ScoringResponsePolicyTest.java`
- `backend/src/test/java/com/xiyu/bid/compliance/domain/policy/ScoringAndClarificationIntegrationTest.java`
- `backend/src/test/java/com/xiyu/bid/compliance/domain/policy/Quality1360BusinessAcceptanceTest.java`
- `backend/src/test/java/com/xiyu/bid/compliance/domain/policy/Quality1360InstructionScopeTest.java`
- `backend/src/test/java/com/xiyu/bid/compliance/domain/policy/Quality1360RoleAndLocationTest.java`
- `backend/src/test/java/com/xiyu/bid/compliance/domain/policy/Quality1360RegressionTest.java`
- `backend/src/test/java/com/xiyu/bid/compliance/domain/policy/Quality1360DesignStraighteningTest.java`
- `backend/src/test/java/com/xiyu/bid/compliance/domain/policy/R7EvidenceRoleTest.java`

其他现有代码先冻结，不趁裁剪增加新语言覆盖或继续追求统一解析器。若实际依赖超出白名单，先提交具体根因和范围修订，不直接写入。文档只更新既有spec/plan/tasks、compliance README、教训库和本主题Wiki。

**方案B（保留原能力）**：不撤回自然文本评分留白自动FAIL，先把字段识别与计数重建为同一事实来源。这是另一项明确的能力保留任务，需重新明确其格式范围和证据，不属于方案A的最小裁剪。不能承诺所有中文写法都自动识别，也不能再通过换agent/重命名轮次扩预算。

## 范围纪律

路线判定：resume=conflicting_drift；intent=bug（裁剪不可靠实现）；complexity=medium；risk=[data_integrity]；domain=已有后端及必要文档；spec_backend=speckit；controller=none（旧审查进程已停止）；proof_mode=RED-first+退回前后同样本对照；assurance=adversarial；delivery_target=pr，来源为此前明确授权，禁止merge/deploy/线上重算。

当前阶段仅R0→Resume Gate→Entry Gate→S1/S3范围裁定。方案A未批准前，不改业务源码、测试或活动验收契约BA10–13。不能将“严格按流程”解释为自动批准全球能力变更，也不据此新开一轮。原有PR授权不重新索取。

health/codehealth工具当前未提供，缺失已记录；本阶段不冒称已做结构健康比较，也不默认给予S6豁免。后续执行前需明确可用的测量/替代证据与豁免，而不是拿测试数量当健康分数。

## 验证计划（批准后才执行）

1. 固定原件双SHA、25项矩阵和8个测试文件的业务边界；先用原样例验证安全裁剪目标，不新建中文反例能力目标。
2. 原件评分/资格样例、否定承诺、处罚提示、系统报价六项不再错误FAIL；8项已确认正确处理不退化；11项证据不足只记录未验收，不能宣传全部通过。
3. 其他已确认直接违规、资格缺陷、负偏离和纸面义务保护必须通过；评分自动硬判收回需明确测试契约变化，保留原输入。
4. ModeB清理；删除源文件后必须clean编译，避免target中残留class掩盖引用；运行实际合规模块及三项架构、组件25与构建：

```text
mvn -f backend/pom.xml clean test -Dtest=com.xiyu.bid.compliance.**,FPJavaArchitectureTest,ArchitectureTest,MaintainabilityArchitectureTest
python3 /tmp/xiyu-1360-diagnosis/verify-original-business.py
npx vitest run src/views/Project/stages/components/QualityCheckDetail.qa.spec.js
npm run build
```

5. 使用仓库真实三技能门禁，S6独立只读审查。历史累计三次NEEDS_FIXES不清零；本提案申请人工裁定后仅一次完整验收/审查窗口。再失败即停止，不另起循环、不更换代理续命。基线未改动的测试问题如出现，只报告并保留证据，不顺手改阈值或拓展性能优化。
6. 只有当前代码S6=MERGE_READY、正常提交后HEAD验收通过，才正常push和脚本创建PR。没有新审查凭证就不交付；不绕hook、不伪造PASS、不合并部署。

## 需要人工裁定的一件事

是否批准方案A：本次收回新增自然文本评分留白自动FAIL，疑点人工，保留其他明确缺陷保护，并只给一次收敛后的完整验收/审查窗口？方案B表示继续保留该自动能力并单独重设计，不能假装仍是同一个最小修复。

## 用户裁定生效（2026-11-06）
用户回复“同意”，采用方案A及一次完整验收/审查窗口；原三轮失败不清零。仅本次窗口允许实施白名单裁剪。health/codehealth未安装，本次显式采用结构比较（删除解释器、分支/行数及生产引用检查）与既有三项架构测试作为替代证据；不宣称获得工具健康评分，不为业务测试或独立审查豁免。

## 最新提交PR指令下的结构收口（2026-11-06）
用户在上次BLOCKED后再次明确要求“提交PR”。执行器据此继续必要交付动作：仅移动PricingSubmissionPolicy.instruction内依赖当前channels.start的4行句界定位到渠道循环、动作循环之前，消除四层嵌套。此为执行器对必要且可逆结构修改的范围判断，不冒称用户逐文件批准；不改变报价匹配/否定/unknown/证据索引、不新增方法/词表/测试预期。旧三轮及上次唯一窗口失败保留，不清零。
本次只允许该单文件纯移动和必要记录；按characterization模式先跑既有报价与业务测试，再同样断言验证。之后ModeB→真实完整验证→独立只读审查→相同内容提交→最终HEAD验收→push/PR，任何新Major再次停止，不自动fix或追加范围，不合并部署。health替代仍只限已记录的结构比较及既有架构测试，不豁免审查。

## 2026-11-07 用户批准的纯结构收口
用户明确回复“允许这项纯结构收口后提交PR”。只应用私有pricing-return-count-proposal.patch：PricingSubmissionPolicy中纸面义务原块提为reconcileRequiredPaper私有方法，合并相邻同结果守卫，主方法return由7降至5。规则、文案、优先级、原文索引及全部测试预期不变，不扩报价/评分能力。旧三轮及后续阻断完整保留。
路线继续既有SpecKit、controller=none，proof=characterization，delivery=PR。现有74项报价及业务回归前后相同断言；ModeB→完整合规/架构/原件回放/组件/构建→独立只读审查→相同内容提交→干净HEAD验收→正常push/PR；本窗口禁止自动fix或追加范围，新Major停止。不合并部署重算。health替代沿用既有结构比较及架构测试，不冒称工具分数。

提交钩子预检：Optional.get正则把List<Integer> offsets.get误识别为Optional.get。仅在同一报价文件将Instruction构造赋予局部hit后Optional.of(hit)，不修改钩子、规则或索引，保持同一对象构造及返回。

## 2026-11-07 用户继续执行守卫合并补丁
用户在响应性两守卫私有补丁及范围裁定问题之后明确回复“继续”。只应用responsiveness-guard-proposal.patch：ResponsivenessRequirementEvidencePolicy中其他检查项和非PASS两个同结果守卫合并为短路OR，空值优先顺序不变，6return降为5。无新方法、规则、文案、测试预期或业务能力；其他源冻结。既有报价补丁保留。
沿用SpecKit、controller=none、characterization、PR交付；先同组真实入口回归前后验证，ModeB→完整验证→独立审查→提交→干净HEAD验收→push/PR。所有旧失败保留，不清零；不自动fix或追加范围。health替代沿用记录，不冒称工具分数，不合并部署重算。
