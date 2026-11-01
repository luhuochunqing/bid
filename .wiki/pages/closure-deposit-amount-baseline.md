---
title: 结项保证金管理口径（金额等值校验与投标专员填写权限）
space: engineering
category: guide
tags: [结项, 保证金, 转服务费, need_deposit, bid-Team, CO-573]
created: 2026-10-22
updated: 2026-10-23
health_checked: 2026-11-01
sources:
  - backend/src/main/java/com/xiyu/bid/project/service/ProjectClosureDepositAssembler.java
  - backend/src/main/java/com/xiyu/bid/project/controller/ProjectClosureController.java
  - backend/src/main/java/com/xiyu/bid/project/entity/ProjectDepositSnapshot.java
  - backend/src/test/java/com/xiyu/bid/project/service/ProjectClosureServiceTest.java
  - src/views/Project/stages/ClosureStage.vue
backlinks:
  - _index
---
# 结项保证金管理口径

> **一句话**：结项页的保证金金额，立项 `deposit_amount` 大于 0 时用立项金额，否则用未取消的投标保证金费用合计。转平台服务费必须等于该金额；部分退回加转服务费也必须等于该金额。同时，结项保证金退回情况及项目总结由投标专员（`bid-Team`）与投标项目负责人（`bid-projectLeader`）填写并提交。

## 规则

1. 选择「转平台服务费」时，转服务费金额必须等于保证金金额。
2. 选择「部分退回，部分转平台服务费」时，退回金额 + 转服务费金额必须等于保证金金额。
3. 比较仍在 `ProjectClosureGatePolicy`。金额只在 `ProjectClosureDepositAssembler` 决定，预览和提交共用。
4. `need_deposit` 按忽略大小写的 `yes` 识别。库里存的是小写 `yes`，`"YES".equals` 对不上。
5. **结项填写与提交权限**：
   - 结项 4 字段（退回状态、退回日期、凭证文件、项目总结）由投标专员（`bid-Team`）及投标项目负责人（`bid-projectLeader`）负责在 `DRAFT` 或 `REJECTED` 状态下编辑并提交申请。
   - 投标管理员/组长仅负责在 `PENDING` 状态下审核，不得越权在表单中代填，4 字段呈现只读状态。
   - 后端 `POST /api/projects/{projectId}/closure` 接口 `@PreAuthorize` 必须对齐放行 `ADMIN`、`BID_PROJECTLEADER` 与 `BID_TEAM` 角色。

## 根因

CO-573 的等值比较本来就在闸门和结项页上。比较被跳过，是因为快照给出的基准不对：

- 还没有结项退回状态时，金额取未取消 `BID_BOND` 合计。
- 已经有退回状态后，金额只取立项 `deposit_amount`，不再看费用。
- 立项金额和费用合计不一致时，提交按费用合计放行，保存后再预览按立项金额判不平。
- 立项写了小写 `yes` 且没有费用时，`hasDeposit` 为 false，等值规则根本不跑。
- **角色错配导致无法填写与提交**：历史修复将编辑权收窄为 `bid-projectLeader`（销售）。但实际业务中多数项目的实际投标负责人系统角色为 `bid-Team`（投标专员），导致表单直接渲染为静态纯文本只读信息，且提交接口此前未放行 `BID_TEAM`。

## 不改写的历史数据

已审批通过的结项行不回写。典型是立项 120000、费用合计 184000、按费用合计做过部分退回的项目，仍保持原审批结果。审批通过路径不重新做金额等值检查。

## 验证

- `ProjectClosureServiceTest` 覆盖：小写 `yes` 使用立项金额、部分退回之和必须等于立项金额、转服务费等于立项金额可以提交、没有立项金额时沿用费用合计。
- `ClosureStage.spec.js` 覆盖：投标专员 `bid-Team` 在草稿状态下 `canEditDeposit=true`、`canSubmitClosure=true`；`ProjectStagePermissionTest` 覆盖后端 submit 接口放行 `BID_TEAM`。

