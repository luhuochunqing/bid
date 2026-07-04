# Tasks: AI 案例切片语义检索

**Input**: Design documents from `/specs/028-ai-case-slice-semantic-search/`

**Prerequisites**: plan.md (required), spec.md (required), data-model.md, contracts/api.md, research.md, quickstart.md

**Tests**: TDD 已启用；每个纯核心类与应用服务必须先写测试（RED → GREEN → REFACTOR）。

**Organization**: Tasks grouped by user story to enable independent implementation and testing.

**Format**: `[ID] [P?] [Story] Description with exact file path`

- **[P]**: Can run in parallel
- **[Story]**: US1 / US2 / US3

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Reserve migration version, acquire locks, and prepare cross-cutting code conventions.

- [ ] T001 Reserve Flyway version `V1135` via `bash scripts/next-migration-version.sh --reserve` and create empty migration + rollback files
- [ ] T002 Acquire agent locks for hot paths: `db/migration-mysql/`, `com.xiyu.bid.ai.client`, `com.xiyu.bid.casework`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Database schema, entity, repository, and in-memory vector cache MUST be ready before any user story.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T003 Create Flyway migration `backend/src/main/resources/db/migration-mysql/V1135__create_bid_case_slice.sql` matching `data-model.md`
- [ ] T004 Create Flyway rollback `backend/src/main/resources/db/rollback/migration-mysql/U1135__create_bid_case_slice.sql`
- [ ] T005 [P] Create JPA entity `backend/src/main/java/com/xiyu/bid/casework/infrastructure/BidCaseSlice.java`
- [ ] T006 [P] Create repository `backend/src/main/java/com/xiyu/bid/casework/infrastructure/BidCaseSliceRepository.java`
- [ ] T007 [P] Create vector codec `backend/src/main/java/com/xiyu/bid/casework/infrastructure/EmbeddingVectorCodec.java` (float[] ↔ byte[])
- [ ] T008 Create in-memory vector cache `backend/src/main/java/com/xiyu/bid/casework/infrastructure/BidCaseSliceVectorCache.java`
- [ ] T009 [P] Write unit test `backend/src/test/java/com/xiyu/bid/casework/infrastructure/BidCaseSliceVectorCacheTest.java`
- [ ] T010 [P] Write unit test `backend/src/test/java/com/xiyu/bid/casework/infrastructure/EmbeddingVectorCodecTest.java`

**Checkpoint**: `mvn test -Dtest=BidCaseSliceVectorCacheTest,EmbeddingVectorCodecTest` passes; migration can be applied by Flyway.

---

## Phase 3: User Story 1 - 评分项智能推荐相似应答 (Priority: P1) 🎯 MVP

**Goal**: Provide `GET /api/case-slices/recommend?projectId=&scoringItemId=` returning Top-20 historical answer slices ranked by semantic + business-score.

**Independent Test**: Pick a real `ProjectScoreDraft`, call the endpoint, and verify the response contains semantically relevant slices.

### Tests for User Story 1

> Write these tests FIRST and ensure they FAIL before implementation.

- [ ] T011 [P] [US1] Write unit test `backend/src/test/java/com/xiyu/bid/casework/domain/policy/CosineSimilarityPolicyTest.java`
- [ ] T012 [P] [US1] Write unit test `backend/src/test/java/com/xiyu/bid/casework/domain/policy/BidCaseSliceMatchPolicyTest.java`
- [ ] T013 [P] [US1] Write unit test `backend/src/test/java/com/xiyu/bid/casework/application/service/BidCaseSliceRecommendAppServiceTest.java`
- [ ] T014 [P] [US1] Write contract test `backend/src/test/java/com/xiyu/bid/casework/controller/BidCaseSliceControllerTest.java` for `/api/case-slices/recommend`

### Implementation for User Story 1

- [ ] T015 [P] [US1] Create domain input record `backend/src/main/java/com/xiyu/bid/casework/domain/model/BidCaseSliceMatchCriteria.java`
- [ ] T016 [P] [US1] Create domain output record `backend/src/main/java/com/xiyu/bid/casework/domain/model/BidCaseSliceRecommendation.java`
- [ ] T017 [US1] Create pure core `backend/src/main/java/com/xiyu/bid/casework/domain/policy/CosineSimilarityPolicy.java`
- [ ] T018 [US1] Create pure core `backend/src/main/java/com/xiyu/bid/casework/domain/policy/BidCaseSliceMatchPolicy.java`
- [ ] T019 [US1] Create assembler `backend/src/main/java/com/xiyu/bid/casework/application/BidCaseSliceRecommendationAssembler.java`
- [ ] T020 [US1] Create application service `backend/src/main/java/com/xiyu/bid/casework/application/service/BidCaseSliceRecommendAppService.java`
- [ ] T021 [US1] Create controller `backend/src/main/java/com/xiyu/bid/casework/controller/BidCaseSliceController.java` with `/api/case-slices/recommend`

**Checkpoint**: `mvn test -Dtest=BidCaseSlice*Test` passes; calling `/api/case-slices/recommend` with a valid scoring item returns ranked results.

---

## Phase 4: User Story 2 - 历史切片批量入库与向量化 (Priority: P1)

**Goal**: Import 8144 slices from JSONL and generate embeddings in batches with resume/retry/rate-limit.

**Independent Test**: Run import CLI and verify 8144 rows in `bid_case_slice`; run batch embed and verify ≥90% have non-null `embedding`.

### Tests for User Story 2

- [ ] T022 [P] [US2] Write unit test `backend/src/test/java/com/xiyu/bid/ai/client/QwenEmbeddingClientTest.java`
- [ ] T023 [P] [US2] Write unit test `backend/src/test/java/com/xiyu/bid/ai/client/OpenAiCompatibleClientEmbeddingTest.java`
- [ ] T024 [P] [US2] Write unit test `backend/src/test/java/com/xiyu/bid/casework/application/service/BatchEmbeddingServiceTest.java`
- [ ] T025 [P] [US2] Write unit test `backend/src/test/java/com/xiyu/bid/casework/application/CaseSliceJsonlImporterTest.java`

### Implementation for User Story 2

- [ ] T026 [US2] Extend `backend/src/main/java/com/xiyu/bid/ai/client/AiProvider.java` with `embed(String text)`
- [ ] T027 [US2] Extend `backend/src/main/java/com/xiyu/bid/ai/client/AiProviderRuntimeConfig.java` with `embeddingBaseUrl` and `embeddingModel`
- [ ] T028 [US2] Add embedding method to `backend/src/main/java/com/xiyu/bid/ai/client/OpenAiCompatibleClient.java`
- [ ] T029 [US2] Create `backend/src/main/java/com/xiyu/bid/ai/client/EmbeddingClient.java` interface (mirror for non-AiProvider callers if needed)
- [ ] T030 [US2] Create `backend/src/main/java/com/xiyu/bid/ai/client/QwenEmbeddingClient.java`
- [ ] T031 [US2] Create `backend/src/main/java/com/xiyu/bid/ai/client/NoopEmbeddingClient.java`
- [ ] T032 [US2] Update `backend/src/main/java/com/xiyu/bid/ai/client/RoutingAiProvider.java` to route `embed()` calls
- [ ] T033 [US2] Create `backend/src/main/java/com/xiyu/bid/casework/application/CaseSliceJsonlImporter.java` (JSONL → `BidCaseSlice`)
- [ ] T034 [US2] Create application service `backend/src/main/java/com/xiyu/bid/casework/application/service/BatchEmbeddingService.java`
- [ ] T035 [US2] Create CLI runner `backend/src/main/java/com/xiyu/bid/bootstrap/CaseSliceImportCommandLineRunner.java`
- [ ] T036 [US2] Add admin endpoint `POST /api/case-slices/admin/batch-embed` in `BidCaseSliceController.java`

**Checkpoint**: `mvn test -Dtest=QwenEmbeddingClientTest,OpenAiCompatibleClientEmbeddingTest,BatchEmbeddingServiceTest,CaseSliceJsonlImporterTest` passes; CLI runner can import and batch-embed a sample JSONL.

---

## Phase 5: User Story 3 - 按评分项维度触发推荐 (Priority: P2)

**Goal**: Expose `GET /api/case-slices/recommend/by-query?query=` for ad-hoc semantic search without binding to a project scoring item.

**Independent Test**: Call the endpoint with query text and verify semantic relevance.

### Tests for User Story 3

- [ ] T037 [P] [US3] Add contract test for `/api/case-slices/recommend/by-query` in `BidCaseSliceControllerTest.java`

### Implementation for User Story 3

- [ ] T038 [US3] Add query-text recommendation method to `BidCaseSliceRecommendAppService.java`
- [ ] T039 [US3] Add `GET /api/case-slices/recommend/by-query` endpoint to `BidCaseSliceController.java`

**Checkpoint**: `mvn test -Dtest=BidCaseSliceControllerTest` passes; ad-hoc query returns results.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Architecture gates, integration verification, and documentation.

- [ ] T040 [P] Create ArchUnit test `backend/src/test/java/com/xiyu/bid/architecture/BidCaseSliceArchitectureTest.java`
- [ ] T041 [P] Run `mvn test -Dtest=ArchitectureTest` and fix any new violations
- [ ] T042 [P] Run `mvn test -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest`
- [ ] T043 Run backend full test suite `cd backend && mvn test`
- [ ] T044 Run integration verification: import 8144 slices → batch embed → call recommend endpoints
- [ ] T045 [P] Update `specs/028-ai-case-slice-semantic-search/quickstart.md` with final commands and verification results
- [ ] T046 Release agent locks via `npm run agent:lock-release -- --path <path>` for all acquired locks

**Checkpoint**: All tests green, integration checklist in `quickstart.md` completed, branch ready for PR.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies; must reserve migration version first.
- **Foundational (Phase 2)**: Depends on Setup. Blocks all user stories.
- **User Stories (Phase 3+)**: All depend on Foundational phase.
  - US1 and US2 can proceed in parallel once Foundational is done (US2 embeddings are required for US1 integration, but contracts/tests can be developed in parallel).
  - US3 depends on US1 recommendation machinery.
- **Polish (Final Phase)**: Depends on all user stories.

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational; no dependency on US2 for code/tests.
- **User Story 2 (P1)**: Can start after Foundational; no dependency on US1 for code/tests.
- **User Story 3 (P2)**: Depends on US1 recommendation service and controller.

### Within Each User Story

- Tests MUST be written and FAIL before implementation.
- Domain models before policies.
- Policies before application services.
- Application services before controllers.
- Assembler/DTO conversion in application layer, not in entities.

---

## Parallel Execution Examples

### User Story 1 parallel burst

```bash
# Tests first (parallel):
Task: "Write CosineSimilarityPolicyTest"
Task: "Write BidCaseSliceMatchPolicyTest"
Task: "Write BidCaseSliceRecommendAppServiceTest"
Task: "Write BidCaseSliceControllerTest for /recommend"

# Then implementation (parallel where no dependency):
Task: "Create BidCaseSliceMatchCriteria / BidCaseSliceRecommendation records"
Task: "Create CosineSimilarityPolicy"
Task: "Create BidCaseSliceMatchPolicy"
Task: "Create BidCaseSliceRecommendationAssembler"
Task: "Create BidCaseSliceRecommendAppService"
Task: "Create BidCaseSliceController"
```

### User Story 2 parallel burst

```bash
# Tests first (parallel):
Task: "Write QwenEmbeddingClientTest"
Task: "Write OpenAiCompatibleClientEmbeddingTest"
Task: "Write BatchEmbeddingServiceTest"
Task: "Write CaseSliceJsonlImporterTest"

# Then implementation (parallel where no dependency):
Task: "Extend AiProvider + AiProviderRuntimeConfig"
Task: "Add embedding to OpenAiCompatibleClient"
Task: "Create QwenEmbeddingClient / NoopEmbeddingClient"
Task: "Update RoutingAiProvider"
Task: "Create CaseSliceJsonlImporter"
Task: "Create BatchEmbeddingService"
Task: "Create CaseSliceImportCommandLineRunner"
Task: "Add admin batch-embed endpoint"
```

---

## Implementation Strategy

### MVP First (US1 + Foundational)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (database, entity, cache).
3. Complete Phase 3: User Story 1 (recommend by scoring item).
4. **STOP and VALIDATE**: manually test `/api/case-slices/recommend` with seeded slices.

### Incremental Delivery

1. After MVP validation, complete Phase 4: User Story 2 (import + batch embedding).
2. Run integration verification with real 8144 slices.
3. Complete Phase 5: User Story 3 (ad-hoc query).
4. Complete Phase 6: Polish, ArchUnit, documentation, release locks.

---

## Task Count Summary

- Total tasks: 46
- Setup: 2
- Foundational: 8
- User Story 1: 11 (4 tests + 7 implementation)
- User Story 2: 15 (4 tests + 11 implementation)
- User Story 3: 3 (1 test + 2 implementation)
- Polish: 7
