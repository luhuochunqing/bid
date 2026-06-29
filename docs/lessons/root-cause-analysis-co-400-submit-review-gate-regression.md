# CO-400 submitForReview 误用任务完成闸门导致标书审核无法提交 根因分析

> 日期: 2026-06-29
> 排查者: codex
> 修复 PR: 待创建 / `agent/codex/fix-co400-submit-review-gate-regression`
> 教训条目: `docs/lessons/lessons-learned.md §25`

---

## 现场还原

**症状素描**：项目 `/project/113` 在 DRAFTING 阶段，用户点击「提交投标审核」按钮，前端弹出"仍有 1 个任务未完成，无法提交标书审核"，无法发起标书审核流程。

**边界划定**：
- 「提交投标审核」对应后端 `POST /api/projects/{id}/drafting/submit-review` → `ProjectDraftingService.submitForReview`
- 与「提交投标」(`submitBid`，推进到评标阶段) 是不同的业务入口
- zcode 2026-06-21 曾修复过 `submitBid` 同类问题（PR !923），但 `submitForReview` 路径一直保留着任务闸门

**思维沙箱**：不要把 `AllTasksCompletedPolicy` 改成把更多任务状态视为完成；真正变化的是 `submitForReview` 的业务前置条件——发起审核时标书可能仍在编制，任务完成与否是审核人的判断，不是闸门。

---

## 剥洋葱：逆向调用链

### Layer 1 — API 入口层

用户点击「提交投标审核」后进入 `ProjectDraftingService.submitForReview`。

```java
// backend/src/main/java/com/xiyu/bid/project/service/ProjectDraftingService.java:113-124
public ProjectDraftingViewDto submitForReview(Long projectId, Long reviewerId, Long currentUserId) {
    ProjectLeadAssignment lead = assertCanSubmit(projectId, currentUserId);
    assertBidReadiness(projectId, "无法提交标书审核");  // ← 零号病人
    ...
}
```

### Layer 2 — 闸门复用层

`submitForReview` 误调用 `assertBidReadiness`，复用了为 `submitBid` 设计的「任务全完成 + 标书文件已上传」闸门：

```java
// backend/src/main/java/com/xiyu/bid/project/service/ProjectDraftingService.java（修复前 L226-240）
private void assertBidReadiness(Long projectId, String action) {
    List<AllTasksCompletedPolicy.TaskState> taskStates = taskRepository.findByProjectId(projectId).stream()
            .map(t -> t.getStatus() == null
                    ? AllTasksCompletedPolicy.TaskState.TODO
                    : AllTasksCompletedPolicy.TaskState.valueOf(t.getStatus().name()))
            .toList();
    boolean hasBidDocument = !projectDocumentRepository
            .findByProjectIdAndFiltersOrderByCreatedAtDesc(
                    projectId, BidReadinessPolicy.BID_DOCUMENT_CATEGORY, null, null)
            .isEmpty();
    BidReadinessPolicy.Decision d = BidReadinessPolicy.check(taskStates, hasBidDocument);
    if (!d.allowed()) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, d.reason() + "，" + action);
    }
}
```

### Layer 3 — 闸门核心层

`BidReadinessPolicy.check` 内部先检查任务全完成，再检查标书文件：

```java
// backend/src/main/java/com/xiyu/bid/project/core/BidReadinessPolicy.java（修复前 L32-48）
public static Decision check(List<AllTasksCompletedPolicy.TaskState> taskStates,
                              boolean hasBidDocument) {
    AllTasksCompletedPolicy.Decision taskDecision = AllTasksCompletedPolicy.decide(taskStates);
    if (!taskDecision.allowed()) {
        int incomplete = ((AllTasksCompletedPolicy.Decision.Deny) taskDecision).incompleteCount();
        return Decision.deny(Decision.Cause.STATE,
                "仍有 " + incomplete + " 个任务未完成");
    }
    if (!hasBidDocument) {
        return Decision.deny(Decision.Cause.STATE, "尚未上传标书文件");
    }
    return Decision.permit();
}
```

`AllTasksCompletedPolicy.decide` 把所有非 `COMPLETED` 状态都计入 incomplete（`TODO`/`REVIEW` 均算）：

```java
// backend/src/main/java/com/xiyu/bid/project/core/AllTasksCompletedPolicy.java:24
if (s == null || s != TaskState.COMPLETED) {
    incomplete++;
}
```

---

## 零号病人定位

**第一行错误：**

```java
// backend/src/main/java/com/xiyu/bid/project/service/ProjectDraftingService.java:118（修复前）
assertBidReadiness(projectId, "无法提交标书审核");
```

**必然性解释：**

1. 项目 113 在 DRAFTING 阶段，立项阶段自动生成的种子任务 id=5「【待立项】」残留为 `TODO` 状态未自动关闭。
2. 用户点击「提交投标审核」→ `submitForReview` 调用 `assertBidReadiness`。
3. `assertBidReadiness` 调用 `BidReadinessPolicy.check`，先检查任务全完成。
4. `AllTasksCompletedPolicy.decide` 把 `TODO` 计入 incomplete，返回 `Deny(1)`。
5. `BidReadinessPolicy.check` 返回 `deny("仍有 1 个任务未完成")`。
6. `submitForReview` 抛出 `ResponseStatusException(409, "仍有 1 个任务未完成，无法提交标书审核")`。
7. 用户看到错误提示，无法发起标书审核。

**状态变迁图：**

```text
立项阶段自动生成种子任务（id=4「待分配」、id=5「待立项」）
  → 用户标记 id=4 为 COMPLETED
  → 项目流转到 DRAFTING 阶段，id=5 残留为 TODO
  → 用户上传标书文件，点击「提交投标审核」
  → submitForReview 调用 assertBidReadiness
  → BidReadinessPolicy.check 发现 id=5 是 TODO
  → AllTasksCompletedPolicy.decide 返回 Deny(1)
  → 409，无法发起审核
```

---

## 生产日志证据（按 lessons §23 全链路日志排查 SOP）

| 字段 | 值 |
|---|---|
| 报错时间 | 2026-06-29 15:38:05 / 15:38:38 / 15:38:47（3 次重试） |
| 用户 | 06234（admin，OSS 用户） |
| 客户端 IP | 172.16.86.222 |
| 接口 | `POST /api/projects/113/drafting/submit-review` |
| HTTP 状态 | 409 Conflict |
| 前端提示 | 仍有 1 个任务未完成，无法提交标书审核 |
| traceId | `a2f9262d77d842029712a4595481d242` |
| 响应耗时 | 19ms（说明在 DB 查询后立即拒绝） |

**GlobalExceptionHandler 日志**（直接证据）：

```
2026-06-29T15:38:05.078526188+08:00
HTTP 409 CONFLICT - URI: /api/projects/113/drafting/submit-review,
Reason: 仍有 1 个任务未完成，无法提交标书审核
traceId=a2f9262d77d842029712a4595481d242
```

**DB 证据**（项目 113 的 tasks 表）：

| id | title | status | updated_at |
|---|---|---|---|
| 4 | 【待分配】第 100 个包子商机 | `COMPLETED` | 2026-06-29 15:35:00 |
| 5 | 【待立项】第 100 个包子商机 | **`TODO`** | 2026-06-25 22:55:07 |

**git blame 证据**（复发原因）：

| 行号 | commit | 作者/日期 | 说明 |
|---|---|---|---|
| L118 (submitForReview) | `95da4695b` | zhoufan 2026-06-25 | CO-346「submitForReview 补齐任务闸门」**首次添加** |
| L171 (submitBid) | `95da4695b` | zhoufan 2026-06-25 | 同一 commit **把 zcode PR !923 的修复一并回退** |

---

## 临时止血（已执行）

**操作**：将项目 113 的 task id=5 状态从 `TODO` 改为 `COMPLETED`（曾尝试 `CANCELLED`，但 `Task.Status` 枚举只有 `TODO/REVIEW/COMPLETED`，会引发 500，已回滚为 `COMPLETED`）。

```sql
UPDATE tasks SET status='COMPLETED',
    completion_notes='CO-400 临时止血：立项阶段残留种子任务，标书编制阶段已无业务意义，由 Agent 排查根因时关闭',
    updated_at=NOW()
WHERE id=5 AND project_id=113 AND status='TODO';
```

**新发现的隐患**：`AllTasksCompletedPolicy.TaskState` 枚举只有 `TODO/REVIEW/COMPLETED` 三态，缺 `CANCELLED`。详见 `docs/lessons/lessons-learned.md §25` 后续优化项 1。

---

## 验证与修复

### 修复 diff（核心）

1. **拆分 `BidReadinessPolicy.check`** 为两个语义清晰的方法：

```diff
- public static Decision check(List<AllTasksCompletedPolicy.TaskState> taskStates,
-                               boolean hasBidDocument) {
-     AllTasksCompletedPolicy.Decision taskDecision = AllTasksCompletedPolicy.decide(taskStates);
-     if (!taskDecision.allowed()) {
-         int incomplete = ((AllTasksCompletedPolicy.Decision.Deny) taskDecision).incompleteCount();
-         return Decision.deny(Decision.Cause.STATE,
-                 "仍有 " + incomplete + " 个任务未完成");
-     }
-     if (!hasBidDocument) {
-         return Decision.deny(Decision.Cause.STATE, "尚未上传标书文件");
-     }
-     return Decision.permit();
- }

+ public static Decision checkBidDocumentUploaded(boolean hasBidDocument) {
+     if (!hasBidDocument) {
+         return Decision.deny(Decision.Cause.STATE, "尚未上传标书文件");
+     }
+     return Decision.permit();
+ }

+ public static Decision checkBidSubmissionReady(List<AllTasksCompletedPolicy.TaskState> taskStates,
+                                                 boolean hasBidDocument) {
+     AllTasksCompletedPolicy.Decision taskDecision = AllTasksCompletedPolicy.decide(taskStates);
+     if (!taskDecision.allowed()) {
+         int incomplete = ((AllTasksCompletedPolicy.Decision.Deny) taskDecision).incompleteCount();
+         return Decision.deny(Decision.Cause.STATE,
+                 "仍有 " + incomplete + " 个任务未完成");
+     }
+     return checkBidDocumentUploaded(hasBidDocument);
+ }
```

2. **`ProjectDraftingService.submitForReview` 改用 `checkBidDocumentUploaded`**（移除任务闸门）：

```diff
- assertBidReadiness(projectId, "无法提交标书审核");
+ assertBidDocumentUploaded(projectId, "无法提交标书审核");
```

3. **`submitBid` 和 `gateAdvanceToEvaluation` 保持调用 `checkBidSubmissionReady`**（保留 zhoufan 修复语义不回退，避免重蹈"反复回退"覆辙）。

### 防复发测试

- `BidReadinessPolicyTest.checkBidDocumentUploaded_permits_whenHasBidDocument`（正例）
- `BidReadinessPolicyTest.checkBidDocumentUploaded_denies_whenNoBidDocument`（反例）
- `BidReadinessPolicyTest.checkBidSubmissionReady_*`（5 个原测试改名，保留语义）
- `ProjectDraftingServiceTest.submitForReview_incompleteTasks_withBidDocument_delegatesToBidReview`（CO-400 修复正例，覆盖本次复发路径）
- `ProjectDraftingServiceTest.submitForReview_missingBidDocument_denied_409`（反例，保留标书文件校验）
- `ProjectDraftingServiceTest.submitBid_approvedReview_incompleteTasks_denied_409`（保留 zhoufan 修复语义）

### 验证命令

```bash
cd backend
mvn test -Dtest=BidReadinessPolicyTest,ProjectDraftingServiceTest,AllTasksCompletedPolicyTest,BidReviewPolicyTest
mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest
```

结果：113 个测试通过，0 failure/error/skip。

---

## 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| 零号病人已定位 | `ProjectDraftingService.submitForReview` L118 调用 `assertBidReadiness` | ✅ |
| 必然性已证明 | 项目 113 存在 1 个 TODO 任务 → `AllTasksCompletedPolicy.decide` deny → `BidReadinessPolicy.check` deny → 409 | ✅ |
| 复发原因已查明 | 2026-06-25 commit `95da4695b` 同时回退 `submitBid` 修复 + 首次给 `submitForReview` 加闸门 | ✅ |
| 生产日志验证 | traceId=`a2f9262d77d842029712a4595481d242`，3 次 409 全部抓到，DB 确认 task id=5 是 TODO | ✅ |
| 修复 diff 已提供 | 拆分 `BidReadinessPolicy` + `submitForReview` 改用 `checkBidDocumentUploaded` | ✅ |
| 防复发测试已设计 | `submitForReview_incompleteTasks_withBidDocument_delegatesToBidReview` + `submitForReview_missingBidDocument_denied_409` | ✅ |

**Verdict**: ✅ **PASS**

### 防复发测试清单

1. `submitForReview` 必须覆盖「任务未完成 + 标书文件已上传 → 200 委托」正例（CO-400 修复正例）。
2. `submitForReview` 必须覆盖「无标书文件 → 409」反例（保留标书文件校验）。
3. `submitBid` 必须继续覆盖「审核通过 + 任务未完成 → 409」反例（保留 zhoufan 修复语义不回退）。
4. `BidReadinessPolicy` 必须为 `checkBidDocumentUploaded` 和 `checkBidSubmissionReady` 各提供正反例。
5. 不要通过扩大 `AllTasksCompletedPolicy` 终态集合来绕过业务语义问题。

---

## 相关文档

- `docs/lessons/lessons-learned.md §23` — 全链路日志排查 SOP
- `docs/lessons/lessons-learned.md §24` — Policy 修改必须审视整个权限矩阵
- `docs/lessons/lessons-learned.md §25` — submitBid / submitForReview 闸门不可共用（本次新增）
- `docs/lessons/root-cause-analysis-submit-bid-review-gate.md` — zcode 2026-06-21 历史根因分析（同类问题）
- `backend/src/main/java/com/xiyu/bid/project/core/BidReadinessPolicy.java` — Policy 实现（已拆分）
- `backend/src/main/java/com/xiyu/bid/project/service/ProjectDraftingService.java` — 调用方（已修正）
- 生产日志 traceId：`a2f9262d77d842029712a4595481d242`（2026-06-29 15:38:05）
