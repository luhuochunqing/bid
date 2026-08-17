# Tasks: 评分解析与打分的花费守卫

**Input**: Design documents from `/specs/044-score-llm-spend-guard/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/score-spend-guard.md, quickstart.md

**Tests**: 宪法要求 TDD。各故事先写失败测试再改实现。

**Organization**: 按用户故事分阶段。US2/US3/US4 都改 `ScoreScoringAppService.java`，必须串行。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖未完成任务）
- **[Story]**: US1–US4 对应 spec 四条用户故事

## Path Conventions

- 后端：`backend/src/main/java/com/xiyu/bid/scoreparse/`
- 前端：`src/composables/projectDetail/`、`src/views/Project/stages/components/`

---

## Phase 1: Setup

**Purpose**: 对齐契约与现网入口，不新建工程

- [X] T001 通读 `specs/044-score-llm-spend-guard/contracts/score-spend-guard.md` 与现网 `TenderDocumentStoredListener.java` / `ScoreParseAppService.java` / `ScoreScoringAppService.java` / `useScoreParseDrawer.js`，确认策略进 `scoreparse.domain`、不新建业务包

---

## Phase 2: Foundational（任务来源与指纹列）

**Purpose**: 任务能区分 AUTO/MANUAL，打分任务能记下指纹——挡住所有故事

**⚠️ CRITICAL**: 用户故事实现前必须完成

- [X] T002 用 `bash scripts/new-migration.sh` 给 `score_parse_task` 增加可空列 `trigger_source`、`bid_content_hash`、`item_set_hash`、`chapter_hashes`，给 `score_result` 增加可空列 `reuse_kind`；旧行默认 MANUAL / FRESH
- [X] T003 [P] 在 `backend/src/main/java/com/xiyu/bid/scoreparse/entity/ScoreParseTask.java` 增加上述字段
- [X] T004 [P] 在 `backend/src/main/java/com/xiyu/bid/scoreparse/entity/ScoreResult.java` 增加 `reuseKind`
- [X] T005 扩展 `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseTaskStateService.java` 的 `createTask`，写入 `trigger_source`（缺省 MANUAL）

**Checkpoint**: 列与实体就绪，尚未改触发语义

---

## Phase 3: User Story 1 - 自动路径不再偷偷连打 (Priority: P1) 🎯 MVP

**Goal**: 保存招标事件与抽屉静默共用「从未解析且无项才自动新建」；30 分钟内 AUTO 失败 2 次熔断自动路径

**Independent Test**: 已有 PARSE 历史再保存招标不建新任务；窗口内 2 次 AUTO 失败后再 AUTO 被拒，MANUAL 仍可

### Tests for User Story 1

- [X] T006 [P] [US1] 新增 `backend/src/test/java/com/xiyu/bid/scoreparse/domain/AutoParseGateTest.java`：无任务无项允许；有 PARSE 或有项禁止
- [X] T007 [P] [US1] 新增 `backend/src/test/java/com/xiyu/bid/scoreparse/domain/AutoFailCircuitTest.java`：窗口内 2 次 AUTO FAILED 熔断；MANUAL COMPLETED 解除
- [X] T008 [US1] 扩展 `backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreParseAppServiceTest.java`：AUTO + 已有历史不建任务；MANUAL 仍建

### Implementation for User Story 1

- [X] T009 [P] [US1] 新增 `backend/src/main/java/com/xiyu/bid/scoreparse/domain/AutoParseGate.java`（纯函数，<80 行）
- [X] T010 [P] [US1] 新增 `backend/src/main/java/com/xiyu/bid/scoreparse/domain/AutoFailCircuit.java`（纯函数，<80 行）
- [X] T011 [US1] 改 `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseAppService.java`：解析 body/内部 `source`；AUTO 先过门闩与熔断；监听器只走 AUTO
- [X] T012 [US1] 改 `backend/src/main/java/com/xiyu/bid/scoreparse/application/TenderDocumentStoredListener.java`：门闩或熔断不通过则只打日志，不调 `triggerParseFromEvent` 建新任务
- [X] T013 [US1] 改 `src/composables/projectDetail/useScoreParseDrawer.js`：静默 `startParse` 传 `source=AUTO`，「重新解析」传 `MANUAL` 或不传

**Checkpoint**: US1 可单独验收；换招标保存也不自动解析

---

## Phase 4: User Story 2 - 投标文件没变就不要再打 (Priority: P1)

**Goal**: 投标字节哈希 + 清单指纹与上次成功打分相同则 SKIPPED，手点也跳过

**Independent Test**: 同一文件连打两次，第二次不调 assess；换文件则评估

### Tests for User Story 2

- [X] T014 [P] [US2] 新增 `backend/src/test/java/com/xiyu/bid/scoreparse/domain/BidScoreSkipPolicyTest.java`：双哈希相同跳过；任一不同或无成功打分不跳过
- [X] T015 [US2] 扩展 `backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreScoringAppServiceTest.java`（若无则新建）：哈希相同不调用 analyzer；成功打分写入 `bid_content_hash` / `item_set_hash`

### Implementation for User Story 2

- [X] T016 [US2] 新增 `backend/src/main/java/com/xiyu/bid/scoreparse/domain/BidScoreSkipPolicy.java`
- [X] T017 [US2] 改 `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreScoringAppService.java`：触发时算哈希；SKIPPED 不跑 LLM 并提示；成功 FULL 打分落指纹。盯 300 行，超限把算哈希/落库拆同包小类
- [X] T018 [US2] 契约 `GET /items` meta 带 `lastScoringOutcome=SKIPPED` 与 hint；`useScoreParseDrawer.js` 展示「文件未变化」

**Checkpoint**: 手点重新打分且文件未变 → 零 LLM

---

## Phase 5: User Story 3 - 只改一章时只重打相关项 (Priority: P2)

**Goal**: 脏章节只重评相关项；切不出章则 FULL 并说明

**Independent Test**: 只改一章则 FRESH 项数 < 总项数；无标题结构则 FULL

### Tests for User Story 3

- [X] T019 [P] [US3] 新增 `backend/src/test/java/com/xiyu/bid/scoreparse/domain/BidChapterDirtySetTest.java`：一章变化只标该章；无标题则 empty/全量信号
- [X] T020 [P] [US3] 新增 `backend/src/test/java/com/xiyu/bid/scoreparse/domain/ScoreItemChapterMatchTest.java`：quote 含章标题算相关；不确定算相关

### Implementation for User Story 3

- [X] T021 [P] [US3] 新增 `backend/src/main/java/com/xiyu/bid/scoreparse/domain/BidChapterDirtySet.java`
- [X] T022 [P] [US3] 新增 `backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreItemChapterMatch.java`
- [X] T023 [US3] 改 `ScoreScoringAppService.java`：非 SKIPPED 时比章节指纹；相关项 FRESH 重评，其余 REUSED；写下 `chapter_hashes`；meta hint 含重评项数
- [X] T024 [US3] 抽屉结果表能区分本次重评与沿用（读 `reuseKind` / hint）

**Checkpoint**: 改一章不再打全部项

---

## Phase 6: User Story 4 - 人选重打范围 (Priority: P3)

**Goal**: ALL / UNSATISFIED / ITEMS；文件未变仍整表 SKIPPED；无额度

**Independent Test**: 文件有变化时勾选 3 项只更新这 3 项

- [X] T025 [US4] 打分请求 DTO + `ScoreScoringAppService` 接受 `scope`/`itemIds`；缺省 ALL
- [X] T026 [US4] `useScoreParseDrawer.js` 与 `ScoreParseDrawer.vue`：重新打分可选全部 / 仅不满足 / 勾选；提交带 scope
- [X] T027 [US4] Vitest：勾选范围只打这些项；文件未变仍走 SKIPPED

**Checkpoint**: FR-009 / FR-010（无额度）可验收

---

## Phase 7: Polish

- [X] T028 按 `specs/044-score-llm-spend-guard/quickstart.md` 跑所列 mvn/vitest 并记录结果
- [X] T029 确认 `ScoreParseAppService.java`、`ScoreScoringAppService.java`、`useScoreParseDrawer.js` 未破 300 行

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup**: 无依赖
- **Foundational**: 依赖 Setup；**挡住全部 US**
- **US1**: 依赖 Foundational
- **US2**: 依赖 Foundational；可与 US1 并行（不同主文件：listener vs scoring），若两人同时改 AppService 则串行
- **US3**: 依赖 US2（同一 ScoringAppService + 指纹列）
- **US4**: 依赖 US2（跳过优先于范围）；与 US3 同改 ScoringAppService 则接在 US3 后
- **Polish**: 依赖打算交付的故事

### User Story Dependencies

- **US1 (P1)**: 独立于哈希/章节
- **US2 (P1)**: 独立于 US1
- **US3 (P2)**: 接在 US2 之后
- **US4 (P3)**: 接在 US2 之后；与 US3 抢同一文件时跟在 US3 后

### Parallel Opportunities

- T003 与 T004 可并行
- T006/T007、T009/T010 可并行
- T014 与 T019/T020 可并行（不同测试类）
- T021 与 T022 可并行

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. T001 → Phase 2（列）
2. Phase 3 US1
3. **STOP**：已解析项目再保存招标，确认不再自动 PARSE

### Incremental Delivery

1. US1 门闩+熔断 → 消掉事件连打
2. US2 哈希跳过 → 消掉没改文件的全文重打
3. US3 脏章节
4. US4 人选范围（无额度）

---

## Notes

- 不做项目额度 / Token 账本 / 强制重打
- 换招标不自动解析（澄清 A）
- 提交盯 300 行；ScoringAppService 膨胀则把哈希/章节编排拆同包新类
- 每完成一个故事按 quickstart 对应节验证后再往下

---

## Phase 8: Convergence

对照 `spec.md` / `plan.md` / `data-model.md` / `research.md` 与现网代码的缺口。不改既有 T001–T029。

- [X] T030 在 `AutoFailCircuit` / `ScoreParseAutoGuard` 落实「出现一条 MANUAL + COMPLETED 立即解除熔断」：窗口内 AUTO FAILED ≥2 后，若已有更新的 MANUAL COMPLETED，则 `isOpen=false`；补 T007 未覆盖的解除用例。per FR-003, data-model:熔断, research#2, T007 (partial)
- [X] T031 抽屉读取 `meta.circuitOpen` 展示熔断说明（自动路径已停、请检查文件后手点重新解析/打分）；`AUTO_CIRCUIT_OPEN` 对人返回同一句中文，禁止只抛英文码。per FR-004, US1/AC3 (partial)
- [X] T032 `ScoreScoringItemPicker`：已切出章节且脏章非空、但没有任何评分项能对应到脏章时，MUST 退回 FULL 并说明「无法把评分项对应到章节，已全量打分」，不得产出 `INCREMENTAL` 且 `toAssess` 为空。per FR-008, research#4 (contradicts)
- [X] T033 增量 hint 带上脏章标题（如「重评 N 项（第 x、y 章）」），写入任务/meta，使 SC-004 用户能说出本次因哪些章重评。per FR-011, SC-004, US3/AC1 (partial)
- [X] T034 `ScoreItemChapterMatch`：无 quote/evidence 且维度名称对不上时视为不确定，返回相关（重评）；现网 `return false` 与类注释/规格假设相反。改测试 `uncertainDefaultsToRelated` 第一断言。per spec Assumptions「不确定则宁可重评」, T020 (contradicts)
- [X] T035 AUTO 打分遇到进行中任务时跟随已有任务（返回现任务 id/status），与解析 AUTO 一致，不得另开并行、也不要用 409 挡自动跟随。MANUAL 打分互斥语义保持现网。per FR-002 (partial)
- [X] T036 把 `BidChapterDirtySet.java`（117）和 `ScoreParseItemsMetaBuilder.java`（118）压到默认 <100 行（硬上限仍 300）。per Constitution IV, plan: Split-First (partial)
