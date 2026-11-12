---
title: 测试项目1371预计得分全空的分类输入错位诊断
space: engineering
category: lessons
tags: [scoreparse, estimate, historical-data, knowledge-category]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/EstimatedScoreService.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/KnowledgeCategoryPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreEstimateRefreshService.java
  - backend/src/test/java/com/xiyu/bid/scoreparse/domain/Project1371ColumnInvariantsTest.java
backlinks:
  - _index
created: 2026-11-12
updated: 2026-11-12
health_checked: 2026-11-12
---

# 测试项目1371预计得分全空的分类输入错位诊断

## 状态与证据边界

- 初始diagnose阶段只读取证；随后用户授权本地修复。测试服务器和数据库未修改，未触发远端刷新、重解析或采纳。
- 2026-11-12 测试服务器部署记录：`0e71c2f0f-api8080`，14:17:59启动；包含 !3005、!3008、!3009。本次未逐类核对运行JAR字节码与Git。
- 日志：14:30:27.895 `POST /api/projects/1371/score-parse/estimates/refresh` 200，193ms；随后 GET items 200。HTTP200本身不证明正确得分。
- 只读MySQL：14项全部`est_score=NULL`；7项SUBJECTIVE、1项财务、6项未识别类别。生效项2104—2117源于Task354，created_at为2026-10-30；最近解析任务仍是354（MANUAL_APPROVED）。
- 私有证据目录：`/tmp/xiyu-1371-diagnosis-20261112/`，含数据库快照、日志、代码、分类对照和evidence.json。含客户原始条款的证据不入库；临时目录不保证长期保留。

## 已证实的致错链

1. 诊断时旧版本的历史生效项具体因素名存入`code`，`dim`仅为商务/技术。正文存在，名称未完全丢失，但放在分类器不读取的字段。
2. 诊断时旧版`EstimatedScoreService.estimateItem`把`dim/detail`交给`KnowledgeCategoryPolicy.categorize`，不读取`code`。
3. 诊断时旧版的泛维度、无管道符正文仅允许财务/信用回退；只有特定递增业绩规则能走额外PROJECT回退。其余主动库类别返回OTHER。
4. `isPassiveOrUnsupportedCategory`对OTHER提前返回，不进入dispatchMatch；所以这些记录不能据此判断为知识库无资料。
5. !3008修复certificateOnly空跑，已刷新全部生效项，但不修复历史字段语义、不重解析、不重新分类主客观。!3009修改解析链，现有Task354没有因此自动更新。

## 区分实验

使用当前提交未改动的KnowledgeCategoryPolicy、ScoreItemConstraintExtractor、IncrementalCountScorePolicy源码，在本地临时目录javac编译。读取真实MySQL快照，保持正文不变，对照dim与同一记录code作为分类输入，无数据库写入。

| 项目内因素 | 当前输入结果 | 用具体因素名对照 |
|---|---|---|
| 运营能力及业绩 | OTHER | PROJECT |
| 服务案例及业绩 | OTHER | PROJECT |
| 品牌及货源渠道管控及佐证材料 | OTHER | BRAND |
| 仓储能力 | OTHER | WAREHOUSE |
| 赊销额度能力、物流配送 | OTHER | OTHER |

实验仅证明路由因果，不证明替换字段即可正确计算分数，更不证明知识库候选满足原文全部条件。

## 其他无分值边界

- 财务项缺系统自动底表；赊销和物流承诺不属于现有五类知识库的完整自动评分能力。
- 7项当前标为主观；其中技术对接混合客观案例分与团队评价，整体主观短路会隐藏可拆分的客观部分。资源实力/服务能力正文仅保留标题与分值，不能认定内容完整。
- 两项业绩包含平台运营年限、供应商横向排名、订单/发票条件；知识库命中数量不能替代全部规则。
- 仓储包含山西地域、面积、11地市覆盖及代储承诺。当前一般仓库请求只传面积，地域为空；不得仅修分类后按存在性给整项满分。

## 诊断时的逃逸原因与最小修复建议

- Project1371ColumnInvariantsTest实际读取project21-mro.md，不能证明本次山焦Task354的数据语义和预计得分正确。
- 诊断时旧刷新测试使用具体dim因素名，没有覆盖code存名称、dim存大类的历史形态；本地修复已新增该形态回归，见下节。
- 应在来源可信的因素名解析边界统一code/dim语义，并明确历史项处理；不能全量把编号当名称，不能扫描全文关键词强行归库。
- 修复必须覆盖历史生效项→分类→真实匹配约束→分值→保存→读取→UI。复合、排名、承诺及材料不足应明确部分可算/不可算原因。
- 未重解析原文、未核验上游文件hash、未计算4项正确预计分、未还原10月历史写入执行；最早已证实错误观察为现有数据库dim。GET extraction/latest返回409，未取响应体，不对其确切原因作结论。

## 跨模块影响

- 刷新只改预判列，保留既有规则和scoreType，因此不会自动纠正旧解析数据。
- 名称修复可能改变匹配对象；计分策略若不能表达原文条件，结果仍可能错误。必须单独验证路由正确和得分正确。

## 用户授权后的本地修复（未部署）

- 计算时以KnowledgeCategoryPolicy.factorName统一历史因素名；持久化code/dim/detail/weight/scoreType不变。
- CertificateScoreEstimateSupport与ScoreEstimateFreshness使用同一因素名，避免新结果立即过期。
- ScoreEstimateLimitPolicy区分排名、平台指标、品牌类目/计数/授权区间和仓储地区网点/承诺的未覆盖条件，输出明确待评原因；保留现有六区域计算，但不绕过附加网点/代储承诺。
- 真实MySQL测试已覆盖简单客观项数值和复杂项待评；同一项目原规则列及stage2记录逐列相等。
- 私有14行回放：原4个OTHER恢复PROJECT/BRAND/WAREHOUSE，但仍因未覆盖条件待评；其余财务/主观/承诺类亦未产生数字。该修改不构成项目1371预计数值得分恢复或线上验收。
- 本地RED/GREEN与review/verify证据见任务工作区.omc及前述本机临时证据目录。后续发布须在授权后执行真实按钮→MySQL→API/UI回读验收。

## 第一性原理复核（2026-11-12，业务代码只读）

- 最小业务关系：预计分由原文计分规则与已核验事实共同决定；候选资料命中、字段非空、匹配比例不能单独证明分数。
- 新增限制策略的设计局限：ScoreEstimateLimitPolicy在规则计算前按关键词整项短路。隔离对照“提供1份业绩不得分，每增加1份业绩得1分，最高得5分。业绩合同按签订时间排序列示。”已能被IncrementalCountScorePolicy识别，但限制策略把材料排列顺序解释成供应商排名。这是普通业务语义反例，不是项目1371现场条款；不要把测试可识别片段又当作整条条件已被完整核验。
- 既有策略适用范围过宽：WarehouseRegionalScorePolicy仅凭六地区或三个地区词即认定适用，evaluate固定15分基础、缺区扣3分、增仓最高5分。隔离对照“六大地区内每覆盖一个地区得1分，满分6分”，华北/东北/华东三条仓库实际返回6分，按对照规则应为3分。该结论针对策略输入，不指称1371存在同样已运行计分事故。应先限制原支持公式的适用前提，不能把该策略作为通用地区评分器。
- 语义边界：MatchTierPolicy的requiredCount缺省1使一个候选成为FULL，PartialScorePolicy把FULL映为权重满分。这是既有通用契约，只有匹配已证明整条条件且原文允许满分时才能用于业务计分；不能用它替代逐份计分。
- 架构判断：新增限制类位于纯核心，没有新增框架或存储副作用；主要问题是业务语义边界。KnowledgeMatchResult DTO在构造时将字段非空标为ConditionProof.PASS，只能证明字段存在，不能证明满足本项目阈值。规则核验应由领域策略给结论，DTO负责传递。
- 不宜把问题统一归因缺底表：WarehouseEntity已有province、area、hasPropertyCert/hasInvoice/hasLeaseContract等字段，但WarehouseMatchedItem仅传region/area/status等；授权实体已有brandId、authorizationType、authStartDate，而BrandMatchedItem未传全部这些信息。先核对已有数据真实值、补正确筛选与事实透传，再判断是否需要新模型；附件标记为真仍不等同于材料内容合规。
- 复用范围：IncrementalCountScorePolicy已有计数/封顶算术；CertificateScoreRule/CertificateScorePolicy已有规则支持判定、证据核验、未知处理的模式；KnowledgeMatchResult已有RecordProof/ConditionProof。复用这些能力，避免新建DSL、通用规则引擎、重复知识库和证据平台。品牌去重、最低份数及具体条件仍需按业务补齐，不能把业绩文本解析器直接改名当品牌解析器。
- 建议顺序：收窄已支持规则的识别前提；取现有真实候选及必要字段；按少数明确支持的规则计算；返回数值及未决条件。支持边界来自规则是否完整解释，不来自不断增长的禁算关键词。字段兼容因素名修复可保留；限制策略仅作为临时防误算保护，不作为最终计分能力交付。
- 只执行本地编译类对照，没有修改业务源/测试、没有远端读写。证据：/tmp/xiyu-1371-first-principles-audit/probe-output.txt；业务源及测试与上一轮验证清单哈希一致。本次结论不把先前局部LGTM扩展为通用计分或上线许可。

## 当前口径：知识库满足度粗估（用户确认，替代严格禁算方案）

- 用户明确：有100%、无0%、部分按比例，预计分=权重×min(M/N,1)。它是投标前粗估，不复刻正式评审。
- 方案已写入specs/086-score-estimate-refresh/{spec,plan,tasks}.md，并形成docs/artifacts/score-estimate-simple-plan-v1-20261112.html。本轮仅交付方案，T013—T018未实施。
- 保留因素名兼容、权限/CAS/刷新回执；后续删除临时ScoreEstimateLimitPolicy，预计分不再调用固定六区域公式或严格全文条件门禁。数量不明确默认1且明确依据，金额/年份不当数量；资料去重、可用状态、异常不伪造0。
- 复用PartialScorePolicy增加M/N直接计分，最终分值一次保留2位；复用匹配服务、状态和回执，不新增规则平台、表或HTTP接口；阶段二保持原样。
- 新版验收要求1371已识别四个客观知识库项在正常查询成功时有数值（可为0）；具体值取决于实际资料。旧141项/旧LGTM不是新版通过凭证，旧review已标SUPERSEDED。

## 简单粗估实施结果（当前本地状态，未部署）

- T013—T017完成。估计值统一按min(M/N,1)×weight，最终一次保留两位。临时禁算类已删除；预计分不再套六区域固定公式和严格材料/排名短路。
- 复用现有资料服务读取可用候选：品牌按brandId去重、剔除未来/过期/作废授权；证书按规范身份去重并保留明确中文名称相关性，GB/T19001与ISO9001同一身份；人员粗估复用ACTIVE筛选，strict match关键词门禁不变。
- 数量同时收集直接要求与简单计分推导目标，冲突或不明确默认1，并写依据；年份金额不当数量。异常仍无分且说明失败，不冒充0。
- 182项后端、57项前端及构建通过，独立双审查第2轮LGTM。第1轮证书身份和数量冲突均有RED→GREEN实际反例；隔离植入“只要有记录就全分”缺陷，半分/精确比例断言重新失败。
- 本地真实MySQL四类同形数据得到数值（含0），不改原规则列或stage2。该用例不代表测试服务器1371实际分数；本轮未部署或刷新远端。
- 原严格方案的141项及旧LGTM保留为历史，不是本轮验收凭据。最新本地证据见.omc/verify-1371-simple.md与review-1371-simple.md；T018远端验收仍待后续授权。
