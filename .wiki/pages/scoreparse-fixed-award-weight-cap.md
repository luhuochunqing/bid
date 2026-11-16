---
title: 规则单次固定得分未识别为上限导致 OMS/WMS 计分翻转
space: engineering
category: lessons
tags: [scoreparse, scoring, weight, effective-weight, match-ratio, oms, wms]
sources:
  - backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreMaximumPolicy.java
  - backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreItemAssessor.java
  - backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreItemAssessorTest.java
backlinks:
  - _index
created: 2026-11-16
updated: 2026-11-16
health_checked: 2026-11-16
---

# 规则单次固定得分未识别为上限导致 OMS/WMS 计分翻转

## 1. 现象

- 测试环境 project 1364 items 497/498：规则原文「2.具备OMS订单管理系统的，得3分，否则不得分；」「3.具备WMS仓储管理系统的，得3分，否则不得分；」，表格权重均为 6。
- 任务 377：OMS matchRatio=100 → 6 分 OK；WMS matchRatio=50 → 3 分 PENDING。
- 任务 378 重跑：两者结果互换。同一规则随模型比例波动在 6 分/3 分之间翻转。

## 2. 根因

- `ScoreMaximumPolicy.overall(text)` 只识别「最高/最多/满分 X 分」声明；「得3分」这种单次固定得分未被视为本项上限。
- `ScoreItemAssessor.effectiveWeight(item)` 因此直接取表格权重 6，`computeStage2Score(6, ratio)` 在 100 时给 6、50 时给 3；`ScoreStatusPolicy` 以 6 为满分判定，3 分只能 PENDING。

## 3. 修复

- 新增 `ScoreMaximumPolicy.fixedAward(text)`，仅当同时满足以下条件时返回该固定分值：
  1. 无「最高/最多/满分」声明；
  2. 全文仅一个分値提及（`X分`）；
  3. 该分値由「得X分」引出；
  4. 文本不含「每」（排除逐项累计/按量规则）。
- `effectiveWeight`：`overall()` 为 null 时回退 `fixedAward()`，再与表格权重取小。OMS/WMS 的 effectiveWeight 由 6 稳定为 3。
- 多编号子项（如「1）…得3分；2）…得3分」）因存在多个分値提及，不触发封顶，仍按表格权重计分。
- 主观项不走该路径，行为不变。

## 4. 回归与边界

- 单次「得3分」+权重 6：ratio NULL/100 → 3 分 OK；ratio 50 → 2 分 PENDING。
- 「得3分，每增加1个加1分，满分5分」→ 仍按 5 分封顶。
- 多编号子项各有得分 → 保持表格权重 6。
- 无固定得分声明 → 行为不变。

## 5. 验证证据

- `ScoreItemAssessorTest`：35 运行 0 失败；实现前先确认 RED（目标场景 3 个参数失败）。
- scoreparse 全模块 2108 运行 11 失败；临时回退本次补丁后基线复跑同为 11 失败、同批类，确认既有失败与本次改动无关。
- 仅提 PR，未合入、未部署、未重算线上任务。
