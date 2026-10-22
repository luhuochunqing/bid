---
title: 结项保证金金额口径（立项优先，转服务费必须等值）
space: engineering
category: guide
tags: [结项, 保证金, 转服务费, need_deposit, CO-573]
created: 2026-10-22
updated: 2026-10-22
health_checked: 2026-10-22
sources:
  - backend/src/main/java/com/xiyu/bid/project/service/ProjectClosureDepositAssembler.java
  - backend/src/main/java/com/xiyu/bid/project/entity/ProjectDepositSnapshot.java
  - backend/src/test/java/com/xiyu/bid/project/service/ProjectClosureServiceTest.java
backlinks:
  - _index
---
# 结项保证金金额口径

> **一句话**：结项页的保证金金额，立项 `deposit_amount` 大于 0 时用立项金额，否则用未取消的投标保证金费用合计。转平台服务费必须等于该金额；部分退回加转服务费也必须等于该金额。

## 规则

1. 选择「转平台服务费」时，转服务费金额必须等于保证金金额。
2. 选择「部分退回，部分转平台服务费」时，退回金额 + 转服务费金额必须等于保证金金额。
3. 比较仍在 `ProjectClosureGatePolicy`。金额只在 `ProjectClosureDepositAssembler` 决定，预览和提交共用。
4. `need_deposit` 按忽略大小写的 `yes` 识别。库里存的是小写 `yes`，`"YES".equals` 对不上。

## 根因

CO-573 的等值比较本来就在闸门和结项页上。比较被跳过，是因为快照给出的基准不对：

- 还没有结项退回状态时，金额取未取消 `BID_BOND` 合计。
- 已经有退回状态后，金额只取立项 `deposit_amount`，不再看费用。
- 立项金额和费用合计不一致时，提交按费用合计放行，保存后再预览按立项金额判不平。
- 立项写了小写 `yes` 且没有费用时，`hasDeposit` 为 false，等值规则根本不跑。

## 不改写的历史数据

已审批通过的结项行不回写。典型是立项 120000、费用合计 184000、按费用合计做过部分退回的项目，仍保持原审批结果。审批通过路径不重新做金额等值检查。

## 验证

`ProjectClosureServiceTest` 覆盖：小写 `yes` 使用立项金额、部分退回之和必须等于立项金额、转服务费等于立项金额可以提交、没有立项金额时沿用费用合计。
