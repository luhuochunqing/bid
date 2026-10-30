# Tasks: 案例沉淀并发与后台状态闭环

## 1. RED 证据

- [x] 运行并记录现有案例沉淀与临时文件测试基线。
- [x] 编写真实 MySQL 三线程同项目领取测试，确认旧实现无法满足。
- [x] 编写真实 HTTP/磁盘的不同 taskId 文件隔离测试。
- [x] 编写前端后台状态生命周期测试。

## 2. 持久化与调度

- [x] 新增 V1222/U1222 任务表迁移、实体和 Repository。
- [x] 实现 project 行锁领取、陈旧任务回收和最新状态查询。
- [x] 统一手动/自动触发入口，AFTER_COMMIT 后使用有界线程池调度。
- [x] 实现 PROCESSING、COMPLETED、FAILED 转移和最终写入 fencing。

## 3. 文件隔离

- [x] 将唯一 taskId 传入 `BidDocumentLoader`。
- [x] 确认 reserve/open/release 全部使用同一 taskId。

## 4. API 与前端

- [x] POST 返回 202 任务句柄；新增最新任务 GET。
- [x] 新增共享 composable，处理恢复、轮询、可见性和 timer 清理。
- [x] ResultConfirmStage 与 ClosureStage 改为展示后台真实状态。

## 5. 验证与清理

- [x] 运行定向后端/前端测试、迁移门禁、ArchitectureTest 和构建。
- [x] 执行 speckit-analyze、AI slop cleanup、xiyu code review。
- [x] 核对工作区 diff、HEAD 与测试证据；不推送、不建 PR、不部署。

## 6. 失败说明修复（2026-10-30）

- [x] 复现监听器任务原因丢失与页面提示消失。
- [x] 统一失败原因并给文件配额异常携带实际值/上限。
- [x] 两阶段页面持续展示原因，重试不残留旧原因。
- [x] 完成后端/MySQL/前端/构建及独立审查（100 项后端、44 项前端；工作区交付）。
