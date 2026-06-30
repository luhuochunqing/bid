# Data Model: MySQL Integration Test Rollout

**Feature**: 005-mysql-integration-test-rollout
**Date**: 2026-06-30

> 本文挡描述被测实体与表结构，用于指导集成测试数据构造与断言。表结构以 `backend/src/main/resources/db/migration-mysql/` 的 Flyway 脚本为唯一真相源（`docs/generated/db-schema.md` 是自动生成的快照，可能滞后）。

## 被测实体 1: User（EffectiveRoleResolver 依赖）

**实体类**: [backend/src/main/java/com/xiyu/bid/entity/User.java](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/entity/User.java)

**表**: `users`

**关键字段**（集成测试关注）:

| 字段 | 类型 | 可空 | 测试关注点 |
|---|---|---|---|
| `id` | BIGINT | NO | 主键，测试用固定 ID（如 9001L）便于清理 |
| `username` | VARCHAR | NO | `RoleCodeCachePort.getRoleCode(username)` 查询键 |
| `role_id` | BIGINT | YES | OSS 用户常为 NULL（CO-361/CO-373 根因） |
| `external_org_source_app` | VARCHAR | YES | OSS 标识（非空=OSS 用户，空/null=本地用户） |
| `full_name` | VARCHAR | YES | `assignOnCrmLink` 用作 assigneeName fallback |

**关键行为**:
- `user.getRoleCode()`：实体方法，`role_id=NULL` 时回退返回 `"manager"`（CO-373 五次反复修复的根因，已 `@Deprecated`）
- 集成测试通过 `userRepository.save()` 写入真实 `users` 表，验证 `EffectiveRoleResolver` 读到的真实 DB 状态

**Role 实体**（关联）:

**表**: `roles`

| 字段 | 类型 | 测试关注点 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `code` | VARCHAR | 角色码（如 `bid-Team`、`bid-projectLeader`），`User.role_id` 指向此 |

**测试数据构造约定**:
- OSS 用户：`role_id=NULL`、`external_org_source_app='ehsy-oss'`、`username='test-int-oss-001'`
- 本地用户：`role_id` 指向真实 `roles` 记录（如 `bid-Team`）、`external_org_source_app=null`
- Role 记录：用 Flyway 已迁移的真实角色数据（不重复插入），仅在需要时查 `roles` 表拿 ID

## 被测实体 2: Tender（TenderCommandService 核心实体）

**实体类**: [backend/src/main/java/com/xiyu/bid/entity/Tender.java](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/entity/Tender.java)

**表**: `tenders`

**关键字段**:

| 字段 | 类型 | 可空 | 测试关注点 |
|---|---|---|---|
| `id` | BIGINT | NO | 主键 |
| `title` | VARCHAR | NO | 标题 |
| `status` | ENUM/VARCHAR | NO | 状态机：`PENDING_ASSIGNMENT` / `TRACKING` / `BIDDING` / `WON` / `LOST` / `ABANDONED` |
| `purchaser_hash` | VARCHAR(16) | YES | **UNIQUE 约束**（CO-265 重复检测） |
| `purchaser_name` | VARCHAR | YES | 采购方名称，hash 来源 |
| `crm_opportunity_id` | VARCHAR | YES | **UNIQUE 约束**（CO-297 双层防御 DB 层） |
| `crm_opportunity_name` | VARCHAR | YES | CRM 商机名 |
| `project_manager_name` | VARCHAR | YES | `assignOnCrmLink` / `tryAutoAssign` 写入 |
| `project_manager_id` | BIGINT | YES | `projectManagerIdResolver` 解析结果 |
| `external_id` | VARCHAR | YES | 外部 ID（事件发布用） |
| `creator_id` | BIGINT | YES | 创建人 ID |
| `evaluation_source` | VARCHAR | YES | `linkCrmOpportunity` 写 `BID_SYSTEM_LINK` |

**状态机约束**（`TenderStatusTransitionPolicy.assertTransition`）:
- `PENDING_ASSIGNMENT` → `TRACKING`（`tryAutoAssign` 成功）
- `TRACKING` → `EVALUATED` / `BIDDING` / `ABANDONED`
- `BIDDING` → `WON` / `LOST`
- `BIDDING` / `WON` / `LOST` / `ABANDONED` 状态**禁止**更换 CRM 商机（`assertCrmLinkAllowed`）

**UNIQUE 约束验证场景**:
- `purchaser_hash` 重复 → `createTender` 抛 `TenderDuplicateException`（CO-265）
- `crm_opportunity_id` 重复 → `linkCrmOpportunity` 抛 `DataIntegrityViolationException`，被 `crmOccupancyChecker.translateUniqueConstraintViolation` 转为 409（CO-297）

## 被测实体 3: TenderAttachment（级联事务验证）

**实体类**: [backend/src/main/java/com/xiyu/bid/tender/entity/TenderAttachment.java](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/tender/entity/TenderAttachment.java)

**表**: `tender_attachments`

| 字段 | 类型 | 测试关注点 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `tender_id` | BIGINT | FK → `tenders.id` |
| `file_name` | VARCHAR | 附件名 |
| `file_url` | VARCHAR | 附件 URL（`saveAttachments` 验证非空） |
| `file_type` | VARCHAR | 附件类型 |

**测试关注点**: `deleteTender` 应在同一事务中删除 `tenders` 和 `tender_attachments`（级联事务一致性）。

## 被测实体 4: TenderAssignmentRecord（跨表事务验证）

**实体类**: [backend/src/main/java/com/xiyu/bid/batch/entity/TenderAssignmentRecord.java](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/batch/entity/TenderAssignmentRecord.java)

**表**: `tender_assignment_records`

| 字段 | 类型 | 测试关注点 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `tender_id` | BIGINT | FK → `tenders.id` |
| `assignee_id` | BIGINT | 接手人 ID（`assignOnCrmLink` 写入） |
| `assignee_name` | VARCHAR | 接手人姓名 |
| `assigned_by_id` | BIGINT | 分配人 ID |
| `assigned_by_name` | VARCHAR | 分配人姓名 |
| `type` | ENUM | `DISPATCH` / `TRANSFER` 等（`assignOnCrmLink` 写 `DISPATCH`） |
| `remark` | VARCHAR | 备注 |
| `assigned_at` | DATETIME | 分配时间 |

**测试关注点**:
- `assignOnCrmLink` 写 `DISPATCH` 记录，验证 `assignee_id` / `assignee_name` / `type` 真实落库
- `tryAutoAssign` 失败时，无 `DISPATCH` 记录落库（事务回滚）

## 实体关系图

```
users (1) ──< tenders (creator_id)
users (1) ──< tender_assignment_records (assignee_id, assigned_by_id)
roles (1) ──< users (role_id, 可空)

tenders (1) ──< tender_attachments (tender_id)
tenders (1) ──< tender_assignment_records (tender_id)
```

## 测试数据 ID 约定

为避免与 Flyway 迁移的真实数据冲突，测试用固定 ID 段：

| 实体 | ID 段 | 清理条件 |
|---|---|---|
| `users` | 9001L - 9099L | `id IN (9001..9099)` 或 `username LIKE 'test-int-%'` |
| `tenders` | 自增（不固定 ID） | `title LIKE 'test-int-%'` 或 `creator_id IN (9001..9099)` |
| `tender_attachments` | 自增 | `tender_id IN (SELECT id FROM tenders WHERE creator_id IN (9001..9099))` |
| `tender_assignment_records` | 自增 | `tender_id IN (SELECT id FROM tenders WHERE creator_id IN (9001..9099))` |

**清理顺序**（FK 反向）:
1. `DELETE FROM tender_assignment_records WHERE tender_id IN (...)`
2. `DELETE FROM tender_attachments WHERE tender_id IN (...)`
3. `DELETE FROM tenders WHERE creator_id IN (9001..9099)`
4. `DELETE FROM users WHERE id IN (9001..9099)`

## 状态转换说明

`TenderCommandService` 测试涉及的状态转换：

| 方法 | 起始状态 | 目标状态 | 触发条件 |
|---|---|---|---|
| `createTender` | (none) | `PENDING_ASSIGNMENT` | 默认状态 |
| `tryAutoAssign` 成功 | `PENDING_ASSIGNMENT` | `TRACKING` | `autoAssignmentService.autoAssignIfPossible` 返回 matched |
| `tryAutoAssign` 失败 | `PENDING_ASSIGNMENT` | `PENDING_ASSIGNMENT`（不变） | 抛 RuntimeException，事务回滚 |
| `linkCrmOpportunity` | `TRACKING` | `TRACKING`（不变，CO-310 两步流程） | 关联 CRM 商机 + 写 DISPATCH 记录 |
| `linkCrmOpportunity` | `BIDDING`/`WON`/`LOST`/`ABANDONED` | (抛 409) | `assertCrmLinkAllowed` 拒绝 |
| `deleteTender` | 任意 | (删除) | `commandAccessGuard.assertCanDeleteTender` 通过 |
