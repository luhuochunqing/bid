---

description: "保证金看板状态筛选修复任务清单"
---

# Tasks: 保证金看板状态筛选修复

**Input**: Design documents from `/specs/025-fix-margin-filter/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: 本任务遵循 TDD（Constitution III），测试任务 MUST 先写并验证 FAIL，再写实现。

**Organization**: 任务按 User Story 分组，便于独立实现和测试。但由于 P1/P2 改同一文件的同一方法，按 TDD 顺序串行执行。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Web app**: `backend/src/`, `frontend/src/`
- 后端测试：`backend/src/test/java/com/xiyu/bid/resources/service/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 确认任务分支和环境就绪

- [x] T001 任务分支 `agent/codex/fix-margin-filter-not-working` 已创建并同步 origin/main（早操已完成）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 无阻塞前置任务。复用既有的 `Fee`、`ProjectInitiationDetails` 实体和派生表 UNION ALL 结构，无需新建实体或迁移。

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - 筛选"未到期"看到所有未到期行 (Priority: P1) 🎯 MVP

**Goal**: 修复 PENDING 筛选分支，让立项占位行（`exp_return_date IS NULL`）能被正确返回

**Independent Test**: 在测试环境准备 1 条已缴纳且 `fee_date >= NOW()` 的 BID_BOND fee + 1 条 `need_deposit='YES'` 但无 fees 的立项记录，点击"未到期"筛选，应看到 2 行。

### Tests for User Story 1 (TDD - 先写失败测试)

- [ ] T002 [US1] 写失败测试 `filterByStatusPending_shouldIncludeInitBranchRows_withNullExpReturnDate` 在 `backend/src/test/java/com/xiyu/bid/resources/service/MarginQuerySupportMysqlIntegrationTest.java`
  - 造数据：1 条 `project_initiation_details.need_deposit='YES', deposit_amount>0` 且无对应 BID_BOND fees 的立项记录
  - 调用：`filterByStatus("PENDING")` 或等价的查询入口
  - 断言：返回结果包含该 init 占位行（行数 >= 1，且包含该 project_id）
  - 验证：`mvn test -Dtest=MarginQuerySupportMysqlIntegrationTest#filterByStatusPending_shouldIncludeInitBranchRows_withNullExpReturnDate` 应 FAIL（Red）

### Implementation for User Story 1

- [ ] T003 [US1] 修复 `appendStatusFilter` 方法的 `case "PENDING"` 分支在 `backend/src/main/java/com/xiyu/bid/resources/service/MarginQuerySupport.java`
  - 修改前：`AND m.exp_return_date >= NOW()`
  - 修改后：`AND (m.exp_return_date IS NULL OR m.exp_return_date >= NOW())`
  - 不动其他 case 分支
  - 验证：`mvn test -Dtest=MarginQuerySupportMysqlIntegrationTest#filterByStatusPending_shouldIncludeInitBranchRows_withNullExpReturnDate` 应 PASS（Green）

**Checkpoint**: User Story 1 应完全可用，"未到期"筛选返回所有标签为"未到期"的行

---

## Phase 4: User Story 2 - 筛选"已退回"看到所有已退回行 (Priority: P2)

**Goal**: 修复 RETURNED 筛选分支，让 CANCELLED 状态的 BID_BOND fee 能被正确返回

**Independent Test**: 在测试环境准备 1 条 `fees.status='RETURNED'` + 1 条 `fees.status='CANCELLED'` 的 BID_BOND，点击"已退回"筛选，应看到 2 行。

### Tests for User Story 2 (TDD - 先写失败测试)

- [ ] T004 [US2] 写失败测试 `filterByStatusReturned_shouldIncludeCancelledFees` 在 `backend/src/test/java/com/xiyu/bid/resources/service/MarginQuerySupportMysqlIntegrationTest.java`
  - 造数据：1 条 `fees.status='CANCELLED', fee_type='BID_BOND'` 的记录
  - 调用：`filterByStatus("RETURNED")` 或等价的查询入口
  - 断言：返回结果包含该 CANCELLED 行
  - 验证：`mvn test -Dtest=MarginQuerySupportMysqlIntegrationTest#filterByStatusReturned_shouldIncludeCancelledFees` 应 FAIL（Red）

### Implementation for User Story 2

- [ ] T005 [US2] 修复 `appendStatusFilter` 方法的 `case "RETURNED"` 分支在 `backend/src/main/java/com/xiyu/bid/resources/service/MarginQuerySupport.java`
  - 修改前：`AND m.status = 'RETURNED'`
  - 修改后：`AND m.status IN ('RETURNED','CANCELLED')`
  - 不动其他 case 分支
  - 验证：`mvn test -Dtest=MarginQuerySupportMysqlIntegrationTest#filterByStatusReturned_shouldIncludeCancelledFees` 应 PASS（Green）

**Checkpoint**: User Story 1 AND 2 都应独立可用

---

## Phase 5: User Story 3 - 筛选"已超期"行为保持不变 (Priority: P3)

**Goal**: 回归保护——验证 OVERDUE 分支未被 P1/P2 修复破坏，立项占位行不被误算为"已超期"

**Independent Test**: 准备 1 条 `fees.status='PAID', fee_date < NOW()` + 1 条立项占位行，点击"已超期"筛选，应只看到 1 行（立项占位行不应出现）。

### Tests for User Story 3 (回归测试，应直接 PASS)

- [ ] T006 [US3] 写回归测试 `filterByStatusOverdue_shouldExcludeInitBranchRows_withNullExpReturnDate` 在 `backend/src/test/java/com/xiyu/bid/resources/service/MarginQuerySupportMysqlIntegrationTest.java`
  - 造数据：1 条 `fees.status='PAID', fee_date < NOW()` + 1 条 init 占位行
  - 调用：`filterByStatus("OVERDUE")` 或等价的查询入口
  - 断言：只返回 fees 行，不返回 init 占位行
  - 验证：`mvn test -Dtest=MarginQuerySupportMysqlIntegrationTest#filterByStatusOverdue_shouldExcludeInitBranchRows_withNullExpReturnDate` 应直接 PASS（OVERDUE 分支无需改动）

### Implementation for User Story 3

- [ ] T007 [US3] 验证 `appendStatusFilter` 方法的 `case "OVERDUE"` 分支在 `backend/src/main/java/com/xiyu/bid/resources/service/MarginQuerySupport.java` 无需改动
  - 当前谓词：`AND m.status NOT IN ('RETURNED','CANCELLED') AND m.exp_return_date < NOW()`
  - `NULL < NOW()` 在 MySQL 中为 NULL（falsy），自动排除 init 占位行，语义正确
  - 如测试 T006 PASS，则本任务完成（无需代码改动）

**Checkpoint**: 所有 3 个 User Story 都应独立可用，筛选语义与 label() 完全对齐

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 全量回归验证 + 提交

- [ ] T008 跑 margin 模块全部测试：`cd backend && mvn test -Dtest=MarginQuerySupportMysqlIntegrationTest,MarginServiceTest`
- [ ] T009 [P] 跑架构测试：`cd backend && mvn test -Dtest=ArchitectureTest`（确认未破坏 FP-Java 边界）
- [ ] T010 [P] 前端构建：`npm run build`（确认无前端副作用，本任务无前端改动）
- [ ] T011 [P] 检查 git status 确认只修改了授权文件（MarginQuerySupport.java + MarginQuerySupportMysqlIntegrationTest.java + specs/025-fix-margin-filter/* 文档）
- [ ] T012 提交原子 commit：`fix(margin): 保证金看板状态筛选对齐 label() 语义 (CO-XXX)`
- [ ] T013 推送任务分支：`git push origin agent/codex/fix-margin-filter-not-working`
- [ ] T014 创建 PR（Gitee），PR 描述包含：根因分析、修复方案、测试覆盖、回滚条件

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 已完成（T001 ✅）
- **Foundational (Phase 2)**: 无前置任务，直接进入 User Story
- **User Stories (Phase 3-5)**: 按 TDD 顺序串行
  - US1 (P1) → US2 (P2) → US3 (P3)
  - 不能并行：P1/P2 改同一文件的同一方法（appendStatusFilter）
- **Polish (Phase 6)**: 依赖所有 User Story 完成

### User Story Dependencies

- **User Story 1 (P1)**: 无依赖，MVP 首要交付
- **User Story 2 (P2)**: 依赖 US1 的测试基础设施（造数据模式），但代码改动独立
- **User Story 3 (P3)**: 回归保护，依赖 US1/US2 完成后验证 OVERDUE 未被破坏

### Within Each User Story

- 测试 MUST 先写并验证 FAIL（Red）
- 再写实现使测试 PASS（Green）
- US3 例外：OVERDUE 分支不改，测试应直接 PASS

### Parallel Opportunities

- T009（架构测试）/ T010（前端构建）/ T011（git status 检查）可并行
- 其他任务按 TDD 顺序串行

---

## Parallel Example: Polish Phase

```bash
# 并行跑架构测试、前端构建、git status 检查
Task: "mvn test -Dtest=ArchitectureTest"
Task: "npm run build"
Task: "git status 确认授权文件"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. T002 写 PENDING 失败测试 → 验证 FAIL
2. T003 修复 PENDING 分支 → 验证 PASS
3. **STOP and VALIDATE**: 在测试环境验证"未到期"筛选返回所有未到期行
4. 如紧急，可先合并 US1 作为 MVP

### Incremental Delivery

1. US1 (PENDING 修复) → 测试 → 可作为 MVP 部署
2. US2 (RETURNED 修复) → 测试 → 累加部署
3. US3 (OVERDUE 回归保护) → 测试 → 累加部署
4. Polish → 全量验证 → PR

### Single Developer Strategy（本任务推荐）

由于改动范围小（1 个文件 + 1 个测试文件），建议单人串行完成：
1. T002 → T003（US1 Red→Green）
2. T004 → T005（US2 Red→Green）
3. T006 → T007（US3 回归验证）
4. T008-T014（Polish + 提交）

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- 本任务改同一文件（MarginQuerySupport.java）的同一方法（appendStatusFilter），P1/P2 不能并行
- 测试造数据需用 Testcontainers MySQL（已有基础设施）
- 提交前 MUST 跑 pre-push gate（14 道门禁）
- PR 描述 MUST 包含根因分析（引用 lessons-learned SOP）
