# Feature Specification: 修复 CO-400 submitForReview 误用任务完成闸门导致标书审核无法提交

**Feature Branch**: `agent/codex/fix-co400-submit-review-gate-regression`

**Created**: 2026-06-29

**Status**: Draft

**Input**: 生产环境项目 113 报错 `POST /api/projects/113/drafting/submit-review` 返回 409「仍有 1 个任务未完成，无法提交标书审核」，根因为 `ProjectDraftingService.submitForReview` 误调用 `assertBidReadiness`，把"任务全完成"闸门错配到"提交标书审核"业务入口。

## 问题分析

### 现场还原（按【全链路日志排查 SOP】取证）

| 字段 | 值 |
|---|---|
| 报错时间 | 2026-06-29 15:38:05 / 15:38:38 / 15:38:47（3 次重试） |
| 用户 | 06234（admin，OSS 用户） |
| 接口 | `POST /api/projects/113/drafting/submit-review` |
| HTTP 状态 | 409 Conflict |
| traceId | `a2f9262d77d842029712a4595481d242` |
| 响应耗时 | 19ms（说明在 DB 查询后立即拒绝） |

**DB 证据**（项目 113 的 tasks 表）：

| id | title | status | updated_at |
|---|---|---|---|
| 4 | 【待分配】第 100 个包子商机 | `COMPLETED` | 2026-06-29 15:35:00 |
| 5 | 【待立项】第 100 个包子商机 | **`TODO`** | 2026-06-25 22:55:07 |

项目状态：`stage=DRAFTING`（已进入编制阶段）。立项阶段自动生成的种子任务 id=5 残留为 TODO 未自动关闭，触发 `AllTasksCompletedPolicy.decide` 返回 `Deny(1)`。

### 完整调用链

```
[前端] DraftingBidPanel.vue:70 submitBidForReview(113)
   ↓ POST /api/projects/113/drafting/submit-review
[后端] ProjectDraftingService.submitForReview [L113-124]
   ↓ L118: assertBidReadiness(projectId, "无法提交标书审核")
   ↓ L226-240: 私有方法 assertBidReadiness
   ↓ L236: BidReadinessPolicy.check(taskStates, hasBidDocument)
   ↓ BidReadinessPolicy.java L35: AllTasksCompletedPolicy.decide([COMPLETED, TODO])
   ↓ AllTasksCompletedPolicy.java L24: s != COMPLETED → incomplete++
        → Deny(1)
   ↓ BidReadinessPolicy.java L37-39: deny("仍有 1 个任务未完成")
   ↓ ProjectDraftingService.java L238:
       throw ResponseStatusException(CONFLICT, "仍有 1 个任务未完成，无法提交标书审核")
   ↓ HTTP 409
```

### 业务语义错配

`submitForReview`（**提交标书给审核人审核**）和 `submitBid`（**审核通过后推进到评标阶段**）是业务语义完全不同的两个入口：

| 入口 | 业务语义 | 是否应要求"任务全完成" |
|---|---|---|
| `submitForReview` | 发起审核，让审核人提前介入审查 | ❌ 不应（标书可能仍在编制） |
| `submitBid` | 审核已通过，推进阶段 | 可议（但 zcode 2026-06-21 已确认不应） |

[BidReadinessPolicy.java:14-15](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/project/core/BidReadinessPolicy.java#L14) 注释错误地称"两处业务入口复用闸门避免规则漂移"——这恰恰是**规则错配**的源头。

### 复发原因（git blame 证据）

| 行号 | commit | 作者/日期 | 说明 |
|---|---|---|---|
| L118 (submitForReview) | `95da4695b` | zhoufan 2026-06-25 | CO-346「submitForReview 补齐任务闸门」**首次添加** |
| L171 (submitBid) | `95da4695b` | zhoufan 2026-06-25 | 同一 commit **把 zcode PR !923 的修复一并回退** |

**复发时间线**：

| 时间 | 事件 | 来源 |
|---|---|---|
| 2026-06-21 | zcode 发现 `submitBid` 同类 Bug，PR !923 移除任务闸门，留下防复发测试要求 | `docs/lessons/root-cause-analysis-submit-bid-review-gate.md` |
| 2026-06-25 | zhoufan 在 CO-346 修复中（commit `95da4695b`）同时给 `submitBid` 和 `submitForReview` 加回任务闸门 | git blame L118/L171 |
| 2026-06-29 15:38 | 用户在项目 113 触发同类 Bug | 生产日志 traceId=`a2f9262d77d842029712a4595481d242` |

这违反了：
- `docs/lessons/lessons-learned.md §24` "修改 Policy 时必须审视整个权限矩阵"——CO-346 修复时未审视 `submitBid/submitForReview` 闸门是否对称
- `docs/lessons/lessons-learned.md §23` SOP 第 4 步"禁止乱猜"——CO-346 修复时未用日志证据验证业务语义
- zcode 防复发要求"`submitBid` 必须覆盖「审核通过但任务未完成仍允许提交」正例"——该测试要么没加，要么被一并回退

## 范围边界

### 修复范围

- 拆分 `BidReadinessPolicy.check` 为两个语义清晰的方法：
  - `checkBidDocumentUploaded(hasBidDocument)` — 仅校验标书文件已上传
  - `checkBidSubmissionReady(taskStates, hasBidDocument)` — 校验任务全完成 + 标书文件已上传（保留 `submitBid` 行为）
- `ProjectDraftingService.submitForReview` 改用 `checkBidDocumentUploaded`
- `ProjectDraftingService.submitBid` 保持调用 `checkBidSubmissionReady`（保留 zcode 修复语义，避免再次回退争议）
- 恢复/新增防复发测试
- 修正 `BidReadinessPolicy` 误导性注释

### 不在范围

- `AllTasksCompletedPolicy.TaskState` 添加 CANCELLED 枚举值（影响面大，独立任务）
- 立项阶段种子任务残留问题（治本但涉及 ProjectInitiationService，独立任务）
- `submitBid` 是否也应移除任务闸门（zcode 历史已确定保留，不在本次讨论）

## 测试场景

### 后端 `submitForReview`

| 场景 | 任务状态 | 标书文件 | 审核记录 | 预期结果 |
|---|---|---|---|---|
| 标书文件已上传 + 任务未完成 | [COMPLETED, TODO] | true | 无 | **200 推进** |
| 标书文件未上传 | [COMPLETED] | false | 无 | 409 拒绝 |
| 审核已在进行中 | [TODO] | true | REVIEWING | 409 拒绝（BidReviewPolicy.canSubmitReview 拒绝） |

### 后端 `submitBid`（保留 zcode 修复语义，防回退）

| 场景 | 审核状态 | 任务状态 | 预期结果 |
|---|---|---|---|
| 审核已通过 + 任务全完成 | APPROVED | [COMPLETED] | 推进到 EVALUATING |
| 审核已通过 + 任务未完成 | APPROVED | [COMPLETED, TODO] | 409 拒绝（保留 zcode 修复后语义） |
| 审核未通过 | REVIEWING | [COMPLETED] | 409 拒绝 |

## 关联文档

- `docs/lessons/root-cause-analysis-submit-bid-review-gate.md` — zcode 2026-06-21 历史根因分析（同类问题）
- `docs/lessons/lessons-learned.md §23` — 全链路日志排查 SOP
- `docs/lessons/lessons-learned.md §24` — Policy 修改必须审视整个权限矩阵
- 生产日志 traceId：`a2f9262d77d842029712a4595481d242`（2026-06-29 15:38:05）
