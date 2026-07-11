# Tasks: 限流提示友好化优化

**Input**: Design documents from `/specs/034-friendly-rate-limit/`

**Prerequisites**: plan.md (required), spec.md (required)

**Tests**: 包含测试任务。本次任务采用 TDD，测试先行。

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 确认当前分支和基线，获取文件锁，准备开发环境。

- [ ] T001 在 codex worktree 确认当前分支为 `agent/codex/friendly-rate-limit` 且基于最新 origin/main
- [ ] T002 对 `src/api/client.js`、`backend/src/main/java/com/xiyu/bid/config/RateLimitFilter.java` 运行 `who-touches.sh` 检查冲突
- [ ] T003 为 `src/api/client.js`、`backend/src/main/java/com/xiyu/bid/config/RateLimitFilter.java`  acquire agent lock

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 创建前后端纯核心组件，为三个用户故事提供共享能力。

**⚠️ CRITICAL**: 所有用户故事依赖此阶段完成。

### Tests for Foundational (Write FIRST, ensure FAIL)

- [ ] T004 [P] 编写 `RateLimitResponseFactoryTest.java` 测试：验证 429 元数据 → `ApiResponse` 映射（含中文 msg、code、retryAfter）
- [ ] T005 [P] 编写 `rate-limit-message-resolver.spec.js` 测试：验证后端响应 → 前端文案映射（含/不含 Retry-After、含/不含 msg）

### Implementation for Foundational

- [ ] T006 创建 `backend/src/main/java/com/xiyu/bid/exception/RateLimitResponseFactory.java` 纯核心工厂
- [ ] T007 创建 `src/api/rate-limit-message-resolver.js` 纯核心文案解析器

**Checkpoint**: 纯核心组件编译/解析通过，对应单元测试 Red → Green

---

## Phase 3: User Story 1 - 限流提示文案更友好 (Priority: P1)

**Goal**: 把前端 429 默认提示从命令式技术语言改为温和、可操作的表达，并支持显示具体等待秒数。

**Independent Test**: 在任意页面触发 429 后，提示文案为"操作太快了，请稍等几秒再试"或带具体秒数；AI 解析场景保留业务文案。

### Tests for User Story 1 (Write FIRST, ensure FAIL)

- [ ] T008 [P] [US1] 更新/新增 `client.spec.js` 测试：429 无 Retry-After 时显示默认友好文案
- [ ] T009 [P] [US1] 更新/新增 `client.spec.js` 测试：429 有 Retry-After 时显示"请等待 X 秒后再试"
- [ ] T010 [P] [US1] 更新/新增 `client.spec.js` 测试：AI 解析 429 仍显示"AI 服务请求过于频繁，请稍后再试，当前可手动填写"

### Implementation for User Story 1

- [ ] T011 [US1] 修改 `src/api/client.js`，429 分支调用 `rate-limit-message-resolver.js` 并显示友好文案

**Checkpoint**: User Story 1 可独立验证——模拟 429 响应，toast 文案符合预期

---

## Phase 4: User Story 2 - 前端避免重复和打扰式提示 (Priority: P1)

**Goal**: 同一窗口内多个 429 请求只弹一次提示；轮询类请求触发 429 时不弹 toast。

**Independent Test**: 同时触发 3 个 429 只显示 1 个 toast；通知中心轮询 429 不弹 toast。

### Tests for User Story 2 (Write FIRST, ensure FAIL)

- [ ] T012 [P] [US2] 更新/新增 `client.spec.js` 测试：3 个并发 429 在 3 秒内只触发 1 次 `ElMessage.warning`
- [ ] T013 [P] [US2] 更新/新增 `client.spec.js` 测试：`config.silentRateLimit=true` 的 429 请求不触发 toast
- [ ] T014 [P] [US2] 更新/新增 `useNotifications.spec.js` 测试：轮询收到 429 后进入 60 秒退避

### Implementation for User Story 2

- [ ] T015 [US2] 在 `src/api/client.js` 增加 module-level 限流提示冷却期（如 3 秒），合并重复提示
- [ ] T016 [US2] 在 `src/api/client.js` 增加 `config.silentRateLimit` 分支，对轮询类请求跳过 toast
- [ ] T017 [US2] Review `src/composables/useNotifications.js`，确保轮询 429 退避逻辑与新协议兼容（如需调整则修改）

**Checkpoint**: User Story 2 可独立验证——并发 429 只弹一次，轮询静默退避

---

## Phase 5: User Story 3 - 后端返回标准、可消费的限流信息 (Priority: P2)

**Goal**: `RateLimitFilter` 返回项目统一 `ApiResponse` 格式和 `Retry-After` 头，并记录可观测日志。

**Independent Test**: 任意 429 响应体为 `ApiResponse` 格式且含中文 `msg`；响应头含 `Retry-After`；日志记录 key/path/retryAfter。

### Tests for User Story 3 (Write FIRST, ensure FAIL)

- [ ] T018 [P] [US3] 更新 `RateLimitFilterTest.java`：验证 429 响应体为统一格式，且 `msg` 为中文友好文案
- [ ] T019 [P] [US3] 更新 `RateLimitFilterTest.java`：验证 429 响应头包含 `Retry-After`
- [ ] T020 [P] [US3] 更新 `RateLimitFilterTest.java`：验证被限流时日志输出包含 key、path、retryAfter

### Implementation for User Story 3

- [ ] T021 [US3] 修改 `backend/src/main/java/com/xiyu/bid/config/RateLimitFilter.java`，被限流时调用 `RateLimitResponseFactory` 并返回 `ApiResponse` 格式
- [ ] T022 [US3] 在 `RateLimitFilter.java` 中计算并设置 `Retry-After` 响应头
- [ ] T023 [US3] 在 `RateLimitFilter.java` 中增强限流日志，记录 key、path、retryAfterSeconds

**Checkpoint**: User Story 3 可独立验证——`RateLimitFilterTest` 全绿

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 全量验证、文档同步、代码清理。

- [ ] T024 [P] 运行前端测试：`npm run test:unit -- --run`（聚焦相关测试）
- [ ] T025 [P] 运行后端测试：`cd backend && mvn test -Dtest=RateLimitFilterTest,RateLimitResponseFactoryTest`
- [ ] T026 [P] 运行架构测试：`cd backend && mvn test -Dtest=ArchitectureTest`
- [ ] T027 [P] 运行前端门禁：`npm run check:front-data-boundaries`、`npm run check:line-budgets`
- [ ] T028 更新 `CLAUDE.md` 中 `<!-- SPECKIT START -->` 活跃 feature 列表，添加 `specs/034-friendly-rate-limit/plan.md`
- [ ] T029 提交所有变更，push 到 `agent/codex/friendly-rate-limit`，并创建 PR（Gitee）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-5)**: All depend on Foundational phase completion
  - US1、US2、US3 可并行由不同 agent 执行
  - 推荐顺序：US3（后端协议）→ US1（前端文案）→ US2（前端交互），但三者无强依赖
- **Polish (Phase 6)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: 依赖 Foundational 的 `rate-limit-message-resolver.js`
- **User Story 2 (P1)**: 依赖 Foundational 的 `rate-limit-message-resolver.js`，与 US1 共享 `client.js`
- **User Story 3 (P2)**: 依赖 Foundational 的 `RateLimitResponseFactory.java`

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- T004/T005/T008/T009/T010/T012/T013/T014/T018/T019/T020 等测试任务可在代码实现前并行编写
- T006（后端工厂）和 T007（前端解析器）可并行
- US1/US2/US3 可由三个不同 agent 并行执行
- T024/T025/T026/T027 验证任务可并行

---

## Parallel Example: Three-Agent Execution

```text
Agent A (Backend Protocol):
  T004 → T006 → T018/T019/T020 → T021/T022/T023 → T025

Agent B (Frontend Copy):
  T005 → T007 → T008/T009/T010 → T011 → T024

Agent C (Frontend Interaction):
  T005 (read-only) → T012/T013/T014 → T015/T016/T017 → T024
```

---

## Implementation Strategy

### MVP First

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational（纯核心组件）
3. Complete Phase 5: User Story 3（后端协议）
4. Complete Phase 3: User Story 1（前端文案）
5. Complete Phase 4: User Story 2（前端交互）
6. Complete Phase 6: Polish

### Parallel Team Strategy

- **Agent A（后端协议专家）**: 负责 US3 + 后端纯核心工厂 + 后端测试
- **Agent B（前端文案专家）**: 负责 US1 + 前端纯核心解析器 + 前端文案测试
- **Agent C（前端交互专家）**: 负责 US2 + client.js 提示合并/静默 + 前端交互测试
- **Lead（当前 session）**: 负责 Setup、Polish、跨 agent 协调、最终验证

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- 所有 Java 新增/修改文件必须遵守 300 行硬上限
- 所有新增纯核心组件不得依赖框架（Spring/Element Plus/Vue Router 等）
- 提交前必须跑通前后端相关测试和门禁
