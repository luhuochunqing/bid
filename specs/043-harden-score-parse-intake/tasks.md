# Tasks: 评分解析生产风险收口

**Input**: Design documents from `/specs/043-harden-score-parse-intake/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/score-parse-auto-trigger.md, quickstart.md

**Tests**: 宪法要求 TDD。各故事先写失败测试再改实现。

**Organization**: 按用户故事分阶段；US2/US3 都改 `InitiationTenderTextResolver.java`，必须串行。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、不依赖未完成任务）
- **[Story]**: US1–US4 对应 spec 四条用户故事

## Path Conventions

- 后端：`backend/src/main/java/com/xiyu/bid/scoreparse/`
- 前端：`src/composables/projectDetail/`、`src/components/project/detail/`
- 说明：`docs/implementation-notes/`

---

## Phase 1: Setup

**Purpose**: 对齐契约与现网文件，不新建工程

- [X] T001 通读 `specs/043-harden-score-parse-intake/contracts/score-parse-auto-trigger.md` 与现网 `ScoreParseItemsDTO.java` / `InitiationTenderTextResolver.java` / `useScoreParseDrawer.js`，确认本特性只改这些入口、不新建包

---

## Phase 2: Foundational（自动解析门闩的数据）

**Purpose**: items.meta 能回答「这个项目有没有过 PARSE 任务」——US1 前端门闩的前置

**⚠️ CRITICAL**: US1 实现前必须完成

- [X] T002 在 `backend/src/main/java/com/xiyu/bid/scoreparse/dto/ScoreParseItemsDTO.java` 的 `Meta` 增加可空字段 `lastParseStatus`、`lastParseError`（契约 `contracts/score-parse-auto-trigger.md`）
- [X] T003 在 `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseAppService.java` 的 `buildMeta` 填入该项目最近一条 PARSE 任务的 status/error；无 PARSE 行则两者为 null
- [X] T004 扩展 `backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreParseAppServiceTest.java`：无任务 meta 为 null；FAILED 任务带出 errorMessage

**Checkpoint**: `GET /items` 已能区分「从未解析」与「解析过」

---

## Phase 3: User Story 1 - 解析失败后不再悄悄重打 (Priority: P1) 🎯 MVP

**Goal**: 仅当从未有过 PARSE 任务且无评分项时，打开抽屉自动解析一次

**Independent Test**: 空清单 + lastParseStatus=null 调一次 parse；FAILED 再打开不调 parse

### Tests for User Story 1

> 先写测试并确认 FAIL，再改实现

- [X] T005 [P] [US1] 在 `src/composables/projectDetail/useScoreParseDrawer.spec.js` 增加：空 items 且 meta.lastParseStatus 为 null 时调用 `triggerParse`；lastParseStatus 为 FAILED/COMPLETED 时不调用；FAILED 时展示 lastParseError

### Implementation for User Story 1

- [X] T006 [US1] 改 `src/composables/projectDetail/useScoreParseDrawer.js`：仅 `scoreItems.length===0 && lastParseStatus==null` 时自动**新建** `startParse({silent:true})`；FAILED 时写入 error 文案，不自动新建。进行中跟随由 T020 补充。
- [X] T007 [US1] 核对 `src/views/Project/stages/components/ScoreParseDrawer.vue` 空态仍提供「重新解析」，失败原因可见

**Checkpoint**: US1 可单独验收；打开抽屉不再因 FAILED 重打 LLM

---

## Phase 4: User Story 2 - 超大招标文件被拒绝 (Priority: P1)

**Goal**: 读取立项文件时硬限制 50MB，复用 HttpClient，禁止无界整包读入

**Independent Test**: Content-Length>50MB 或累计读满 50MB+1 失败；刚好 50MB 成功

### Tests for User Story 2

- [X] T008 [US2] 在 `backend/src/test/java/com/xiyu/bid/scoreparse/application/InitiationTenderTextResolverTest.java` 增加：Content-Length 超限不读 body；流式累计超限失败；刚好 50MB 视为允许

### Implementation for User Story 2

- [X] T009 [US2] 改 `backend/src/main/java/com/xiyu/bid/scoreparse/application/InitiationTenderTextResolver.java`：共享/可注入 HttpClient（构造一次）；OBS/HTTP 先看 Content-Length，再流式读，超过 50MB 中止；本地字节同样先量长度。禁止 `BodyHandlers.ofByteArray()` 无界读

**Checkpoint**: 超大文件不会整包进堆；≤50MB 仍可抽取

---

## Phase 5: User Story 3 - 立项读失败回退底稿 (Priority: P1)

**Goal**: 立项读失败用快照正文；hasSource 与 resolve 同一套成功条件

**Independent Test**: 坏立项 + 非空快照 → resolve 用快照且 hasSource=true；两者都无 → empty 且 triggerParse 400

### Tests for User Story 3

- [X] T010 [US3] 在 `backend/src/test/java/com/xiyu/bid/scoreparse/application/InitiationTenderTextResolverTest.java` 增加：立项抽取抛错/空正文时回退快照；hasSource 与 resolve.isPresent() 一致
- [X] T011 [P] [US3] 在 `backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreParseAppServiceTest.java` 确认无源时 400 文案仍为「请先在立项阶段上传招标文件」

### Implementation for User Story 3

- [X] T012 [US3] 改 `backend/src/main/java/com/xiyu/bid/scoreparse/application/InitiationTenderTextResolver.java`：立项失败只记日志不抛「没有招标文件」；回退非空 `extracted_text`；`hasSource` 改为 `resolve(...).isPresent()`
- [X] T013 [US3] 确认 `ScoreParseAppService.triggerParseInternal` / `doExecuteParse` 只依赖上述 resolve 结果，不另写一套「有文档行就算有源」

**Checkpoint**: 坏立项不再挡住可用底稿；两入口结论一致

---

## Phase 6: User Story 4 - 文案与上线说明 (Priority: P2)

**Goal**: 去掉初稿引导；说明里写清产品决定与现行路径

**Independent Test**: 拆解对话框无「生成初稿」；实施笔记含产品决定与路径

- [X] T014 [P] [US4] 改 `src/components/project/detail/ProjectTenderBreakdownDialog.vue`：上传提示改为拆解/评分解析，删除「可用于 AI 生成初稿」
- [X] T015 [P] [US4] 更新 `docs/implementation-notes/score-parse-initiation-tender.md`：初稿入口下线是产品决定；路径为立项招标 → 评分抽屉 → 编制投标后实际打分

**Checkpoint**: FR-009 / FR-010 可验收

---

## Phase 7: Polish

- [X] T016 按 `specs/043-harden-score-parse-intake/quickstart.md` 跑通所列 mvn/vitest 命令并记录结果
- [X] T017 确认 `ScoreParseAppService.java`、`InitiationTenderTextResolver.java`、`useScoreParseDrawer.js` 未超过 300 行硬上限

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖
- **Foundational (Phase 2)**: 依赖 Setup；**挡住 US1**
- **US1**: 依赖 Phase 2
- **US2**: 可与 US1 并行（不同文件）；与 US3 同改 resolver，必须在 US3 之前完成
- **US3**: 依赖 US2（同一 `InitiationTenderTextResolver.java`）
- **US4**: 可与 US1/US2 并行（不同文件）
- **Polish**: 依赖打算交付的故事
- **Convergence (Phase 8)**: 依赖 T001–T017 落地后的对照；T018–T020 已补完并回写 spec/契约

### User Story Dependencies

- **US1 (P1)**: 依赖 meta 字段；不依赖 US2/US3
- **US2 (P1)**: 独立于 US1
- **US3 (P1)**: 接在 US2 的 resolver 改动之后
- **US4 (P2)**: 独立

### Parallel Opportunities

- T005 与 T002–T004 不可并行（US1 测试要等 meta 语义定死；可在 T004 后立刻写）
- T014 与 T015 可并行
- T014/T015 可与 T008–T013 并行
- US2 与 US1 可两人分头（前端 / 后端下载）

### Parallel Example: US4

```bash
Task: "改 ProjectTenderBreakdownDialog.vue 去掉初稿文案"
Task: "更新 score-parse-initiation-tender.md 上线说明"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. T001 → Phase 2（meta）
2. Phase 3 US1
3. **STOP**：打开失败过的项目抽屉，确认不再自动 parse

### Incremental Delivery

1. US1 门闩 → 消掉 LLM 重打
2. US2 50MB → 消掉 OOM
3. US3 回退底稿 → 消掉「有底稿却报没文件」
4. US4 文案/说明

### Parallel Team Strategy

- A：Phase 2 + US1
- B：US2 然后 US3
- C：US4

---

## Notes

- 手动「重新解析」必须仍能 POST /parse（后端不因「已有历史任务」拒绝）
- 「自动开始」= 新建 PARSE 任务；PENDING/PROCESSING 跟随不算新建（T020）
- 提交时盯 300 行门禁，resolver 若膨胀则把流式下载拆到同包新类（计划允许）
- 每完成一个故事按 quickstart 对应节验证后再往下
- 立项招标读上限 50MB，不适用宪法通用附件 20MB（plan 已登记例外）

---

## Phase 8: Convergence

对照 spec/plan 与现码后补回的缺口。不改已完成的 T001–T017。

- [X] T018 堵住无源自动解析循环：`POST /parse` 在无可用正文时 400 且不建任务，导致 `lastParseStatus` 仍为 null，每次打开抽屉会再 POST。须让后续打开不再自动打（例如先落 FAILED 任务或把「已尝试无源」写入 meta），并仍提示「请先在立项阶段上传招标文件」。涉及 `ScoreParseAppService.java` / `useScoreParseDrawer.js` 及对应测试。per FR-007 / US3/AC3 (partial)
- [X] T019 立项文件超过 50MB 且无可用底稿时，向用户返回并展示「招标文件超过 50MB」类说明；有底稿时仍回退底稿。现码 `capSize`/`tryExtract` 吞掉 `TOO_LARGE_MESSAGE`，无源时只回「请先在立项阶段上传招标文件」。涉及 `InitiationTenderTextResolver.java` / `ScoreParseAppService.java` 及 `InitiationTenderTextResolverTest`。per FR-003 / US2/AC2 (partial)
- [X] T020 打开评分抽屉时若 `lastParseStatus` 为 PENDING/PROCESSING，跟随已有任务并轮询至终态，不得只展示空清单等用户再点「重新解析」。后端继续复用进行中任务、不另开并行。涉及 `useScoreParseDrawer.js` 及 `useScoreParseDrawer.spec.js`。per US1/AC4 / Edge 进行中反复打开 (partial)
