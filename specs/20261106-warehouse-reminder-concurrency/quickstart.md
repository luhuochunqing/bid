# 验证

在 backend 执行 `mvn -Dtest=WarehouseExpiryScanTaskTest,NotificationDeliveryJobServiceTest,ReminderConcurrencyIntegrationTest test`。集成测试默认独立 H2；使用 `-Dreminder.test.url=jdbc:mysql://localhost:13316/reminder_verify -Dreminder.test.username=root -Dreminder.test.password=reminder_test` 对专用 MySQL8 测试库重复验证。仅测试库允许 create-drop，禁止配置为业务库。
