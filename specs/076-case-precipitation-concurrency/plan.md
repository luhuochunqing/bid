# Implementation Plan: 案例沉淀并发与后台状态闭环

## 架构

```text
manual/auto trigger
       |
       v
claim(project row lock) --> existing active task --return same taskId-->
       |
       v
new PENDING task -> AFTER_COMMIT dispatcher -> scoreParseExecutor
       |                                      |
       | status API                           v
       +---------------------------- PENDING -> PROCESSING
                                              |
                                     unique taskId.part
                                              |
                              fenced transaction: cases + COMPLETED
```

## 决策

1. 使用独立任务表，避免把案例状态硬塞进评分解析有界上下文。
2. 使用 project 行锁实现数据库级串行领取；不依赖进程内锁或 HTTP 幂等缓存。
3. 复用现有事件监听器作为异步执行边界，并显式指定已有有界线程池；不额外增加一层只转发的执行器。
4. `taskId` 从领取开始贯穿下载、解析和释放，projectId 只表达业务归属。
5. 前端复用一个 composable 管理两个页面的查询、轮询和生命周期，避免重复状态机。

## API 契约

- `POST /api/cases/precipitate?projectId=` 返回 HTTP 202：`taskId,status,reused,message`。
- `GET /api/cases/precipitation-task?projectId=` 返回最新任务；无任务返回 204。
- 状态对象包含：`taskId,projectId,status,stage,caseCount,message,createdAt,updatedAt,completedAt`。

## 失败与恢复

- 调度拒绝：任务转 FAILED，用户消息为“系统繁忙，请稍后重试”。
- 文档或 AI 失败：记录内部日志，任务仅保存脱敏用户消息。
- 陈旧活动任务：领取时在锁内判定超时并转 FAILED，再创建新 taskId。
- 迟到旧任务：最终事务锁任务并检查状态；非活动任务不写案例。

## 宪章检查

- 真 MySQL/JPA 持久化，不使用 Map 假状态。
- 先 RED 后 GREEN；并发断言使用真实线程和数据库锁。
- application/infrastructure 分层，不让纯策略依赖 Spring。
- projectId API 继续调用 `ProjectAccessScopeService`。
- Flyway 提供 V/U 对称脚本，不修改历史迁移。

## 验证

- 后端：领取并发、文件隔离、陈旧恢复、fencing、控制器契约、现有案例沉淀回归。
- 前端：刷新恢复、轮询终态、可见性、卸载清理、两个页面复用。
- 治理：迁移校验、ArchitectureTest、定向 Maven/Vitest、构建与代码审查。
