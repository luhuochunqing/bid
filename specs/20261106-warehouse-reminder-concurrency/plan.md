# 仓库提醒并发修复 Implementation Plan
**Goal:** 两实例并发时同仓库只生成一次，同任务只发送一次。
**Architecture:** 复用现有行作为协调点，TransactionTemplate 显式独立 READ_COMMITTED 事务，JPA PESSIMISTIC_WRITE。在锁内重新加载当前对象并判断状态，生成/投递结果与状态同事务提交。无 schema 或外部 API 变更。
**Tech Stack:** Java 21 / Spring Boot 3.2 / JPA / MySQL 8 / JUnit 5。
## Technical Context
事务属于 imperative shell，WarehouseStatusCalculator 等纯核心保持不变。扫描事务仅覆盖一个仓库；投递事务仅覆盖一次外发及状态保存。候选列表不持锁。推送调用用 NOT_SUPPORTED 暂停任务事务，避免 REQUIRED 依赖抛错污染重试写入；外层事务连接及行锁仍保留。每个活跃 worker 推送期间可能需要第二条数据库连接。
## Constitution Check
真实数据库并发测试，外发边界隔离，不触达真实用户；不改 main 锚点；保留 FP-Java 分层与行预算。working_tree 授权优先，不自动提交/推送/部署；已有任务分支复用。复杂任务先完成 spec/plan/tasks。
## Project Structure / File Scope
- WarehouseExpiryScanTask / WarehouseRepository：仓库行锁与通知生成同事务。
- NotificationDeliveryJobService / NotificationDeliveryTaskRepository：任务行锁、最新状态、发送与状态保存同事务。
- 两份既有单测、NEW ReminderConcurrencyIntegrationTest / ReminderConcurrencyTestConfig：独立数据库事务并发与回滚。
- specs、本次 wiki、lessons：证据与使用限制。
## Alternatives
不采用 JVM synchronized：跨进程无效。不采用停止未知实例：只暂时掩盖。不增加新幂等表：本次可复用现有持久化行；减少迁移范围。
## Validation
先写重复扫描/重复任务的真实数据库测试，在旧代码获得 RED，再实现。后清理，重跑受影响单测、并发测试、架构测试、构建；三技能审查。外部发送已成功而提交前崩溃仍存在不确定窗口，不声称 exactly-once。
## Implementation Steps
- [ ] US1：测试两个扫描重叠创建；锁仓库并在锁内去重；验证失败回滚。
- [ ] US2：测试两个 worker 读取同一任务；锁任务并重新判断状态；验证未来重试/死信/任务缺失不发送。
- [ ] 验证独立事务真实提交；清理、回归、独立审查、Wiki 收口。
