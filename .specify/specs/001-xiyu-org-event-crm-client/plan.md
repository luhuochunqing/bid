# Implementation Plan: 西域事件库 SDK 订阅与 CRM 出向客户端

**Branch**: `agent/claude-add-xiyu-org-event-sdk-and-crm-client` | **Date**: 2026-05-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-xiyu-org-event-crm-client/spec.md`

## Summary

为投标系统后端补齐与西域平台的两块对接能力：(1) 组织架构事件订阅——通过 ClientSDK + HTTP 灾备双路接收 BaseOssDept/BaseOssUser 变更事件，实现增量同步、全量初始化和日常对账三条独立路径；(2) CRM 出向客户端——封装 Token 生命周期管理（缓存/单飞/401 强制清理/自动续约）和 7 个业务接口（客户查询、负责人、菜单树、员工、消息发送），统一重试/超时/脱敏/监控行为。

## Technical Context

**Language/Version**: Java 21 (backend, Spring Boot 3.3)

**Primary Dependencies**: Spring Boot 3.3, Spring Data JPA (Hibernate), Flyway (MySQL 8.0 migrations), Redis 7 (event dedup cache), Micrometer (metrics), ClientSDK (com.ehsy.eventlibrary — pending customer delivery), Spring Web (RestTemplate / WebClient for HTTP outbound to CRM)

**Storage**: MySQL 8.0 — new tables: `organization_event_inbox`, `local_department`, `local_user`; Flyway migration scripts under `backend/src/main/resources/db/migration-mysql/`

**Testing**: JUnit 5 + Mockito + ArchUnit (architecture tests) + Spring Boot Test (integration with real MySQL/Redis)

**Target Platform**: Linux server (Docker / Docker Compose)

**Project Type**: web-service (backend only, no frontend changes)

**Performance Goals**: Event sync P95 latency < 5 min; full init 100k entities < 30 min; CRM query P95 < 1s

**Constraints**: FP-Java architecture (core/domain separation), Real-API only (no mock), file budget 200/300 lines, no new mock adapters, Spotless + google-java-format

**Scale/Scope**: ~10k departments + ~90k users; 7 CRM outbound endpoints; 2 new backend packages

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. FP-Java Architecture | ✅ PASS | Event processing core logic (dedup, upsert, reconcile) will be in `domain/` package; SDK/HTTP adapters and CRM HTTP client in `infrastructure/`; application services coordinate |
| II. Real-API Only | ✅ PASS | No mock mode introduced. ClientSDK and CRM are real external dependencies, managed via configuration. Stub implementations for local dev testing are NOT mock mode — they live in test scope only |
| III. TDD | ✅ PASS | Tests written first per requirement. Architecture tests validate package boundaries. E2E tests for critical paths (event consume → local write) |
| IV. Split-First & Simplicity | ✅ PASS | Each adapter, domain service, and repository in its own file. File count expected: ~20-25 files, each <200 lines |
| V. Boring Proven Patterns | ✅ PASS | Standard Spring Boot service/repository pattern. No reactive programming, no event sourcing, no CQRS. Simplicity-first |

## Project Structure

### Documentation (this feature)

```text
specs/001-xiyu-org-event-crm-client/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (CRM API contracts)
│   ├── crm-auth.yaml    # applyToken, logout
│   ├── crm-customer.yaml # customer search, contact query
│   ├── crm-menu.yaml    # menu tree
│   ├── crm-employee.yaml # employee info
│   └── crm-message.yaml # message send
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/
├── organization/                    # NEW: 组织架构事件同步
│   ├── domain/
│   │   ├── OrganizationEventInbox.java      # 事件实体
│   │   ├── LocalDepartment.java             # 本地部门实体
│   │   ├── LocalUser.java                   # 本地用户实体
│   │   ├── EventDeduplicationPolicy.java    # 幂等策略 (traceId+spanId+topic)
│   │   ├── OrganizationUpsertPolicy.java    # 业务主键 upsert
│   │   └── ReconciliationPolicy.java        # 对账差异分类规则
│   ├── application/
│   │   ├── EventSyncService.java            # 事件消费编排
│   │   ├── FullInitService.java             # 全量初始化
│   │   └── ReconciliationService.java       # 日常对账
│   ├── infrastructure/
│   │   ├── ClientSdkAdapter.java            # SDK @AcceptEvent 订阅适配器
│   │   ├── HttpFallbackController.java      # HTTP 灾备入口 (@RestController)
│   │   ├── XiyuOrganizationApiClient.java   # 回查西域组织架构接口
│   │   └── OrganizationRepository.java     # JPA Repository
│   └── config/
│       └── OrganizationSyncProperties.java  # @ConfigurationProperties
├── crm/                             # NEW: CRM 出向客户端
│   ├── domain/
│   │   ├── CrmToken.java                    # Token 值对象
│   │   ├── CrmTokenCache.java               # 内存缓存 + 单飞逻辑
│   │   ├── CrmCustomer.java                 # 客户查询结果
│   │   └── CrmMessageRouter.java            # 消息发送策略（拆分/路由）
│   ├── application/
│   │   ├── CrmAuthService.java              # Token 生命周期
│   │   ├── CrmCustomerService.java          # 客户查询
│   │   ├── CrmMenuService.java              # 菜单树（含缓存）
│   │   ├── CrmEmployeeService.java          # 员工查询
│   │   └── CrmMessageService.java           # 消息发送
│   ├── infrastructure/
│   │   ├── CrmHttpClient.java               # 统一 HTTP 客户端 (HTTPS/Bearer/重试/脱敏)
│   │   └── CrmResponseHandler.java          # 统一响应解析 (code-msg-data-success)
│   └── config/
│       └── CrmProperties.java               # @ConfigurationProperties
└── shared/                          # 可能新增的共享工具
    └── infrastructure/
        └── SensitiveDataMasker.java          # 脱敏工具（Token/手机/邮箱）
```

**Structure Decision**: 选用 Web application 结构。两个新包 `organization/` 和 `crm/` 各自遵循 FP-Java 分层（domain/application/infrastructure/config）。共享的脱敏工具放入现有 `shared/` 包。不创建新的 Maven 模块，保持在现有 `backend` 模块内。

## Complexity Tracking

> No violations to justify — all constitution gates passed.
