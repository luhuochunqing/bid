# Task List: 资源管理 — 保证金管理与招标平台账号管理

**Feature**: 020-resource-management | **Branch**: `agent/qoder/resource-management` | **Date**: 2026-06-09

---

## Phase 1: 数据库设计与迁移

- [ ] **Task 1.1**: 编写 Flyway V 脚本创建 `project_deposit` 表
  - 字段：id, project_id, deposit_amount, deposit_status, deposit_time, refund_amount, service_fee_amount, refund_status, expected_return_date, overdue_days, created_at, updated_at
  - 外键：project_id → project(id)
  - 索引：project_id, deposit_status, refund_status, expected_return_date

- [ ] **Task 1.2**: 编写 Flyway V 脚本创建 `bid_platform` 表
  - 字段：id, platform_name, platform_url, login_account, password_encrypted, binding_contact, has_ca, created_at, updated_at, deleted
  - 索引：platform_name, deleted

- [ ] **Task 1.3**: 编写 Flyway V 脚本创建 `platform_ca` 表
  - 字段：id, ca_no, ca_type, platform_id, seal_info, password_encrypted, valid_until, keeper_id, keeper_name, status, created_at, updated_at, deleted
  - 外键：platform_id → bid_platform(id)
  - 索引：ca_no, platform_id, keeper_id, valid_until, status

- [ ] **Task 1.4**: 编写 Flyway V 脚本创建 `ca_borrow_record` 表
  - 字段：id, borrow_type, ca_id, platform_id, borrower_id, borrower_name, approver_id, approver_name, borrow_reason, borrow_time, expected_return_date, actual_return_date, status, created_at, updated_at
  - 外键：ca_id → platform_ca(id), platform_id → bid_platform(id)
  - 索引：borrower_id, approver_id, status, expected_return_date

- [ ] **Task 1.5**: 编写 Flyway V 脚本创建 `platform_operation_log` 表
  - 字段：id, entity_type, entity_id, operation_type, operator_id, operator_name, old_value, new_value, created_at
  - 索引：entity_type, entity_id, operator_id, created_at

- [ ] **Task 1.6**: 编写所有表的 U 回滚脚本（含 IF EXISTS 安全检查）

- [ ] **Task 1.7**: 本地执行 Flyway 迁移验证

---

## Phase 2: 后端基础层（Entity + Repository）

- [ ] **Task 2.1**: 实现 `ProjectDeposit` JPA Entity + `DepositStatus`/`RefundStatus` 枚举

- [ ] **Task 2.2**: 实现 `BidPlatform` JPA Entity

- [ ] **Task 2.3**: 实现 `PlatformCA` JPA Entity + `CaStatus` 枚举

- [ ] **Task 2.4**: 实现 `CaBorrowRecord` JPA Entity + `BorrowStatus` 枚举

- [ ] **Task 2.5**: 实现 `PlatformOperationLog` JPA Entity

- [ ] **Task 2.6**: 实现 5 个 Repository 接口（Spring Data JPA）

- [ ] **Task 2.7**: 编写 Entity + Repository 基础测试

---

## Phase 3: 后端纯核心（Domain Policy）

- [ ] **Task 3.1**: 实现 `DepositStatisticsPolicy` — 统计卡片计算纯核心
  - 输入：保证金列表
  - 输出：支付总额、未退回总额、未退回笔数、超期未退回金额、超期未退回笔数

- [ ] **Task 3.2**: 实现 `CaBorrowPolicy` — 领用状态机纯核心
  - 状态流转：PENDING → APPROVED → BORROWED → RETURNED / REJECTED
  - 校验：同一CA同一时间只能被一人借出

- [ ] **Task 3.3**: 实现 `ReminderPolicy` — 提醒策略纯核心
  - CA有效期提醒：30天、15天、7天、已过期
  - 借用归还提醒：30天、15天、7天、已超期

- [ ] **Task 3.4**: 实现 `PlatformValidator` — 平台账号/CA 校验纯核心
  - 必填项校验、格式校验、唯一性校验

- [ ] **Task 3.5**: 编写纯核心单元测试（覆盖率 ≥ 80%）

---

## Phase 4: 后端应用层（Service）

### 保证金管理

- [ ] **Task 4.1**: 实现 `DepositQueryService` — 列表查询 + 筛选 + 分页

- [ ] **Task 4.2**: 实现 `DepositStatisticsService` — 统计卡片数据计算

- [ ] **Task 4.3**: 实现 `DepositExportService` — Excel 导出

- [ ] **Task 4.4**: 实现项目立项/结项事件监听，同步保证金数据

### 招标平台账号管理

- [ ] **Task 4.5**: 实现 `PlatformQueryService` / `PlatformCommandService`

- [ ] **Task 4.6**: 实现 `CAQueryService` / `CACommandService`

- [ ] **Task 4.7**: 实现密码加密/解密服务（复用系统工具）

### 领用审批

- [ ] **Task 4.8**: 实现 `BorrowQueryService` / `BorrowCommandService`
  - 发起申请、审批通过、审批驳回、登记归还

### 操作日志

- [ ] **Task 4.9**: 实现 `PlatformLogService` — 记录操作日志

### 定时提醒

- [ ] **Task 4.10**: 实现 `ReminderTaskService` — 每日扫描 + 发送提醒

- [ ] **Task 4.11**: 编写 Service 层集成测试

---

## Phase 5: 后端接口层（Controller）

- [ ] **Task 5.1**: 实现 `DepositController` + DTO/VO

- [ ] **Task 5.2**: 实现 `PlatformController` + DTO/VO

- [ ] **Task 5.3**: 实现 `CAController` + DTO/VO

- [ ] **Task 5.4**: 实现 `BorrowController` + DTO/VO

- [ ] **Task 5.5**: 实现 `PlatformLogController` + DTO/VO

- [ ] **Task 5.6**: 配置权限注解（@PreAuthorize）

- [ ] **Task 5.7**: 编写 Controller 层测试

---

## Phase 6: 前端页面开发

### 保证金管理

- [ ] **Task 6.1**: 创建 `DepositList.vue` 页面 + 路由配置

- [ ] **Task 6.2**: 实现 `DepositSearch.vue` 搜索区组件

- [ ] **Task 6.3**: 实现 `DepositTable.vue` 表格组件

- [ ] **Task 6.4**: 实现 `DepositCards.vue` 统计卡片组件

- [ ] **Task 6.5**: 实现导出功能

- [ ] **Task 6.6**: 注册左侧导航栏「资源管理」→「费用管理」→「保证金退还跟踪」

### 招标平台账号管理

- [ ] **Task 6.7**: 创建 `PlatformList.vue` + `PlatformForm.vue`

- [ ] **Task 6.8**: 实现密码脱敏/显示切换组件 `PasswordField.vue`

- [ ] **Task 6.9**: 创建 `CAList.vue` + `CAForm.vue`

- [ ] **Task 6.10**: 注册左侧导航栏「资源管理」→「招标平台账号管理」

### 领用审批

- [ ] **Task 6.11**: 创建 `BorrowList.vue` 领用记录列表

- [ ] **Task 6.12**: 创建 `BorrowApply.vue` 发起领用

- [ ] **Task 6.13**: 创建 `BorrowApprove.vue` 审批/归还

### 操作日志

- [ ] **Task 6.14**: 创建 `OperationLog.vue` 操作日志页面

- [ ] **Task 6.15**: 注册左侧导航栏「资源管理」→「操作日志」

---

## Phase 7: 集成测试与 E2E

- [ ] **Task 7.1**: 前后端联调 — 保证金台账完整流程

- [ ] **Task 7.2**: 前后端联调 — 平台账号/CA 增删改查

- [ ] **Task 7.3**: 前后端联调 — 领用审批全流程

- [ ] **Task 7.4**: Playwright E2E 测试 — 保证金台账

- [ ] **Task 7.5**: Playwright E2E 测试 — 平台账号管理

- [ ] **Task 7.6**: ArchUnit 门禁验证

- [ ] **Task 7.7**: 项目权限门禁验证（ProjectAccessGuardCoverageTest）

---

## Phase 8: 代码审查与提交

- [ ] **Task 8.1**: 自测通过（`mvn test` + `npm run build`）

- [ ] **Task 8.2**: 本地预提交门禁（`bash scripts/ci-pre-pr.sh`）

- [ ] **Task 8.3**: 创建 PR（`scripts/pr-create.sh`）

- [ ] **Task 8.4**: 合并至 main

---

## Task Summary

| Phase | Tasks | Est. Effort |
|-------|-------|-------------|
| Phase 1 | 7 | 4h |
| Phase 2 | 7 | 6h |
| Phase 3 | 5 | 6h |
| Phase 4 | 11 | 12h |
| Phase 5 | 7 | 8h |
| Phase 6 | 15 | 16h |
| Phase 7 | 7 | 10h |
| Phase 8 | 4 | 4h |
| **Total** | **63** | **~66h** |
