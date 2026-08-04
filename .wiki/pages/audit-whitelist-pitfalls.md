---
title: 审计白名单陷阱——@Auditable action 命名必须对齐 AuditActionPolicy
space: engineering
category: guide
tags: [审计, Auditable, AuditActionPolicy, 白名单, audit_logs]
sources:
  - backend/src/main/java/com/xiyu/bid/audit/core/AuditActionPolicy.java
  - backend/src/main/java/com/xiyu/bid/aspect/AuditableAspect.java
  - docs/lessons/lessons-learned.md
backlinks:
  - _index
  - lessons-learned
created: 2026-08-04
updated: 2026-08-04
health_checked: 2026-08-04
---
# 审计白名单陷阱

> `@Auditable` 注解加上 ≠ 审计生效。action 命名不命中 `AuditActionPolicy` 白名单即被静默丢弃，
> 该坑已复发两次（CO-324、CO-602 PR !2256），评审和自测都必须覆盖消费端。

## 机制

`AuditableAspect` 写日志前调用 `AuditActionPolicy.shouldRecord(action)`（AuditableAspect.java:75）：

1. 查询类前缀（READ/QUERY/VIEW/SEARCH/LIST/GET + `_`）→ 直接丢弃（设计如此，查询不落审计）
2. 其余 action 必须 equals / startsWith(KEY+"_") / endsWith("_"+KEY) 命中 KEY_ACTIONS
   （CREATE/UPDATE/DELETE/SUBMIT/APPROVE/DOWNLOAD 等约 30 个词）→ 否则静默丢弃

## 两次复发记录

| 时间 | action 命名 | 后果 | 修复 |
|---|---|---|---|
| CO-324 | `PROJECT_CLOSURE_APPROVED` 等过去分词/组合形式 | audit_log 不写入、项目动态只剩前端伪造基线 | KEY_ACTIONS 补 APPROVED/REJECTED/CREATED 等 + 匹配规则 |
| CO-602 PR !2256 | `PERFORMANCE_BUNDLE_EXPORT_TRIGGER/LIST/STATUS/DOWNLOAD` | 四个端点审计一条不落库 | PR !2258：KEY_ACTIONS 加 DOWNLOAD；注解改短动词 + entityType 惯例 |

## 规范

1. 加 `@Auditable` 前心算 `shouldRecord("你的action")`，或直接在 `AuditActionPolicyTest` 加断言验证
2. action 用短动词（CREATE/UPDATE/DELETE/READ/DOWNLOAD…）+ `entityType`，对齐全项目 219 处惯例
3. 审计类修复的验收标准是**真实触发后查 `audit_logs` 表**，不是"注解加上了"
4. 查询端点（list/get/status）用 `action="READ"` 作标记即可，按设计不会落库，不要为此造新动词
