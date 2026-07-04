# Implementation Plan: CO-498 项目复盘阶段提交后解锁结项阶段 tab

**Feature Branch**: `agent/zcode/co498-retrospective-closure-tab-unlock`

**Created**: 2026-07-04

**Spec**: [spec.md](./spec.md)

---

## 技术方案

### 现有架构认知（实证于代码）

`ProjectStageController.get()` 当前的关键代码（`backend/src/main/java/com/xiyu/bid/project/controller/ProjectStageController.java:45-83`）：

```java
ProjectStage actual = service.currentStage(projectId);
// CO-443: 已提交结项申请但审批未通过时，阶段尚未实际推进到 CLOSED，
// 但前端进度导航栏应显示 CLOSED 为「进行中」而非「待进入」。
ProjectStage current = (actual != ProjectStage.CLOSED && service.hasClosureSubmission(projectId))
        ? ProjectStage.CLOSED
        : actual;
List<ProjectStage> next = current.isTerminal()
        ? List.of()
        : service.allowedNext(projectId);
List<String> completed = Arrays.stream(ProjectStage.values())
        .filter(s -> s.ordinal() < current.ordinal())
        .map(Enum::name).toList();
List<String> accessible = new ArrayList<>(completed);
accessible.add(current.name());
// ... (CO-315 评标审核人追加 DRAFTING 的特殊处理) ...
```

**关键观察**：
1. CO-443 已调用 `service.hasClosureSubmission(projectId)` 判断 closure 是否存在 → **结果可复用，无需重复查询 DB**
2. `accessible` 已用 `ArrayList` 构建，可变 → **追加 CLOSED 不破坏既有结构**
3. controller 已注入 `closureRepository`，但 CO-443 用的是 `service.hasClosureSubmission()`（service 层封装），保持一致更内聚

### 实现变更（最小侵入）

**唯一改动点**：`ProjectStageController.get()`，在 `accessible.add(current.name())` 之后、CO-315 评标审核人逻辑之前，追加一段：

```java
// CO-498: 复盘阶段(stage=RETROSPECTIVE) 且未提交结项申请时，解锁 CLOSED tab。
// 项目负责人需进入结项 tab 提交结项申请，否则结项审核流程从源头死锁。
// 不按角色区分——角色矩阵下沉到 ClosureStage.canSubmitClosure(canApprove)。
// 当 closure 已存在（任一 review_status），CO-443 假 CLOSED 已生效（current=CLOSED），
// CLOSED 已在 completed 列表里，无需再加。
boolean retrospectiveWithoutClosure = actual == ProjectStage.RETROSPECTIVE
        && !service.hasClosureSubmission(projectId);
if (retrospectiveWithoutClosure) {
    accessible.add(ProjectStage.CLOSED.name());
}
```

**注意几点**（tradeoff 与决策记录）：
- 用 `actual`（真实 stage）而非 `current`（CO-443 调整后的 stage）做判定 — 因为 `current` 在 closure 已提交时会被改成 CLOSED，此时 `retrospectiveWithoutClosure` 应为 false。用 `actual == RETROSPECTIVE` 更精确表达"真实还在复盘阶段"。
- `!service.hasClosureSubmission(projectId)` 与 CO-443 的判定**完全一致**，第二次调用是同一 service 方法的幂等查询（service 层若有缓存则无成本，若无缓存两次 DB 查询也极轻量）。可选优化：抽局部变量 `boolean hasClosure = service.hasClosureSubmission(projectId)` 复用。**采用抽局部变量**，避免重复调用造成误解。

**最终实现（修订版）**：

```java
ProjectStage actual = service.currentStage(projectId);
boolean hasClosureSubmission = service.hasClosureSubmission(projectId);
ProjectStage current = (actual != ProjectStage.CLOSED && hasClosureSubmission)
        ? ProjectStage.CLOSED
        : actual;
List<ProjectStage> next = current.isTerminal()
        ? List.of()
        : service.allowedNext(projectId);
List<String> completed = Arrays.stream(ProjectStage.values())
        .filter(s -> s.ordinal() < current.ordinal())
        .map(Enum::name).toList();
List<String> accessible = new ArrayList<>(completed);
accessible.add(current.name());
// CO-498: 复盘阶段未提交结项申请时，解锁 CLOSED tab（详见 spec.md FR-1）
if (actual == ProjectStage.RETROSPECTIVE && !hasClosureSubmission) {
    accessible.add(ProjectStage.CLOSED.name());
}
String defaultOpenStage = current.name();
// ... 后续 CO-315 评标审核人逻辑不变
```

### 不改的部分（边界守护）

- ❌ `RetrospectiveService.submit()` — 维持 `5d1b36b53` 行为
- ❌ `ProjectStageTransitionPolicy` — FSM 不变
- ❌ `ClosureStage.canSubmitClosure` / `canApprove` — 角色矩阵不变
- ❌ 前端 `ProjectStageTimeline.vue` / `ClosureStage.vue` — 后端字段放开后自动生效
- ❌ `ProjectStageService.hasClosureSubmission()` — 既有方法签名不变

### Constitution 对齐

| 原则 | 对齐情况 |
|---|---|
| I. FP-Java | ✅ 不动纯规则（ProjectStageTransitionPolicy），改的是 Controller 编排层 |
| II. Real-API Only | ✅ 不引入 Mock |
| III. TDD | ✅ 先写 4 个新测试用例（Red）→ 实现（Green）→ 抽局部变量（Refactor） |
| IV. Split-First | ✅ Controller 改完仍 < 120 行（远低于 200 行软上限） |
| VI. Authorization Unification | ✅ Controller 用 `isAuthenticated()`，项目权限下沉 `ProjectAccessScopeService`，提交权限下沉 `ClosureStage.canSubmitClosure` |
| `Project Access Guard` | ✅ Controller 已调用 `projectAccessScopeService.assertCurrentUserCanAccessProject` |

### 无 Flyway 迁移

本变更不改 schema，`accessibleStages` 是计算字段不入库。`projects` / `project_closure` 表结构维持现状。

---

## 测试策略

### Red 阶段：新增 4 个测试用例

在 `ProjectStageControllerTest.java` 末尾追加（紧跟 `co443_retrospectiveDone_noClosure_showsInProgress` 之后）：

```java
@Test
void co498_retrospectiveWithoutClosure_unlocksClosedTab() throws Exception {
    authenticate("06234");
    when(authService.resolveUserIdByUsername("06234")).thenReturn(100L);
    when(stageService.currentStage(42L)).thenReturn(ProjectStage.RETROSPECTIVE);
    when(stageService.hasClosureSubmission(42L)).thenReturn(false);
    when(stageService.allowedNext(42L)).thenReturn(List.of(ProjectStage.CLOSED));
    when(bidReviewAppService.getReviewState(42L)).thenReturn(
            new BidReviewAppService.ReviewState("REVIEWING", 9999L, null, "其他人", List.of()));
    when(closureRepository.findByProjectId(42L)).thenReturn(java.util.Optional.empty());

    mockMvc.perform(get("/api/projects/42/stage").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.currentStage").value("RETROSPECTIVE"))
            .andExpect(jsonPath("$.data.terminal").value(false))
            .andExpect(jsonPath("$.data.accessibleStages", org.hamcrest.Matchers.hasItem("CLOSED")));
}

@Test
void co498_retrospectiveWithClosureDraft_doesNotUnlockClosedTwice() throws Exception {
    authenticate("06234");
    when(authService.resolveUserIdByUsername("06234")).thenReturn(100L);
    when(stageService.currentStage(42L)).thenReturn(ProjectStage.RETROSPECTIVE);
    when(stageService.hasClosureSubmission(42L)).thenReturn(true);
    when(bidReviewAppService.getReviewState(42L)).thenReturn(
            new BidReviewAppService.ReviewState("REVIEWING", 9999L, null, "其他人", List.of()));
    ProjectClosure closure = ProjectClosure.builder().reviewStatus("DRAFT").build();
    when(closureRepository.findByProjectId(42L)).thenReturn(java.util.Optional.of(closure));

    mockMvc.perform(get("/api/projects/42/stage").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            // CO-443 假 CLOSED: current 被改成 CLOSED（hasClosureSubmission=true）
            .andExpect(jsonPath("$.data.currentStage").value("CLOSED"))
            // CLOSED 已在 completed 列表里，accessibleStages 不应重复出现两次
            .andExpect(jsonPath("$.data.accessibleStages", org.hamcrest.Matchers.hasItem("CLOSED")))
            .andExpect(jsonPath("$.data.accessibleStages",
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasSize(7))));
}

@Test
void co498_resultPendingStage_doesNotUnlockClosedTab() throws Exception {
    authenticate("06234");
    when(authService.resolveUserIdByUsername("06234")).thenReturn(100L);
    when(stageService.currentStage(42L)).thenReturn(ProjectStage.RESULT_PENDING);
    when(stageService.hasClosureSubmission(42L)).thenReturn(false);
    when(stageService.allowedNext(42L)).thenReturn(List.of(ProjectStage.RETROSPECTIVE));
    when(bidReviewAppService.getReviewState(42L)).thenReturn(
            new BidReviewAppService.ReviewState("REVIEWING", 9999L, null, "其他人", List.of()));

    mockMvc.perform(get("/api/projects/42/stage").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessibleStages",
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("CLOSED"))));
}

@Test
void co498_retrospectiveWithoutClosure_unlocksClosedForAllRoles() throws Exception {
    // 同一状态下，不同角色看到的 accessibleStages 一致（角色矩阵下沉到 ClosureStage）
    // 已有 co315 / non_reviewer 用例覆盖审核人/非审核人视角；本用例补充 RETROSPECTIVE 阶段的角色一致性
    for (String username : new String[]{"06234", "09118"}) {
        authenticate(username);
        when(authService.resolveUserIdByUsername(username)).thenReturn(100L);
        when(stageService.currentStage(42L)).thenReturn(ProjectStage.RETROSPECTIVE);
        when(stageService.hasClosureSubmission(42L)).thenReturn(false);
        when(stageService.allowedNext(42L)).thenReturn(List.of(ProjectStage.CLOSED));
        when(bidReviewAppService.getReviewState(42L)).thenReturn(
                new BidReviewAppService.ReviewState("REVIEWING", 9999L, null, "其他人", List.of()));
        when(closureRepository.findByProjectId(42L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/projects/42/stage").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessibleStages", org.hamcrest.Matchers.hasItem("CLOSED")));
    }
}
```

### Green 阶段：实现变更

修改 `ProjectStageController.get()`（按"最终实现（修订版）"段落）。

### Refactor 阶段

- 抽局部变量 `hasClosureSubmission` 消除重复 service 调用（已在实现中体现）
- 确认 controller 行数没增加太多（仍 < 120 行）

### 回归保障

- 既有 `co315_*` / `non_reviewer_*` / `co443_*` 4 个用例必须全绿（验证 FR-3 边界守护）
- `ProjectRetrospectiveServiceTest` 既有用例（特别是 `submit_transitionsToRetrospectiveStage_onlyOnce_notToClosed`）必须全绿（验证 AC-6）

---

## 风险评估

| 风险 | 严重度 | 缓解 |
|---|---|---|
| CLOSED 在 accessibleStages 出现两次（重复） | LOW | `co498_retrospectiveWithClosureDraft_doesNotUnlockClosedTwice` 测试覆盖；用 `actual == RETROSPECTIVE` 精确判定，closure 已存在时 current=CLOSED，CLOSED 已在 completed 中 |
| 前端 ClosureStage 加载时项目权限校验失败（403） | LOW | 不改 `ProjectAccessScopeService`，既有权限校验对结项 tab 同样适用；已通过 `assertCurrentUserCanAccessProject` 验证 |
| admin 进入结项 tab 后误以为能提交 | LOW | `canSubmitClosure` 已限制为 bid-projectLeader，admin 看不到"提交结项"按钮；UI 上无引导文案是已知 tradeoff，不在本次范围 |
| 已部署的 release `76c425667` 与本修复不兼容 | LOW | 本修复是后端 controller 微调，无 schema 变更，无新依赖，向前兼容 |

---

## 复杂度追踪

| 维度 | 当前 | 变更后 | 阈值 |
|---|---|---|---|
| `ProjectStageController.java` 行数 | 112 | ~118 | 软上限 200，硬上限 300 |
| 新增依赖 | 0 | 0 | — |
| 新增 Bean | 0 | 0 | — |
| Flyway 迁移 | 0 | 0 | — |

---

## Constitution Check

已对照 `.specify/memory/constitution.md` v1.3.1 全部原则（详见上方"Constitution 对齐"表）。无违反，无需在 Complexity Tracking 中记录例外。
