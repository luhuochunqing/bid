# Feature Specification: CO-498 项目复盘阶段提交后解锁结项阶段 tab

**Feature Branch**: `agent/zcode/co498-retrospective-closure-tab-unlock`

**Created**: 2026-07-04

**Status**: Draft

**Input**: 线上 bug — 项目 157 复盘阶段（RETROSPECTIVE）提交后，结项阶段（CLOSED）tab 在导航时间线上对所有项目成员不可见不可点（显示"待进入"），导致项目负责人无法进入结项阶段提交结项申请，整个结项审核流程死锁。

---

## 问题分析

### 现场还原（按【全链路日志排查 SOP】§23 取证）

| 字段 | 值 |
|---|---|
| 报告项目 | project 157（name=?? 123, tender_id=943） |
| 操作用户 | user=7220 / 06234（roleCode=admin，权限含 retrospective.submit / closure.request / closure.review） |
| 复盘提交时间 | 2026-07-04 17:07:45（traceId=`de440c759b95463cb4d071eb9c4f6632`） |
| 当前 DB 状态 | `projects.stage=RETROSPECTIVE`, `status=WON`, `closed_at=NULL` |
| 关联表 | `project_retrospective`(id=27, APPROVED) ✅，`project_closure` ❌ 不存在，`project_result`(id=77, WON) ✅ |
| 现象 | 复盘提交后没有任何 `Project stage transitioned project=157 RETROSPECTIVE→CLOSED` 日志，stage 停在 RETROSPECTIVE |

### Layer 3 git 追溯：根因 commit

```
5d1b36b53  fix(retrospective): 复盘提交后阶段停在 RETROSPECTIVE，由结项审核流程驱动到 CLOSED
           作者：褚永恩  时间：2026-07-04 13:47:13 +0800  已在 origin/main
           随 release 76c425667-api8080 于 2026-07-04 08:58:39Z 上线
```

**该 commit 删除了 `ProjectRetrospectiveService.submit()` 中"RETROSPECTIVE→CLOSED"的二次自动推进**（动机是修 CO-443 `alreadyClosed` 误判 bug，本身正确），但暴露了下游导航断层：

- `ProjectStageController.get()` 返回的 `accessibleStages` = `completedStages + [currentStage]`，**不包含 CLOSED**
- 前端 `ProjectStageTimeline.vue:74-81` 的 `isUnlocked()` 据此把 CLOSED tab 锁死，显示"待进入"
- 项目负责人进不去结项 tab → 无法提交结项申请 → 整个结项审核流程从源头死锁

### 与既有设计的关系

| 既有机制 | 行为 | 是否改 |
|---|---|---|
| CO-443 "假 CLOSED" | 已提交结项申请（有 closure 记录）但审批未通过时，导航条显示结项"进行中" | 不改 |
| CO-403 结项职责分离 | `ClosureStage.canSubmitClosure`：仅 `bid-projectLeader` 可提交，admin/组长/投标负责人/辅助 只审核 | 不改 |
| `RetrospectiveService.submit()` | 复盘提交后阶段停在 RETROSPECTIVE，不直达 CLOSED | 不改 |
| `ProjectStageTransitionPolicy` FSM | RETROSPECTIVE→CLOSED 线性顺推，CLOSED 终态 | 不改 |

---

## 用户场景与测试 *(mandatory)*

### User Story 1 - 项目负责人提交复盘后能进入结项 tab（Priority: P1）

作为投标项目负责人（`bid-projectLeader`），当我提交复盘后，我期望在项目时间线上看到并能点击"结项"tab，以便进入结项阶段填写保证金退回情况并提交结项申请。

**Why this priority**: 这是本次 bug 直接堵死的入口。不修复，整个结项审核流程无法启动，项目永远卡在 RETROSPECTIVE。

**Independent Test**: 项目 stage=RETROSPECTIVE 且无 closure 记录时，调用 `GET /api/projects/{id}/stage`，断言返回的 `accessibleStages` 包含 `"CLOSED"`。

**Acceptance Scenarios**:

1. **Given** 项目 157 当前 stage=RETROSPECTIVE、无 closure 记录，**When** 任意项目成员调用 `GET /api/projects/157/stage`，**Then** 返回 `accessibleStages` 包含 `"CLOSED"`，前端 CLOSED tab 可点击。
2. **Given** 项目 stage=RETROSPECTIVE、已存在 closure 记录（DRAFT/PENDING 任一），**When** 调用 stage 接口，**Then** `accessibleStages` 不包含 `"CLOSED"`（CO-443 假 CLOSED 已生效，导航条已显示结项"进行中"）。
3. **Given** 项目 stage != RETROSPECTIVE（如 INITIATED/DRAFTING/EVALUATING/RESULT_PENDING/CLOSED），**When** 调用 stage 接口，**Then** 行为与现状完全一致，`accessibleStages` 不变。

---

### User Story 2 - 审核人能进入结项 tab 查看申请（Priority: P2）

作为投标管理员/组长/投标负责人/辅助（审核人），当项目负责人提交结项申请后，我期望能进入结项 tab 查看申请详情并审核（无论项目阶段是否仍在 RETROSPECTIVE）。

**Why this priority**: US-1 解锁后，结项申请一旦提交（review_status=PENDING），审核人就需要能进入 tab。但本故事的核心保障是"角色矩阵不回归"——审核人能否看到申请仍由 `ClosureStage.canApprove` 控制，与本次变更正交。

**Independent Test**: US-1 修复后，closure 进入 PENDING 状态时，审核角色访问 CLOSED tab 能加载 ClosureStage 并看到审核按钮。

**Acceptance Scenarios**:

1. **Given** 项目 stage=RETROSPECTIVE、closure.review_status=PENDING，**When** admin/组长/投标负责人/辅助 进入结项 tab，**Then** ClosureStage 正常加载，`canApprove` 计算为 true 时显示审核通过/驳回按钮。
2. **Given** 同上场景，**When** `bid-projectLeader`（提交人）进入结项 tab，**Then** 不显示审核按钮（`canApprove` 对提交人为 false），仅显示"提交结项"按钮但已 disabled（review_status=PENDING 时 `canSubmitClosure` 返回 false）。

---

### User Story 3 - admin 角色可见 tab 但提交受角色矩阵限制（Priority: P3）

作为系统管理员（admin），当我（或代为）提交复盘后，我期望时间线上的"结项"tab 可见可点，以便我评估是否需要通知项目负责人前去提交结项申请（admin 不自己提交，受 `canSubmitClosure` 角色矩阵限制）。

**Why this priority**: admin 是本次 bug 报告人，验证 admin 体验不回归是必要的，但 admin 不是结项流程主路径。

**Independent Test**: admin 角色访问项目 157，能看到 CLOSED tab 可点击，进入后不显示"提交结项"按钮。

**Acceptance Scenarios**:

1. **Given** admin 用户 06234、项目 157 stage=RETROSPECTIVE 无 closure，**When** 调用 stage 接口，**Then** `accessibleStages` 包含 `"CLOSED"`（与 US-1 一致，不按角色区分）。
2. **Given** admin 进入结项 tab，**When** ClosureStage 渲染，**Then** `canSubmitClosure` 返回 false（admin != bid-projectLeader），不显示"提交结项"按钮；若 closure 已 PENDING，`canApprove` 为 true，显示审核按钮。

---

## 功能需求 *(mandatory)*

### FR-1: 复盘阶段解锁结项 tab（后端）

`ProjectStageController.get()` 必须在响应中包含 CLOSED 当且仅当满足全部条件：
- `projects.stage == RETROSPECTIVE`
- `project_closure` 表不存在该 `project_id` 的记录（即 closure 未提交）

实现约束：
- CLOSED 加入 `accessibleStages` 字段（不影响 `currentStage`/`completedStages`/`allowedNextStages`/`terminal`）
- 不按角色区分（角色矩阵下沉到 `ClosureStage.canSubmitClosure`）
- 复用既有 `closureRepository.findByProjectId(projectId)` 已注入的依赖，不新增 Bean

### FR-2: 行为幂等性

- 同一项目在相同状态下重复调用 stage 接口，结果必须一致
- 解锁逻辑只读 `project_closure` 表是否存在记录，不依赖 `review_status` 取值

### FR-3: 不改的部分（边界守护）

下列行为必须维持 `5d1b36b53` 之后的状态，禁止回归：
- `RetrospectiveService.submit()` 提交复盘后 stage 仍停在 RETROSPECTIVE，不直达 CLOSED
- `ProjectStageTransitionPolicy.decide()` FSM 不变（RETROSPECTIVE→CLOSED 仍需显式 requestTransition）
- `ClosureStage.canSubmitClosure` 角色矩阵不变（仅 `bid-projectLeader` 可提交）
- `ClosureStage.canApprove` 角色矩阵不变（admin/组长/投标负责人/辅助 审核职责分离）
- CO-443 "假 CLOSED" 机制不变（有 closure 记录时 `current` 显示为 CLOSED）

---

## 验收标准 *(mandatory)*

| AC ID | 描述 | 测试手段 |
|---|---|---|
| AC-1 | stage=RETROSPECTIVE 且无 closure 记录时，`accessibleStages` 包含 `"CLOSED"` | ProjectStageControllerTest 单测 |
| AC-2 | stage=RETROSPECTIVE 且有 closure 记录（任一 review_status）时，`accessibleStages` 不含 `"CLOSED"` | ProjectStageControllerTest 单测（DRAFT/PENDING/APPROVED/REJECTED 4 个状态参数化） |
| AC-3 | stage != RETROSPECTIVE 时，`accessibleStages` 行为与现状一致 | ProjectStageControllerTest 回归断言 |
| AC-4 | 解锁对所有通过项目权限校验的成员一致，不按角色区分 | ProjectStageControllerTest（admin/bid-projectLeader/审核人 三角色同断言） |
| AC-5 | 项目 157 修复后端到端：admin 看到 CLOSED tab 可点击；项目负责人进入后可填表+提交 | 手工端到端验证（生产环境灰度） |
| AC-6 | `RetrospectiveService.submit()` 行为不变，仍只推进 RESULT_PENDING→RETROSPECTIVE | ProjectRetrospectiveServiceTest 回归 |
| AC-7 | CO-443 假 CLOSED 机制不变（有 closure 记录时 current=CLOSED、terminal 看 reviewStatus==APPROVED） | ProjectStageControllerTest.co443_* 既有用例全绿 |

---

## 范围边界

### In Scope

- `ProjectStageController.get()` 后端逻辑调整
- `ProjectStageControllerTest` 新增测试用例（覆盖 AC-1 ~ AC-4、AC-7 回归）
- 端到端验证项目 157（AC-5）
- `implementation-notes.md` 活文档（决策、tradeoff、变更）

### Out of Scope

- 前端代码改动（验证后端放开后前端自动生效）
- `RetrospectiveService.submit()` 改动
- `ClosureStage.canSubmitClosure` / `canApprove` 角色矩阵改动
- `ProjectStageTransitionPolicy` 改动
- admin 代为提交结项申请的兜底入口（后续 enhancement）

---

## 关联文档

- 根因 commit: `5d1b36b53` (origin/main)
- 关联工作项: CO-443（假 CLOSED 机制）、CO-403（结项职责分离）、CO-497（timeline 异步 snapshot 回声，与本 bug 同 release 上线但无关）
- 受影响代码:
  - `backend/src/main/java/com/xiyu/bid/project/controller/ProjectStageController.java`
  - `backend/src/test/java/.../ProjectStageControllerTest.java`
- 前端（无改动，仅验证）:
  - `src/components/project/stage/ProjectStageTimeline.vue`
  - `src/views/Project/stages/ClosureStage.vue`

---

## Clarifications

### Session 2026-07-04

- Q: 修复方向？ → A: 方案 A — 解锁结项 tab（维持 CO-443 结项审核流程不变）
- Q: 推进方式？ → A: 走完整 Spec Kit 流程门禁
- Q: 访问范围？ → A: 所有项目成员可见可点，提交仍由 `canSubmitClosure` 限制为 bid-projectLeader
- Q: Spec Kit git hook？ → A: 跳过 hook，直接在任务分支 `agent/zcode/co498-retrospective-closure-tab-unlock` 上跑
