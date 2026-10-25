---
title: 标讯转项目预填必填项补齐与已有字段防覆盖纪律（PR !2821）
space: engineering
category: analysis
tags: [bug, 根因分析, 立项预填, 标讯转项目, 数据覆盖防护]
created: 2026-10-10
updated: 2026-10-10
health_checked: 2026-10-25
sources:
  - backend/src/main/java/com/xiyu/bid/tender/service/EvaluationToInitiationMapper.java
  - backend/src/main/java/com/xiyu/bid/tender/service/InitiationPrefillService.java
  - src/views/Project/stages/useInitiationStageActions.js
  - docs/lessons/lessons-learned.md
backlinks:
  - _index
---
# 标讯转项目预填必填项补齐与已有字段防覆盖纪律

PR: https://gitee.com/allinai888/bid/pulls/2821

---

## 现场还原

标讯转项目后，用户提交立项时抛出校验异常：缺少开标时间 `bidOpenTime`。
排查发现：
1. CO-323 引入的后端预填 `EvaluationToInitiationMapper.applyTenderFields` 仅带入了 `ownerUnit` 和 `customerType`，漏掉了立项必填字段 `bidOpenTime`；
2. PR !2821 初版补齐了 `bidOpenTime`，但在将幂等分支收敛到 `applyTenderFields` 时，忽视了 `applyTenderFields` 缺少目标字段 null 检查的隐患，导致若立项实体已有用户手动修改过的业主单位或客户类型，幂等执行会无条件将其覆盖为标讯原始值，且在 JPA 受管事务下静默落库。

---

## 核心修复与防回归

1. **防覆盖契约严格落地**：
   `applyTenderFields` 针对 `ownerUnit`、`customerType`、`bidOpenTime` 统一执行 `e.get...() == null` 判定，严格实现"只补空值、绝不覆盖"。
2. **前后端双链路对齐**：
   前端 `useInitiationStageActions.js` 在 `load()` 时对缺少 `bidOpenTime` 的存量数据执行 `fillMissingRequiredFromTender` 兜底，并增加时间格式容错。
3. **测试覆盖**：
   后端 `EvaluationToInitiationMapperTest` 与 `InitiationPrefillServiceTest` 分别锁定"空值补齐"与"已有值保留"断言；前端 `useInitiationStageActions.spec.js` 覆盖兜底补充。
