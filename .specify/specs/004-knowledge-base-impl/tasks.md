# Tasks: 知识库 (Knowledge Base)

**Input**: Design documents from `/specs/004-knowledge-base-impl/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

**Tests**: Test-Driven Development (TDD) is mandatory per project constitution. Test writing and execution tasks are included under each user story.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and base structure configuration

- [ ] T001 [P] Create folders for new casework packages in `backend/src/main/java/com/xiyu/bid/casework/` (domain, application, infrastructure)
- [ ] T002 [P] Create frontend routes in `src/router/index.js` mapping `/knowledge` sub-routes (archive, cases, certificates, deposit)
- [ ] T003 Create Flyway migration files for database updates in `backend/src/main/resources/db/migration-mysql/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core tables setup and project access verification handlers

- [ ] T004 Setup Flyway SQL baseline migration and execute migrations locally to create `project_archive`, `archive_file`, `archive_log`, `knowledge_case` tables
- [ ] T005 Create Java Architecture unit test entries in `backend/src/test/java/com/xiyu/bid/FPJavaArchitectureTest.java` verifying boundary check constraints for `casework` and `knowledge_case`
- [ ] T006 [P] Add JPA entity maps for `ProjectArchive` and `ArchiveFile` in `backend/src/main/java/com/xiyu/bid/casework/infrastructure/`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - 项目文件自动即时归档与台账检索 (Priority: P1) 🎯 MVP

**Goal**: 立项后根据阶段自动分类归档文件，提供多维台账及右滑 60% 只读详情抽屉，支持 Hover 文件明细浮层。

**Independent Test**: 在项目管理中创建新项目并上传文件，验证项目档案台账列表中即时更新。

### Tests for User Story 1

- [ ] T007 [P] [US1] Create unit tests in `backend/src/test/java/com/xiyu/bid/casework/domain/ArchiveDomainTest.java` verifying file category calculation logic (Must FAIL before code is written)
- [ ] T008 [P] [US1] Create integration test in `backend/src/test/java/com/xiyu/bid/casework/application/ProjectArchiveWorkflowServiceTest.java` verifying project access restriction and multi-category queries

### Implementation for User Story 1

- [ ] T009 [P] [US1] Create domain record class `ArchiveFileCategoryPolicy` in `backend/src/main/java/com/xiyu/bid/casework/domain/` to handle safe file tagging
- [ ] T010 [P] [US1] Implement JPA repositories for `ProjectArchiveRepository` and `ArchiveFileRepository` in `backend/src/main/java/com/xiyu/bid/casework/infrastructure/`
- [ ] T011 [US1] Implement application service `ProjectArchiveWorkflowService` in `backend/src/main/java/com/xiyu/bid/casework/application/` leveraging `ProjectAccessScopeService` for data isolation
- [ ] T012 [US1] Create HTTP Controller `ProjectArchiveController` in `backend/src/main/java/com/xiyu/bid/casework/controller/` mapping query paths
- [ ] T013 [P] [US1] Create frontend components in `src/views/Knowledge/components/FileCategoryPopover.vue` displaying category bubbles on Hover
- [ ] T014 [US1] Create main台账 view in `src/views/Knowledge/views/ProjectArchive.vue` utilizing the El-Table, popover, date filters, and Drawer (60% width) for archive details

**Checkpoint**: User Story 1 is fully functional and testable independently.

---

## Phase 4: User Story 2 - 台账与归档文件包一键导出 (Priority: P2)

**Goal**: 支持根据当前检索条件对项目及文件集合进行一键导出，提供双 Sheet Excel 报表或 ZIP 文件压缩包。

**Independent Test**: 使用查询条件过滤台账，点击导出验证 Excel 生成以及 ZIP 包结构 `[项目名]/[文档分类]/原文件名` 和 `_台账.xlsx` 包含。

### Tests for User Story 2

- [ ] T015 [P] [US2] Create integration test in `backend/src/test/java/com/xiyu/bid/casework/application/ArchiveExportServiceTest.java` for Excel sheet metadata matching and ZIP directory structure consistency

### Implementation for User Story 2

- [ ] T016 [US2] Extend Universal Excel exporter `ExcelExportService` in `backend/src/main/java/com/xiyu/bid/export/service/` registering `PROJECT_ARCHIVE` template mapping
- [ ] T017 [US2] Implement ZIP packager in `backend/src/main/java/com/xiyu/bid/casework/application/StreamingZipPackager.java` utilizing `ZipOutputStream` and `StreamingResponseBody`
- [ ] T018 [US2] Add Excel & ZIP download endpoints to `ProjectArchiveController`
- [ ] T019 [P] [US2] Implement "Export Excel" and "Export ZIP" triggers in `src/views/Knowledge/views/ProjectArchive.vue` with API call triggers

**Checkpoint**: User Story 2 functions on top of US1 successfully.

---

## Phase 5: User Story 3 - AI 驱动的应答案例沉淀与一键复用 (Priority: P1)

**Goal**: 项目结项后，后台自动拉取招标文件中的评分项与标书文件调用侧车大模型，清洗匹配并切片写入案例库，前端支持卡片式检索、一键复制复用。

**Independent Test**: 项目结项后检查 `knowledge_case` 表写入。在案例库面板检索案例，点击复用验证剪贴板写入以及 `reuseCount` 递增。

### Tests for User Story 3

- [ ] T020 [P] [US3] Create unit tests in `backend/src/test/java/com/xiyu/bid/casework/domain/CaseExtractionDomainTest.java` verifying chunk segment matching scoring logic

### Implementation for User Story 3

- [ ] T021 [US3] Implement asynchronous extractor task listener `ProjectClosedEventListener` in `backend/src/main/java/com/xiyu/bid/casework/application/` subscribing to project closure events
- [ ] T022 [US3] Implement sidecar HTTP client orchestrator `SidecarCaseExtractor` in `backend/src/main/java/com/xiyu/bid/casework/application/` requesting conversion and LLM section slicing
- [ ] T023 [P] [US3] Implement `KnowledgeCaseRepository` and `KnowledgeCaseController` endpoints
- [ ] T024 [P] [US3] Create frontend grid layout component in `src/views/Knowledge/views/CaseGrid.vue` representing flat cards
- [ ] T025 [US3] Add Copy clipboard copy helper in `src/views/Knowledge/utils/clipboard.js` and bind to "📋 复用" buttons with immediate increment API triggers

**Checkpoint**: AI-driven case repository is fully functional.

---

## Phase 6: User Story 4 - 资质证书管理、AI 回填与到期预警 (Priority: P2)

**Goal**: 行政和投标管理员统一维护公司所有公司级资质证书。上传证书时支持 AI 智能识别有效期并回填，即将到期 3 个月时发送告警消息。

**Independent Test**: 新增证书并上传 PDF，校验表单内容回填。调整到期时间触发扫描任务，验证消息通知。

### Tests for User Story 6

- [ ] T026 [P] [US4] Create unit test in `backend/src/test/java/com/xiyu/bid/qualification/domain/QualificationValidationPolicyTest.java` validating expiry days and status transitions

### Implementation for User Story 6

- [ ] T027 [P] [US4] Expand `QualificationCertificate` domain model and infrastructure tables
- [ ] T028 [US4] Implement Spring Cron Scheduler `QualificationExpiryScanTask` in `backend/src/main/java/com/xiyu/bid/qualification/application/` scanning for upcoming expiration items (90 days window)
- [ ] T029 [US4] Create upload endpoint in `QualificationController` delegating to Sidecar parser for metadata extraction
- [ ] T030 [P] [US4] Create frontend views in `src/views/Knowledge/views/CertificatesList.vue` with upload dialog and automatic JSON binding.

---

## Phase 7: User Story 5 - 敏感证书借阅安全防护与审计日志 (Priority: P2)

**Goal**: 敏感证书附件预览/下载强校验 OA 审批表单状态，全周期操作自动记录只读安全日志并供抽屉内查询。

**Independent Test**: 未授权用户下载资质提示 403。授权用户下载成功且操作日志时间轴增加明细记录。

### Tests for User Story 7

- [ ] T031 [P] [US5] Create controller integration test in `backend/src/test/java/com/xiyu/bid/qualification/controller/QualificationSecurityTest.java` mapping forbidden paths for unapproved OA requests

### Implementation for User Story 7

- [ ] T032 [US5] Implement OA validation handler checking `QualificationBorrowRecord` approvals in application service layer
- [ ] T033 [US5] Create audit logs record models and repositories for `QualificationActionLog` and `ArchiveLog`
- [ ] T034 [P] [US5] Implement audit logger interceptors on preview/download API paths
- [ ] T035 [P] [US5] Show audit log timeline element in details drawers on the frontend side

---

## Phase 8: User Story 6 - 保证金归还跟踪与费用看板 (Priority: P3)

**Goal**: 提供保证金可视化看版卡片和缴纳归还明细表。

**Independent Test**: 看板顶部统计数据与列表项明细和金额完全对应。

### Implementation for User Story 8

- [ ] T036 [P] [US6] Create `DepositTracking` JPA entity, repository, and controller endpoints
- [ ] T037 [P] [US6] Create frontend dashboard layout view in `src/views/Knowledge/views/DepositDashboard.vue` containing HSL gradient cards and El-Table tracker.

---

## Phase 9: Polish & Cross-Cutting Concerns

- [ ] T038 Review and run ArchUnit tests checking package size and FP-Java constraints on all files
- [ ] T039 Execute frontend performance bundle size check and lint verification (`pnpm check:line-budgets` + `npm run build`)
- [ ] T040 Complete quickstart.md checks in dev branch

---

## Dependencies & Execution Order

### Phase Dependencies

1. **Setup (Phase 1)**: Must run first.
2. **Foundational (Phase 2)**: Depends on Phase 1, blocks all subsequent User Stories.
3. **User Stories (Phases 3 to 8)**: Run in parallel after Phase 2 is complete.
4. **Polish (Phase 9)**: Runs after all user stories are complete.

### Parallel Opportunities

- Once Foundational is ready, Developer A can start on **User Story 1** (T007-T014), Developer B on **User Story 3** (T020-T025), and Developer C on **User Story 4** (T026-T030) as they use distinct package structures and tables.
- Frontend components and Backend Controllers can be developed in parallel as endpoints contracts are fully defined in `api.md`.

---

## Implementation Strategy

### MVP First (User Story 1 & 3)

1. Complete Setup and Foundational block tasks.
2. Complete User Story 1 (Project Archive table) and User Story 3 (AI Case matching extraction).
3. Validate basic file archiving and LLM-assisted chunking in live API dev environment.
4. Integrate remainder modules sequentially.
