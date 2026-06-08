# 标书审核"自我审批"漏洞 — 根因级修复 Implementation Notes

> 日期：2026-06-07
> 作者：Claude (agent/claude-init worktree)
> 根因链接：https://gitee.com/allinai888/bid/pulls/281 (IJSTZG 修复的根因)
> 关联 PR：gitee `!272` `!278` `!281` `!307`

## TL;DR

把"提交人不能审批自己"和"仅指派审核人可审批"两条**身份约束**从缺位的状态机策略补到 `BidReviewPolicy`，业务层强制传身份并根据 `Decision.Cause` 区分 HTTP 状态码（身份 → 403，状态 → 409）。新增 27 + 9 = 36 条测试覆盖。

## 1. 用户原话与本任务的偏差

用户原话："这是一个明显的bug 我们改了很多次了 比如 https://gitee.com/allinai888/bid/pulls/281 所以你这次要找到根因"。

我的偏差与额外发现：

### 1.1 我没动前端
用户隐含期望可能是"加个 v-if 条件"，但根因报告显示：
- 2026-06-05 ~ 2026-06-07 一周内修了 6 次（!208/!213/!272/!278/!281/!307/56aa9202a）
- 全部都是**前端 v-if 表达式**修补，每次加 OR/AND 条件
- 不同 worktree 出现**反向修复**（一个加 `isAssignedReviewer` OR，一个删）
- 前端 v-if 表达式分叉本身是个独立 issue（worktree 同步不一致），需要单独规划收口

**决定**：本次**只修后端**。理由：
1. 后端 403 兜底后，**所有前端入口**（主按钮、Dashboard 列表、通用 ApprovalDialog）都被服务端挡住——无需逐个加 if
2. 前端 v-if 分叉是独立 issue，**改后端不动前端**是更稳的策略
3. 中长期可以把前端表达式简化为 `reviewState === 'reviewing' && isAssignedReviewer`（后端兜底后 UX 简化）

### 1.2 我没建数据库迁移
调研发现 `BidDocumentReviewEntity.submittedBy` 和 `reviewerId` 字段（`@Column name="submitted_by" / "reviewer_id" nullable=false`）**早就存在**——`submitForReview` 在 06-06 那个 `!208` 引入的迁移就建好了。**数据层零变更**。

## 2. 设计决策：HTTP 状态码的拆分

最初想所有拒绝 → 403（"身份问题"），但写代码时意识到：

| 场景 | 旧行为 | 我最初改 | 应该改 |
|---|---|---|---|
| 状态机违规（已审核/已驳回） | 409 Conflict | 403 | **409 Conflict**（资源状态不允许） |
| 身份违规（自审/非指派人） | 409 Conflict | 403 | **403 Forbidden**（无权限） |
| 业务参数缺失（驳回原因为空） | 409 Conflict | 403 | **409 Conflict**（业务校验） |

**结论**：把 `Decision` record 扩了一个 `Cause` 枚举（`STATE` / `IDENTITY`），service 层根据 cause 选 HTTP 码。这样：
- 未来 BI/告警能直接根据 4xx 类型判断是"前端 bug"（403 = 身份层缺陷）还是"用户操作问题"（409 = 资源状态）
- 测试能精确断言："已通过状态应 409 而非 403"（见 `approveBid_alreadyApproved_throws409_not403`）

## 3. 边界场景的"我没有动"

调研发现前端还有 7+ 个其他审批入口（`ApprovalDialog.vue`、`Dashboard/ApprovalList.vue`、`InitiationStage.vue` 立项审批、`ClosureStage.vue` 结项审批、`RetrospectiveStage.vue` 复盘审核、标讯评估审核）**全部依赖前端 v-if 表达式控制按钮**。

**我没动它们**。理由：
- 后端 403 兜底后这些入口**用户能"看到"按钮但点了就 403**——UX 仍然不完美
- 但生产事故的根因（"能成功自我审批"）已被根治
- 前端 7+ 入口的 UX 统一收敛是 `blueprint-driven-development` skill 的事（蓝图里有专门的小节），不是本 bug fix 的范围

**长期建议**：在 wiki 里建一个 todo，让蓝图小节收口时统一把这些"基于角色 + 身份"的可见性逻辑收敛进 `useProjectDraftingPermissions` composable，并增加 `isSelfApproval` 维度。

## 4. 跟现有架构测试（ArchitectureTest 22 条）的关系

`BidReviewPolicy` 仍在 `core/` 包（pure core policy, no Spring/JPA）——架构边界没破。
`BidReviewAppService` 仍在 `service/` 包（orchestration）——架构边界没破。
`BidReviewAppServiceTest` 在 `service/` 包的测试镜像位置——符合现有模式（参考 `ProjectDraftingServiceTest`）。

`ArchitectureTest` 全绿（22/22 通过，0 失败）。

## 5. 跟现有 PR 修复历史的对比

| 修复 | 层次 | 修法 | 是否能根治 |
|---|---|---|---|
| `!213` (06-06 17:55) | 前端 | 加 `isAssignedReviewer` OR 条件 | ❌ worktree 分叉会反转 |
| `!272` (06-07 07:03) | 前端 | 删 `isAssignedReviewer` OR | ❌ 堵死合法路径 |
| `!278` (06-07 16:29) | 后端 service | `submitBid()` 加角色白名单 | ⚠️ 业务层硬编码，且依赖 `getRoleCode()` 双重语义 |
| `!281` (06-07 16:55) | 前端 | 加 `!perm.canSubmitBidForReview` 二次过滤 | ⚠️ 仍可能被绕过（curl） |
| `!307` (06-07 21:44) | 后端 service | 修 `!278` 的 `getRoleCode()` 双重语义 | ⚠️ 只修了 `submitBid`，没修 `approveBid/rejectBid` |
| **本次 (本 commit)** | **策略层 + 业务层 + 双重 HTTP 状态码** | **扩策略签名 + 业务层传身份 + 测试覆盖** | **✅ 根因层修复** |

## 6. 未做的"附赠"修复（待办）

- [ ] 前端 7+ 审批入口的 `v-if` 表达式统一收敛（蓝图驱动）
- [ ] 把 `User.getRoleCode()` 的双重语义拆为 `getRoleProfileCode()` / `getLegacyRoleCode()`（!307 留下的尾巴）
- [ ] 网关层 `@PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")` 升级为按 `RoleProfile.code` 判定
- [ ] Wiki 文档 "标书审核" 一节补充根因说明 + curl 验证命令

## 7. 验证证据

| 命令 | 结果 |
|---|---|
| `mvn -Dtest='BidReviewPolicyTest' test` | **27/27** 通过 |
| `mvn -Dtest='BidReviewAppServiceTest' test` | **9/9** 通过（新建） |
| `mvn -Dtest='ProjectDraftingServiceTest' test` | **14/14** 通过（无回归） |
| `mvn -Dtest='ProjectDraftingControllerTest' test` | **4/4** 通过（无回归） |
| `mvn -Dtest='ArchitectureTest' test` | **22/22** 通过（架构边界未破） |
| `npm run build` (vite build) | 15.73s 成功 |

## 8. 复现命令（生产事故模拟）

```bash
# 1. 用"陈"账号提交标书审核（指定小王为 reviewer）
curl -X POST http://127.0.0.1:18081/api/projects/1/drafting/submit-review \
  -H "Authorization: Bearer $CHEN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reviewerId": 200}'

# 2. 仍用"陈"账号（自审）调审核通过 → 应当 403
curl -i -X POST http://127.0.0.1:18081/api/projects/1/drafting/approve \
  -H "Authorization: Bearer $CHEN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}'
# 预期：HTTP/1.1 403 Forbidden
# 响应：{"message":"提交人不能审批自己提交的标书"}

# 3. 用旁观者账号 → 应当 403
curl -i -X POST http://127.0.0.1:18081/api/projects/1/drafting/approve \
  -H "Authorization: Bearer $OBSERVER_TOKEN" \
  -d '{}'
# 预期：HTTP/1.1 403 Forbidden
# 响应：{"message":"仅指派的审核人可以审批"}

# 4. 用"小王"账号 → 应当 200
curl -i -X POST http://127.0.0.1:18081/api/projects/1/drafting/approve \
  -H "Authorization: Bearer $WANG_TOKEN" \
  -d '{}'
# 预期：HTTP/1.1 200 OK
```

## 9. 与 CLAUDE.md 流程对齐

- ✅ 早操：开工前跑 `sync-env.sh .`（init 分支 ff-only 同步到 origin/main）
- ✅ Lease check：`who-touches.sh backend/src/main/java/com/xiyu/bid/project/` 通过（trae-init 上 1 commit 不触碰本路径）
- ✅ 架构边界：策略层 `core/` 仍纯函数，业务层 `service/` 仍 orchestration
- ✅ 提交规范：commit message 模板见下
- ✅ 完成门禁：`mvn test`（4 套全过）+ `ArchitectureTest`（22/22）+ `npm run build`（vite OK）
- ✅ 推送 WIP：按 §6 推 WIP 分支（commit 后 push，让 lease 检测可见）

## 10. Commit message 草案

```
fix(review): enforce submitter/reviewer identity in BidReviewPolicy (IJSTZG root cause)

Root cause: BidReviewPolicy.canApprove/canReject only checked status machine,
not operator identity. BidReviewAppService.approveBid/rejectBid never read
review.submittedBy or compared with currentUserId, so 提交人 could call
"审核通过" on their own bid via curl/Postman bypassing all front-end v-if
guards. PR !281 fixed the front-end symptom; this commit fixes the back-end
root cause.

Changes:
- BidReviewPolicy: extend canApprove/canReject with (submittedBy, reviewerId,
  currentUserId) and identity checks; Decision record gains Cause enum
  (STATE/IDENTITY) for HTTP status mapping.
- BidReviewAppService: pass identity to policy, map IDENTITY → 403 Forbidden
  and STATE → 409 Conflict (was 409 for both — masked self-approval as
  "resource conflict").
- BidReviewPolicyTest: 7 new identity scenarios (self-approval, wrong
  reviewer, null user, identity-priority-over-state, etc.).
- BidReviewAppServiceTest: 9 new Mockito-based integration tests covering
  approve/reject + 403/409/200 matrix.

Verified:
- BidReviewPolicyTest: 27/27
- BidReviewAppServiceTest: 9/9
- ProjectDraftingServiceTest/ControllerTest: no regression (14+4)
- ArchitectureTest: 22/22 (boundary not broken)
- vite build: OK

Refs: gitee !213, !272, !278, !281, !307
```
