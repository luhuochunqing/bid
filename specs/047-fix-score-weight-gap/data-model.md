# Data Model: 根治评分项权重合计缺口

**Branch**: `agent/trae/047-fix-score-weight-gap` | **Date**: 2026-08-18
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## 概览

本 feature 仅**新增一个持久化字段**承载归一化"原始合计"信号，不新建实体表。核心运算发生在纯核心
`WeightNormalizationPolicy`（内存对象变换，无数据模型变更）。

## 实体 1：ScoreCandidate（评分项候选，已存在，仅语义不变）

| 字段 | 类型 | 说明 | 变更 |
|------|------|------|------|
| code | String | 评分项编号 | 不变 |
| dim | String | 评分项名称 | 不变 |
| detail | String | 详细要素 | 不变 |
| **weight** | BigDecimal | 权重绝对分值 | **归一化会改写其值**（补齐/收敛至会让 total=100） |
| scoreTypeGuess | String | 客观/主观初判 | 不变 |
| contextNote | String | 评分规则上下文 | 不变 |
| sourceText | String | 原文依据 | 不变 |
| location | String | 页码位置 | 不变 |
| semanticPattern | String | 语义命中模式 | 不变 |

**约束**：归一化只改写 `weight`，其余字段（code/dim/detail/sourceText/…）保持不变（spec Key Entities）。

**State Transition**：归一化不引入状态机。`ScoreCandidate` 为纯 core record，不可变；归一化输出
新 `ScoreCandidate`（复用同 index 的候选，仅 weight 替换）。

## 实体 2：ScoreParseTask（解析任务，已存在，新增 1 字段）

| 字段（新增） | 类型 | 可空 | 说明 |
|------|------|------|------|
| **originalTotalWeight** | BigDecimal | 是 | 归一化发生前的权重合计快照；未归一化时为 null |

映射列：`original_total_weight DECIMAL(10,2) DEFAULT NULL`。

**语义约定**（对接 FR-005）：
- 值为 `null` → 未归一化（原始合计即 100 或在容差内）
- 值为 `90` → 归一化曾把合计 90 补齐到 100，前端可呈现"原 90 已补齐"
- 值为 0 时 **不写入 null**（total=0 不归一，保持 null，weightWarning 兜底）

已有列参考（沿用先例）：`skipped_chunks INTEGER`（V1193）、`error_message TEXT` 均采用
`@Column(columnDefinition=…)` 显式声明，本新字段遵循同风格。

## 实体 3：ScoreParseItemsDTO.Summary（响应 DTO，新增 1 字段）

| 字段（新增） | 类型 | 可空 | 说明 |
|------|------|------|------|
| **originalTotalWeight** | BigDecimal | 是 | 归一化前合计信号，来自最新 PARSE task；null=未归一化 |

**交互关系**：Summary.originalTotalWeight ← ScoreParseTask.originalTotalWeight（同值透出）。
`weightWarning` 字段保留（归一化后落库合计=100，前端聚合值恒为 100 时 weightWarning 不再置真，语义一致）。

## 关系

- `ScoreParseTask` 1 → N `ScoreItem`（已存在，task 记录解析/评估任务，items 为落库评分项）。归一化只
  改 `ScoreItem.weight` 的写入值，实体关系不变。
- `ScoreParseItemsDTO.Summary` 是对 items 的聚合视图 + task 信号透出，无独立存储。

## 验证规则（源自 spec FR-001~FR-006）

1. 落库 items 权重合计 MUST 恒=100（不含 0 项失败终态）——SC-001
2. `total=0`（全部不可解析）MUST 不归一、不写 originalTotalWeight——FR-004
3. `total∈[99.5,100.5]` MUST 原样透传、originalTotalWeight=null——US1 场景 3
4. 归一化输出对同一输入 MUST 确定性可复现（最大余数法 + index 升序 tie-break）——US2/SC-003
5. 归一化仅改写 weight，其余候选字段不变——US2 相对比例保持
6. 空候选集不触发归一化，沿用 spec 046 ItemCountCheck "0 项失败" 终态——Edge Case

## 迁移脚本（Phase 2 实现时创建）

```sql
-- V1194__add_original_total_weight.sql (主)
--     ALTER TABLE score_parse_task ADD COLUMN original_total_weight DECIMAL(10,2) DEFAULT NULL;
-- 回滚脚本 (U-对应)
--     ALTER TABLE score_parse_task DROP COLUMN original_total_weight;
```
> 实际以 `scripts/new-migration.sh add_original_total_weight` 生成的迁移号与内容为准（本计划仅供类型/列名参考）。