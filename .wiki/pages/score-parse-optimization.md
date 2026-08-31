---
title: 评分解析优化记录
space: engineering
category: lessons-learned
sources:
  - docs/lessons/root-cause-analysis-score-parse-table-merge-cells.md
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreTableRowParser.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreTableBlockLocator.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreMergeResultGuard.java
updated: 2026-08-31
health_checked: 2026-08-31
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


