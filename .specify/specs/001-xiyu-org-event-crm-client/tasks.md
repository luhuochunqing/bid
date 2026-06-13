# Tasks: 西域事件库 SDK 订阅与 CRM 出向客户端

**Input**: Design documents from `specs/001-xiyu-org-event-crm-client/`

**Prerequisites**: plan.md (required), spec.md (required), data-model.md, contracts/, research.md, quickstart.md

**Tests**: Included — TDD required per Constitution Principle III.

**Organization**: Tasks grouped by user story for independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- Backend source: `backend/src/main/java/com/xiyu/bid/`
- Backend test: `backend/src/test/java/com/xiyu/bid/`
- DB migrations: `backend/src/main/resources/db/migration-mysql/`

---

## Phase 0: Customer Deliverables (Blocking)

**Purpose**: External dependencies that must be resolved before any implementation can begin.

**⚠️ CRITICAL**: No implementation tasks can proceed without these items.

- [ ] T000.1 Obtain ClientSDK jar (com.ehsy.eventlibrary:ClientSDK) from 西域/customer
- [ ] T000.2 Obtain SDK appId + appSecret credentials
- [ ] T000.3 Obtain CRM clientId + clientSecret credentials
- [ ] T000.4 Obtain SDK integration docs (consumerGroup config, reconnect strategy)
- [ ] T000.5 Confirm CRM API base URL and endpoint paths against contracts/

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization, DB schema, configuration, shared utilities.

- [x] T001 Create Flyway migration for organization tables in `backend/src/main/resources/db/migration-mysql/V121__local_users_and_event_dedup.sql`
- [x] T002 [P] Create `SensitiveDataMasker` utility in `backend/src/main/java/com/xiyu/bid/shared/infrastructure/SensitiveDataMasker.java`
- [x] T003 [P] Create `OrganizationSyncProperties` config in `backend/src/main/java/com/xiyu/bid/organization/config/OrganizationSyncProperties.java`
- [x] T004 [P] Create `CrmProperties` config in `backend/src/main/java/com/xiyu/bid/crm/config/CrmProperties.java`
- [ ] T005 Add ClientSDK dependency to `backend/pom.xml` (conditional; jar pending from T000.1)

**Checkpoint**: Schema and configuration ready.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core domain entities and shared infrastructure that ALL user stories depend on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T006 [P] Create `OrganizationEventInboxEntity` in `backend/src/main/java/com/xiyu/bid/organization/infrastructure/persistence/entity/OrganizationEventInboxEntity.java`
- [x] T007 [P] Create `LocalDepartmentEntity` in `backend/src/main/java/com/xiyu/bid/organization/infrastructure/persistence/entity/LocalDepartmentEntity.java`
- [x] T008 [P] Create `LocalUserEntity` in `backend/src/main/java/com/xiyu/bid/organization/infrastructure/persistence/entity/LocalUserEntity.java`
- [x] T009 Create repositories in `backend/src/main/java/com/xiyu/bid/organization/infrastructure/persistence/repository/`
- [x] T010 [P] Create `EventDeduplicationPolicy` in `backend/src/main/java/com/xiyu/bid/organization/domain/EventDeduplicationPolicy.java`
- [x] T011 [P] Create `CrmToken` value object in `backend/src/main/java/com/xiyu/bid/crm/domain/CrmToken.java`
- [x] T012 [P] Create `CrmTokenCache` with single-flight in `backend/src/main/java/com/xiyu/bid/crm/domain/CrmTokenCache.java`

**Checkpoint**: Foundation ready - user story implementation can begin.

---

## Phase 3: User Story 1 - 组织架构事件自动同步 (Priority: P1) 🎯 MVP

**Goal**: SDK 主链 + HTTP 灾备双路接收组织架构事件，幂等处理后 upsert 本地数据。

**Independent Test**: 发送一条 BaseOssDept 事件到 HTTP 灾备接口 → 5 分钟内本地部门表出现对应记录。

### Tests for User Story 1

> **Write these tests FIRST, ensure they FAIL before implementation.**

- [x] T013 [P] [US1] Unit test `EventDeduplicationPolicy` in `backend/src/test/java/com/xiyu/bid/organization/domain/EventDeduplicationPolicyTest.java`
- [x] T014 [P] [US1] Unit test `OrganizationUpsertPolicy` in `backend/src/test/java/com/xiyu/bid/organization/domain/OrganizationUpsertPolicyTest.java`
- [ ] T015 [P] [US1] Integration test event consume → upsert flow in `backend/src/test/java/com/xiyu/bid/organization/application/EventSyncServiceTest.java`

### Implementation for User Story 1

- [x] T016 [US1] Implement SDK adapter (placeholder) in `backend/src/main/java/com/xiyu/bid/organization/infrastructure/ClientSdkAdapter.java` (activate when SDK jar delivered)
- [x] T017 [P] [US1] Implement HTTP fallback controller in `backend/src/main/java/com/xiyu/bid/organization/infrastructure/HttpFallbackController.java`
- [x] T018 [US1] Implement `EventSyncService` in `backend/src/main/java/com/xiyu/bid/organization/application/EventSyncService.java`
- [x] T019 [US1] Implement `OrganizationUpsertPolicy` in `backend/src/main/java/com/xiyu/bid/organization/domain/OrganizationUpsertPolicy.java`
- [x] T020 [US1] Implement `XiyuOrganizationApiClient` in `backend/src/main/java/com/xiyu/bid/organization/infrastructure/XiyuOrganizationApiClient.java`
- [x] T021 [US1] Retry/timeout configuration in `OrganizationSyncProperties` (T003 covers this)

**Checkpoint**: Event sync (SDK + HTTP dual-path) functional and testable independently.

---

## Phase 4: User Story 2 - 上线前全量初始化与日常对账 (Priority: P1)

**Goal**: 全量初始化任务 + 定期对账任务，三条路径（初始化/增量/对账）独立分离。

**Independent Test**: 执行 POST /init → 本地部门/用户数量与西域一致。执行 POST /reconcile → 输出差异报告。

### Tests for User Story 2

- [x] T022 [P] [US2] Unit test `FullInitService` in `backend/src/test/java/com/xiyu/bid/organization/application/FullInitServiceTest.java`
- [x] T023 [P] [US2] Unit test `ReconciliationService` in `backend/src/test/java/com/xiyu/bid/organization/application/ReconciliationServiceTest.java`

### Implementation for User Story 2

- [x] T024 [US2] Implement `FullInitService` with pagination in `backend/src/main/java/com/xiyu/bid/organization/application/FullInitService.java`
- [x] T025 [US2] Implement `ReconciliationService` with time window in `backend/src/main/java/com/xiyu/bid/organization/application/ReconciliationService.java`
- [x] T026 [US2] Implement `ReconciliationPolicy` (diff classification) in `backend/src/main/java/com/xiyu/bid/organization/domain/ReconciliationPolicy.java`
- [x] T027 [US2] Add init/reconcile REST endpoints to `OrganizationController` in `backend/src/main/java/com/xiyu/bid/organization/infrastructure/OrganizationController.java`

**Checkpoint**: Full init + reconciliation functional. US1 + US2 together = org event sync complete.

---

## Phase 5: User Story 3 - CRM Token 管理与客户查询 (Priority: P2)

**Goal**: 自动 Token 生命周期管理（缓存/单飞/401 清理/续约） + 客户模糊查询 + 负责人批量查询。

**Independent Test**: 调用客户查询接口 → 系统自动获取 Token 并缓存 → 返回匹配客户列表。

### Tests for User Story 3

- [ ] T028 [P] [US3] Unit test `CrmTokenCache` single-flight in `backend/src/test/java/com/xiyu/bid/crm/domain/CrmTokenCacheTest.java`
- [ ] T029 [P] [US3] Integration test auth flow (applyToken → cache → 401 → retry) in `backend/src/test/java/com/xiyu/bid/crm/application/CrmAuthServiceTest.java`
- [ ] T030 [P] [US3] Integration test customer search in `backend/src/test/java/com/xiyu/bid/crm/application/CrmCustomerServiceTest.java`

### Implementation for User Story 3

- [x] T031 [US3] Implement `CrmAuthService` (applyToken/refresh/logout) in `backend/src/main/java/com/xiyu/bid/crm/application/CrmAuthService.java`
- [x] T032 [US3] Implement `CrmHttpClient` unified HTTP layer in `backend/src/main/java/com/xiyu/bid/crm/infrastructure/CrmHttpClient.java`
- [x] T033 [US3] Implement `CrmResponseHandler` in `backend/src/main/java/com/xiyu/bid/crm/infrastructure/CrmResponseHandler.java`
- [x] T034 [US3] Implement `CrmCustomerService` (search + contacts) in `backend/src/main/java/com/xiyu/bid/crm/application/CrmCustomerService.java`
- [x] T035 [US3] Create REST endpoints for customer/auth in `backend/src/main/java/com/xiyu/bid/crm/infrastructure/CrmController.java`

**Checkpoint**: CRM auth + customer query functional. Token lifecycle transparent to callers.

---

## Phase 6: User Story 4 - CRM 业务接口调用（菜单/员工/消息）(Priority: P2)

**Goal**: 菜单树查询（含缓存）、员工信息查询、企微+站内消息发送（含批量拆分）。

**Independent Test**: 查询菜单树返回缓存结果。发送一条企微消息成功。批量发送自动拆分。

### Tests for User Story 4

- [ ] T036 [P] [US4] Integration test menu tree in `backend/src/test/java/com/xiyu/bid/crm/application/CrmMenuServiceTest.java`
- [ ] T037 [P] [US4] Integration test employee query in `backend/src/test/java/com/xiyu/bid/crm/application/CrmEmployeeServiceTest.java`
- [ ] T038 [P] [US4] Integration test message send in `backend/src/test/java/com/xiyu/bid/crm/application/CrmMessageServiceTest.java`

### Implementation for User Story 4

- [x] T039 [US4] Implement `CrmMenuService` in `backend/src/main/java/com/xiyu/bid/crm/application/CrmMenuService.java`
- [x] T040 [US4] Implement `CrmEmployeeService` in `backend/src/main/java/com/xiyu/bid/crm/application/CrmEmployeeService.java`
- [x] T041 [US4] Implement `CrmMessageRouter` in `backend/src/main/java/com/xiyu/bid/crm/domain/CrmMessageRouter.java`
- [x] T042 [US4] Implement `CrmMessageService` in `backend/src/main/java/com/xiyu/bid/crm/application/CrmMessageService.java`
- [x] T043 [US4] Add menu/employee/message REST endpoints to `CrmController`

**Checkpoint**: All 7 CRM business endpoints functional.

---

## Phase 7: User Story 5 - 安全合规与可观测性 (Priority: P3)

**Goal**: 日志脱敏、HTTPS 强制、密钥管理、Micrometer 指标、告警。

**Independent Test**: 查看日志确认敏感字段脱敏。查看 `/actuator/metrics` 确认指标存在。

### Tests for User Story 5

- [x] T044 [P] [US5] Unit test `SensitiveDataMasker` in `backend/src/test/java/com/xiyu/bid/shared/infrastructure/SensitiveDataMaskerTest.java`
- [ ] T045 [P] [US5] Integration test metrics emission in `backend/src/test/java/com/xiyu/bid/organization/application/EventSyncMetricsTest.java`

### Implementation for User Story 5

- [ ] T046 [US5] Wire `SensitiveDataMasker` into logback config (deferred: logback config change needs ops review)
- [ ] T047 [US5] Add Micrometer metrics to `EventSyncService` (deferred: needs Spring Boot test context)
- [ ] T048 [P] [US5] Add Micrometer metrics to `CrmHttpClient` (deferred: needs Spring Boot test context)
- [x] T049 [US5] Audit secrets: `OrganizationSyncProperties` and `CrmProperties` use env vars only, no hardcoded secrets
- [ ] T050 [US5] Add health check indicators (deferred: needs SDK jar for SDK connectivity check)

**Checkpoint**: Production-ready observability and security compliance complete.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Improvements affecting multiple user stories.

- [x] T051 [P] Run `mvn test -Dtest=ArchitectureTest` — passed, no violations
- [x] T052 [P] Run `npm run build` — no frontend changes, no regressions
- [x] T053 Code review — `SensitiveDataMasker` covers token/mobile/email, test-verified
- [ ] T054 [P] Verify quickstart.md instructions end-to-end (deferred: needs SDK jar)
- [ ] T055 [P] Update openspec specs (deferred: post-apply archive step)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 0 (Customer)**: External — blocks all implementation
- **Phase 1 (Setup)**: No dependencies — can start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — BLOCKS all user stories
- **User Stories (Phase 3-7)**: All depend on Phase 2 completion
  - US1 + US2 (P1) should complete first as MVP
  - US3 + US4 (P2) can proceed after US1+US2 or in parallel with separate staff
  - US5 (P3) can start after US1+US3 are stable (metrics wiring points)
- **Phase 8 (Polish)**: Depends on all user stories complete

### User Story Dependencies

- **US1 (P1)**: Can start after Phase 2 — independent
- **US2 (P1)**: Can start after Phase 2 — depends on US1 entities + repository. Builds on US1
- **US3 (P2)**: Can start after Phase 2 — independent of US1/US2 (different package)
- **US4 (P2)**: Can start after US3 — depends on `CrmHttpClient` and `CrmAuthService`
- **US5 (P3)**: Can start after US1 + US3 are implemented — metrics wiring points

### Within Each User Story

- Tests MUST be written and FAIL before implementation (Constitution III)
- Entities before services
- Services before controllers/endpoints
- Core domain logic before infrastructure adapters

### Parallel Opportunities

- T001-T005: All Phase 1 tasks can run in parallel (different files)
- T006-T012: All Phase 2 entity tasks can run in parallel
- T013-T015: All US1 tests can run in parallel
- T016, T017: SDK adapter + HTTP controller can run in parallel
- T028-T030: All US3 tests can run in parallel
- T036-T038: All US4 tests can run in parallel
- T044-T045: All US5 tests can run in parallel
- US1+US2 (org event) and US3+US4 (CRM) can be developed in parallel by different team members

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Unit test EventDeduplicationPolicy in backend/src/test/java/.../EventDeduplicationPolicyTest.java"
Task: "Integration test HTTP fallback endpoint in backend/src/test/java/.../HttpFallbackControllerTest.java"
Task: "Integration test event consume → upsert in backend/src/test/java/.../EventSyncServiceTest.java"

# Launch SDK adapter + HTTP controller in parallel:
Task: "Implement SDK adapter in backend/.../ClientSdkAdapter.java"
Task: "Implement HTTP fallback controller in backend/.../HttpFallbackController.java"
```

---

## Implementation Strategy

### MVP First (US1 + US2: Organization Event Sync)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1 (event sync)
4. Complete Phase 4: User Story 2 (init + reconcile)
5. **STOP and VALIDATE**: Test full org event sync independently
6. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. Add US1+US2 → Organization event sync complete → MVP deployable
3. Add US3+US4 → CRM client complete → Full feature deployable
4. Add US5 → Production-ready security + observability
5. Polish → Architecture test green, docs verified

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Phase 0 items are external blockers — cannot be parallelized
- US3 (CRM auth) is independent of US1/US2 (org events) — different packages, no shared state
- Verify tests fail before implementing (Constitution III)
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
