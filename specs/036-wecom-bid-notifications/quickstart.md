# Quickstart: 投标关键节点企微通知

## 本地验证步骤

### 1. 编译与架构门禁

```bash
cd /Users/user/xiyu/worktrees/kimi/backend
mvn -Pjava-quality,java-quality-spotbugs,quality-strict checkstyle:check pmd:check spotbugs:check
mvn test -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest
```

### 2. 运行新增单元测试

```bash
cd /Users/user/xiyu/worktrees/kimi/backend
mvn test -Dtest='*PendingInitiationNotificationTest,*PendingClosureApplicationNotificationTest,*NotificationDedupTest'
```

### 3. 运行集成测试（可选）

```bash
mvn test -Dtest='*TenderEvaluationServiceTest,*ProjectStageServiceTest'
```

### 4. 手动验证

#### 待立项通知

1. 登录投标管理员账号；
2. 进入"已评估"标讯详情；
3. 点击"立即投标"；
4. 检查项目负责人是否收到企微消息，内容包含标讯名称与跳转链接。

#### 待结项申请通知

1. 登录投标负责人账号；
2. 进入处于"复盘阶段"的项目详情；
3. 执行"推进至项目结项阶段"（结项审核通过）；
4. 检查项目负责人是否收到企微消息，内容包含项目名称与跳转链接。

## 调试要点

- 通知是否创建：查询 `notification` 表，过滤 `type = 'PENDING_INITIATION'` 或 `'PENDING_CLOSURE_APPLICATION'`；
- 企微任务是否生成：查询 `notification_delivery_task` 表；
- 企微是否发送成功：查看 `OutboundLog` 与后端日志中 `WecomMessageCenterClient` 的响应；
- 去重是否生效：在 5 分钟内重复操作，应只有一条 `notification` 记录。
