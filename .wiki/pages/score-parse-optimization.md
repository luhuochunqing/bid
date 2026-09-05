---
title: 评分解析优化记录
space: engineering
category: lessons-learned
tags: []
created: 2026-09-05
updated: 2026-09-01
health_checked: 2026-09-05
sources:
  - docs/lessons/root-cause-analysis-score-parse-table-merge-cells.md
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
