# Tasks: 仓库提醒并发修复
## US1 提醒生成
- [x] T001 [US1] 在 ReminderConcurrencyIntegrationTest.java 编写并运行真实数据库并发扫描 RED。
- [x] T002 [US1] 修改 WarehouseExpiryScanTask.java / WarehouseRepository.java，用独立事务和仓库行锁消除先查后写竞争，更新既有单测。
## US2 投递
- [x] T003 [US2] 在 ReminderConcurrencyIntegrationTest.java 编写并运行预读同一任务的 RED。
- [x] T004 [US2] 修改 NotificationDeliveryJobService.java / NotificationDeliveryTaskRepository.java，行锁内校验最新状态，更新既有单测。
## 完成门禁
- [x] T005 覆盖回滚、重试时间、terminal/missing任务、关闭仓库；MySQL真实并发验证。
- [x] T006 清理并执行受影响回归、架构、构建、独立只读审查；回填证据和 Wiki。

## 独立审查修复（第二轮）
- [x] T007 在 ReminderConcurrencyIntegrationTest.java 中真实事务代理失败 RED 证明 retry 写入被回滚。
- [x] T008 在 NotificationDeliveryJobService.java 暂停任务事务执行推送，外层继续持锁，恢复后保存失败决策。
