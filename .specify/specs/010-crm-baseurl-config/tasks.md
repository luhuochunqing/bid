# Tasks: CRM BaseUrl 配置重构

**Input**: Design documents from `/specs/010-crm-baseurl-config/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Tests are included as this is a configuration refactor that MUST maintain backward compatibility.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 [P] Acquire agent lock for CRM configuration paths: `backend/src/main/java/com/xiyu/bid/crm/` and `backend/src/main/java/com/xiyu/bid/settings/`
- [x] T002 [P] Verify existing CRM code structure and identify all files requiring modification

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T003 Extend `CrmProperties` with multi-BaseUrl support in `backend/src/main/java/com/xiyu/bid/crm/config/CrmProperties.java`
  - Add `authBaseUrl`, `customerBaseUrl`, `messageBaseUrl`
  - Add nested path configuration classes (`CrmAuthPaths`, `CrmCustomerPaths`, `CrmMessagePaths`)
  - Add `getEffectiveAuthBaseUrl()`, `getEffectiveCustomerBaseUrl()`, `getEffectiveMessageBaseUrl()` methods with backward compatibility fallback to `baseUrl`
  - Add runtime parameter fields: `tokenCacheTtlSeconds`, `maxRetries`, `retryBaseDelayMs`, `connectTimeoutMs`, `readTimeoutMs`
- [x] T004 [P] Update `CrmHttpClient` to support multiple BaseUrls in `backend/src/main/java/com/xiyu/bid/crm/infrastructure/CrmHttpClient.java`
  - Add overloaded `post()` method accepting full URL instead of path-only
  - Maintain backward compatibility with existing `post(path, token, body)` signature

**Checkpoint**: Foundation ready - CrmProperties can hold multi-BaseUrl config and CrmHttpClient can route to different URLs

---

## Phase 3: User Story 1 - 多域名 CRM 接口调用 (Priority: P1) 🎯 MVP

**Goal**: CRM 相关接口能够正确路由到不同的域名

**Independent Test**: Run `CrmPropertiesTest` to verify effective BaseUrl resolution with and without new configuration

### Tests for User Story 1

- [x] T005 [P] [US1] Create `CrmPropertiesTest` in `backend/src/test/java/com/xiyu/bid/crm/config/CrmPropertiesTest.java`
  - Test `getEffectiveAuthBaseUrl()` returns `authBaseUrl` when set
  - Test `getEffectiveAuthBaseUrl()` falls back to `baseUrl` when `authBaseUrl` is null/empty
  - Same tests for `getEffectiveCustomerBaseUrl()` and `getEffectiveMessageBaseUrl()`

### Implementation for User Story 1

- [x] T006 [US1] Update `CrmAuthService` to use `authBaseUrl` in `backend/src/main/java/com/xiyu/bid/crm/application/CrmAuthService.java`
  - Inject `CrmProperties` and use `getEffectiveAuthBaseUrl()` for token operations
- [x] T007 [P] [US1] Update `CrmCustomerService` to use `customerBaseUrl` in `backend/src/main/java/com/xiyu/bid/crm/application/CrmCustomerService.java`
  - Use `getEffectiveCustomerBaseUrl()` for customer search and contacts
- [x] T008 [P] [US1] Update `CrmMenuService` to use `authBaseUrl` in `backend/src/main/java/com/xiyu/bid/crm/application/CrmMenuService.java`
  - Use `getEffectiveAuthBaseUrl()` for menu tree
- [x] T009 [P] [US1] Update `CrmEmployeeService` to use `authBaseUrl` in `backend/src/main/java/com/xiyu/bid/crm/application/CrmEmployeeService.java`
  - Use `getEffectiveAuthBaseUrl()` for employee info
- [x] T010 [US1] Update `CrmMessageService` to use `messageBaseUrl` in `backend/src/main/java/com/xiyu/bid/crm/application/CrmMessageService.java`
  - Use `getEffectiveMessageBaseUrl()` for message sending

**Checkpoint**: At this point, each CRM service routes to its correct BaseUrl, with backward compatibility preserved

---

## Phase 4: User Story 2 - YAPI 真实路径对接 (Priority: P1)

**Goal**: CRM 客户端使用真实的 YAPI 接口路径

**Independent Test**: Verify `CrmHttpClient` constructs URLs matching YAPI documentation format

### Tests for User Story 2

- [ ] T011 [P] [US2] Create `CrmHttpClientUrlTest` in `backend/src/test/java/com/xiyu/bid/crm/infrastructure/CrmHttpClientUrlTest.java`
  - Test URL construction for each of the 7 CRM endpoints
  - Verify path configuration from `CrmProperties` is correctly applied

### Implementation for User Story 2

- [x] T012 [US2] Update `CrmAuthService` with configurable paths in `backend/src/main/java/com/xiyu/bid/crm/application/CrmAuthService.java`
  - Use `properties.getAuth().getApplyTokenPath()` instead of hardcoded `/auth/applyToken`
  - Use `properties.getAuth().getLogoutPath()` instead of hardcoded `/auth/logout`
- [x] T013 [P] [US2] Update `CrmCustomerService` with configurable paths in `backend/src/main/java/com/xiyu/bid/crm/application/CrmCustomerService.java`
  - Use `properties.getCustomer().getSearchPath()` instead of hardcoded `/customer/search`
  - Use `properties.getCustomer().getContactsPath()` instead of hardcoded `/customer/contacts/batch`
- [x] T014 [P] [US2] Update `CrmMenuService` with configurable paths in `backend/src/main/java/com/xiyu/bid/crm/application/CrmMenuService.java`
  - Use `properties.getAuth().getMenuTreePath()` instead of hardcoded `/menu/tree`
- [x] T015 [P] [US2] Update `CrmEmployeeService` with configurable paths in `backend/src/main/java/com/xiyu/bid/crm/application/CrmEmployeeService.java`
  - Use `properties.getAuth().getEmployeePath()` instead of hardcoded `/employee/info`
- [x] T016 [US2] Update `CrmMessageService` with configurable paths in `backend/src/main/java/com/xiyu/bid/crm/application/CrmMessageService.java`
  - Use `properties.getMessage().getSendPath()` instead of hardcoded `/message/send`

**Checkpoint**: All 7 CRM endpoints use configurable paths from `CrmProperties`

---

## Phase 5: User Story 3 - 运行时参数可配置 (Priority: P2)

**Goal**: CRM 运行时参数纳入系统设置管理

**Independent Test**: Modify Settings via API and verify CRM services pick up new values within 5 seconds

### Tests for User Story 3

- [ ] T017 [P] [US3] Create `CrmSettingsIntegrationTest` in `backend/src/test/java/com/xiyu/bid/crm/application/CrmSettingsIntegrationTest.java`
  - Test that `CrmProperties` reads from Settings when available
  - Test configuration priority: Settings > application.yml > defaults

### Implementation for User Story 3

- [ ] T018 [US3] Extend Settings entity/response with CRM config in `backend/src/main/java/com/xiyu/bid/settings/`
  - Add `crmConfig` JSON field to Settings entity (or extend existing JSON config structure)
  - Update `SettingsResponse` DTO to include CRM configuration
- [ ] T019 [P] [US3] Create `CrmSettingsLoader` in `backend/src/main/java/com/xiyu/bid/crm/config/CrmSettingsLoader.java`
  - Service that loads CRM config from Settings table on startup and on change
  - Publishes `CrmConfigChangedEvent` when configuration changes
- [ ] T020 [US3] Update `CrmProperties` to support dynamic refresh in `backend/src/main/java/com/xiyu/bid/crm/config/CrmProperties.java`
  - Add `@RefreshScope` or event listener to react to `CrmConfigChangedEvent`
  - Merge Settings values with `@ConfigurationProperties` values (Settings wins)
- [ ] T021 [P] [US3] Add CRM configuration card to frontend Settings page in `src/views/Settings/`
  - Create `CrmSettingsCard.vue` component
  - Include form fields for: authBaseUrl, customerBaseUrl, messageBaseUrl, tokenCacheTtlSeconds, maxRetries, connectTimeoutMs
  - Include "Test Connection" button for each BaseUrl
- [ ] T022 [US3] Add CRM settings API endpoints in `backend/src/main/java/com/xiyu/bid/settings/controller/SettingsController.java` (or create `CrmSettingsController`)
  - `GET /api/settings/crm` - retrieve current CRM configuration
  - `PUT /api/settings/crm` - update CRM configuration

**Checkpoint**: CRM configuration can be viewed and modified from the system settings page, changes take effect within 5 seconds

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final validation, documentation, and cleanup

- [x] T023 [P] Update `application-dev.yml` example configuration in `backend/src/main/resources/application-dev.yml`
  - Add commented example of new multi-BaseUrl configuration
  - Document backward compatibility behavior
- [x] T024 [P] Update Wiki documentation `.wiki/pages/integration-oa-crm.md`
  - Document new configuration options
  - Update BaseUrl table with configuration instructions
- [x] T025 Run backend tests: `cd backend && mvn test -Dtest="*Crm*Test"`
- [x] T026 Run ArchitectureTest: `cd backend && mvn test -Dtest=ArchitectureTest`
- [x] T027 [P] Run frontend build: `npm run build`
- [ ] T028 Verify backward compatibility: Start application with OLD `baseUrl` config only, verify all CRM services still work
- [x] T029 Release agent locks

---

## Dependency Graph

```
Phase 1 (Setup)
    │
    ▼
Phase 2 (Foundational)
    │
    ├──► Phase 3 (US1: Multi-BaseUrl)
    │       │
    │       └──► Phase 4 (US2: YAPI Paths)
    │               │
    │               └──► Phase 5 (US3: Settings Integration)
    │                       │
    │                       └──► Phase 6 (Polish)
    │
    └──► Phase 3, 4, 5 can run sequentially (US1 → US2 → US3)
```

## Parallel Execution Opportunities

- **T001, T002**: Lock acquisition and code analysis (no dependencies)
- **T005, T011, T017**: Test files can be written in parallel with implementation
- **T006-T010**: Each service update is independent once T003/T004 complete
- **T012-T016**: Each path update is independent
- **T018, T019, T021**: Backend settings + frontend card can be developed in parallel

## Implementation Strategy

1. **MVP**: Complete Phase 1-3 (US1) to enable multi-BaseUrl routing
2. **Incremental**: Add US2 (YAPI paths) and US3 (Settings) sequentially
3. **Backward Compatibility**: Always maintained - old `baseUrl` config continues to work
