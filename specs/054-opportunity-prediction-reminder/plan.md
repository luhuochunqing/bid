# 商机预测事件失败重试 - 实现计划 (054)

## 目标

修复商机预测事件发布失败后仍被永久视为完成的问题，同时保持站内信与企微“每项目只提醒一次”。

## 设计

- `opportunity_prediction_reminder_log` 继续表示站内提醒已经创建，并以 `project_id` 唯一键负责通知去重。
- 新增可空的 `event_published_at`：非空表示 CRM 事件库已受理，空值表示后续扫描需要只重试事件。领取令牌及领取时间用于多 Pod 原子竞争和 10 分钟超时恢复。
- 新提醒在独立短事务中先插入唯一日志并 flush，再创建通知；并发实例会在通知前由 `project_id` 唯一键决出执行者。事务提交后在事务外调用 SDK，返回 `true` 后用另一短事务补写 `event_published_at`。
- 已有待重试日志绕过“已经提醒”分支，先条件更新领取令牌；领取成功者才重建 10 字段事件 payload 并调用 SDK，不再创建通知。失败释放令牌，进程中断遗留的领取 10 分钟后可恢复。
- 商机事件序列化固定输出 10 个 key，字符串、数值和日期无值时统一输出空字符串。
- 历史行迁移时 `event_published_at = notified_at`，避免上线后重放全部历史事件。已知失败项目由测试环境单独置空验收。

## 边界

- 纯核心边界：预测、提醒判定和字段解析仍由现有 `domain`/`OpportunityPredictionReminderSupport` 负责，本次不引入 I/O。
- 命令式外壳：`OpportunityPredictionReminderService` 负责无事务扫描与事务外 SDK 调用；`OpportunityPredictionReminderTransactionService` 负责本地短事务。
- 投递语义是至少一次；SDK 已受理而本地事务未提交的极小故障窗口可能重复推送，需要客户按事件业务键保证消费幂等。

## 验证

- 红灯：补“首次失败后再次扫描只重推事件”“通知校验失败不落日志”和“空值仍输出 10 字段”回归测试。
- 绿灯：定向 JUnit、Flyway 正反脚本覆盖、架构/行数门禁。
- 回归：`mvn test`、`npm run build`、`scripts/pre-push-gate.sh`。
