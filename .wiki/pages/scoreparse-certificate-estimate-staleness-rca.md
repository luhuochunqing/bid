---
title: 证书评分漏认、历史预判残留与组合条件失真根因
space: engineering
category: lessons
tags: [scoreparse, certificate, stale-estimate, conjunction, root-cause]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/KnowledgeCategoryPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/EstimatedScoreService.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/match/CertMatchService.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/MatchTierPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/CertificateScoreRule.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/CertificateScorePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseItemsReadSupport.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseItemsMetaBuilder.java
backlinks:
  - _index
created: 2026-10-26
updated: 2026-11-18
health_checked: 2026-11-18
---

# 证书评分漏认、历史预判残留与组合条件失真根因

## 历史诊断状态与证据边界

- 模式：diagnose；未修改业务代码、未部署、未重算、未激活规则、未修改生产数据。
- 取证运行版本：生产发布记录 `838ee5742`；隔离回放直接加载生产 JAR 提取的 class。
- Wiki 所在分支基线：`b7f3dac5e`；该基线不是生产运行版本。
- 原始 API、SQL、字节码及实验日志：仅存本地 `/tmp/xiyu-1117-cert-diagnosis-20261026/`，不入库。
- 完整记录：同目录 `evidence.json`；校验器只检查引用/摘要/结构，不替代因果实验。

## 已证实的三层机制

| 层次 | 触发与机制 | 区分证据 |
|---|---|---|
| 历史分类漏认 | 分类由 dim+detail 改成只看 dim 后，“管理体系”名称不含当时的证书关键词；细则中的 ISO 不参与分类，返回 OTHER | `7812cb6f6` 的父版本同输入返回 CERT；9月历史源码 `affe10738` 返回 OTHER；生产 class 返回 CERT。`f0c3c2565` 才加入“体系”关键词 |
| 历史预判残留 | 估分是解析通过/人工采纳/激活后的副作用；读取不重算，待复核候选保留旧项。规则指纹没有估分引擎版本/知识库修订/校验时点，因此修代码不能使旧预判失效 | 项目1117项225来自task119，更新停在9月22日；后续task135/145/148均为 REVIEW_REQUIRED。GET仍返回旧依据，active_version为空 |
| 当前组合匹配失真 | 原文“三项齐全，缺一不得分”被压成最多6个普通关键词；请求 requiredCount=null。资质SQL任一关键词LIKE即可召回，数量默认要求1条，无逐标准AND校验，FULL直接得满分 | 生产实际查询返回FULL但未包含ISO45001；隔离MySQL移除任一必需证书或只保留无关体系证书，实际估分仍10分/OK |

历史部署的9月22日精确JAR/SHA未取得；历史引入点依据Git对照与同输入回放，不声称已取得当日生产进程证据。

## 项目1117证据摘要

- 评分要求：ISO9001、ISO14001、ISO45001齐全得10分，缺少一项不得分。
- 真实资质：ISO9001有效至2026-12-18；ISO14001及ISO45001有效至2026-11-05。退役示例不计入。
- 当前已生效项：`score_item.id=225`，`parse_task_id=119`，`PENDING`，依据仍为“未识别到知识库匹配类别”。
- 最新解析：task148，`COMPLETED / REVIEW_REQUIRED / ORIGINAL_SCHEME_AMBIGUOUS`；不能把COMPLETED理解为已更新生效项。
- 当前 extraction/latest 返回409 `EXTRACTION_FEATURE_DISABLED`；旧版复核读取路径仍独立存在，不能据此推断整个系统无人工采纳能力。
- Legacy元信息：无activeVersion时拼接最新解析任务时间，评分项却仍属于旧task。不能把meta.parseTime当作该项的重算时间。

## 隔离反例：生产字节码 + 真实MySQL8 + 真实JPA

实验直接调用生产 `EstimatedScoreService.estimateItem`，真实 `CertMatchService` 与 JPA repository；不Mock数据访问。仅在新建临时数据库改变证书fixture，不操作生产。覆盖单项计算和匹配边界，不冒充HTTP全链路或版本写回事务验收。

| 本地fixture | 实际 | 原文要求 | 结果 |
|---|---|---|---|
| 三证齐全 | 10分/OK；召回名单仍缺ISO45001 | 10分 | 数字正确，证据不完整 |
| 缺ISO9001 | 10分/OK | 0分 | 目标行为失败 |
| 缺ISO14001 | 10分/OK | 0分 | 目标行为失败 |
| 缺ISO45001 | 10分/OK | 0分 | 目标行为失败 |
| 仅ISO20000、ISO27001 | 10分/OK | 0分 | 目标行为失败 |
| 无证书 | 0分/DANGER | 0分 | 正常对照 |

## 同类范围核查

- 2026-10-26生产现存 `score_item`：179条，17个项目。
- “未识别到知识库匹配类别”依据：74条，15个项目；取证未截断（SQL上限500，返回74）。
- 生产分类器回放后：16条/9个项目可分类，含CERT4、FINANCIAL7、CREDIT3、BRAND1、WAREHOUSE1。
- CERT4条分布于项目30、45、1117，足以证明旧分类结果残留有跨项目表现；不能据此宣称四条都应满分。
- 余58条不能一律判错；包含主观方案、价格/人工项，以及“企业荣誉”等当前仍不支持的名称。
- 财务/信用能分类也不代表可自动计分：当前服务本来返回人工确认语义。

## 正确性约束与最小修复边界（未实施）

1. 评分要求中的具体证书标准/等级/组合关系必须保留，别用普通召回词替代。三证齐全要求逐标准有效命中，缺任一不能满分；不同无关证书数量不能凑数。
2. 分类要区分要求本体与材料说明；不能全局恢复扫整段detail，否则原厂授权/人员/信用等说明会再次串库。
3. 给已生效规则提供独立的预判更新/失效判定；计算绑定规则身份、引擎版本、知识库状态与有效性时点，具体最小实现需设计确认。保留现有指纹CAS以阻止旧计算覆盖新规则。
4. 候选解析/生效规则/知识库预判/实际投标文件评分分别展示版本与时间。更新预判不得隐式采纳候选或改写阶段2结果。
5. 历史修复应先生成受影响清单，再按明确授权更新；禁止全表把PENDING改OK。

## 逃逸原因与副作用

- 现有分类测试和匹配测试分开；匹配服务测试多用Mock repository，不能验证真实LIKE召回是否保留必需标准。
- 估分单测把FULL响应直接喂给服务，未覆盖原文→关键词→真实证书查询→组合满足状态的同一断言。
- 最低防回归组合：三证齐全、缺任意一证、仅无关证书、过期/退役、断行别名、普通方案不串库、旧预判更新、候选不覆盖、规则切换后旧计算不得写回。
- 影响边界：阶段1满足状态和预计分可能假阴性也可能假阳性；阶段2仍需实际标书证明与签章，不能用知识库齐全替代。

关联：[[score-parse-service]]、[[score-parse-adoption-consistency]]、[[scoreparse-performance-evidence-clarity]]。


## 2026-10-26 修复候选与反证闭环

- 状态：working_tree本地候选，未提交/推送/发布/生产重算；实现规格见下方本地路径。
- 实现：CertificateScoreRule/Policy完整解释已支持的评分段，未知剩余约束待人工；CertMatchService提供逐证书身份与库存id/名称/到期日证据，旧通用match仅保持召回兼容。
- 新鲜度：V1225增加nullable指纹和计算时间；计算引擎、规则、证书事实或核验日改变即STALE，读取隐藏旧分并剔除汇总。当前保守绑定全企业证书事实。
- 独立刷新：POST estimates/refresh复用项目守卫和规则CAS，保存时核对证书输入；明确失败项只清空自身，不阻断其他有效项；候选和阶段2不写入。
- 第1轮独立审查发现附加条件被丢弃、材料引用/人员培训种类串库、等级contains误判及失败项整批拒写，补RED后修复。已知缺必需证书时其他unknown不能掩盖确定0分。
- 后端大套720项通过，普通用户刷新与跨项目403在真实JPA测试补证（该类8项）；前端57项、完整构建与真实MySQL迁移forward/rollback重复执行通过。
- 浏览器为实际Drawer组件的隔离HTTP/JPA/MySQL harness，非全项目路由或生产UAT：STALE隐藏旧10→FRESH10/OK→必需证书退役STALE→FRESH0/DANGER；候选和阶段2保护表SHA一致。
- 规格路径：specs/081-certificate-score-correctness/spec.md；本地最终证据：.omc/browser-receipt.json及.omc/verify-01a17626-f4c9-7fa2-907b-668c1de5ed8b.md。
- 人工规则边界：自动支持明确单ISO、原文三ISO全有全无、明确四荣誉完整阶梯。无法证明企业认证种类/等级/有效期或无法解释完整计分条件时继续待确认。

- **最终审查未通过（2026-10-26）**：三轮NEEDS_FIXES触发人工升级。证书路由仍依赖“具有”等少数细则前缀；企业荣誉原文以“投标人具有”开头时会退出核验路径，旧满分可能残留。候选不得视为根因闭环或发布就绪；应先确认“明确证书维度独立路由、闭合解析只决定自动裁决”的契约，保留方案/人员/培训排除。前述测试与组件证据仅覆盖各自用例。

- **人工确认后恢复（2026-10-26）**：用户确认“证书评分识别独立于计分语法，无法解释条件统一待确认”。明确CERT/企业荣誉维度不依赖细则前缀，方案/人员/团队/培训/实施计划排除优先；其他业务类别不串库。新增3维度×4未知/空细则的读侧与真实MySQL刷新矩阵，先RED后GREEN；后端最终726项零失败/错误，规格独立28断言通过。最终实际Drawer+HTTP+MySQL证据同时验证三ISO10/OK、未知企业荣誉PENDING/null、退役ISO0/DANGER，旧满分和汇总正确清除，候选/阶段2逐字不变。此记录取代上方未闭合P1状态，保留审查历史；仅本地候选，未提交部署。

## 2026-11-11 表格续行管道符与断句三体系规则修复

- **现象定位**：项目 1117（招商积余）中管理体系评分项提取结果末尾附带表格管道符（`| 10.0 | 否`），且前置动词缺失、直接以 `ISO45001...` 开头。
- **机制与修复**：
  1. `CertificateScoreRule.scoringClause` 扩展表格管道符安全剥离，支持 3 列及以上续行残行尾部 `| 分值 | 是/否` 的非标元数据剥离；
  2. `single` 与 `triplePattern` 支持可选动词前缀，对直接以标准体系名称开头的条目具备解析能力；
  3. 对声明“三项齐全得满分，缺少一项不得分”的条款，规范化识别三体系闭环约束（ISO9001/ISO14001/ISO45001），在库资质齐全时正确判定 10 分与 `OK`（满足）。
- **验证**：`CertificateScorePolicyTest#testProject1117TrailingPipeAndTripleStandardClause` 与关联测试全绿。

## 2026-11-18 独立分值体系认证预判漏认

- 项目 1133 已生效的「体系认证」条款对四类证书分别计 1、1、1、3 分。旧预判将「有效期内的」截为名称前缀「期内的」，四类证书全部漏认。旧计数比例也无法还原不同的单项分值。
- 预判对闭合的逐证给分条款按证书标准号与别名对应，再累计原条款分值。预判依据展示台账记录、证书有效期、核验日以及持证主体待核验提示。
- 预判用逐证累加的原始总分判定满足状态，再按 `est_score` 两位小数精度舍入，供落库、依据和结果指纹使用。缺证时，0.497 分即使舍入为满分 0.50，状态仍为 `PENDING`。台账短名「信息安全认证」与明确的 ISO 27001 标准号一起识别。
- 生产条款与台账的离线回放包含四证齐全、仅质量和信息安全、仅缺信息安全三个对照，修复后分别为 6、4、3 分。正常刷新通过隔离 MySQL 验证旧结果失效与落库回读。正式评分仍按投标材料证据执行。
