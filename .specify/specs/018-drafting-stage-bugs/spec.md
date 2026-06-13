# 测试规格 — 标书制作阶段 3 个前端 Bug 修复

## 关联 Issue

https://gitee.com/allinai888/bid/issues/IJSZ42

## 问题清单

### Bug 1: 审核人名称显示数字 ID

**零号病人**: `src/views/Project/stages/DraftingStage.vue:202`
**根因**: 提交 `56aa9202a` 在修复问题2 时，删除了 `submitBidForReview()` 成功后从 reviewerOptions 取出 selected.name 赋值给 reviewerName 的代码。
**修复**: 恢复 `submitBidForReview()` 中 `reviewerName` 的赋值逻辑。

### Bug 2: 投标负责人误显驳回/审核按钮

**状态**: 已在 `origin/main` 修复（第66行 `perm.canReviewBid && reviewState === 'reviewing'`）
**证据**: `git show origin/main:src/views/Project/stages/DraftingStage.vue | sed -n '63,70p'`
**验证**: 仅做回归测试

### Bug 3: 审核人页面完成投标区域隐藏

**状态**: 前端逻辑正确（`canSubmitBid` 对 `auditor` 为 `false`），PR #307 已保证后端 API 也会拒绝 auditor。但 issue 报告"运行时仍能看到"，需排查潜在时序问题。
**修复**: 添加前端双保险：在 `advanceToEvaluation` 中加前端角色判断 + 后端 API 已由 PR #307 保证。

## 测试用例

### 前端单元测试

#### Bug 1: submitBidForReview 成功后 reviewerName 正确设置

1. **Given** 已选择审核人（bidReviewerId=5），reviewerOptions 包含 `[{id:5, name:'赵审计'}]`
   **When** submitBidForReview 成功
   **Then** reviewerName 应为 `'赵审计'`，而非回退到 5

2. **Given** 已选择审核人（bidReviewerId=5），但 reviewerOptions 中找不到对应项
   **When** submitBidForReview 成功
   **Then** reviewerName 应为空字符串（不应崩溃）

3. **Given** 页面加载时后端返回 reviewerName
   **When** load() 完成
   **Then** reviewerName 应正确设置，且 reviewerOptions 中应包含该审核人选项

#### Bug 2: 投标负责人不显示审核按钮

4. **Given** 当前用户角色为 `sales`（roleGroup='lead_assist'），reviewState='reviewing'
   **When** 渲染模板
   **Then** 驳回/审核通过按钮不可见

#### Bug 3: auditor 看不到完成投标区域

5. **Given** 当前用户角色为 `auditor`（roleGroup='auditor'），reviewState='approved'
   **When** 渲染模板
   **Then** "完成投标"区域不可见

6. **Given** 当前用户角色为 `sales`（roleGroup='lead_assist'），reviewState='approved'
   **When** 渲染模板
   **Then** "完成投标"区域可见

### 后端 API 回归测试

7. **Given** auditor 角色用户，项目审核已通过
   **When** 调用 POST /api/projects/{id}/drafting/submit-bid
   **Then** 返回 403 "当前角色无权限提交投标"
