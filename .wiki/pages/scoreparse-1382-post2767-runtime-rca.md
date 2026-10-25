---
title: 项目1382部署2767后的分层阻断与真实运行取证
space: engineering
category: debugging
tags: [scoreparse, pdf, source-evidence, runtime, regression]
sources:
  - document-converter-sidecar/converter.py
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/TextPrimaryEvidenceBinder.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/LogicalRowEvidenceBinder.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoreWeightEvidencePolicy.java
  - src/views/Project/stages/components/ScoreParseDrawer.vue
  - backend/src/test/java/com/xiyu/bid/scoreparse/golden/GoldenCasesReplayTest.java
backlinks:
  - .wiki/pages/scoreparse-1382-pdf-scheme-gate-rca.md
created: 2026-09-26
updated: 2026-09-29
health_checked: 2026-10-25
archive: false
---

# 项目1382部署2767后的分层阻断与真实运行取证

## 结论和状态

只读诊断，未修改业务代码、数据库或运行配置，未重启、部署、重新解析。用户此前执行的两次解析均真实失败。!2767修复的表格章归属和事实ID唯一性部分已经生效，但真实链路仍有多个独立阻断，其中表内“第一部分”误切章节由该PR新增守卫触发。不能继续将“召回16项且分数加起来等于100”当作整个解析正确的证据。

证据包：本任务worktree的 `.runtime/root-cause/1382-post2767/evidence.json`；包含SSH日志、只读SQL、当前源码快照和隔离探针。原始客户材料及引文仅保留在忽略目录，不提交。机械证据校验通过仅证明结构与摘要一致。

## 服务器与数据库事实

- 测试服务器 `172.16.38.78`，hostname `winbid-01`，部署版本 `e2502f5de`，后端于2026-09-26 19:39:33启动。
- 容器运行的 `converter.py` SHA-256与该版本本地源码一致：`eb130795561ff96ca5654f6144b430f0282c92ddb5708b7d2d1a88eb0f271298`。
- 输入为办公用品.pdf，日志文件大小1607617字节，转换正文268163字符。

| 任务 | 完成时间 | 候选数 | 获得方案资格 | 阻断 |
| --- | --- | ---: | ---: | --- |
| 271，部署前 | 9月25日21:35:07 | 56 | 0 | 方案歧义；56条只有44个唯一ID |
| 272，部署后 | 9月26日19:52:44 | 52 | 13 | 叶子合计67；共享格权重、类别/名称、定位等失败 |
| 273，部署后 | 9月26日20:06:06 | 56 | 0 | 43条SOURCE_LOCATOR_NOT_VERIFIED、12条ITEM_SCHEME_SCOPE_MISMATCH、1条SOURCE_SCHEME_NOT_VERIFIED |

任务273 UUID `10117741-558e-4885-bbe3-6ddfcb6ed648`；traceId `a49a431101ba4244a51f0f4a92ae0a4e`。日志显示177条校验问题，随后明确“保留现有评分项不覆盖”。

正式 `score_item` 的3条仍来自任务266，创建/更新时间均为9月25日17:40:17，权重1、45、12，合计58。最新任务时间与旧正式数据被同时展示。

## 致错链与控制实验

### 1. 新章节守卫误切表内子部分

真实PDF物理第38页表格从y=56.94延伸至755.29。内部y=418.1538的“第一部分”被 `_pdf_page_unresolved_section_break_tops` 当作章节断点。第38页表格在断点上方，因此仍保留第三章；页末状态却变成空章、cut=true，传到第39页。该页12分报价事实能够定位 `doc0-p39-t0015-r0-c3`，但因无方案归属而失败。

隔离对照只移除第38页表格内部断点，其余条件不变：第39页归属从 `('', true)` 恢复为 `('第三章 评审办法', false)`。这是因果实验，不是已经实施的修复。最终设计还必须验证真正跨章、章节标题在表格内等反例，不能全局忽略“第X部分”。

### 2. 引文跨单元格后无法回指

任务272的7分业绩引文只包含规则，能绑定单元格；任务273相同规则前加了“供应商业绩\n（7分）\n”，变成名称格+规则格。`TextPrimaryEvidenceBinder`先做单格包含匹配，失败后尝试逻辑行；但PDF产物的单元格没有 `logicalRowId`，`LogicalRowEvidenceBinder`跳过所有这些格。纯文本兜底也无法在按视觉行混排的正文里连续命中该拼接引文。

使用当前Java源码编译隔离探针，仅更换同一事实的引文：

- 466字符名称+规则：`table='' cells=[]`。
- 455字符原规则：`table=p34-t0010 cells=[p34-t0010-r2-c3]`。

这解释了相同输入两次解析为何从部分可定位变成全部不合格。不能用任意模糊字符串匹配解决；需要在原文行/单元格结构中验证组成片段及其同一条目关系。

### 3. 类别被用作条目名称校验

任务272持久化的 `dimension` 常为“商务”“技术”，而 `OriginalScoreWeightEvidencePolicy` 要求它出现在所引条目行。跨页续表的网点、商城运营等行没有上层“商务”文字，故拒绝，即使引文已定位。

隔离Java对照：相同3分事实、同一引文与定位，仅把dimension从“商务”换成原文名称格文本，结果从 `ITEM_SOURCE_SCOPE_MISMATCH` 变为该证据策略的空问题列表。这里只证明这一个拒绝的机制，不代表整份文档自动通过。类别与条目名称需分开建模或明确字段契约。

### 4. 权重证据路由不一致与共享行缺少条目范围

任务272真实证据还包括：15/45/40父项共用分值构成格却得到 `ITEM_WEIGHT_SOURCE_UNKNOWN expected=[]`；28分报价行的候选最大值集合同时出现40、28或40、28、12，被拒绝。进一步用当前Java权重策略隔离回放：同一“商务部分：15分”引文只切换table/text证据路由，table得到expected=[]，text通过。这证明两条证据路径的最大分识别契约不一致。表格路径的正则不识别该类别冒号声明，且读取整行候选数值；28分条目所在共享行含40/28/12，实际回放再次得到expected=[40,28,12]。需要绑定类别/条目对应的原文子范围，而不能把整行所有数字视为该条目唯一最大分。其他父子计分、跨页完整性仍需全链路回放，不能声称这些局部对照已证明100分闭合。

### 5. UI掩盖新失败的具体形态

`ScoreParseDrawer.vue`只在 `scoreItems.length===0 && candidateFacts.length>0` 时展示候选卡；本例有3条旧正式项，所以新候选及各自失败原因不可见。`useScoreParseDrawer.js`却更新为最新任务完成时间。发布保护不覆盖旧项是预期行为；新旧结果同屏缺乏来源区分导致用户认为“重新解析完全没变化”。

## 为什么此前的测试与修复未收敛

`project1382RealShapeArtifact`用expected.json中的dimension、quote、weight直接生成理想名称格、规则格及独立“评分分值”列，并预填正确章归属；对应模型事实来自同一份expected。因此未穿过真实PDF转换、模型跨格引文、缺少独立权重列及页面状态这几层。本轮新增的11行Markdown夹具不含真实第38/39页表内“第一部分/第二部分”形态。

这些测试可以验证各自规则，但不能证明真实文件端到端可发布。之前文档的“召回不是瓶颈”只适用于当时那次任务与对应16条目标事实，不能外推为所有重跑的定位和字段都正确。

## 最小完整修改边界与验收约束

1. PDF表格内子标题与章级事件的边界；覆盖真实38/39页及真正跨章负例。
2. PDF多格、跨页证据回指；保留组成片段和来源格，禁止无证据拼接或模糊采纳。
3. 分类与条目名称的字段契约；保持DTO、校验及展示一致。
4. 共享格最大分、父子计分单元、跨页报价权重需继续用真实产物隔离验证。
5. 最新复核任务与旧正式项分别标明来源，旧项存在时仍可见新候选及阻断原因。

验收必须从真实PDF进入Sidecar、后端证据链、落库、真实读取接口到UI；验证商务15、技术45、报价40，13个叶子100分，父级不重复计分，履约考核条目不混入。局部反证实验和合成金标通过均不能代替此验收。

## 证据限制

- 使用本机先前保留的客户PDF，SHA-256为 `18061117e166335881c6a1864f708e5109988af773aea321b26c0f4b751ae18f`。文件大小、转换正文长度及表格ID与当前服务器记录一致，但未重新下载当前OBS对象做SHA比对。
- 本地OCR初始化不可用；探针覆盖原生文本/表格路径。线上 `TextPrimaryEvidenceBinder.bundle` 构造bundle时未携带textBlocks，故不能用本地OCR缺失解释这条绑定路径的失败。
- 未保存或重放线上模型完整原始响应；通过持久化引文、资格证据与运行日志确定当前拒绝机制。
- 未运行完整回归，也未实施业务修复。后续修复不能据本报告直接声称可发布或已验收。

## 2026-09-27 终局修复与 91 分留存根因剖析

### 1. 为什么界面长期停留在 91 分？
- 数据库正式表 `score_item` 数据全部来源于 2026-09-27 17:30:48 admin 手工采纳的 Task 275 快照。
- `ScoreUnitPolicy` 中间层价格节点因缺少表格被过滤，导致父项 40 分与两个底层叶子（28+12）均被当作独立叶子重复计入（$40+28+12+11=91$ 分）。
- 随后发起的任务因叶子和不等于声明总分，决策全为 `REVIEW_REQUIRED`，被防洗数门禁拦截无法覆盖正式表。

### 2. 为什么绝对不能直接开全局版本化开关（APP_SCORE_PARSE_EXTRACTION_VERSION_ENABLED）？
- 开启后将引发 8 处致命拦截：8000 字符限制硬崩、轻度残缺置 FAILED、传统召回丢失 Scheme 导致入库 0 项、存量项目 token.versionId 为空锁死、总分零容错强等门禁、多标段拒收、409 互斥锁、前端界面隐藏手动激活面板。
- 1382 的根因在抽取与对齐层，开关不具备抽取能力，开启后 1382 依然因 $91 \neq 100$ 无法自动激活，且全站常规项目将被打崩。

### 3. 本次落地的 3 处定向算法修复
- **Sidecar 表内断点误切修复**：`converter.py` 新增 `_pdf_line_inside_table`，带 1.0pt 容差排除位于表格内部的普通文本行断点。
- **ChildAggregation D6 聚合判定修复**：`ChildAggregation.java` 新增 `additive` 累加判定，收敛 `sharedCap` 关键字，解耦父项自身满分与子项共享上限约束。
- **跨单元格引文匹配修复**：`PdfRowEvidenceBinder.java` 引入 k-combination 组合搜索与全量匹配优先消解，支持跨过分值列的多格事实绑定与纯正项目名清洗。

### 4. 2026-09-28 标书原文恢复与多 Peer 兄弟项全盘熔断修复

针对项目 1382 评分项在界面上标书原文列显示为破折号 "—"（报价 28 分与 12 分项原文缺失）的物理根因与落地修复：
- **Sidecar 表格跨页续页判定放宽**：`converter.py` 的 `_is_pdf_score_table_continuation` 增加 `not first_cell` 判定，允许续页首单元格为空字符串时继续合并，保持第 34~39 页物理表完整合并为单张物理表（p34-t0010）；
- **续表上下文闭合放宽**：`LogicalRuleSourceUnit.closed()` 在检测到单元格处于第三章评审办法等评分方案根章节时，放宽对 PDF 跨页续表严格独立序号列的限制，允许按方案范围闭合；
- **解除兄弟项连带熔断**：`OriginalRuleRestoration` 中移除连带未闭合判断，当前项规则自身闭合且有结构性证据时，不再被未闭合的兄弟 Peer 连带清空原文；
- **ChildAggregation 枚举词解冲突**：移除 `exclusive` 正则中的 `中\s*[：:为]`，收敛为 `优/良/差` 档位匹配，杜绝标书原文中“其中：”枚举引导词将 SUM 父子项误伤为互斥分档；
- **真实 PDF 178 表 Artifact 替换人造假夹具**：以 Sidecar 对《办公用品.pdf》真实抽取的 178 表 Artifact 作为唯一金标测试源，彻底杜绝手工拼接表头带来的虚假安全感。

### 5. 2026-09-28 跨章节隔离与表内续行聚合：Sidecar→后端→落库→UI 真实闭环 100 分自动通过

针对测试环境部署后重新解析依然停留在 91 分（丢了报价 28 分和 12 分等关键叶子项，且出现 14 项虚假违规导致降级为 REVIEW_REQUIRED）的底层根因与根治方案：
- **跨章节误绑防御**：在 `LogicalRowEvidenceBinder` 中增加 `TextPrimaryEvidenceBinder.tableInFactScope(fact, firstTable, schemes)` 守卫，并在 `TextPrimaryEvidenceBinder` 中明确履约/考核类章节事实不得绑定到评分表，彻底消除误绑导致的 14 项虚假违规（`ITEM_SOURCE_SCOPE_MISMATCH`, `RULE_MISSING`, `PARENT_NOT_FOUND` 等）；
- **PDF 表内续行逻辑行聚合**：在 `converter.py` 的 `_link_pdf_logical_rows` 与 `LogicalRuleSourceUnit.java` 中增加表内续行检测（前置序号/名称列为空且末列非空时，双向扩展纳入同一逻辑行），完整拼接跨行规则文本，恢复 `uniqueSpan` 闭合能力；
- **同格多 Peer 标题模糊对齐**：在 `OriginalRuleUnitText.java` 中增加第一行标题匹配，精准切分同单元格内的“第一部分/第二部分”多 Candidate 规则边界；
- **Sidecar→后端→落库→UI 全闭环真实测试**：新建 `Project1382FullPipelineIntegrationTest`，使用物理抽取导出的 178 表产物与真实 45 项事实，打通提取判定（`AUTO_APPROVED`）、叶子项转换（13 项）、数据库持久化（`ScoreItemPersistenceService.persistItems`）、UI 聚合器（`SummaryAggregator` 100.00 分，无警告）全链路。

### 6. 2026-09-28 线上 Task 285 真实双表拓扑（大纲表+明细表）与同格多分值解耦：彻底达成 AUTO_APPROVED 覆写落库

针对部署到测试服务器后，重新解析触发 Task 285 依然停留在 91 分的排查闭环：
- **线上旧数据不被覆盖的根因**：`StructuredTableExtractionHandler.java:244` 具有防洗数守卫，只有当 `decision == AUTO_APPROVED` 时才会调用 `persistItems` 刷新 `score_item`；一旦为 `REVIEW_REQUIRED`，系统打印 `保留现有评分项不覆盖`，页面永远显示旧的 91 分。
- **阻断 1 (ITEM_WEIGHT_SOURCE_UNKNOWN)**：第 33 页大纲表一个单元格内同时写了商务 15、技术 45、报价 40，历史代码在 `OriginalScoreWeightEvidencePolicy.java:109` 限制了 `!claim.parent()`，导致 3 个父项全部在此单元格被判权重未知；修复：解除 `!claim.parent()` 限制，允许父项在同单元格多分值时精确匹配自身权重。
- **阻断 2 (MULTI_TABLE_DECLARED_TOTAL_UNKNOWN)**：线上大模型将第 33 页识别为表 1、第 34 页识别为表 2，两张表各自末尾都没有独立“合计100分”，历史分表策略简单按 `tablesById.size() > 1` 判定为多表，强行要求每张表都有独立声明总分；修复：在 `ScoreTableSplitPolicy` 与 `MultimodalExtractionReviewPolicy` 中重构多表识别逻辑，仅基于包含有效叶子项的表格数量判定多表，纯父项大纲表不触发独立总分门禁，使 13 个有效叶子项顺畅对齐文档级 100 分声明总分。
- **全链路端到端闭环验证**：引入 Task 285 线上真实 37 项 facts（双表拓扑），在 `Project1382FullPipelineIntegrationTest` 走通全链路测试，验证 `AUTO_APPROVED`、13 个候选落库、满分 100 分无 warning。

### 7. 2026-09-28 线上 Task 288 大模型 declaredTotal 留空与表外 0 分指引句误阻自动发布门禁根治

针对测试服务器 Task 288 触发 `REVIEW_REQUIRED` 导致保留旧 91 分数据的 5 项关键阻断根治：
- **阻断 1 (MODEL_TOTAL_MISSING)**：大模型按 prompt「无法确认就填空」将 `declaredTotal` 留空，发布门禁先前一票否决整单；修复：`MultimodalExtractionReviewPolicy.normalized` 优雅回退到原文证据证实的 100 分，`OriginalScoreEvidencePolicy.verify` 仅在原文也缺失总分时才拦截；
- **阻断 2~4 (0分指引句报权重来源不明)**：商务、技术、其他评分标准 3 句表外“见评审办法前附表”指引句被收为评分项且权重为 0，正文无 0 分依据报 `ORIGINAL_WEIGHT_SOURCE_UNVERIFIED`；修复：在 `MultimodalExtractionReviewPolicy.assignScope` 确立表外 0 分守卫（`tableId.isBlank() && weight <= 0` 统一划为 `OUT_OF_SCHEME`），并在 `OriginalScoreQualificationPolicy` 快速放行；
- **阻断 5 (报价公式说明报 SOURCE_LOCATOR_NOT_VERIFIED)**：报价方法公式段落因排版多行跨度产生行号差异，划入 `OUT_OF_SCHEME` 后快速放行，消除误拦截；
- **盲区修复 (转义空白字符兼容)**：在 `EvidenceText.normalize` 中将字面量 `\n`、`\r`、`\t` 兼容为空白符，杜绝跨页引文在长段落首页截断处比对失败；
- **全链路闭环验证**：在 `Project1382FullPipelineIntegrationTest` 中重放线上 Task 286 真实 37 项事实，判定 `AUTO_APPROVED`，过滤出 13 项有效叶子评分项，总分精确 100 分自动入库。

### 8. 2026-09-29 线上 Task 289 父项权重同行跨列分值读取碰撞与合同考核项范围误报彻底根治

针对部署后重新解析 Task 289 日志显示 `fallback=ITEM_WEIGHT_SOURCE_MISMATCH, decision=REVIEW_REQUIRED` 保留旧 91 分的根因与闭环：
- **阻断 1 (ITEM_WEIGHT_SOURCE_MISMATCH)**：在明细评分表 `p34-t0010` 第 2 行第 1 列为父项“商务评分标准”（15分在大纲表声明），第 2 列为子项“供应商业绩（7分）”。历史代码扫描整行提取 title 分值，误将子项的 7 分赋给父项导致 `15 != 7` 报错；同理技术评分标准被子项 10 分报错，报价评分标准被 `[40, 28]` 报 `ITEM_WEIGHT_SOURCE_UNKNOWN`；修复：在 `OriginalScoreWeightEvidencePolicy` 中增加列守卫（`cell.column() <= anchor.column()`），严禁读取右侧子项列分值；并在 `declaredDimensionSubtotal` 中剥离 `(?:评分)?标准$` 后缀，支持大纲正文/表格的分值构成声明比对；
- **阻断 2 (合同条款考核项报 SOURCE_LOCATOR_NOT_VERIFIED)**：大模型从合同章节提取的 17 项合同履约考核指标（`dimension="service"`，code 含 `_KH`/`_PJ`，分值 5/10/15 分），因历史代码将 `OUT_OF_SCHEME` 早期返回逻辑限制在 `weight <= 0`，导致带分值的考核项进入逐字引文校验被误杀；修复：在 `OriginalScoreQualificationPolicy` 中只要是 `OUT_OF_SCHEME`（不限权重）一律短路排除，并在引文比对前优先校验作用域；并在 `MultimodalExtractionReviewPolicy` 中准确将无表 service 维度考核项归入 `OUT_OF_SCHEME`；
- **全链路真实重现与验证**：导出线上 Task 289 全量 37 项事实（`task-289-live-facts.json`），在 `Project1382FullPipelineIntegrationTest.verifyTask289LiveOnlineFacts` 中 100% 真实重放，确证 `AUTO_APPROVED`，过滤出 13 项有效叶子项，满分 100 分自动入库。
