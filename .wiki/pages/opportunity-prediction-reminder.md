---
title: 商机预测提醒
space: engineering
category: feature
tags: [商机预测, 提醒, 定时任务, 通知中心, 企微, CRM事件库, opportunity-prediction]
sources:
  - specs/054-opportunity-prediction-reminder/spec.md
  - specs/054-opportunity-prediction-reminder/data-model.md
  - docs/implementation-notes/CO-商机预测提醒.md
  - backend/src/main/java/com/xiyu/bid/workbench/service/OpportunityPredictionReminderService.java
backlinks:
  - _index
created: 2026-08-24
updated: 2026-08-24
health_checked: 2026-09-05
---
# 商机预测提醒

## 核心结论

- **提醒时机 = 预测招标时间**：`OpportunityPredictionPolicy.predict()` 返回的"预测招标时间"即"该去创建商机的时点"；
  提醒条件 `today >= predicted` 且从未提醒过（`OpportunityPredictionReminderPolicy`）。
  有服务周期截止时间时，到点即自然满足"央企提前 6 个月 / 其他提前 3 个月"。
- **每项目只提醒一次**：独立表 `opportunity_prediction_reminder_log`（`project_id` 唯一键），发送成功后落表，DB 层兜底防并发双发。
- **接收人**：项目经理（`Project.managerId`，启用过滤）+ 投标管理员（`/bidAdmin`）+ 投标组长（`bid-TeamLeader`），合并去重。
- **渠道**：站内信（`NotificationApplicationService.createNotification`，企微经 `NotificationCreatedEvent` 自动镜像，按工号推送无工号 SKIP）+ 西域事件库（`OpportunityPredictionEventPublisher` → `/eventbus/publishEvent`）。

## 关键决策（规范外）

1. **项目经理来源**：定时任务无登录态，`CrmAuthService` 禁止系统服务号换 CRM JWT，不能实时反查 CRM；
   取"自动分配"已落库的 `Project.managerId`（即 CRM 查得结果的本地落库形态）。
2. **文案兜底**：无服务周期截止日期时省略"服务周期将于…截止"句，只保留"预测下次招标时间为…，请到CRM创建商机。"
3. **事件发布隔离**：事件库失败只记日志，不阻断站内信/企微主链路。
4. **结果类型回退**：`ProjectResult.resultType` 优先，缺失回退 `Project.status.name()`。

## 通知链路复用

- 通知：`CreateNotificationRequest(OPPORTUNITY_PREDICTION, "Project", projectId, title, body, payload, recipients)`；
  payload 含 `targetUrl=/bidding/customer-opportunities` 深链（前端 `resolveNotificationRoute` 已支持）。
- 事件：`TenderEventCode.OPPORTUNITY_PREDICTION("OpportunityPrediction", "bid", "OPPORTUNITY", "projectId")`；
  开关 `xiyu.integrations.tender-event.sdk.enabled`（默认 false，需配置 true）。

## 数据模型

`opportunity_prediction_reminder_log`：id / project_id(UNIQUE) / predicted_bid_date / purchaser_name / notified_at / created_at。

## 相关

- 预测规则：`OpportunityPredictionPolicy`（四条规则 + 6 个月展示窗口 + 90 天红点）
- 既有参考：`WarehouseExpiryScanTask`（每日 09:00 调度模板）、`QualificationExpiryNotificationService`（编排模板）
