# Implementation Plan: 投标关键节点企微通知

**Branch**: `agent/kimi/enterprise-wecom-notifications` | **Date**: 2026-07-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/036-wecom-bid-notifications/spec.md`

## Summary

在现有投标流程中新增两个企微通知触点：
1. 投标管理员对评估后标讯点击"立即投标"并成功创建项目后，向项目负责人发送"待立项"企微通知。
2. 项目阶段从 `RETROSPECTIVE` 推进至 `CLOSED` 后，向项目负责人发送"待结项申请"企微通知。

实现上复用现有站内通知 + 自动企微镜像链路，新增纯核心策略类负责模板变量拼装与去重判断，应用服务层仅做编排与触发。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.2, JPA (Hibernate), Flyway

**Storage**: MySQL 8.0

**Testing**: JUnit 5, Mockito, Spring Boot Test, ArchUnit

**Target Platform**: Linux server / Docker

**Project Type**: Web application (Vue 3 frontend + Spring Boot backend)

**Performance Goals**: 通知创建接口 P95 < 200ms；企微发送走异步任务，不影响主流程响应时间。

**Constraints**:
- FP-Java Profile：纯核心不可读写数据库/API/文件/时间/随机数/日志；
- Split-First Rule：Application Service、Domain Policy、Mapper、Repository 必须拆分；
- 单 Java 文件硬上限 300 行，软上限 200 行；
- 通知发送为附加操作，失败不得阻塞主业务流程；
- 所有新增代码通过 ArchUnit / Checkstyle / PMD / SpotBugs。

**Scale/Scope**: 两个通知触点，影响标讯投标立项与项目结项两个应用服务，无前端页面改动。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. FP-Java Architecture | Pass | 新增纯核心：`BidNotificationPolicy`、`ProjectClosureNotificationPolicy`、`NotificationDedupPolicy`；应用服务仅编排 |
| II. Real-API Only | Pass | 复用现有真实企微消息中心网关，不引入 Mock |
| III. Test-Driven Development | Pass | 每个纯核心类与编排方法均有对应单元测试 |
| IV. Split-First & Simplicity | Pass | 通知创建、接收人解析、模板生成、去重判断分属不同类，单文件 < 200 行 |
| V. OSS Integration | N/A | 不涉及 OSS 调用 |
| VI. Authorization Unification | Pass | 复用现有 `@PreAuthorize` 与 `ProjectAccessScopeService`，不新增角色白名单 |
| VII. Defensive Collection & Graceful Degradation | Pass | 通知装饰性操作 try-catch 降级，不阻塞主流程；无新增 2 参数 `Collectors.toMap` |
| VIII. Boring Proven Patterns | Pass | 复用现有 `NotificationApplicationService` 与 `ProjectNotificationRecipientPolicy` |

## Project Structure

### Documentation (this feature)

```text
specs/036-wecom-bid-notifications/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── notification-trigger-contract.md
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/
├── tender/service/
│   └── TenderEvaluationService.java            # 在 proceedToBid 成功后触发待立项通知
├── project/service/
│   └── ProjectStageService.java                # 在 RETROSPECTIVE→CLOSED 后触发待结项申请通知
├── notification/
│   ├── core/
│   │   ├── BidNotificationPolicy.java          # 纯核心：待立项通知模板变量与去重
│   │   ├── ProjectClosureNotificationPolicy.java # 纯核心：待结项申请通知模板变量与去重
│   │   └── NotificationDedupPolicy.java        # 纯核心：去重窗口判断
│   ├── application/
│   │   ├── BidNotificationApplicationService.java # 编排：创建待立项通知
│   │   └── ProjectClosureNotificationApplicationService.java # 编排：创建待结项申请通知
│   └── service/
│       └── ProjectNotificationRecipientPolicy.java # 已存在：解析 PROJECT_OWNER
│
backend/src/test/java/com/xiyu/bid/
├── notification/core/
│   ├── BidNotificationPolicyTest.java
│   ├── ProjectClosureNotificationPolicyTest.java
│   └── NotificationDedupPolicyTest.java
├── notification/application/
│   ├── BidNotificationApplicationServiceTest.java
│   └── ProjectClosureNotificationApplicationServiceTest.java
└── tender/service/TenderEvaluationServiceNotificationTest.java
└── project/service/ProjectStageServiceNotificationTest.java
```

**Structure Decision**: 按 FP-Java Profile 拆分纯核心与应用编排；通知触发点下沉到现有 Application Service 内部，不新增 Controller；测试按领域分层与现有测试结构对齐。

## Implementation Notes

- **去重策略**：基于 `NotificationRepository.findBySourceEntityTypeAndSourceEntityIdAndTypeAndCreatedAtAfter` 的 5 分钟滑动窗口，与 `NotificationDedupPolicy` 纯核心判断结合。
- **发送链路**：复用现有 `NotificationApplicationService.createNotification` + `ProjectNotificationRecipientPolicy.resolveRecipients(PROJECT_OWNER)`，企微镜像由消息中心自动处理，本次不直接调用企微客户端。
- **失败降级**：两个通知应用服务均 try-catch 通知创建异常并记录 warn，确保标讯投标 / 项目阶段转换主流程不受影响。
- **命名来源**：调用方将 tenderName/projectName 等显示字段传入应用服务，应用服务不再查询 Tender/Project Repository，保持编排类职责单一。
- **完成状态**：所有 Phase 2-5 任务已完成；相关单元测试、架构测试、Checkstyle/PMD/SpotBugs 已全绿。

## Complexity Tracking

> 无 Constitution Check 违规，无需复杂度说明。
