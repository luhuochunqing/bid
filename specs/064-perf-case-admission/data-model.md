# Phase 1 Data Model: 业绩评分项案例推荐精准准入

**Feature**: specs/064-perf-case-admission | **Date**: 2026-09-16

> 本文件描述领域对象与规则（纯核心），不是数据库 schema。本次**无数据库变更**。

## 1. 品类-金额门槛（CategoryThreshold）

| 字段 | 类型 | 说明 |
|---|---|---|
| category | String | 归一品类名：`办公用品` / `标准工业品` |
| amountWan | BigDecimal | 门槛金额（万元），由 元/千/万/亿 归一 |
| rawText | String | 产生该门槛的原文分句（用于理由展示/追溯） |

**来源**：`PerformanceThresholdExtractor.extract(requirementText)`

**规则**：
- 仅在“品类词与金额同分句”时配对；
- 同一品类多次出现时，全部保留（准入取“任一门槛达成”即通过）；
- 无法配对金额的品类词不产出门槛（仅作为品类命中）。

## 2. 业绩要求（PerformanceRequirementResult，扩展）

| 字段 | 类型 | 变更 |
|---|---|---|
| matchedRequirementText | String | 合并后的全部实质条款原文（原为第一条） |
| keywords | List\<String\> | 全部条款并集去重 |
| targetCategories | List\<String\> | 条款中出现的品类并集 |
| amountThresholds | List\<String\> | 原文金额串并集（保留兼容） |
| **thresholds** | List\<CategoryThreshold\> | **新增**：结构化品类-金额门槛 |

## 3. 候选业绩证据（PerformanceEvidence，策略内部中间结构）

| 字段 | 类型 | 说明 |
|---|---|---|
| hasPerformanceSignal | boolean | 命中业绩/供货/供应/发票/中标/年度/累计金额等证据词 |
| categories | Set\<String\> | 候选文本命中的品类（办公用品族 / 工业品族） |
| categoryAmounts | Map\<String, BigDecimal\> | 品类 → 该品类分句内金额证据最大值（万元） |

**来源**：`PerformanceCaseAdmissionPolicy.extractEvidence(candidateFullText)`

## 4. 准入判定（AdmissionVerdict）

| 字段 | 类型 | 说明 |
|---|---|---|
| admitted | boolean | 是否准入 |
| tier | int | 1=门槛达成；2=品类命中；3=业绩信号；0=拒绝 |
| reasons | List\<String\> | 判定依据（用于日志与后续理由展示） |

**分层规则**（编排层保留最优可用集合，见 research R3）：
- 证据门：命中业绩信号词或具备金额证据，方可进入分层；两者皆无（纯人员/技术文案）→ 拒绝；
- 硬拒绝：存在金额证据且 `max(categoryAmounts[c]) < threshold(c)`（明确低于门槛）；
- Tier-1：`品类命中 && 命中品类门槛达成`；
- Tier-2：`品类命中`（存在品类门槛但缺金额佐证）；
- Tier-3：仅命中业绩信号（无品类/金额要求时即该层）；
- 保留规则：存在 Tier1/Tier2 时保留全部 Tier1+Tier2；否则降级保留 Tier3；全无则返回空列表。

## 5. 案例类别（KnowledgeCase.scoringCategory）

- 存量标签：`技术/商务/实施服务/资质业绩/...`；
- 归类规则变更：`classifyText` 中「业绩」优先于「资质」→ 含“业绩”的混合文本进入 `PERFORMANCE`；
- 约束：不含“业绩”的纯资质文本（如「资质等级证书」）仍为 `QUALIFICATION`；人员类文本（PERSONNEL）优先级不变。

## 6. 状态流转 / 校验

- 无状态机变更；推荐链路仍为 `请求 → 权限校验 → 取候选 → [新增：准入过滤与分层] → 打分排序 → 分页响应`。
- 校验点：
  - 过滤后候选为空且无任何业绩信号 → 允许返回空列表（诚实降级）；
  - 资格项缺失/无业绩条款 → 准入不施加品类/金额约束（仅业绩信号层）；
  - 非业绩评分项 → 完全走既有链路（准入不介入）。
