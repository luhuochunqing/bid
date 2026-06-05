# Tasks: 项目档案时间戳补全

**Input**: Design documents from `/specs/016-project-archive-stage-timestamps/`

**Prerequisites**: plan.md (✅), spec.md (✅), research.md (✅), data-model.md (✅), contracts/archive-detail.md (✅)

**Feature**: 为 Project 实体新增 `initiatedAt`、`evaluatingAt`、`closedAt` 三个阶段时间戳字段，ProjectStageService 状态转换时自动填充，ProjectArchiveDetailService 改为从 Project 实体读取并返回给前端。前端 ArchiveDetailDrawer.vue 无需改造（已正确引用 DTO 字段）。

---

## Phase 1: Foundational (Database Migration + Entity)

**Purpose**: 数据库迁移脚本 + Project 实体字段新增。所有用户故事依赖此阶段的完成。

**⚠️ CRITICAL**: Phase 2~5 均依赖 Phase 1 完成。

- [ ] T001 Reserve migration version via `scripts/next-migration-version.sh --reserve` (confirm next version is V1044)
- [ ] T002 [P] Create V1044__project_stage_timestamps.sql in backend/src/main/resources/db/migration-mysql/ with:
  - `ALTER TABLE projects ADD COLUMN initiated_at DATETIME NULL`
  - `ALTER TABLE projects ADD COLUMN evaluating_at DATETIME NULL`
  - `ALTER TABLE projects ADD COLUMN closed_at DATETIME NULL`
  - `UPDATE projects SET initiated_at = created_at WHERE initiated_at IS NULL` (only initiated_at is backfilled; evaluating_at and closed_at remain NULL for existing records — no historical data available)
- [ ] T003 [P] Create U1044__project_stage_timestamps.sql in backend/src/main/resources/db/rollback/migration-mysql/ with:
  - `ALTER TABLE projects DROP COLUMN IF EXISTS initiated_at`
  - `ALTER TABLE projects DROP COLUMN IF EXISTS evaluating_at`
  - `ALTER TABLE projects DROP COLUMN IF EXISTS closed_at`
- [ ] T004 Add 3 LocalDateTime fields to Project.java in backend/src/main/java/com/xiyu/bid/entity/Project.java:
  - `private LocalDateTime initiatedAt;`
  - `private LocalDateTime evaluatingAt;`
  - `private LocalDateTime closedAt;`
  - Add `@Column(name = "initiated_at")`, `@Column(name = "evaluating_at")`, `@Column(name = "closed_at")` annotations
- [ ] T005 Run backend migration dry-run to verify V1044 SQL syntax: `cd backend && ./mvnw flyway:dry-run -Dflyway.url="jdbc:mysql://localhost:3306/xiyu_bid_cursor" -Dflyway.user=root -Dflyway.password=XiyuDB\!2026` (or via dev-services: `npm run dev:flyway:dryrun` from repo root)
- [ ] T005b [P] Add migration integration test in backend/src/test/java/com/xiyu/bid/db/ProjectStageTimestampsMigrationTest.java:
  - Test: V1044 migration adds all 3 columns without error on a clean test DB
  - Test: the UPDATE statement sets `initiated_at = created_at` for all existing rows
  - Test: after migration, existing projects have `initiated_at` populated but `evaluating_at` and `closed_at` remain NULL
  - Use `@FlywayTest` annotation or `@Sql` to run V1044 before the test
  - Verify column types and nullability match the entity definition

**Checkpoint**: Migration dry-run passes → Project entity has 3 new fields → user stories can begin

---

## Phase 2: User Story 1 - 状态变更自动记录时间节点 (Priority: P1)

**Goal**: ProjectStageService.requestTransition 在推进至 EVALUATING/CLOSED 阶段时自动填充 evaluatingAt/closedAt；ProjectService.createProject 在首次创建时填充 initiatedAt。

**Independent Test**: JUnit 单元测试模拟 ProjectStageService.requestTransition，验证 Project 实体时间戳字段被正确赋值。

### Tests

- [ ] T006 [P] [US1] Add unit test in backend/src/test/java/com/xiyu/bid/project/service/ProjectStageServiceTest.java:
  - Test: transitioning to EVALUATING sets evaluatingAt when null
  - Test: transitioning to CLOSED sets closedAt when null
  - Test: re-entering EVALUATING does NOT overwrite existing evaluatingAt (first-write wins)
  - Test: re-entering CLOSED does NOT overwrite existing closedAt (first-write wins)
  - **Test: failed state transition (invalid transition path) does NOT set any timestamp** (transaction rollback negative test — verify timestamps are not modified when the transition is rejected or throws)
  - **Test: state transition with existing null timestamps is atomic** (verify that if the DB write fails mid-transition, no timestamp is persisted)
- [ ] T007 [P] [US1] Add unit test in backend/src/test/java/com/xiyu/bid/project/service/ProjectServiceTest.java:
  - Test: createProject sets initiatedAt when null
  - Test: createProject does NOT overwrite existing initiatedAt

### Implementation

- [ ] T008 [P] [US1] Modify ProjectStageService.java in backend/src/main/java/com/xiyu/bid/project/service/:
  - In requestTransition, after stage decision succeeds and before/after `project.setStage(target)`:
    - `if (target == ProjectStage.EVALUATING && project.getEvaluatingAt() == null) project.setEvaluatingAt(LocalDateTime.now());`
    - `if (target == ProjectStage.CLOSED && project.getClosedAt() == null) project.setClosedAt(LocalDateTime.now());`
  - Ensure these side-effects are in the Application Service layer (Shell), not in pure core
- [ ] T009 [P] [US1] Modify ProjectService.java in backend/src/main/java/com/xiyu/bid/project/service/:
  - In createProject or saveProject method, after Project is first persisted:
    - `if (project.getInitiatedAt() == null) project.setInitiatedAt(LocalDateTime.now());`

**Checkpoint**: ProjectStageService transitions populate timestamps → ProjectService creation populates initiatedAt → mvn test -Dtest=ProjectStageServiceTest,ProjectServiceTest passes

---

## Phase 3: User Story 2 - 查看档案详情中的真实时间节点 (Priority: P1)

**Goal**: ProjectArchiveDetailService 从 Project 实体读取 initiatedAt/evaluatingAt/closedAt 并映射至响应 DTO 的 initiatedAt/bidSubmissionAt/closedAt，不再从 Tender 降级获取。

**Independent Test**: Integration test 或直接 API 验证 GET /api/archive/{id} 响应中时间戳字段非空。

### Implementation

- [ ] T010 [US2] Modify ProjectArchiveDetailService.java in backend/src/main/java/com/xiyu/bid/casework/application/:
  - Read `Project.project.initiatedAt` → map to `response.initiatedAt`
  - Read `Project.project.evaluatingAt` → map to `response.bidSubmissionAt`
  - Read `Project.project.closedAt` → map to `response.closedAt`
  - Remove old Tender-based fallback logic for these 3 fields
  - Keep existing fallback: `initiatedAt` falls back to `Project.createdAt` if null (for projects without tender)
  - Keep existing `bidOpeningAt` from `Tender.bidOpeningTime` (unchanged)

### Tests

- [ ] T011 [P] [US2] Add integration test in backend/src/test/java/com/xiyu/bid/casework/application/ProjectArchiveDetailServiceTest.java:
  - Test: project with all 3 timestamps set → archive detail returns those exact values
  - Test: project with null timestamps → archive detail returns null for those fields
  - Test: project with only initiatedAt → archive detail returns initiatedAt, others null

**Checkpoint**: GET /api/archive/{id} returns real timestamps from Project entity → Frontend renders correctly (no change needed in ArchiveDetailDrawer.vue)

---

## Phase 4: User Story 3 - 批量导入历史档案时间线 (Priority: P2)

**Goal**: 历史档案导入接口支持在创建 Project 记录时直接指定 initiatedAt/evaluatingAt/closedAt 时间戳值，不被系统自动覆盖。

**Independent Test**: 后端导入 API 批量导入测试数据集，验证数据库中各时间戳字段按导入值填充。

### Implementation

- [ ] T012 [US3] Create historical import endpoint:
  - **No existing endpoint found** — must create new endpoint
  - Add `POST /api/projects/import` in ProjectController.java
  - Request body: `ProjectImportRequest.java` (see T013)
  - Delegate to `ProjectService.importProject(ProjectImportRequest)`
  - **CRITICAL**: The service method MUST accept optional `initiatedAt`/`evaluatingAt`/`closedAt` and pass them through without auto-filling from `LocalDateTime.now()` (only auto-fill when the field is null)
  - The endpoint is idempotent: importing the same data twice creates duplicate projects (no upsert)
  - Security: requires ADMIN or BID_ADMIN role
- [ ] T013 [P] [US3] Add import DTO if not exists in backend/src/main/java/com/xiyu/bid/project/dto/:
  - `ProjectImportRequest.java` with optional `LocalDateTime initiatedAt`, `evaluatingAt`, `closedAt` fields
  - Or extend existing create DTO with nullable timestamp fields
- [ ] T014 [US3] If new endpoint is needed, add REST endpoint in ProjectController.java:
  - `POST /api/projects/import` accepting ProjectImportRequest with timestamps
  - Delegate to ProjectService.importProject() / createProject()

### Tests

- [ ] T015 [P] [US3] Add unit test for historical import:
  - Test: import with custom initiatedAt/evaluatingAt/closedAt → Project entity has those exact values
  - Test: import with partial timestamps → only provided fields are set, others remain null
  - Test: import with future timestamps → system accepts without validation (respect historical data)

**Checkpoint**: Historical projects can be imported with original timeline → Archive detail shows imported timestamps

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: 验证、文档更新、E2E 测试。

- [ ] T016 [P] Run full backend test suite: `cd backend && ./mvnw test -Dtest=ProjectStageServiceTest,ProjectServiceTest,ProjectArchiveDetailServiceTest`
- [ ] T017 [P] Run frontend build to confirm no regressions: `npm run build` from repo root
- [ ] T018 [P] Enhance E2E smoke test in e2e/knowledge-project-archive-flow.spec.js:
  - **Step 1**: Login as lizong (admin), create a new project or navigate to an existing project
  - **Step 2**: Advance the project through stages: INITIATED → DRAFTING → EVALUATING (use the stage advance action in the project detail page)
  - **Step 3**: Navigate to archive detail view (Knowledge → Project Archive → find the project → open detail)
  - **Step 4**: Assert `initiatedAt` is NOT null and matches YYYY-MM-DD HH:mm format
  - **Step 5**: Assert `bidSubmissionAt` is NOT null (project progressed through EVALUATING) and matches YYYY-MM-DD HH:mm format
  - **Step 6**: Assert `closedAt` is "-" (not yet reached CLOSED stage)
  - **Step 7**: Advance project to CLOSED stage
  - **Step 8**: Re-open archive detail, assert `closedAt` is NOT null and matches YYYY-MM-DD HH:mm format
  - This validates the full end-to-end flow: state transition → timestamp capture → archive detail display
- [ ] T019 [P] Update CLAUDE.md or implementation-notes.md to document new Project entity fields if needed

**Checkpoint**: All tests pass → Feature is complete

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (Migration + Entity)
        │
        ▼
Phase 2 (US1: Stage timestamps) ──┐
Phase 3 (US2: Archive detail)      │── Can proceed in parallel
Phase 4 (US3: Historical import) ───┘
        │
        ▼
Phase 5 (Polish + E2E)
```

### User Story Dependencies

| Story | Depends On | Reason |
|-------|-----------|--------|
| US1 (Stage timestamps) | Phase 1 | Cannot write to fields that don't exist |
| US2 (Archive detail) | Phase 1 | Cannot read fields that don't exist |
| US3 (Historical import) | Phase 1 | Cannot import into fields that don't exist |

### Within Each User Story

- Phase 2 (US1): T006–T007 (tests) → T008–T009 (implementation)
- Phase 3 (US2): T010 (implementation) → T011 (tests)
- Phase 4 (US3): T012–T013 (implementation) → T014–T015 (tests)
- Phase 5 (Polish): T005b (migration integration test) ∥ T016 (backend tests) ∥ T017 (frontend build) ∥ T018 (E2E smoke)

---

## Parallel Execution Opportunities

### Within-Phase Parallel (different files, no shared work)

```
Phase 1:
  Task T002 (V1044 migration) || Task T003 (U1044 rollback) || Task T005 (dry-run) || Task T005b (migration integration test)

Phase 2:
  Task T006 (ProjectStageService unit tests) || Task T007 (ProjectService unit tests)
  Task T008 (ProjectStageService.java) || Task T009 (ProjectService.java)

Phase 4:
  Task T012 (import endpoint) || Task T013 (import DTO)

Phase 5:
  Task T016 (backend tests) || Task T017 (frontend build) || Task T018 (E2E smoke)
```

---

## Implementation Strategy

### MVP First (US1 + US2 — Priority P1)

1. Complete Phase 1: Migration + Entity fields
2. Complete Phase 2: US1 (stage timestamps in ProjectStageService + ProjectService)
3. Complete Phase 3: US2 (ProjectArchiveDetailService reads from Project)
4. **STOP and VALIDATE**: Archive detail API returns real timestamps → MVP complete

### Incremental Delivery

1. Phase 1 → Foundation ready (3 new columns in DB + entity)
2. Phase 2 + Phase 3 → MVP: Real timestamps in archive detail
3. Phase 4 → US3: Historical import support
4. Phase 5 → Polish: E2E smoke test, documentation

### Parallel Team Strategy

| Developer | Tasks |
|-----------|-------|
| Dev A | Phase 1 (Migration + Entity) |
| Dev B | Phase 2 (US1: Stage service timestamps) |
| Dev C | Phase 3 (US2: Archive detail service) |
| Dev A/B | Phase 4 (US3: Historical import) |
| Dev C | Phase 5 (Polish + E2E) |

---

## Notes

- **[P]** tasks = different files, no dependencies on each other — safe to parallelize
- **No Setup phase** needed: feature builds on existing codebase
- **No frontend code changes** required: ArchiveDetailDrawer.vue already correctly references DTO fields and uses formatDateTime()
- **Migration version**: V1044 (next sequential version after V1043)
- **First-write-wins**: All timestamp fields use `if (field == null)` guard — import values take precedence, auto-fill only when null
- **ArchUnit compliance**: Timestamp population in ProjectStageService is Shell/Application Service — no pure core changes needed
