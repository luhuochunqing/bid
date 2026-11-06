# 数据模型

复用 warehouse.id / notification_delivery_task.id 为锁定协调点；notification和投递结果仍用既有实体存储，无新表、新列、迁移。锁在事务提交/回滚后释放。
