# Implementation Plan: 修复 CO-400 submitForReview 误用任务完成闸门

**Spec**: `022-fix-co400-submit-review-gate-regression`
**Created**: 2026-06-29
**Status**: Draft

## 设计决策

### 决策 1：拆分 BidReadinessPolicy 而非移除 submitForReview 闸门调用

**选项 A**：直接从 `submitForReview` L118 移除 `assertBidReadiness` 调用
- 优点：改动最小
- 缺点：会同时移除"标书文件已上传"校验，导致审核人可能收到无标书的项目

**选项 B（采用）**：拆分 `BidReadinessPolicy.check` 为两个语义清晰的方法
- `checkBidDocumentUploaded(hasBidDocument)` — 仅校验标书文件已上传（submitForReview 用）
- `checkBidSubmissionReady(taskStates, hasBidDocument)` — 校验任务全完成 + 标书文件已上传（submitBid 用）
- 优点：保留标书文件校验，避免引入新 Bug；语义清晰，便于未来防复发
- 缺点：改动稍大，需调整调用方和测试

### 决策 2：保留 submitBid 的任务闸门（不再次回退）

zcode 2026-06-21 PR !923 移除了 `submitBid` 的任务闸门，但 zhoufan 2026-06-25 commit `95da4695b` 又加回来。本次修复**不再回退 `submitBid`**，原因：
1. zcode 的修复曾引发争议（任务未完成时是否应允许推进到评标），保留 `submitBid` 闸门是 zhoufan 的有意决策
2. 本次 Bug 的是 `submitForReview` 路径，与 `submitBid` 无关
3. 避免重蹈"反复回退"覆辙（lessons §24 已教训）
4. 如果产品决定 `submitBid` 也应移除任务闸门，应作为独立 PR 讨论

### 决策 3：防复发测试覆盖

恢复 zcode 防复发要求 + 新增本次路径覆盖：
- `submitBid_approvedReview_allowsIncompleteTasks` — zcode 2026-06-21 防复发要求（如已被回退则恢复）
- `submitForReview_allowsIncompleteTasks` — 覆盖本次复发路径
- `submitForReview_missingBidDocument_returns409` — 保留标书文件校验反例

## 改动文件清单

### 后端核心代码

| 文件 | 改动 |
|---|---|
| `backend/src/main/java/com/xiyu/bid/project/core/BidReadinessPolicy.java` | 拆分 `check` 为 `checkBidDocumentUploaded` + `checkBidSubmissionReady`；修正误导性注释 |
| `backend/src/main/java/com/xiyu/bid/project/service/ProjectDraftingService.java` | `submitForReview` L118 改用 `checkBidDocumentUploaded`；`assertBidReadiness` 拆为两个辅助方法或参数化 |

### 后端测试

| 文件 | 改动 |
|---|---|
| `backend/src/test/java/com/xiyu/bid/project/core/BidReadinessPolicyTest.java` | 新增 `checkBidDocumentUploaded_*` 测试用例 |
| `backend/src/test/java/com/xiyu/bid/project/service/ProjectDraftingServiceTest.java` | 新增 `submitForReview_allowsIncompleteTasks` + `submitForReview_missingBidDocument_returns409`；如 `submitBid_approvedReview_allowsIncompleteTasks` 已被回退则恢复 |

### 文档

| 文件 | 改动 |
|---|---|
| `docs/lessons/lessons-learned.md` | 新增 §25「submitBid / submitForReview 闸门不可共用，业务语义不同」教训条目 |
| `docs/lessons/root-cause-analysis-co-400-submit-review-gate-regression.md` | 新增本次根因分析报告（参考 `root-cause-analysis-submit-bid-review-gate.md` 格式） |

## 风险与缓解

| 风险 | 缓解措施 |
|---|---|
| 修改 `BidReadinessPolicy` 可能影响 `submitBid` 现有行为 | 拆分时保持 `checkBidSubmissionReady` 与原 `check` 行为完全一致；新增测试覆盖 `submitBid` 不变 |
| 防复发测试可能再次被回退 | lessons-learned §25 明确要求"修改 `BidReadinessPolicy` 时必须检查 submitBid/submitForReview 是否对称" |
| 立项种子任务残留问题未根治 | 在 lessons-learned §25 列为后续优化项，独立任务处理 |
| `AllTasksCompletedPolicy.TaskState` 缺 CANCELLED 枚举值（本次止血时发现） | 在 lessons-learned §25 列为后续优化项，独立任务处理 |

## 验证策略

```bash
cd backend
mvn test -Dtest=BidReadinessPolicyTest,ProjectDraftingServiceTest,AllTasksCompletedPolicyTest,BidReviewPolicyTest
mvn test -Dtest=ArchitectureTest
mvn test -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest
```

预期：全部通过，0 failure/error/skip。
