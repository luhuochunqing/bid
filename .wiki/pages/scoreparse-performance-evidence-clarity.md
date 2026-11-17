---
title: 业绩预计得分证据可信度与中文解释契约
space: engineering
category: guide
tags: [评分解析, 预计得分, 业绩证据, 保守计分, 中文解释, spec067]
created: 2026-09-21
updated: 2026-11-17
health_checked: 2026-11-17
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/EstimatedScoreService.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ProjectEvidenceRequirementPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/KnowledgeCategoryPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/IncrementalCountScorePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/match/ProjectMatchService.java
  - src/views/Project/stages/components/ScoreItemDetailModal.vue
  - specs/067-score-evidence-clarity/spec.md
  - specs/089-evidence-constraints/spec.md
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/KnowledgeEvidenceConstraints.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/EstimateEvidenceSupport.java
  - backend/src/main/java/com/xiyu/bid/compliance/domain/policy/SemanticEvidenceBoundary.java
backlinks:
  - _index
  - score-parse-service
---

# 业绩预计得分证据可信度与中文解释契约

> **一句话**：知识库检索到候选业绩，只能证明“存在可能相关的记录”，不能直接证明该记录满足金额、日期、合同、付款流水和发票等全部评分条件；必要条件无法验证时必须保守计分，并向用户清楚说明缺口。

## 用户可见问题

项目 1117 的“公司业绩”显示 30/30，但详情只给出“知识库命中”和内部字段，案例库又无法检索到可对应的业绩。用户无法回答三个核心问题：命中了哪条记录、该记录满足了哪些招标条件、为什么足以获得满分。

## 根因链路

1. **约束提取缺口**：合同金额提取失败后，请求省略金额过滤，候选记录仍会返回；“每提供一个”中的中文数量词未被识别时，所需数量回退为 1。
2. **事实层级混淆**：`performance_record` 是候选来源，不是“所有条件已验证”的结论。付款流水、发票等材料无法从普通业绩记录自动证明。
3. **解释层泄漏内部协议**：来源码、匹配层级、状态枚举直接显示在页面，既不易懂，也掩盖了待核验条件。
4. **阶梯计分最高分正则边界（项目 22）**：`每增加1个得1分，最高得7分` 包含逗号 `，`，原正则 `[^，。；;\n]*?` 无法跨逗号匹配最高分，导致所需数量未能正确解析为 7 份而是回退使用大项权重 15 分。优化后放行逗号/空白衔接。
5. **首项零分与项目类型泛化（项目 22）**：规则实际要求 1 项不得分、之后每增加 1 项得 1 分、8 项才满 7 分；同时“办公用品”必须收敛为 `OFFICE`，不能与普通“业绩/合同/案例”关键词做 OR 查询，否则无关工业业绩会造成虚假满分。

## 修复后的判定契约

| 层级 | 能证明什么 | 不能据此推导什么 |
|---|---|---|
| 候选命中 | 知识库存在可能相关的业绩记录 | 该记录已满足全部评分条件 |
| 条件验证 | 金额、日期、品类或材料中的某项已有明确证据 | 未验证条件自动视为满足 |
| 计分结论 | 全部必要条件和数量要求均有证据支撑 | 用候选数量替代有效业绩数量 |

规则包含金额、日期或人工佐证材料时，任何必要条件无法可靠解析或自动验证，都应返回“待核验”，不得自动满分。中文“一个”等数量表达按明确数量解析，不再以默认值掩盖失败。

## 面向用户的解释

详情弹窗统一用中文展示：证据来源、匹配范围、匹配记录、已验证条件、待核验条件和计分结论。内部枚举仍可保留在接口与程序契约中，但必须在展示层映射为清晰中文，不得直接暴露给业务用户。

## 回归与生产边界

- 后端规则测试覆盖金额缺失、中文数量、人工佐证和保守计分；前端测试覆盖内部字段中文化与依据分层展示。
- 黄金样例 `case-1117-performance-evidence` 固定项目 1117 类评分条款的单项结构和 100 分总分。
- 项目 22 的 PDF 换行形态固定在 `case-project-1382-pdf-real-shape`：分类必须为客观项，满分所需数量为 8，8 条合格业绩得 7/7；明确项目类型存在时，匹配查询必须使用枚举条件收敛。
- 测试环境浏览器验收需同时覆盖阶段 1 与阶段 2：阶段 1 显示知识库命中、预计 7/7；阶段 2 使用同一生效评分版本和投标 PDF，实际得分 7/7。旧版本结果不能替代当前版本验收。
- 本次变更不自动改写历史预计得分，也不触发生产项目重算；存量项目需在后续获得明确授权后重新执行评分，才能生成新契约下的结果。


## 项目 1120 日期筛选现场诊断（2026-11-17）

- 范围：只读 GET 生产项目评分项和业绩详情，未执行刷新、重解析、采纳或数据写入。未修改业务代码，未核验生产 JAR 的精确 Git SHA。
- 现场接口：`GET /api/projects/1120/score-parse/items`，生效项 719「供应商业绩」保留日期要求：2023-01-01 至首次报价截止日，以合同签订时间为准。
- 现场结果：预计 5/5，状态 OK，依据为要求 5 项、命中 765 项。命中列表包含业绩 #4、#7、#8、#9。
- 反证：`GET /api/knowledge/performance/{id}` 核实上述记录的 signingDate 分别为 2021-12-30、2020-05-27、2021-01-01、2022-01-01，均早于条款下限。合同名称中的年份不作为签约日期的替代证据。
- 当前源码路径：EstimatedScoreService.estimateItem → ProjectMatchService.findEstimateCandidates → repository.findAll → 按候选数量折算预计分。预判没有传入日期条件；严格 match 接口虽支持 signedAfter，预判没有调用该接口。该路径与现场全量计数现象一致，但未做生产字节码比对。
- 源码变更线索：fbf3eb569 将预计分改为知识库数量粗估；不能把粗估简化解释成允许忽略明确日期要求。旧章节的严格规则结论与新版粗估实现需要区分。
- 同项另一缺口：条款含资格业绩基础 60 分、每增加一个合格业绩加 20 分、满分 100 分，当前却换算成要求 5 项。不能仅以 100÷20 代替含基础分的规则。尚未核验资格条件原文，不能据此断言正确目标总份数。
- 后续验证边界：应按真实签约日期筛选，并核对首次报价截止日上界及资格条件，再校验数量与分值。2023-01-01 边界应纳入，2023 年以前及缺少日期的记录不能作为已证明满足日期的证据。正确命中数和分值本次未重算，不能声称排除旧记录后一定降分。


## 本地系统修复与范围（2026-11-17，尚未部署）

| 检查链路 | 已证明的问题 | 修复与回归 |
| --- | --- | --- |
| 业绩预判 | 日期、金额、类型未传入，基础分数量目标5代替3 | 明确条件先筛；完整/年度/年月区间，至今排除未来，缺日期/金额不满足；复用递增目标 |
| 证书预判 | 五星要求被三星同身份证书满足 | 复用已有等级验证，保留M/N粗估 |
| 人员预判 | 仅在职，忽略岗位和证书 | 筛选明确岗位及有效证书，单人仍计一次 |
| 仓库预判 | 仅可用，忽略地区、省市和面积 | 透传真实province字段后筛选，不用大区推断省份 |
| 品牌预判 | 仅当前有效，指定品牌/产品线未筛 | 常见明确品牌表达及现有ProductLine枚举筛选 |
| 阶段二 | 真实旧合同引用仍得正分 | 明确签约日期/金额缺失或不符合，转待核验；引擎版本升级 |
| 资格整体核查 | 一张证书可短路为整体PASS | 去掉无完整条件覆盖依据的例外；单证匹配不变 |
| 预判读取/保存 | 非证书旧结果永久有效 | 新规则封印及CAS保存验证；读侧隐藏旧数字，保留失败解释 |

- 合成反例：`EstimateHardConstraintsTest`、`KnowledgeEvidenceConstraintsTest`、`PerformanceDateScoringTest`、`QualificationWholeProofTest`。真实数据库链：`EstimateHardConstraintsMysqlTest`。
- 不把所有自然语言都视为可自动证明。首次报价截止日/复杂资格适用条件未取得可信来源时，保留待核验；混合合同引用需逐份核对归属，不能用某一日期证明整份引用。
- 规则版本封印防旧结果或规则变化被继续显示；非证书资料库后续变更仍需主动刷新，未实现所有资料库实时指纹联动。
- 生产项目1120数据没有被本次本地代码自动改变，需部署后重新刷新并按原文核验；历史阶段二及质检结果需单独重新执行。

### fix 第1轮：项目及客户类型（2026-11-17）

- 复现：要求2项集采业绩时，1项集采与2项办公曾被计成10/10；要求2项民营企业业绩时，资料库标准客户类型“民企”曾被误排除，计成0/10。两个应用回归的正确结果均为5/10。
- 修复：从资料库 `ProjectType` 枚举提取标准类型，兼容办公用品/办公类和不区分大小写的MRO；“其他证明材料”不被当作其他项目类型。客户类型先将民企/民营企业/民营等既有别名归一，再匹配标准显示值。纯领域层不依赖应用服务。
- 本轮仅执行无需监听端口的回归；MySQL及需要本地监听的验证交主执行器 verify 阶段。未提交、推送、部署。
- 验证证据：启动参数 `JAVA_TOOL_OPTIONS=-javaagent:/Users/user/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.10/byte-buddy-agent-1.14.10.jar`，执行 `mvn -q -o -f backend/pom.xml -Dtest=KnowledgeEvidenceConstraintsTest,EstimateHardConstraintsTest,EstimatedScoreServiceTest,ScoreEstimateFreshnessTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest test`，49项全部通过，失败/错误/跳过均为0；`git diff --check`通过。首次未加载测试代理的应用测试受沙箱自附加限制，重跑未改配置或断言。


## 补充闭环与原件核对

- 原件资格3.2.2要求至少1个合格业绩，支持本项目基础1项加2项的满分目标3；该确认仅适用于本项目，不替代其他项目资格最低数量核对。
- 原件要求单张不低于20万元的增值税发票及验真材料；发票金额不能用contractAmount验证。明确合同金额门槛才筛合同金额，发票/流水等材料保持粗估数字并单独标待核验。
- 原件递交截止日期为2026-08-26，项目登记时间存在差异，未核对全部澄清/延期记录；首次报价上界继续待核验，未使用开标日期替代。
- 四项补回归：名称含完整五星等级而level为空仍可匹配；“二级类目每份”不当品牌名称；授权必须达到明确未来有效日；发票门槛不错误过滤合同金额。
- 旧linked worktree自动审查因共享origin/main从92d522f8b推进到a20d1a7b4被守卫停止。新独立CoW副本在a20d1a7b4重新固定基线并完整重验，旧BLOCKED凭证不复用。原工作区与证据均保留。

### 正式评分与粗估材料提示分离（2026-11-17）

- 自动审查发现：`KnowledgeEvidenceConstraints.quoteFailure` 无条件返回粗估的材料待核验提示，导致真实引用含合规签约日期和合同关键页时仍清空正分；补齐材料也不能解除。
- 正式评分改用 `parseForScoring`，保留日期、合同金额和未知截止日检查；材料证明继续沿用实际投标文本、模型评估、引用真实性和抽取覆盖校验。粗估仍使用 `parse`，附件条件继续显示待核验，不因本次修复转成已证明满足。
- 回归：`PerformanceDateScoringTest` 覆盖合同关键页、合同复印件、发票正例，以及旧日期、金额不足、缺日期、虚构引用和未知截止日负例。正例已在修复前复现失败；不新增材料关键词即证明真实性的规则。
- 本阶段验证：最小保护13项通过；新增正例先RED，修后正式评分8项、领域条件10项及两组架构测试均通过。扩展命令 `mvn -o -f backend/pom.xml -Dtest=KnowledgeEvidenceConstraintsTest,PerformanceDateScoringTest,EstimateHardConstraintsTest,EstimatedScoreServiceTest,ScoreEstimateFreshnessTest,ScoreItemAssessorTest,ScoreAssessmentGuardTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest test` 共125项，2失败、0错误、0跳过，不能标为整体通过。失败来自执行期间新增的 `invoiceAndContractThresholdsRemainDistinctWhenBothAppear`（父类及继承类各执行一次）：发票20万元与合同100万元并列时，50万元合同仍被预判为满分。保留该测试和现场，交主执行器裁决后续修复范围；本阶段未修改该测试或扩展金额提取逻辑。日志为 `/private/tmp/xiyu-fix-r1-green.log`。


## 品牌多授权组合链路

- 已复现结构性顺序问题：同品牌第一份授权不符合产品线/期限、后续授权合格时，预先按brandId去重会丢失合格事实，误计为0。
- 修复在BrandMatchService中对全部当前有效授权执行同一纯条件谓词，再按真实brandId去重；旧单参数可用品牌查询保留。不以品牌名称替代品牌ID，不新增DTO、表或HTTP接口。
- EstimateHardConstraintsMysqlTest.brandConditionsMustPrecedeIdentityDeduplication在真实MySQL中旧版0/10失败，修后验证两种输入顺序均10/10；同品牌两份合格授权、要求两个品牌时仍5/10。
- 自动写入阶段因BrandMatchService不在其变更快照白名单而ESCALATE，旧运行已停止；主执行器按原spec/plan必要依赖接管，把文件纳入新快照后完整重跑，未绕过门禁。
