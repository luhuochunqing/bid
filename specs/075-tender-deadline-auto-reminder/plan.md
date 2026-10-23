# Implementation Plan: 标讯截止节点自动提醒

## 方案

`TenderReminderJob` 继续处理已有 `tender_reminder_settings`。新增 `TenderAutoReminderScanner`：按报名截止、开标时间查询未来 72 小时内、非终态标讯；若该类型没有设置行，则用 `NotificationRecipientResolver.getAdminUserIds()` 实时取接收人，并用 `TenderReminderPolicy.shouldSendReminder` 做窗口和 24 小时判断。

去重基准是该接收人最近一条 `SENT` 日志，不预写设置行。因此 `tender_reminder_logs.reminder_setting_id` 改为可空（V1221）。

## 拒绝的做法

- 只在生产库插入设置行：只能覆盖点名标讯，新标讯仍然没有提醒。
- 自动插入设置并固化接收人：角色成员变化后收不到，提醒设置页会出现用户没配过的行。
- 广播全体投标专员：与现有通知接收人集合不一致，噪音过大。

## 验证

`mvn test -Dtest=TenderAutoReminderScannerTest,TenderReminderJobTest`
