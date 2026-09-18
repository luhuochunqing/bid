# Tasks: 招标文件大文件解析放行

**Input**: Design documents from `/specs/065-tender-large-file-parse/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: 单测任务已包含——Constitution III（TDD, NON-NEGOTIABLE）显式要求 Red→Green→Refactor，先测试后实现。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Web app**: `backend/src/`, `src/`（前端）

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 配置项与新语义异常——US1/US2/US3 共同前置

- [X] T001 [P] 新增 `app.score-parse.tender-file.*` 三配置键（max-file-bytes/max-text-chars/ram-buffer-bytes）在 `backend/src/main/resources/application.yml`
- [X] T002 [P] 新建招标侧文本量超限异常 `OversizedTenderTextException.java` 在 `backend/src/main/java/com/xiyu/bid/scoreparse/application/OversizedTenderTextException.java`（纯异常 + MESSAGE，FP-Java 无框架依赖，~18 行）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: AppService 行预算拆分——不完成则 US1 任何增量都破 300 行硬上限

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T003 行预算复核结论：`ScoreParseAppService.java` 保持 295 行零增量（两处失败出口本就透传 `emptyReason`，免拆分；已验证）在 `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseAppService.java`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - 大文件招标文件正常解析 (Priority: P1) 🎯 MVP

**Goal**: OBS 上传的 >50MB（文本量正常）招标文件可解析成功，不再报 50MB 字节超限；≤50MB 快路径行为不变

**Independent Test**: 80MB 扫描版 PDF 经 OBS 上传后解析成功，评分项清单与 50MB 压缩版项数/总权重一致；无 OOM（见 quickstart V1/V4）

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T004 [US1] 单测：50MB 内存快路径通过用例在 `backend/src/test/java/com/xiyu/bid/scoreparse/application/InitiationTenderTextResolverTest.java`
- [X] T005 [US1] 单测：文本量超限失败 + `emptyReason` 新文案用例在 `backend/src/test/java/com/xiyu/bid/scoreparse/application/InitiationTenderTextResolverTest.java`（depends on T004，同文件串行）
- [X] T006 [US1] 单测：OBS 大文件 spill 落盘路径用例（mock fetcher/extractor）在 `backend/src/test/java/com/xiyu/bid/scoreparse/application/InitiationTenderTextResolverTest.java`（depends on T005，同文件串行）
- [X] T007 [P] [US1] 单测：文本超限 `failTask` 文案透传用例在 `backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreParseAppServiceTest.java`

### Implementation for User Story 1

- [X] T008 [US1] `BoundedHttpDownloader` 改自适应 spill + 配额预检在 `backend/src/main/java/com/xiyu/bid/scoreparse/application/BoundedHttpDownloader.java`（复用 `StreamingBidFileFetcher` 骨架，+40 行内）→ 落地变更：该文件零改动，OBS 下载改走既有 `StreamingBidFileFetcher`（见 T009 内 `loadTenderFile`），`BoundedHttpDownloader` 保留现栋
- [X] T009 [US1] `InitiationTenderTextResolver.loadBytes` 改二态分派 + 文本量口径在 `backend/src/main/java/com/xiyu/bid/scoreparse/application/InitiationTenderTextResolver.java`（改后 ≤260 行；depends on T008 接口形态）→ 落地：`loadBytes/capSize/fetchSigned` 改为 `loadTenderFile/releaseTenderFile/extractLoadedText`，216 行；另新增 `StructuredTableResult.java`（`Result` 记录外移）+ `StructuredTableExtractionHandler` 二态降级（281 行）
- [X] T010 [US1] `ScoreParseAppService` 透传新文案 + 行预算复核（≤300 行）在 `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseAppService.java`（depends on T009）→ 落地：零改动，透传已存在，295 行保持

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently（`mvn test -Dtest=InitiationTenderTextResolverTest,ScoreParseAppServiceTest` 全绿 + quickstart V1）

---

## Phase 4: User Story 2 - 极端超大文本明确拒绝 (Priority: P2)

**Goal**: 文本量超限文档被明确拒绝（含上限值与建议动作），不落库残缺结果、不覆盖已有可用评分项

**Independent Test**: 超限文档触发解析 → 任务 FAILED + 文本量文案；已有评分项保持不变（见 quickstart V2）

### Tests for User Story 2 ⚠️

- [X] T011 [US2] 单测：文本超限失败不覆盖已有 `score_item` 用例在 `backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreParseAppServiceTest.java`（depends on Phase 3 US1 行为）

### Implementation for User Story 2

- [X] T012 [US2] 核验 `AUTO_APPROVED` 方可覆盖纪律对新失败路径生效（无新增代码则记验证结论）在 `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseAppService.java`（depends on T011；若需补 guard 则增量实现 + 行预算复核）→ 落地：无新增代码，`failMessage` 分支在 persist 之前返回，T011 已覆盖

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - 超限指引可操作 (Priority: P3)

**Goal**: 上传前明示限制与解析口径；解析失败态给出下一步动作；按钮文案统一

**Independent Test**: 走查上传区 tip、上传拦截提示、抽屉失败态三处文案（见 quickstart V3 + contracts/parse-error-semantics.md）

### Tests for User Story 3 ⚠️

- [X] T013 [P] [US3] 单测：上传超配额前置拦截用例在 `src/views/Project/stages/InitiationStage.spec.js`
- [X] T014 [P] [US3] 单测：抽屉失败态 error 透传用例在 `src/composables/projectDetail/useScoreParseDrawer.spec.js`

### Implementation for User Story 3

- [X] T015 [P] [US3] 上传区 tip 文案 + `beforeUploadDoc` 配额前置拦在 `src/views/Project/stages/InitiationStage.vue` 与 `src/views/Project/stages/useInitiationStageActions.js`
- [X] T016 [P] [US3] 失败态次行动作文案 + 按钮 `重试`→`重新解析`在 `src/views/Project/stages/components/ScoreParseDrawer.vue`

**Checkpoint**: All user stories should now be independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 门禁、验证与知识沉淀

- [X] T017 [P] 行预算与架构门禁：`ScoreParseAppService.java`/`InitiationTenderTextResolver.java` ≤300 行 + `mvn test -Dtest=ArchitectureTest` 全绿
- [ ] T018 [P] 跑通 quickstart.md V1~V4（含 80MB/压缩版一致性比对）
- [ ] T019 执行 `speckit-analyze` 一致性验证（spec/plan/tasks 交叉检查，按 PLANS 门禁第 5 步）
- [ ] T020 知识沉淀：`knowledge-capture` 回填 `docs/lessons/lessons-learned.md` + `.wiki` checkpoint（收尾纪律）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - US1 (P1) → MVP 主路径；US2 (P2) depends on US1 行为；US3 (P3) 仅依赖 contracts 文案定稿，可与 US1 并行
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - Depends on US1 超限行为实现，但可独立测试
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) - 仅依赖 contracts 文案，可与 US1 并行

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- T004→T005→T006 同文件串行；T007 独立文件可与 T004 并行
- T008→T009→T010 实现链串行；T010 含行预算复核门
- Story complete before moving to next priority

### Parallel Opportunities

- T001 与 T002：不同文件，可并行
- T004（resolver 测试）与 T007（appservice 测试）：不同文件，可并行
- US1（后端）与 US3（前端）：不同栈，可并行（US3 实现 T015/T016 不同文件可并行；测试 T013/T014 不同文件可并行）
- T017 与 T018：门禁与验证可并行

---

## Parallel Example: User Story 1

```bash
# 同一故事内可并行的测试（不同文件）：
Task: "T004 单测 50MB 快路径 in InitiationTenderTextResolverTest.java"
Task: "T007 单测 failTask 透传 in ScoreParseAppServiceTest.java"

# US1 与 US3 跨栈并行示例：
Task: "T008 BoundedHttpDownloader spill 实现"
Task: "T015 InitiationStage tip + beforeUploadDoc 配额拦"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup（T001–T002）
2. Complete Phase 2: Foundational（T003，行预算拆分）
3. Complete Phase 3: User Story 1（T004–T010，先测后实现）
4. **STOP and VALIDATE**: `mvn test -Dtest=InitiationTenderTextResolverTest,ScoreParseAppServiceTest` + quickstart V1
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo
4. Add User Story 3 → Test independently → Deploy/Demo
5. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1（后端流式+口径）
   - Developer B: User Story 3（前端文案+拦截，与 US1 跨栈并行）
3. US2 跟随 US1 完成后收尾；Polish 全员门禁

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
- 本特性无 Flyway 迁移（不改表）；`BidFileQuotaException`/`StreamingBidFileFetcher`/`TempBidFileStore`/`SidecarStreamTextExtractor` 均为复用，不在任务内
- Phase 2（TypeSafe 附录 A）不在本 tasks 范围，另立 spec
