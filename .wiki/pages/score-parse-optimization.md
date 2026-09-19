---
title: 评分解析优化记录
space: engineering
category: lessons-learned
tags: []
created: 2026-09-05
updated: 2026-09-19
health_checked: 2026-09-19
sources:
  - docs/lessons/root-cause-analysis-score-parse-table-merge-cells.md
  - docs/lessons/lessons-learned.md
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreTableRowParser.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreTableBlockLocator.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/grid/CellAnchoredScoreExtractor.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/grid/ColumnTypeCrossVerifier.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/grid/HeuristicTableColumnMapper.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreMergeResultGuard.java
backlinks:
  - _index
---
# 评分解析优化记录

## 2026-08-28 评分表格准入与守卫回补根治修复

### 问题
PR !2443 在单测通过但在真实全量文档（如山焦物资 Project 1369）下失效，解析结果充斥《供应商须知前附表》的程序性非评分条款（采购预备会、开启程序、履约保证金、失信行为等），真正评分细则被挤出。

### 根因
1. **表格块定位泛滥**：`ScoreTableBlockLocator` 仅因前附表含有“分值构成”单行即把整张前附表所有程序行全部提取。
2. **纯分值列误判编号**：`ScoreTableRowParser.pureWeightIndex` 从右向左扫描未排除 `codeIndex`，导致条款编号（如 1.8 / 1.9 / 5.2 / 7.6）被误作为 weight 分值提取。
3. **守卫回补判定盲区**：`ScoreMergeResultGuard` 的漏项回补机制仅判定 `sourceText ⊇ detail`，因表格单行天然满足该条件，将 LLM 显式丢弃的前附表非评分干扰项全量强行回填（`restored=18`），造成下游权重超载并逆向淘汰了真实评分细则。

### 修复
1. `ScoreTableBlockLocator` 增加前附表表头特征拦截（含“编列内容/编列规定/条款内容”且无评分列名时整块跳过）。
2. `ScoreTableRowParser` 严格过滤非评分程序性条款（`NON_SCORE_PROCEDURE_PATTERN`）与总览说明行（`SUMMARY_ROW_PATTERN`），且 `pureWeightIndex` 严格排除 `codeIndex`。
3. `ScoreMergeResultGuard` 修正 `isHighConfidence` 判定，要求候选具备明确评分语义证据（`SCORE_EVIDENCE_PATTERN`），非评分程序条款一律不予回补。

### 效果
- 52 个相关核心单测全部通过。
- 彻底杜绝前附表程序性条款入池与死灰复燃。
- 评分细则（财务状况10分、运营能力20分、品牌货源20分等）完整保留。

## 2026-08-28 树形结构化建模与父大项/子细则解冲突升级

### 问题
在河南能源（Project 1362）等多层级表格与雅江集团（Project 1347）等多标包文档中，解析结果出现分值减半腰斩（如 10分变5分，2.5分变1分）或丢失细则。

### 根因
1. **父大项与子细项双重并存**：多层级表格中，“公司财务状况：10分”等一级大项被提取为候选；LLM 归并时保留了 1.1~1.4 等末级子项并丢弃了父大项，但后置守卫（`ScoreMergeResultGuard`）按一维列表误判为“漏项”，强行回补了 7 个父大项，导致总分翻倍至 187 分，触发下游强制等比缩放导致分值全部腰斩。
2. **多标包未隔离与程序性说明混杂**：多标包文档中，最低价法的说明文字误作为评分项被提取入池。

### 修复
1. **树形守卫不变式（`ScoreMergeResultGuard`）**：引入 `isParentOrSummaryCategory` 判定，当 accepted 清单中已存在子编号项（如 1.1）或同维度细则时，严禁将父大项/分类总览项作为漏项回补，杜绝父子并存与分值翻倍。
2. **右侧叶子细则 + 左侧路径继承（`ScoreTableRowParser`）**：表格解析全面落实右侧叶子明细与分值优先提取，左侧列自动作为 `dim` 分类路径继承并清洗分值后缀，纯大类总览行（如 `| 1 | 详细评审 | 财务状况：10分 |`）直接过滤跳过。
3. **全局归并 Prompt 加固（`ScoreParsePrompts`）**：增加多标包隔离规则、末级打分细则优先保留规则，以及一级分类总览项排除规则。

## 2026-08-31 Project 21 MRO工业品内嵌括号分值解析修复

### 问题
Project 21（MRO工业品综合采购）13 项评分细则总分 100 分，解析结果为 22 个候选、充斥招标代理服务费收费标准表中的计费清单行和合同考核行，实际评分项被覆盖/丢失。

### 根因

1. **`HeuristicTableColumnMapper` 全表文本过滤误伤**：`CONTRACT_ASSESSMENT_HEADER.matcher(allTableText)` 对整张表内容匹配，导致含"合同"字样的评分表（如 MRO 服务方案评分表）整张被过滤跳过。
2. **内嵌括号分值列未被识别**：表头形如 `供应商业绩（7分）` 的列类型无法被原始逻辑识别为 weight 列，导致分值全量丢失。
3. **招标代理服务费收费标准表未过滤**：表头含"计费基数/差额定率/服务费率"的计费清单表被误认为评分表，将数字作为评分行提取。
4. **`CellAnchoredScoreExtractor` 父大类切换时 `currentDim` 未重置**：跨大类时子维度残留，拼接出 `"供应商业绩/财务状况"` 等错误维度。
5. **`ColumnTypeCrossVerifier` 空列误通过**：权重列全为空时未拒绝，导致无效表格通过验证进入提取阶段。

### 修复

1. **`HeuristicTableColumnMapper.infer()`**：
   - 改为仅在表头行（第 0 行）匹配 `CONTRACT_ASSESSMENT_HEADER` 和 `NON_SCORE_CATALOG_PATTERN`，消除误杀。
   - 新增 `bracketCol` 扫描：自动识别形如 `维度（N分）` 的内嵌括号分值列，同时设为 `dimCol` + `weightCol`，并推断 `parentDimCol = bracketCol - 1`。
2. **`ScoreParseConstants.NON_SCORE_CATALOG_PATTERN`**：补充 `计费基数|差额定率|服务费率` 关键词，精准过滤招标代理服务费收费标准表。
3. **`CellAnchoredScoreExtractor`**：
   - `PRICE_SUBITEM_PATTERN` 改为 `[1-9]、` 前缀格式，正确解析 "1、上架折扣率（京东工业品）（28分）"。
   - 父大类（`parentDimCol`）切换时自动重置 `currentDim = ""`，避免跨大类维度残留。
   - `parseWeightFromDetail` 增加括号分值兜底提取（`BRACKET_WEIGHT_PATTERN`）。
4. **`ColumnTypeCrossVerifier`**：权重列须至少有 1 个有效数值或括号分值，否则拒绝整张表。

### 效果
- 13 项细则精准识别，总权重严格等于 100.0 分。
- 零商品目录行、零合同考核行污染。
- 新增金标测试 `testProject21ScoreParsing`，205 项单元测试全部通过。

### 设计原则
- **过滤条件的作用域要尽可能窄**：全表文本过滤比表头行过滤的误伤范围大数十倍，应优先匹配最小有效范围。
- **列类型探测要穷举非标格式**：括号分值（`维度（N分）`）是招标文件高频变体，不能依赖独立 weight 列存在性。


## 2026-08-31 PR !2481 审查修复（二轮）

### 审查发现与修正

1. **根因 1 描述勘误**：`HeuristicTableColumnMapper` 的合同考核表过滤在 main 上本就只匹配表头行（`grid.row(0)`），并非"全表文本过滤误伤"。PR !2481 实际变更 = 新增 `NON_SCORE_CATALOG_PATTERN` 计费表词 + `EXPLICIT_BID_SCORE_HEADER` 逃生词扩容（商务评分/技术评分/报价评分）+ `headerRows` 0/1 语义。
2. **夹具脱敏**：`project21-mro.md` 为真实采购文件，已全量脱敏（人名→联系人甲/乙/丙、电话→010-00000000、邮箱→example.com、银行账号/行号→0、楼宇地址→XX市XX区XX路XX号）。**纪律：真实标书入库前必须脱敏并 grep PII 模式清单；合成样本优先。**
3. **词表单一出处**：新增 10 考核词原在 `NON_SCORE_PROCEDURE_PATTERN` 与 `CONTRACT_ASSESSMENT_PATTERN` 双写，现提取 `ASSESSMENT_KEYWORDS` 共享子表组合，正则语义严格等价（复审逐词机械验证）。
4. **locator 逃生门**：`ScoreTableBlockLocator` / `MarkdownScoreSectionLocator` 的 `isProcedureNoticeTable` 原对考核词命中无条件跳过整表——表头含"考核指标/扣分项"的真实评分表会被整表丢弃。现补 `SCORE_HEADER_ESCAPE_PATTERN`（`评分因素|评审因素`）逃生门。**口径经复审实证收窄：`评分标准/分值/得分` 是考核表也有的泛词（"考核项目|考核内容|评分标准|分值"是履约考核表真实表头），放行会泄漏整张考核表。** 已知接受面：表头含考核词且无强评分列名的评分表仍会被跳过，由行级 NON_SCORE 词表兜底（`locate_assessmentKeywordWithStrongScoreHeader_kept` / `locate_assessmentHeaderWithGenericScoreWord_skipped` 双向锚定）。
5. **价格分加法门控**：`extractPriceSubCandidates` 原仅要求 `size>=2` 即拆分——"取最高/封顶"型会虚增总权重。现要求子项之和==父行分值（`extract_priceSubItems_splitsOnlyWhenSumMatchesParentWeight` / `takesHighestSemantics_keepsParentRow` 双向锚定）。已知局限：父分值由 detail 推导且恰等于最大子项时会误抑制拆分（javadoc 记录）。
6. **阈值恢复**：权重列密度地板恢复 0.4（0.35 为临时下调，括号分支计入有效数后金标实测仍全绿）；`extractBracketWeight` 恢复扫描列 0 但多列表（≥3 列）跳过——序号劫持防住、窄表（列 0 即维度名）不回归（`parse_narrowTwoColumnTable_bracketWeightInFirstColumn_kept` 锚定）。
7. **金标测试加固**：classpath 加载 + 夹具缺失硬失败（消除静默 return）；精确 `hasSize(13)`；`BRACKET_WEIGHT_PATTERN` 四处重复定义收敛至 `ScoreParseConstants` 单一出处。

## 评分表格过滤决策树（2026-08-31，PR !2481 二轮后固化）

> 词表内容以 `ScoreParseConstants` 为唯一出处，本图只画结构。改动任一 pattern / 逃生门前先对照此图枚举消费方。

```
markdown 输入
 │
 ├─ 表头行判定【ScoreTableBlockLocator / MarkdownScoreSectionLocator.isProcedureNoticeTable】
 │    ├─ CONTRACT_ASSESSMENT_PATTERN 命中？
 │    │    ├─ 是 → SCORE_HEADER_ESCAPE_PATTERN（强评分列名）命中？
 │    │    │      ├─ 命中 → 保留（真评分表混入考核词，行级兜底）
 │    │    │      └─ 未命中 → 跳过整表/整章
 │    │    └─ 否 → PROCEDURE_TABLE_MARKER 命中？
 │    │           └─ 是 → SCORE_COLUMN_MARKER（宽版，含"分值/得分"）未命中才跳过
 │    └─ 块内任一行含 SCORE_SEMANTIC → 产出 TableBlock
 │
 ├─ 表头判定【HeuristicTableColumnMapper.infer】
 │    ├─ CONTRACT_ASSESSMENT / NON_SCORE_CATALOG 命中表头
 │    │    且 EXPLICIT_BID_SCORE_HEADER 未命中 → 整表拒绝（全 -1 映射）
 │    ├─ 列映射：CODE/DIM/DETAIL/WEIGHT_HEADER 表头词
 │    │    └─ WEIGHT 未识别 → bracketCol 扫描（括号分值列劫持 weightCol/dimCol，
 │    │        count>0 取最左；单格劫持由 verifier 密度地板兜底）
 │    └─ ColumnTypeCrossVerifier：权重列全空 / 密度 <0.4 / 细则列均长 <4 → 拒绝
 │
 └─ 行级过滤【NON_SCORE_PROCEDURE_PATTERN：ScoreTableRowParser /
       CellAnchoredScoreExtractor / GhostCandidateFilter 三消费方】
        ├─ 词表命中 且 无"得X分" 且 无括号分值 → 丢弃
        └─ 词表命中 但 有"得X分"或括号分值 → 救回（行层救生通道）
```

**双层过滤 + 双重逃生门一览**

- 表头层逃生门：`EXPLICIT_BID_SCORE_HEADER`（mapper，宽词）/ `SCORE_HEADER_ESCAPE_PATTERN`（locator，强评分列名"评分因素|评审因素"）——两版语义不同不可互替。
- 行层救生通道：`hasExplicitGradeScore`（得X分）/ `hasBracketWeight`（括号分值），仅 `ScoreTableRowParser` 挂接；纯数字分值列 + 考核词的行是**已知误杀面**。
- 已知接受面：表头含考核词且无强评分列名的评分表会被整表跳过（依赖行级兜底）。
- 锚定测试：`ScoreTableBlockLocatorTest`（逃生门双向）/ `ScoreTableRowParserTest`（救生通道、优先级）/ `GoldenCasesReplayTest#case-bracket-vs-pure-conflict`（括号优先金标）/ `RealProjectScoreParsingTest#testProject21ScoreParsing`（真实文件金标）。

## 2026-09-01 评分项维度质量增强与首行末级标题提取 (score-parse-dim-quality)

### 问题
1. **维度过宽/缺乏具体细则标题**：部分招标文件表格仅提供父级大类（如"商务部分"）或单一大类，末级子项的具体标题内嵌在 `detail` 评分细则首行（如 `1. 人员配置：...`、`（1）企业资质：...`），直接使用父大类作为维度导致落库维度过宽。
2. **非评分程序/计算公式行泄漏**：在无分值且无有效单元格维度特征时，若仅依据首行提取出短标题可能会误放行非评分条款（如基准价计算方法四说明行）。

### 根因与设计
1. **纯核心末级标题提取器 (`ScoreItemTitleExtractor`)**：
   - 纯函数设计（`extract(String detail) -> Optional<String>`），无 Spring/JPA 依赖（FP-Java）。
   - 仅提取首行原文冒号前的短标题（`MAX_TITLE_LENGTH <= 40`），自动剔除各种前缀编号（`1.` / `(1)` / `（1）` / `第一条`），拒绝标点句子与空内容，100% 逐字保真无幻觉。
2. **网格抽取器维度解析 (`CellAnchoredScoreExtractor`)**：
   - 增加 `resolveParentByRow` 与 `countScoredRows`，按行推断父大类并统计父级对应计分行数。
   - 结合 `rawDimCell`、`detail` 提取标题与父类计分行数精准推断最终维度与 `dimSource`（`CELL` / `TITLE` / `PARENT_SINGLE` / `UNKNOWN`）。
   - 强化无分值行过滤：`cleanDimName(rawDimCell).isBlank() && currentParentDim.isBlank()` 时若无分值一律跳过，防止计算说明行泄漏。
3. **评分项落库维度解析 (`ScoreItemPersistenceService`)**：
   - 对单一大类候选（`isParentOnlyDim()`）在落库时 fallback 尝试从细则提取末级标题，使落库维度更精细具体。
   - 指纹计算与历史打分不受影响。

### 效果
- 全量 464 个评分解析单测全部通过。
- 真实项目金标测试（`RealProjectScoreParsingTest`）0 回退。

## 2026-09-07 Task1 评分项全量保全白名单、价格公式兜底与超载误杀切断

### 问题
项目 1359（中海油标讯，商务 Table2 + 技术 Table3 + 报价 Table4 独立分表结构）解析后：
- 商务表 13 项全部消失——被 `WeightOverloadTrimPolicy` 以"无编号弱项"理由清洗；
- 价格公式表（无显式分值，仅含评审公式）被判定为无效行丢弃，报价评分项完全缺失；
- Sidecar 调用缺少 `X-Schema-Version: 1.0` 请求头，协议版本升级时面临静默 Markdown 降级风险。

### 根因
1. **识别与归一严重耦合**：`WeightOverloadTrimPolicy` 试图用"删项"凑 100 分，导致格式无数字编号的商务表 13 项全部被误判为噪音删除——根本原因是将"全量识别（Task 1）"和"比重归一化（Task 2）"混在一个阶段完成。
2. **价格公式识别缺失**：提取器未识别"报价/价格/公式"特殊计算行，无分值时直接返回 `null` 被后续链路丢弃。
3. **维度推导未区分独立分表与混合单表**：对 1359 这类多表独立结构，提取器未自动为商务项注入 `商务` 大类、技术项注入 `技术` 大类，导致跨表维度层级缺失。

### 修复
1. **`SidecarTableExtractor`**：补齐 `.header("X-Schema-Version", "1.0")`，根治协议版本升级时的静默降级风险。
2. **`StructuredTableCandidateExtractor`**（严守 300 行硬上限）：
   - `isSummaryRow`：过滤 `合计/总计/汇总/总分/小计` 表尾汇总行；
   - `inferSectionDimension`：扫描表格前 15 行，精准推导独立分表大类（`商务/技术/报价`）；混合单表（同时含商务+技术）返回 `null` 不污染；
   - `isPriceFormula`：识别报价公式行，`weight` 兜底 `BigDecimal.ZERO`，完整公式文本保留为 `detail`；
   - `sectionDim` 注入：无 `parentDim` 时直接注入章节大类；已有大类关键词时不重复前缀。
3. **`WeightOverloadTrimPolicy`**：`isProtectedRealItem` 保护屏障——带 `parentDim` 或 `dimSource != UNKNOWN` 的结构化真实项免疫超载剔除。
4. **`ScoreCandidatePostProcessor`**：`filterInvalidWeights` 放行价格公式项（0 分兜底），避免被零分过滤丢弃。

### 决策：零分兜底边界
不能将所有 `weight == null` 统一兜底为 0，否则 1371 等混合单表的资格审查行（如"营业执照一致"）也会变成 0 分评分项（1371 会从 16 项膨胀至 44 项）。必须通过 `isPriceFormula` 精准识别，仅对符合价格/公式计算特征的行兜底。

### 效果
- `StructuredTableCandidateExtractorTest`：21 个测试全绿，含 1359 金标（39 项：商务 13 + 技术 25 + 报价 1）和 1371 回归（16 项 / 严格等于 100.0 分）；
- `WeightOverloadTrimPolicyTest`：10 个测试全绿（含结构化候选无编号不被剔除）；
- `ScoreCandidatePostProcessorTest`：7 个测试全绿（含价格公式项通过治理链）；
- `FPJavaArchitectureTest`：8 条架构规则全绿，零框架依赖。

### 关键 Pattern：解耦识别与归一
> 评分项全量识别（Task 1）必须零漏项；分值比重归一化（Task 2）必须解耦，禁止通过剔除真实业务项来凑分。超载剔除（Overload Trim）只能对"弱置信度噪音"动刀，结构化表格真实项一律豁免。

### 后续（2026-09-07）：miss 可观测性与词表收敛

- `ScoreTableSectionPolicy`：8 组关键词逐字收敛为单一真相源，行文本侧
  （Extractor）与候选字段侧（PostProcessor）共享，互指注释锁定，改词表必须双边同步测试。
- miss 上报：纯核心禁日志，`extractWithSectionReport` 只上报数据
  （`sectionDimMissTables`，口径 = 多表且推导为 null；单表 null 属混合单表设计不上报），
  `ScoreParseAppService` 对非空 miss 打 warn。
- 1371 反膨胀锚定：`noneMatch(weight == 0)`。fixture 中无"营业执照"行，字面断言恒真；
  零分项断言直接锁定"仅价格公式行可兜底"边界。

## 2026-09-07 评分项跨通道归并消重、报价细则去重与超载保护继承（项目 1359 深度优化）

### 问题
项目 1359（https://winbid-test.ehsy.com/project/1359）在 AI 评分标准解析中暴露 6 大缺陷：
1. “财务综合评价（25）”出现两次（条目重复）；
2. 序号 S5 “报价质量（15分）”细则内容整段重复两次；
3. 序号 S9 与 S23 重复（自有配送人员数量，2分），需删除 S9 保留精炼 S23；
4. 序号 S8 与 S22 重复（自有物流配送车辆数量，2分），需删除 S8 保留精炼 S22；
5. 缺失“品牌丰富度（4分）”；
6. 缺失“个性化需求响应度（3分）”。

### 根因与修复
1. **细则等价强制归并与权威维度优选**：
   - 表格提取的首列分类（`二、财务状况`）与 LLM 提取的要素名（`财务综合评价`）异名，旧消重依赖维度重叠，导致漏消重；`ScoreGlobalMergeOrchestrator` 又因维度不同误判为遗漏而强行回注。
   - 修复：`ScoreItemMergePolicy.findMatchingIndex` 对标点归一化后长度 >= 15 的相同细则强制归并；`resolveAuthoritativeDim` 淘汰罗马数字章节标题和冗长句子，优先采纳精炼评分因素；`ScoreGlobalMergeOrchestrator` 增加 `isCoveredByAccepted` 覆盖检查，杜绝向结果集中二次回注已存在的表格项。
2. **定性评分隔离与标点容错去重**：
   - “报价质量”因命中价格词元触发价格跨 chunk 拼接逻辑，两路细则仅差句号/小数点标点，旧行去重失效导致整段文字拼接两次。
   - 修复：`ScoreItemMergePolicy` 显式隔离定性评分项（含“质量/合理性/方案”）；拼接前与行去重时执行 `normalizePunctuation` 标点归一化。
3. **单元格内嵌分值解析与前缀剥离**：
   - 表格单元格原文字为“根据应答人自有物流配送车辆数量进行评分自有物流配送车辆数量（2分） 优秀得2分...”。
   - 修复：`StructuredTableCandidateExtractor` 支持 `BRACKET_NUM`（如 `财务综合评价（25）`），自动剥离“进行评分/进行评价”前缀长句，提取后置文本作为细则。
4. **属性无条件补齐与原文背书免疫**：
   - LLM 归并输出项 detail 与表格池项长度相等时，旧守卫未继承 `parentDim` 与 `CELL` 权威来源。
   - 修复：`ScoreMergeResultGuard.upgradeWithLongestPoolDetail` 对相同细则无条件继承 `parentDim`、`dimSource` 与 `code`；`WeightOverloadTrimPolicy` 增加 `sourceText.contains(detail)` 原文包含保护，防止真实项在超载清洗时被误杀。

### 后续（2026-09-07）：代码去重与预编译正则收敛 (ai-slop-cleaner)

- **常量集中化**：在 `ScoreParseConstants` 中统一定义 `CHINESE_SECTION_HEADING`（`^[一二三四五六七八九十]+、.*`）与 `BRACKET_CONTENT_PATTERN`，彻底替代多处在循环中调用的 `matches(...)` 和 `replaceAll(...)` 动态编译。
- **构造逻辑收敛**：`ScoreItemMergePolicy.enrich` 合并了原来在价格合并与常规合并中重复出现的 11 参数 `ScoreCandidate` 实例化代码，消除重复样板。
- **代码行预算管控**：`StructuredTableCandidateExtractor` (297)、`ScoreItemMergePolicy` (273)、`ScoreGlobalMergeOrchestrator` (277) 均严格严守 300 行硬上限。

## 2026-09-10 评分解析公式项去重、客观业绩精准匹配与证据展示治理（项目 1374）

### 问题
项目 1374（中核集团综合采购，总分 100 分）：
1. 最后一项多出 1 分的“评标基准价为”，其细则与上一项 30 分“价格部分”内容完全重复，使权重和达 101 分触发 `WEIGHT_SUM_MISMATCH`；
2. 客观项“央企案例”有案例却显示不满足（DANGER / 0 分）；
3. 详情弹窗中“表格单元格证据”千篇一律显示 `[自动通过] [LEGACY_FALLBACK] [置信度 100%]`。

### 根因与修复
1. **多重防御过滤价格公式与去重**：
   - 提取侧：`StructuredTableCandidateExtractor` 的 `NON_DIM_TEXT` 增加对“评标基准价/基准折扣率/分值计算”等公式短语的排除；
   - 归并侧：`ScoreGlobalMergeOrchestrator` 增加 `cleanForComparison` 剥离 Markdown 强调符号（`**`等）、标点与空格，并在价格大项与公式项增加跨通道覆盖判断，杜绝向结果集回注多余项。
2. **知识库客观业绩匹配全流程增强**：
   - `KnowledgeCategoryPolicy` 引入高优先级专有词（央企、中央企业、国企、MRO、工业品等），联合 `dim` 和 `detail` 提取业务关键词；增加 `extractSignedAfter` 提取签约起始日期（“2022年1月至今” -> 2022-01-01）；增加 `extractProjectRequiredCount` 推导所需份数；
   - `ProjectMatchService` 增加 `CustomerType` 枚举解析（央企 -> `CENTRAL_SOE`）与强过滤，并将文本关键词扩展至 `industry / contractName / groupCompany` 联合检索，命中业绩库中 20 条央企 MRO 业绩并评出满分（5.00分 / OK）。
3. **前端证据展示体验治理**：
   - `ScoreItemDetailModal.vue` 在无真实单元格证据且为旧流水线兜底时隐藏证据卡片；增加 `extractionMethodDisplay` 字典映射中文名称。

## 2026-09-11 评分标准 3 列表格内嵌分值提取与结构化权威通道防污染加固（项目 1381）

### 问题
张家口银行项目 1381（电商平台采购项目，总分 100 分）：
评分标准为 3 列（序号、评分因素、评分标准），分值内嵌在维度括号内（如 `投标报价（40分）`、`公司综合实力（6分）`，共 8 项合计 100 分）。AI 评分解析仅解析出 1 项（公司综合实力 6分）。

### 根因与修复
1. **表格通道原生权威标记对齐**：
   - `ScoreGlobalMergeOrchestrator` Phase 3a 结构化安全回注机制基于 `"STRUCTURED_TABLE"` 标记；
   - `CellAnchoredScoreExtractor` 抽取的候选将 `semanticPattern` 从 `"TABLE_GRID"` 统一升级为 `"STRUCTURED_TABLE"`，使其天然享受原生权威回注免检权；
   - `cleanDimName` 优化汉字断词换行清洗，平滑剔除多余空格。
2. **合并策略防污染加固**：
   - `ScoreItemMergePolicy` 在 `enrich` 时强化结构化通道与原文背书保护：后置为 `STRUCTURED_TABLE` 或有原文背书时，锁定结构化通道的 `detail`、`sourceText`、`code` 与权重；
   - 封堵 `isPriceMerge` 漏洞：严禁结构化表格与非结构化通道拼接，并强制要求双方均具有原文背书（`hasSourceBacking`），防止 LLM 概括/幻觉文本注入价格评分项。
3. **验证保障**：
   - 补充张家口银行真实 3 列表格单测，验证 8 项 100 分闭环、`STRUCTURED_TABLE` 标记及原文背书完整性；
   - 补充前置无背书 LLM 候选与后置表格候选合并测试；
   - 针对性测试与全量 `scoreparse` 模块 726 个单测全部 PASS。

## 2026-09-19 评分解析超时放大、未认证403与防抖韧性加固（项目 35）

### 问题
用户在项目 35 连续点击“重新解析”评分标准时出现页面卡顿并刷新触发 Nginx 499；此外在取消权限后因 Token 失效访问 API 时服务端返回 403 Forbidden（破坏 HTTP 语义），导致前端 Axios 拦截器未能触发无感刷新或跳回 `/login`，陷入死锁态。

### 根因与修复
1. **Sidecar 提取超时禁止重试**：
   - `MarkItDownSidecarExtractor` 对 REST 请求在 `@Retryable` 中显式指定 `noRetryFor = {ResourceAccessException.class}`。网络读取超时后立即终止重试，直通 `@Recover` 进入本地提取器降级兜底，消除 3 倍超时放大。
2. **Spring Security 401 状态码对齐**：
   - 在 `SecurityConfig` 显式注册 `authenticationEntryPoint`，未认证请求统一返回 HTTP 401 状态码与 JSON 错误响应体，保障与前端 `client.js` 拦截器协议对齐。
3. **前端防抖与并发控制**：
   - `ScoreParseDrawer.vue` 的重新解析按钮绑定 `:disabled="loading || scoringOverlayVisible"`，并在 Composable 中增加运行态前置守卫，彻底杜绝并发重试。

## 2026-09-19 扫描版 PDF 空正文被误报"请先上传招标文件"（错误语义误导）

### 问题
生产项目 37（榆林能源）评分解析持续 FAILED，`score_parse_task.error_message` 为"请先在立项阶段上传招标文件"。但联查 `project_documents`（id=149）→ `bid_file`（id=48）确认立项招标文件完好存在（COMPLETED、OBS object_key 齐全、67.7MB）。

### 根因
1. **文件其实取到了**：`InitiationTenderTextResolver` 正确打印 `name=榆林能源招标文件--副本.pdf`，OBS 下载/SIGN 均正常。
2. **文件是扫描件无文本层**：本地 `pdftotext` 仅提取 106 字节（几乎纯换行）；`pdfimages -list` 显示约 106 页每页 1 张扫描图，生产者 `Microsoft: Print To PDF`。Sidecar `/convert-stream` 返回 200 但正文为空。
3. **空正文分支静默落为 NO_TENDER_MESSAGE**：`resolveExecutionSource` 在 `text.isBlank()` 时不记录原因，最终统一报"请先上传"，把"已上传但无法提取"等价成"根本没上传"。

### 修复
- 新增 `BLANK_TEXT_MESSAGE = "招标文件未识别到可提取文本，可能为扫描版 PDF，需要 OCR 识别"`，空正文分支独立赋值。
- 失败原因优先级：`oversized > blankText > NO_TENDER`。
- 单测 `blankTextWithoutSnapshotReportsOcrMessageInsteadOfNoTender` 锁定。

### 教训
**降级失败原因不得复用"数据缺失"类文案**：能力因内容特性（而非缺失）失败时，必须区分"没给数据"与"给了但不可用"，否则误导用户反复做无效操作。判定依据：真实链路验证（如 `pdftotext`）+ 全链路日志，再定位被哪一层吞掉并改写语义。详见 `docs/lessons/lessons-learned.md` §384。

## 2026-09-19 评分解析项目 30 父项汇总过滤与续行维度丢失修复

### 问题
项目 30（中国电子 2026 年电商服务供应商入围招标项目，Table 2）AI 评分解析中存在两项关键缺陷：
1. **215 分 / 30 项虚高 (P0)**：父项汇总（商务 30 分、技术 45 分）与 28 项细则（100 分）重复累加，候选列表中包含父项汇总行，导致权重合计达 215.0 分，无法一键采纳（采纳要求 100 分）；
2. **续行维度显示为破折号 `—` (P1)**：合并单元格在后续行拆分展开为续行时，未继承 Origin 单元格的有效维度名称，在 UI 上呈现破折号 `—`。

### 根因与修复
1. **父项汇总行准入过滤（采纳策略与候选流）**：
   - 后端 `ScoreReviewAdoptionPolicy`：在 `adopt()` 评估前聚合所有候选事实的 `parentIds` 形成父项集合，在遍历候选时严格排除 `parentIds.contains(fact.id())`，彻底杜绝分类汇总行作为独立细则被采纳入库；
   - 前端 `useScoreCandidateAdoption.js`：定义计算属性 `parentIds = new Set(candidates.map(c => c.parentId).filter(Boolean))`，在 `actionableCandidates` 与 `candidateTotalWeight` 中过滤 `parentIds.has(c.id)`，使候选集与总分精准恢复为 28 项、合计 100.0 分；
   - 前端 `ScoreCandidateReviewCard.vue`：增加权重和不等于 100 时的告警条提醒（`candidateTotalWeight !== 100`），避免非标准分值盲目采纳。
2. **合并单元格续行维度继承**：
   - 后端 `DeterministicScoreFactExtractor`：提取 `resolveOriginFact` 单一辅助方法。当单元格为续行（`isContinuationRow`）且自身未提取到非空维度时，自动回填对应 `origin` 单元格提取的非空维度，消除 UI 上的破折号 `—`。

### 效果与验证
- 后端：`ScoreReviewAdoptionPolicyTest` 与 `DeterministicScoreFactExtractorTest` 全部通过；
- 前端：`useScoreCandidateAdoption.spec.js` 与 `ScoreCandidateReviewCard.spec.js` 全部通过；
- 项目 30 候选列表由 30 项 215 分收敛为 28 项 100.0 分，一键采纳顺畅执行，续行维度完整保留。

