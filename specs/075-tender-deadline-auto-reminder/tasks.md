# Tasks: 标讯截止节点自动提醒

- [x] 验收测试：无设置且在窗口内则发给角色接收人；窗口外、已有设置、24 小时内成功过则不发
- [x] `TenderAutoReminderScanner` 实现扫描、去重、只记录成功日志
- [x] `TenderReminderJob` 汇总自动扫描结果
- [x] V1221 允许 `reminder_setting_id` 为空，并提供回滚
- [x] `TenderReminderJobTest` 与扫描测试通过
