# Feature Specification: 案例沉淀并发与后台状态闭环

## 背景

同一项目可被页面重复点击和结果登记同时触发。现实现用 `case-{projectId}` 作为临时文件标识，多个异步执行会读写并释放同一个 `.part` 文件，造成间歇性文档解析失败；POST 返回后页面又立即结束“沉淀中”状态，用户无法判断后台真实结果。

## 用户故事与验收

### P0：同一项目只有一个进行中任务

- 三个线程同时触发同一项目时，均获得同一 `taskId`。
- 数据库只存在一条 PENDING/PROCESSING 任务，且后台只调度一次。
- 重复触发响应明确标记 `reused=true` 和“正在沉淀”。
- COMPLETED/FAILED 终态后允许创建新任务。

### P0：每次执行的文件资源彼此隔离

- `BidDocumentLoader` 接收调用方生成的唯一 `taskId`，不再从 projectId 派生固定值。
- 两个不同任务下载同一来源时获得不同 `.part` 路径和相同内容摘要。
- 释放其中一个任务不得删除或截断另一个任务的文件。

### P1：页面展示后台真实状态

- POST 仅代表已受理；按钮和文案继续展示 PENDING/PROCESSING。
- 页面挂载或刷新后按 projectId 查询最新任务并恢复状态。
- 前端轮询终态；页面隐藏时暂停，恢复可见时立即刷新，卸载时清理定时器。
- COMPLETED 且 `caseCount=0` 仍是成功终态；FAILED 展示可行动但不泄露内部异常的消息。

## 功能要求

- 任务状态持久化到 MySQL，状态为 PENDING、PROCESSING、COMPLETED、FAILED。
- 领取任务必须在同一短事务内锁定 project 行、检查活动任务并创建任务；耗时 AI 和文档处理在事务外执行。
- 所有手动与自动触发入口统一经过 projectId 幂等门禁。
- 异步执行使用现有 `scoreParseExecutor`，不得创建无限制线程。
- 最终写入案例前必须锁定并确认当前 taskId 仍为活动任务，避免陈旧执行覆盖新任务。
- PENDING/PROCESSING 超过超时阈值后可被标记 FAILED 并允许重试。
- 状态查询与触发均执行项目访问范围校验。

## 数据模型

新增 append-only `case_precipitation_task`：`task_id`、`project_id`、`status`、`stage`、`trigger_user_id`、`case_count`、`user_error_message`、`started_at`、`heartbeat_at`、`completed_at`、`created_at`、`updated_at`。活动任务互斥由 project 行锁事务保证，保留历史终态用于审计。

## 非目标

不引入 Redis 锁、MQ、SSE 或 WebSocket；不复用 `score_parse_task`；不修改 AI 提示词、案例字段或评分解析流程。

## 2026-10-30 用户确认的失败说明修复

- AC-F1：结果登记后仍立即自动触发；无评分项 FAILED，用户明确获知先完成评分项解析再生成。
- AC-F2：文件配额不变；拒绝时说明实际大小及配置上限，不建议无效重试。
- AC-F3：后续失败任务和通知使用同一用户可读原因，原始异常留日志。
- AC-F4：结果登记和结项页面在失败后及刷新后持续展示原因；新任务受理后不残留旧原因。
- AC-F5：区分下载、转换、AI 服务等原因；不泄露原始上游响应、下载签名或存储路径；未知错误明确说明待排查。
