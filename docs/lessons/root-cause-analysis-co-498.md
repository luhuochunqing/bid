# CO-498 项目复盘阶段提交后无法进入结项阶段 根因分析

> 日期: 2026-07-04
> 排查者: zcode
> 修复 PR: https://gitee.com/allinai888/bid/pulls/1681
> 教训条目: `docs/lessons/lessons-learned.md §38`
> 关联工作项: CO-498（本 PR）/ CO-443（假 CLOSED 机制）/ CO-403（结项职责分离）

---

## 现场还原

**症状素描**：项目 `/project/157` 在复盘阶段（RETROSPECTIVE）提交复盘后，前端导航时间线上的"结项"tab 显示为「待进入」且不可点击。项目负责人无法进入结项阶段填写保证金退回情况并提交结项申请，整个结项审核流程从源头死锁。

**边界划定**：
- 复盘提交对应后端 `POST /api/projects/{id}/retrospective` → `ProjectRetrospectiveService.submit`
- 阶段查询对应 `GET /api/projects/{id}/stage` → `ProjectStageController.get`
- tab 解锁判定在前端 `ProjectStageTimeline.vue:74-81` 的 `isUnlocked(stage)`
- **本 bug 不触发任何异常**（HTTP 200 OK，无 5xx），是典型的"业务逻辑错误、Sentry 未覆盖场景"（按 SOP §23 Layer 2 适用）

**思维沙箱**：不要急着改 `RetrospectiveService.submit()` 让它"自动推进到 CLOSED"——这会重新引入 CO-443 已经修过的 `alreadyClosed` 误判 bug。要追问的是：既然 `RetrospectiveService.submit()` 故意停在 RETROSPECTIVE 是设计如此，那么"用户进入结项 tab"的入口在哪里？为什么入口断了？

---

## 剥洋葱：逆向调用链

### Layer 1 — 前端 tab 解锁层

用户看到的"待进入"标签来自前端 `ProjectStageTimeline.vue`：

```javascript
// src/components/project/stage/ProjectStageTimeline.vue:74-81
function isUnlocked(stage) {
  const accessible = snapshot.value?.accessibleStages
  if (Array.isArray(accessible) && accessible.length > 0) {
    return accessible.includes(stage.code)   // ← 唯一判定来源
  }
  const idx = stages.findIndex((s) => s.code === stage.code)
  return idx <= activeIndex.value
}

function describe(stage) {
  // ...
  const accessible = snapshot.value?.accessibleStages
  if (Array.isArray(accessible) && accessible.includes(stage.code) && stage.code !== currentCode.value) {
    return '可进入'
  }
  return '待进入'   // ← CLOSED tab 显示这个
}
```

**关键契约**：tab 是否可点完全取决于后端返回的 `accessibleStages` 字段是否包含该阶段 code。这是前后端隐式契约——后端字段计算逻辑变更会直接影响前端 tab 解锁。

### Layer 2 — Controller 字段计算层

`ProjectStageController.get()`（修复前 `L45-83`）如何计算 `accessibleStages`：

```java
// backend/src/main/java/com/xiyu/bid/project/controller/ProjectStageController.java（修复前）
ProjectStage actual = service.currentStage(projectId);
ProjectStage current = (actual != ProjectStage.CLOSED && service.hasClosureSubmission(projectId))
        ? ProjectStage.CLOSED
        : actual;
// ...
List<String> completed = Arrays.stream(ProjectStage.values())
        .filter(s -> s.ordinal() < current.ordinal())
        .map(Enum::name).toList();
List<String> accessible = new ArrayList<>(completed);
accessible.add(current.name());
// ← CLOSED 只在 current==CLOSED 时才进 accessible
// ← RETROSPECTIVE 阶段（无 closure）时 current=RETROSPECTIVE，CLOSED 永远不进 accessible
```

**bug 核心逻辑**：当 `actual=RETROSPECTIVE` 且 `hasClosureSubmission=false`（项目 157 当前状态）时：
- `current = actual = RETROSPECTIVE`
- `accessible = [INITIATED, DRAFTING, EVALUATING, RESULT_PENDING, RETROSPECTIVE]`（5 项）
- **CLOSED 永远不在 accessible 中** → 前端 tab 锁死

### Layer 3 — Service 行为变更层

为什么 `actual` 会停在 `RETROSPECTIVE`？因为 commit `5d1b36b53` 删除了 `ProjectRetrospectiveService.submit()` 中的二次推进：

```java
// backend/src/main/java/com/xiyu/bid/project/service/ProjectRetrospectiveService.java
// 5d1b36b53 删除的代码：
-        // §2.6: 复盘无需审核，提交即转。submit() 直达 CLOSED。
-        ProjectStage afterRetroTransition = projectStageService.currentStage(projectId);
-        if (afterRetroTransition == ProjectStage.RETROSPECTIVE) {
-            projectStageService.requestTransition(projectId, ProjectStage.CLOSED,
-                    ProjectStageTransitionPolicy.GateInputs.EMPTY);
-        }

// 5d1b36b53 替换为注释：
+        // 复盘提交后阶段停在 RETROSPECTIVE，不直达 CLOSED。
+        // CLOSED 由结项审核流程驱动：用户在 ClosureStage 提交结项申请 → ...
```

`5d1b36b53` 的修改本身**正确**（修了 CO-443 `alreadyClosed` 误判 bug），但删除了直达 CLOSED 后，没人审视 `ProjectStageController.get()` 的 `accessibleStages` 计算逻辑——它一直依赖"复盘提交后 stage 会变 CLOSED"这个隐式假设。一旦 stage 停在 RETROSPECTIVE，CLOSED 就再也进不了 accessible。

---

## 零号病人定位

**第一行错误**：

```java
// backend/src/main/java/com/xiyu/bid/project/controller/ProjectStageController.java:58-62（修复前）
List<String> accessible = new ArrayList<>(completed);
accessible.add(current.name());
// 缺失：当 actual==RETROSPECTIVE 且 !hasClosureSubmission 时，
// CLOSED 应加入 accessible（让项目负责人能进入结项 tab 提交结项申请）
```

**必然性解释**：

1. 项目 157 完成结果登记（WON），stage 推进到 RETROSPECTIVE
2. 用户提交复盘，`ProjectRetrospectiveService.submit()` 按 `5d1b36b53` 设计**停在 RETROSPECTIVE**（不直达 CLOSED）
3. 用户查看项目阶段，`ProjectStageController.get()` 计算返回
4. `actual=RETROSPECTIVE`、`hasClosureSubmission=false` → `current=RETROSPECTIVE` → `accessible=[5项]`，**不含 CLOSED**
5. 前端 `isUnlocked(CLOSED)` 检查 `accessibleStages.includes('CLOSED')` → false → CLOSED tab 显示「待进入」、点击触发 `ElMessage.info('该阶段尚未到达，无法进入')`
6. 项目负责人进不去结项 tab → 无法提交结项申请 → 整个结项审核流程死锁

**状态变迁图**：

```text
复盘提交（按 5d1b36b53 设计）
  → stage 停在 RETROSPECTIVE（正确）
  → closure 记录不存在（用户还没机会提交结项申请）
  → ProjectStageController.get() 计算 accessibleStages
  → current=RETROSPECTIVE，CLOSED 不在 accessible
  → 前端 ProjectStageTimeline.isUnlocked(CLOSED)=false
  → CLOSED tab 锁死，用户进不去
  → 结项申请永远提交不了 → 流程死锁
```

---

## 生产日志证据（按 lessons §23 全链路日志排查 SOP）

### Layer 1：Sentry（不可用）

| 字段 | 值 |
|---|---|
| 生产 release | `76c425667-api8080` |
| `sentryEnabled` | **`false`**（DSN 未配置） |
| 异常捕获 | 无（本 bug 无 5xx 异常） |

**适用判定**：按 SOP §23，属于"Layer 2 适用：业务逻辑错误、Sentry 未覆盖场景"。

### Layer 2：DB + TraceId 全链路还原（项目 157）

**项目 157 真实 DB 状态**（`projects` 表）：

| 字段 | 值 |
|---|---|
| `stage` | `RETROSPECTIVE`（未推进到 CLOSED） |
| `status` | `WON`（中标） |
| `closed_at` | `NULL` |
| `updated_at` | 2026-07-04 17:07:16 |

**关联表状态**：

| 表 | 状态 |
|---|---|
| `project_retrospective` (id=27) | ✅ 存在，`review_status=APPROVED`，`created_at=17:07:46` |
| `project_closure` | ❌ **不存在**（用户根本没走过结项申请流程） |
| `project_result` (id=77) | ✅ 存在，`result_type=WON`，`registered_at=17:07:17` |

**完整时间轴**（`/var/log/xiyu-bid/application.json.log`，traceId 全链路）：

| 时间 | 事件 | traceId |
|---|---|---|
| 17:02:28 | INITIATED→DRAFTING | `7399324f…` |
| 17:06:54 | DRAFTING→EVALUATING | `5fd18b45…` |
| 17:07:05 | EVALUATION IN_PROGRESS→RESULT_OUT | `ca445628…` |
| 17:07:06 | EVALUATING→RESULT_PENDING | `73ead377…` |
| 17:07:16 | RESULT_PENDING→RETROSPECTIVE | `b6201ea0…` |
| 17:07:45 | **Retrospective submitted project=157 status=APPROVED user=7220** | `de440c75…` |
| 之后 | **没有任何 RETROSPECTIVE→CLOSED 的 stage 推进日志** | — |

**关键观察**：复盘提交日志（traceId=`de440c75…`）之后，**完全没有** `Project stage transitioned project=157 RETROSPECTIVE→CLOSED`——与 `5d1b36b53` 设计完全吻合（不自动推进）。提交人 `user=7220` 对应用户 `06234`，`roleCode=admin`，权限集包含 `retrospective.submit`、`closure.request`、`closure.review`。

### Layer 3：git 追溯

```
5d1b36b53  fix(retrospective): 复盘提交后阶段停在 RETROSPECTIVE，由结项审核流程驱动到 CLOSED
           作者：褚永恩  时间：2026-07-04 13:47:13 +0800  已在 origin/main
           随 release 76c425667-api8080 于 2026-07-04 08:58:39Z 上线
```

**该 commit 的完整 commit message**（关键证据）：

> 根因：PR !729（ca1250e6b5, 2026-06-17）让 RetrospectiveService.submit() 直达 CLOSED，
> 绕过了 CO-443 设计的"假 CLOSED"机制和 ClosureService 的结项审核流程。
> 复盘提交后 ClosureService.preview 的 alreadyClosed 判定为 true（stage=CLOSED），
> ClosureStage 的"提交结项"按钮被隐藏，整个结项审核流程被跳过。
>
> 修复：删除 submit() 中"RETROSPECTIVE → CLOSED"的二次推进逻辑，阶段停在 RETROSPECTIVE。

`5d1b36b53` 修了 CO-443，但**没意识到** `ProjectStageController.get()` 的 `accessibleStages` 计算依赖"复盘后 stage=CLOSED"这个隐式假设。删除二次推进后，假设失效，CLOSED tab 永久锁死。

---

## 临时止血（未执行）

本 bug 在生产环境发现后**未做临时数据修复**——因为问题不在数据，而在 controller 计算逻辑。项目 157 的数据状态（`stage=RETROSPECTIVE` + 无 closure）是完全正确的，只是用户无法通过 UI 进入下一步。强制把 `stage` 改成 `CLOSED` 会引入更严重的 `alreadyClosed` 误判（CO-443 已经修过的 bug）。

正确做法是直接修复 controller 并部署，本次走完整 Spec Kit 流程后当天上线。

---

## 验证与修复

### 修复 diff（核心）

修改 `ProjectStageController.get()`，追加 3 行实现 + 注释：

```diff
         projectAccessScopeService.assertCurrentUserCanAccessProject(projectId);
         ProjectStage actual = service.currentStage(projectId);
+        boolean hasClosureSubmission = service.hasClosureSubmission(projectId);
         // CO-443: 已提交结项申请但审批未通过时，阶段尚未实际推进到 CLOSED，
         // 但前端进度导航栏应显示 CLOSED 为「进行中」而非「待进入」。
-        ProjectStage current = (actual != ProjectStage.CLOSED && service.hasClosureSubmission(projectId))
+        ProjectStage current = (actual != ProjectStage.CLOSED && hasClosureSubmission)
                 ? ProjectStage.CLOSED
                 : actual;
         // ...
         List<String> accessible = new ArrayList<>(completed);
         accessible.add(current.name());
+        // CO-498: 复盘阶段(stage=RETROSPECTIVE) 且未提交结项申请时，解锁 CLOSED tab。
+        // 用 actual 而非 current 判定：closure 已存在时 current=CLOSED，CLOSED 已在 completed 中，
+        // 此时不应再次追加（避免 accessibleStages 出现重复 CLOSED）。
+        if (actual == ProjectStage.RETROSPECTIVE && !hasClosureSubmission) {
+            accessible.add(ProjectStage.CLOSED.name());
+        }
         String defaultOpenStage = current.name();
```

**关键决策**：
- **抽局部变量 `hasClosureSubmission`**：复用 CO-443 已有 DB 查询，零额外开销，单一真相源
- **用 `actual` 而非 `current` 判定**：closure 已存在时 `current=CLOSED`（CO-443 假 CLOSED），此时 CLOSED 已在 `completed` 中（`ordinal < current.ordinal`），用 `actual==RETROSPECTIVE` 精确表达"真实仍在复盘阶段"
- **不按角色区分**：角色矩阵下沉到 `ClosureStage.canSubmitClosure`（仅 `bid-projectLeader` 可提交）/ `canApprove`（admin/组长/投标负责人/辅助 审核职责分离），与 CO-403 对齐

### 边界守护（不改的部分）

| 不改的代码 | 理由 |
|---|---|
| `RetrospectiveService.submit()` | 维持 `5d1b36b53` 行为，复盘提交停在 RETROSPECTIVE（避免回退 CO-443 修复） |
| `ProjectStageTransitionPolicy` FSM | RETROSPECTIVE→CLOSED 仍需显式 `requestTransition`，CLOSED 终态不可逆 |
| `ClosureStage.canSubmitClosure` / `canApprove` | 维持 CO-403 结项职责分离矩阵 |
| 前端 `ProjectStageTimeline.vue` | 后端 `accessibleStages` 放开后，前端 `isUnlocked` 自动生效 |

### 防复发测试（4 个新增）

`backend/src/test/java/com/xiyu/bid/project/controller/ProjectStageControllerTest.java`：

| 测试方法 | 验收标准 | 守护目标 |
|---|---|---|
| `co498_retrospectiveWithoutClosure_unlocksClosedTab` | AC-1 核心：stage=RETROSPECTIVE 无 closure 时 accessibleStages 含 CLOSED | 防止主路径再次锁死 |
| `co498_resultPendingStage_doesNotUnlockClosedTab` | AC-3 边界：stage=RESULT_PENDING 时 accessibleStages 不含 CLOSED | 防止解锁逻辑被泛化到非 RETROSPECTIVE 阶段 |
| `co498_retrospectiveWithClosureDraft_doesNotUnlockClosedTwice` | AC-2 重复守护：closure 已存在时 CLOSED 只出现一次 | 防止与 CO-443 假 CLOSED 双重计数 |
| `co498_retrospectiveWithoutClosure_unlocksClosedForAllRoles` | AC-4 角色一致性：admin/审核人/项目负责人看到的 accessibleStages 一致 | 防止角色判定回归 controller |

### 验证命令

```bash
# 1. 新增测试（Red→Green）
cd backend && mvn test -Dtest='ProjectStageControllerTest'
# 期望：9 个测试全绿（既有 5 + 新增 4）

# 2. 广泛回归（含 CO-443 / CO-403 既有用例）
cd backend && mvn test -Dtest='ProjectStage*Test,ProjectClosure*Test,ProjectRetrospective*Test,ProjectStatusPolicyTest,ProjectStageTransitionPolicyTest'
# 期望：270 个测试全绿
```

实际结果：270 个测试通过，0 failure/error/skip。

### Code Review

通过 `google-code-review` skill 审查：**LGTM with Comments**。

核心场景全链路推演（用 sequential-thinking 8 步验证）：
- `GET /stage` → accessibleStages 含 CLOSED ✅
- 前端 `isUnlocked(CLOSED)` = true → tab 可点 ✅
- `handleStageClick(CLOSED)` → `activeStageTab='CLOSED'` ✅
- `<ClosureStage>` 渲染 ✅
- `ProjectClosureService.preview` 返回 200 + DRAFT 状态（不抛 404）✅
- `canSubmitClosure=true`（bid-projectLeader）✅
- 用户填保证金退回 → 提交结项 → 等审核 ✅

**无第二层根因阻塞**。残余 3 项（admin 空白页 UX、brittle 断言、循环跑测试）为后续优化项，不阻塞合入。

---

## 强制二元结论

| 条件 | 验证方式 | 状态 |
|---|---|---|
| 零号病人已定位 | `ProjectStageController.get()` 修复前 L58-62，`accessible` 计算未考虑 RETROSPECTIVE 无 closure 时解锁 CLOSED | ✅ |
| 必然性已证明 | stage=RETROSPECTIVE + 无 closure → current=RETROSPECTIVE → accessible 不含 CLOSED → 前端 tab 锁死 → 死锁 | ✅ |
| 复发原因已查明 | `5d1b36b53` 修 CO-443 时删除了直达 CLOSED，未审视 `accessibleStages` 计算依赖此行为的隐式契约 | ✅ |
| 生产日志验证 | 项目 157 traceId=`de440c75…`，复盘提交后无 RETROSPECTIVE→CLOSED 推进日志，与代码设计吻合 | ✅ |
| 修复 diff 已提供 | `ProjectStageController.get()` 追加 3 行（用 actual 而非 current），抽局部变量复用查询 | ✅ |
| 防复发测试已设计 | 4 个新测试覆盖 AC-1/AC-2/AC-3/AC-4 + 既有 CO-443 三用例回归 | ✅ |
| 顽固 bug 第二层根因排除 | 全链路推演（含 ClosureStage 渲染、preview 加载、canSubmitClosure 计算）无阻塞 | ✅ |

**Verdict**: ✅ **PASS**

### 防复发测试清单

1. `ProjectStageControllerTest` 必须覆盖"stage=RETROSPECTIVE 且无 closure → accessibleStages 含 CLOSED"正例（CO-498 修复正例）。
2. `ProjectStageControllerTest` 必须覆盖"closure 已存在（DRAFT/PENDING/APPROVED 任一）→ accessibleStages 中 CLOSED 只出现一次"反例（防止与 CO-443 假 CLOSED 双重计数）。
3. `ProjectStageControllerTest` 必须覆盖"非 RETROSPECTIVE 阶段 → accessibleStages 不含 CLOSED"反例（防止解锁逻辑泛化）。
4. 既有 `co443_*` 三个用例必须保持全绿（验证本修复不破坏 CO-443 假 CLOSED 机制）。
5. `ProjectRetrospectiveServiceTest` 必须保持全绿（验证复盘提交行为不变，仍停在 RETROSPECTIVE）。

---

## 关联工作项关系图

```text
CO-443（假 CLOSED 机制）
  │  问题：closure 已提交但审批中时，currentStage 应显示 CLOSED（进行中）而非 RETROSPECTIVE
  │  实现：ProjectStageController.get() 用 hasClosureSubmission 把 current 改成 CLOSED
  │
  └─→ 5d1b36b53 修 CO-443 时删除了 RetrospectiveService 直达 CLOSED
        │  动机：避免 stage=CLOSED 触发 ClosureService.preview 的 alreadyClosed 误判
        │  副作用：accessibleStages 计算依赖"复盘后 stage=CLOSED"隐式假设，删除后假设失效
        │
        └─→ CO-498（本 PR）
              问题：复盘提交后 stage 停在 RETROSPECTIVE，CLOSED tab 永久锁死
              修复：ProjectStageController.get() 显式解锁 CLOSED tab（不依赖 stage=CLOSED）
              不动：RetrospectiveService.submit() / canSubmitClosure / CO-403 角色矩阵
```

**教训**：CO-443 与 CO-498 互为镜像——CO-443 的"假 CLOSED 显示"和 CO-498 的"真 RETROSPECTIVE 解锁 CLOSED tab"是两个独立但相关的视图层契约。修其中一个时必须同时审视另一个。

---

## 相关文档

- `docs/lessons/lessons-learned.md §23` — 全链路日志排查 SOP（本次排查使用 Layer 1-3）
- `docs/lessons/lessons-learned.md §38` — 修 bug 时删除代码必须审视隐式前后端字段契约（本次新增）
- `docs/lessons/lessons-learned.md §28` — 权限 Bug 必须审视同一业务动作的所有 UI 入口（同类：修 A bug 漏看 B 链路）
- `.specify/specs/024-co498-retrospective-closure-tab-unlock/spec.md` — Spec Kit 完整规格（7 条 AC + 3 个用户故事）
- `.specify/specs/024-co498-retrospective-closure-tab-unlock/plan.md` — 技术方案 + 8 个决策记录
- `backend/src/main/java/com/xiyu/bid/project/controller/ProjectStageController.java` — 修复点（commit `566e3a85b`）
- 生产日志 traceId：`de440c759b95463cb4d071eb9c4f6632`（2026-07-04 17:07:45，复盘提交）
- 修复 PR：https://gitee.com/allinai888/bid/pulls/1681（merged 2026-07-04 18:18:58 +08:00）
