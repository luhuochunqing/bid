---
title: 项目1133目录页码两侧横线导致伪评分方案
space: engineering
category: debugging
tags: [scoreparse, toc, scheme, project1133]
sources:
  - https://winbid.ehsy.com/project/1133
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringTextScopePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringSchemePolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoreQualificationPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/LogicalRuleSourceUnit.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreTypeClassificationPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/TextPrimaryEvidenceBinder.java
backlinks:
  - .wiki/pages/_index.md
created: 2026-11-17
updated: 2026-11-17
health_checked: 2026-11-17
---

# 项目1133目录页码两侧横线导致伪评分方案

## 现场证据

- 2026-11-17 只读访问生产 review API；最新记录为 parseTaskId=155，taskId=d54eeb06-da43-480f-a915-f672e7badff4，完成于2026-10-28。
- 目录候选标题为「第七章 评分标准 ...........................................- 52 -」；tableIds为空、TEXT_ONLY、总分UNKNOWN，无declaredTotal。52是标题中的页码，未被存为52分。
- 正文候选为「第七章 评分标准」；关联4张表，总分VERIFIED=100，证据doc0-p55-t0008:doc0-p55-t0008-r15-c2。
- 15条候选记录含分类汇总；分类为价格30、商务16、技术54，不能将父子项重复求和。
- 11条资格为ITEM_SCHEME_SCOPE_MISMATCH，4条为SOURCE_LOCATOR_NOT_VERIFIED；全部sourceText为空。任务fallbackReason=ORIGINAL_SCHEME_AMBIGUOUS、adoptionStatus=SCHEME_SELECTION_REQUIRED。

## 当前源码复现

- 基线92d522f8ba7b7d95d3d5882c4da81e3ce1d5a912。
- 直接复制OriginalScoringTextScopePolicy中的TOC_TRAILER和TOC_ENTRY声明到独立Java探针运行。
- 「第七章 评分标准 ...- 52 -」excluded=false；去掉两侧横线成为「...52」则excluded=true；正文标题excluded=false。
- 过滤器仅支持点线直接接数字，或空白接数字并行尾结束；横线包围页码的目录行漏检。
- 标题完整字符串参与schemeId计算；schemeCore也复用同一TOC_TRAILER，因此该目录标题未归一到正文标题。

## 跨模块影响与验证边界

- 目录误入方案根 → 两个方案ID → 原文方案歧义 → 候选资格/发布受阻 → UI要求选择方案。
- 当前源码可复现同类漏检，未核对生产部署SHA，不把本地源码断言为生产逐行执行证据。
- 原文定位失败是另一个已观测阻塞；尚未回放原始转换资产，不能断言全部由目录误判造成。
- 修复验收需覆盖真实输入的目录过滤、正文唯一方案、候选原文绑定和父子计分闭合；仅隐藏下拉选项不足以证明修复。
- 本轮只做诊断与Wiki记录；未修改业务代码、未重解析、未修改线上数据、未推送或部署。
- 相关：[[scoreparse-multimodal-primary]]。

## 2026-11-17 生产日志、SQL与原始PDF续查

- 只读连接生产172.16.10.149；当前deployed-release.json=5cbbf4925。未触发重解析；用户现场新任务248在调查期间自然完成。
- MySQL score_parse_task确认155为2026-10-28旧任务，248为2026-11-17 15:22:22创建、15:27:48完成；不能把旧API快照用于断言新任务结果。
- 原始附件444为61页PDF、376993字节；物理55—57页对应印刷53—55页，评分表12项：30、10、6、10、12、10、3、3、3、3、5、5，合计100。
- 155的15条事实含3个分类汇总；全部source_text为空、item_payload为空。正确方案分到6条技术记录共43分；并非12项完整评分表。
- 本轮只执行Sidecar文档转换核查，无LLM/评分任务写入；当前转换正文39509字符，与旧日志长度一致。旧text:line[1166/1167/1168/1172/1174/1177]在本次转换中分别落到第六章技术部分材料清单（格式自拟等），不是评分表。无旧原始转换快照哈希，不声称历史转换字节同源。
- 旧日志17:38:09：modelFacts=15、fallbackFacts=12；确定性回补因ORIGINAL_SCHEME_AMBIGUOUS及总分闭合失败被拒，保留模型复核事实。原文定位/完整规则恢复问题不能简化为仅目录显示错误。
- 新任务248落库fact ID3603—3614，共12条、100分；全部IN_SCHEME、candidateEligible=true、validation_status=REVIEW_REQUIRED；source_text已非空。接口只返回正文一个方案。
- 新日志15:27:48：模型17条未闭合；baidu_tbl_55_0单表AUTO_APPROVED/100分；总体因ORIGINAL_SCHEME_AMBIGUOUS仍待复核。歧义标题还有「8 评标方法：综合评分法（本项目投标人满足3家时即可开标）。」。全局规则不等于前端当前返回的单个方案。
- 新接口确认「总体组织实施方案」「商品覆盖程度」「项目团队」错误归为OBJECTIVE；原文为带更为优越/基本满足/经验丰富等描述的分档评价。
- 用当前ScoreTypeClassificationPolicy原样静态Pattern对数据库source_text运行Java探针：三项DESCRIPTIVE_PATTERN均false；QUALIFIED_NUMERIC_PATTERN分别命中「满足招标人使用需求且更为优越，得10-7.1」「满足招标人的使用需求，得9-4.1」「提供得0」。该启发式把档位分数当量化条件。线上分类错误已确认；静态Pattern是当前源码复现，未取得生产字节码逐行校对证据。
- 新任务「仓储情况」source_cell_ids还包含r15整行；source_text尾部混入下一行「合计100」，已确认规则边界污染，尚未对生产行合并算法作因果回放。
- SELECT count(*) FROM score_item WHERE project_id=1133仍为0；12条复核候选不是正式生效评分项。未采纳、未改业务代码、未部署。

## 根因机制补充

- TextPrimaryEvidenceBinder.textEvidence（当前源码120—142行）只验证引文在全文归一化后唯一，不要求命中评分章节/分值列/完整规则单元；短标题在材料清单中唯一时可绑定到该清单。与旧任务六个行号落在材料清单的证据相符；后续资格门拒绝该来源，不代表没有发生误定位。
- ScoreTypeClassificationPolicy中主观关键词未覆盖本例「更为优越/基本满足/经验丰富」表达；宽泛的QUALIFIED_NUMERIC_PATTERN把满足/提供后40字内任意数字当成客观条件，包含评分档位和未提供0分。当前源码正则探针已复现。
- LogicalRuleSourceUnit.logicalRow会向后扩展所有isContinuationRow命中的物理行；该方法未先排除ScoreTableSummaryPolicy汇总行，且174—183行把首末列为空的中间有值行判为续行。因此[空,合计,100,空]会被向上吞并。数据库已确认仓储事实含r15及合计100；尚未取得新任务完整原始OCR资产做实际对象回放，具体分支因果保留此边界。

## 本地修复进度（未部署）

- 原始PDF重获资产SHA=f10d3a94504e3b4637683b50e292f7b2d35b642b8c3d59c0d5659db08b34e34c；真实形状r14仓储、r15[空,合计,100,空]，重获不是历史百度响应。
- 最初完整RED为66项5失败0错误；收紧文本来源时发现两项既有无章节规则定位需保留，已通过相邻用例验证。数量门槛反例额外证明不能把「基本满足」一律当主观。
- 真实PDF、金标及相邻回归156项通过，包含12项100分、六主观六客观、完整跨页与无合计污染；不证明模型输出、数据库落库或线上恢复。
- 五处关键实现分别替换为基线字节码后，对应断言均重新失败（目录、方法说明、文本绑定、类型、续行）；测试产物恢复后继续完整验证。
- 客户PDF、完整原文、重获资产及日志移存任务Git私有元数据目录scoreparse-1133-evidence，不纳入Git；docs/reviews中的任务248收据仅保留字段、长度和哈希。

### 扩大回归后的边界校正

- 评分全域+架构首次2179项，96失败、13跳过；不得记为全绿。其中83项的显式失败为缺少docx/lxml的Python运行环境，已核实开发宿主Python可用并重跑。
- Project1382两个回归由新文本来源约束过严引入，HEAD五类字节码覆盖可通过；已补RED→GREEN：保留合同方案外条款定位，让既有资格门明确OUT_OF_SCHEME且candidateEligible=false，而不是丢失定位后变SOURCE_UNVERIFIED。材料清单短标题仍不能绑定为评分证据。
- 其余失败分别按旧实现隔离对照，不能因文件未改就判基线问题，也不改测试预期制造全绿。最终结果另见独立验证收据。

## 离线贯通补证（2026-11-17）

新增Project1133OfflineAcceptanceMysqlTest从同一真实PDF经实际Sidecar HTTP、生产Worker及非版本化EVIDENCE_PIPELINE_V1持久化到隔离MySQL，再由review Controller经MockMvc读取；1项执行通过、无跳过。逐项验证12项100分、6主观6客观、单方案、fact/正式项/API一致、独立PDF说明列完整原文及来源单元格。回退五处Java修复后同一用例因整体REVIEW_REQUIRED失败。没有新增业务实现；历史百度响应、生产Flyway迁移、异步调度、网关/浏览器/OSS授权不属于此离线证据。客户完整输入输出只保留Git私有取证区。详见docs/reviews/scoreparse-1133-verification.md「真实PDF离线贯通验收」。
