# Implementation Plan: 资源管理 — 保证金管理与招标平台账号管理

**Branch**: `agent/qoder/resource-management` | **Date**: 2026-06-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/020-resource-management/spec.md`

---

## Summary

建设「资源管理」模块下的两大子功能：

1. **保证金管理**：统一台账展示所有项目保证金的缴纳、退还、超期情况，支持多维度筛选、搜索、导出和统计卡片展示。
2. **招标平台账号管理**：平台台账集中维护、CA统一管理、领用审批流、到期提醒、审计日志。

**技术路线**：
- 后端：Spring Boot + JPA + Flyway 迁移，FP-Java Profile（纯核心 + Application Service 编排）
- 前端：Vue 3 + Element Plus + Pinia，页面包含保证金台账、平台账号管理、CA管理、领用审批、操作日志
- 数据同步：保证金数据从项目模块监听事件同步，退回/转服务费从结项环节同步

---

## Technical Context

- **Language/Version**: Java 21 + Vue 3 + TypeScript
- **Primary Dependencies**: Spring Boot 3.3 + JPA (Hibernate 6) + MySQL 8.0 + Flyway + JUnit 5 + Mockito + AssertJ
- **Storage**: MySQL 8.0（新增 `project_deposit`、`bid_platform`、`platform_ca`、`ca_borrow_record`、`platform_operation_log` 表）
- **Testing**: JUnit 5 + Mockito + Spring Boot Test；前端 Playwright E2E
- **Target Platform**: Linux server（后端 Spring Boot 单体）
- **Project Type**: Web service（前后端分离，FP-Java Profile）
- **Performance Goals**: 列表查询 < 500ms，统计查询 < 200ms
- **Constraints**:
  - **FP-Java Profile 遵守**：业务规则放入可单测的纯核心（domain/policy），Controller/Application Service 只做取数、事务、保存、消息
  - **DB 迁移**：所有 schema 变更必须附带 Flyway 正向 + 回滚脚本
  - **事务边界**：领用审批、归还登记等操作需保证原子性
  - **架构门禁**：`FPJavaArchitectureTest`、`MaintainabilityArchitectureTest`、`ProjectAccessGuardCoverageTest` 必须绿
  - **Mock 政策**：严禁新增 mock，全部走真实 API
- **Scale/Scope**:
  - 后端：新增 5 个 Entity + Repository + Service + Controller，修改项目模块事件发布
  - 前端：新增 4 个页面 + 路由 + API 接口
  - 估算代码量：后端 ~1500 行，前端 ~2000 行

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 门禁项 | 状态 | 说明 |
|---|---|---|
| FP-Java Profile 遵守 | ✅ Pass | 保证金计算规则、CA状态流转、审批状态机放入纯核心；Application Service 只做编排 |
| 纯核心不变 | ✅ Pass | 纯核心不依赖 Spring、JPA、HttpServletRequest 等框架类 |
| Application Service 只做编排 | ✅ Pass | Service 层负责取数、事务、调用纯核心、保存结果 |
| 业务失败用 Result/Optional 返回 | ✅ Pass | 校验失败返回 ValidationResult，不抛异常做业务分支 |
| 不可变值对象优先 | ✅ Pass | DTO、VO、命令对象用 record 或 final 不可变对象 |
| 业务方法必须返回值 | ✅ Pass | 纯核心方法返回 Result/Optional/实体，不用 void 隐藏状态变化 |
| Split-First Rule | ✅ Pass | 先拆 DepositPolicy、CaBorrowPolicy、PlatformValidator 等纯核心，再实现 |
| 单文件行数 | ⚠️ Watch | 预计 Service/Controller 可能接近 200 行，需提前拆分 |
| Flyway 迁移规范 | ✅ Must Pass | 所有表变更必须附带 V/U 脚本 |
| 回滚脚本 | ✅ Must Pass | 每个 V 脚本对应 U 脚本，含 IF EXISTS 安全检查 |
| 架构门禁（ArchUnit） | ✅ Must Pass | 新增代码必须通过 ArchUnit 门禁 |
| Mock 政策 | ✅ Pass | 全部走真实 API，不新增 mock-adapters、src/mock |

**Constitution Check 结论**：全部通过，单文件行数需持续关注。

---

## Project Structure

### 后端新增模块

```
backend/src/main/java/com/xiyu/bid/resource/
├── deposit/
│   ├── domain/
│   │   ├── ProjectDeposit.java              # JPA Entity
│   │   ├── DepositStatus.java               # 枚举：缴纳状态
│   │   ├── RefundStatus.java                # 枚举：退还状态
│   │   └── DepositStatistics.java           # 统计值对象
│   ├── application/
│   │   ├── DepositQueryService.java         # 查询服务
│   │   ├── DepositCommandService.java       # 命令服务
│   │   └── DepositExportService.java        # 导出服务
│   ├── infrastructure/
│   │   ├── DepositRepository.java           # JPA Repository
│   │   └── DepositJpaRepository.java        # Spring Data JPA
│   └── api/
│       ├── DepositController.java           # REST API
│       └── DepositDto.java                  # DTO/VO
├── platform/
│   ├── domain/
│   │   ├── BidPlatform.java                 # 招标平台 Entity
│   │   ├── PlatformCA.java                  # CA证书 Entity
│   │   ├── CaBorrowRecord.java              # 领用记录 Entity
│   │   ├── CaStatus.java                    # CA状态枚举
│   │   ├── BorrowStatus.java                # 领用状态枚举
│   │   └── PlatformOperationLog.java        # 操作日志 Entity
│   ├── application/
│   │   ├── PlatformQueryService.java
│   │   ├── PlatformCommandService.java
│   │   ├── CaQueryService.java
│   │   ├── CaCommandService.java
│   │   ├── BorrowQueryService.java
│   │   ├── BorrowCommandService.java
│   │   └── PlatformLogService.java
│   ├── infrastructure/
│   │   ├── BidPlatformRepository.java
│   │   ├── PlatformCARepository.java
│   │   ├── CaBorrowRecordRepository.java
│   │   └── PlatformOperationLogRepository.java
│   └── api/
│       ├── PlatformController.java
│       ├── CAController.java
│       ├── BorrowController.java
│       └── PlatformDto.java
└── reminder/
    ├── domain/
    │   └── ReminderPolicy.java              # 提醒策略纯核心
    ├── application/
    │   └── ReminderTaskService.java         # 定时任务服务
    └── api/
        └── ReminderController.java
```

### 前端新增页面

```
src/views/resource/
├── deposit/
│   ├── DepositList.vue          # 保证金台账列表
│   └── components/
│       ├── DepositSearch.vue    # 搜索区
│       ├── DepositTable.vue     # 表格
│       ├── DepositCards.vue     # 统计卡片
│       └── DepositExport.vue    # 导出按钮
├── platform/
│   ├── PlatformList.vue         # 平台账号列表
│   ├── PlatformForm.vue         # 平台账号表单（新增/编辑）
│   ├── CAList.vue               # CA证书列表
│   ├── CAForm.vue               # CA证书表单
│   └── components/
│       ├── PlatformSearch.vue
│       ├── CASearch.vue
│       └── PasswordField.vue    # 密码脱敏/显示组件
├── borrow/
│   ├── BorrowList.vue           # 领用申请列表
│   ├── BorrowApply.vue          # 发起领用
│   └── BorrowApprove.vue        # 审批/归还
└── log/
    └── OperationLog.vue         # 操作日志
```

---

## Database Schema

### 新增表

1. **project_deposit**（保证金记录）
2. **bid_platform**（招标平台账号）
3. **platform_ca**（CA证书）
4. **ca_borrow_record**（领用记录）
5. **platform_operation_log**（操作日志）

详见 Flyway 迁移脚本。

---

## API Design

### 保证金管理 API

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/v1/deposits | 保证金台账列表（分页+筛选） |
| GET | /api/v1/deposits/statistics | 统计卡片数据 |
| GET | /api/v1/deposits/export | 导出 Excel |

### 招标平台账号管理 API

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/v1/platforms | 平台账号列表 |
| POST | /api/v1/platforms | 新增平台账号 |
| PUT | /api/v1/platforms/{id} | 编辑平台账号 |
| DELETE | /api/v1/platforms/{id} | 删除平台账号 |
| GET | /api/v1/platforms/{id}/password | 查看明文密码（需权限） |

### CA 管理 API

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/v1/cas | CA列表 |
| POST | /api/v1/cas | 新增CA |
| PUT | /api/v1/cas/{id} | 编辑CA |
| DELETE | /api/v1/cas/{id} | 删除CA |

### 领用审批 API

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/v1/borrows | 领用记录列表 |
| POST | /api/v1/borrows | 发起领用申请 |
| POST | /api/v1/borrows/{id}/approve | 审批通过 |
| POST | /api/v1/borrows/{id}/reject | 审批驳回 |
| POST | /api/v1/borrows/{id}/return | 登记归还 |

### 操作日志 API

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/v1/platform-logs | 操作日志列表 |

---

## Implementation Phases

### Phase 1: 数据库设计与迁移
- 编写 Flyway V 脚本创建 5 张表
- 编写对应 U 回滚脚本
- 本地验证迁移执行成功

### Phase 2: 后端基础层（Entity + Repository）
- 实现 5 个 JPA Entity
- 实现 5 个 Repository 接口
- 实现基础 CRUD 测试

### Phase 3: 后端纯核心（Domain Policy）
- 实现 DepositStatistics 计算逻辑
- 实现 CaBorrow 状态机
- 实现 ReminderPolicy 提醒策略
- 编写纯核心单元测试

### Phase 4: 后端应用层（Service）
- 实现 DepositQuery/Command/Export Service
- 实现 Platform/CA/Borrow/Log Service
- 实现 ReminderTaskService 定时任务
- 编写 Service 层集成测试

### Phase 5: 后端接口层（Controller）
- 实现所有 REST Controller
- 配置权限注解
- 编写 Controller 测试

### Phase 6: 前端页面开发
- 保证金台账页面（列表、搜索、卡片、导出）
- 平台账号管理页面
- CA 管理页面
- 领用审批页面
- 操作日志页面

### Phase 7: 集成测试与 E2E
- 前后端联调
- Playwright E2E 测试
- ArchUnit 门禁验证

### Phase 8: 代码审查与提交
- 自测通过
- 创建 PR
- 合并至 main

---

## Risk & Mitigation

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 密码加密方案变更 | 高 | 复用系统已有加密工具，统一密钥管理 |
| 项目模块事件同步延迟 | 中 | 采用事件监听模式，保证金表独立，项目变更时异步更新 |
| 单文件行数超标 | 中 | Phase 3 提前拆分 Policy/Validator，避免 Service 膨胀 |
| 权限矩阵复杂 | 中 | 使用 Spring Security 方法级注解 + 自定义 PermissionEvaluator |
| 定时任务重复执行 | 低 | 使用分布式锁（Redis）或数据库唯一约束保证幂等 |
