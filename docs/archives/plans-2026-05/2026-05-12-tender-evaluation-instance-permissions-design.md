# 标讯评估表实例级权限设计

**日期**: 2026-05-12
**作者**: Claude (与 user 头脑风暴产出)
**状态**: Design ready，等待立项 PR

## 背景

PR #241 上线了「标讯评估表」功能，把"能填评估表"硬编码绑给 backend role enum `MANAGER`、"能投标 / 弃标"硬编码绑给 `ADMIN`。

实际业务场景里，"项目经理"和"管理员"是**业务概念**，不是 backend `User.Role` 的固定值；不同客户对角色名定义可能不同。固定 enum 比较立刻产生误判：xiaowang（STAFF 角色）虽然实际负责某条标讯，但当前实现下他既看不到表单也填不了。

## 业务规则（已与 user 敲定）

> 标讯创建时**分配给一个项目经理**。被分配的项目经理负责填评估表；分配人负责投标 / 弃标决策。

| 操作 | 谁能做（实例级判据） |
|---|---|
| 填写 / 提交评估表 | `tender.latestAssignment.assigneeId == currentUser.id` |
| 投标 / 弃标 | `tender.latestAssignment.assignedById == currentUser.id` |
| 只读看 | 现有 `TenderProjectAccessGuard.assertCanAccessTender` 不变 |

**完全脱离角色**：决策端点不再依赖 `@PreAuthorize("hasRole('ADMIN')")`。如果 assigned_by 离职 / 账号失效，按 YAGNI 原则暂不处理（user 决策）。

## 数据基础（已存在，不动 schema）

- `tender_assignment_records` 表已经包含 `assignee_id` / `assigned_by_id` / `assignee_name` / `assigned_by_name`
- `TenderQueryService.fetchAssigneeNames` 已经查 latest 分配记录
- `TenderAssignmentRecordRepository.findLatestByTenderIds` 现成可复用

注意：DetailPage 上 label "**分配人**" 显示的字段是 `assigneeName`（被分配的人）。中文有歧义但不在本任务范围。

## 设计

### 后端

**新增** `backend/src/main/java/com/xiyu/bid/tender/service/TenderAssignmentPermissions.java`（pure helper，≤100 行，FP-Java profile）：
```java
@Component
public class TenderAssignmentPermissions {
    private final TenderAssignmentRecordRepository repo;

    public boolean canFill(Long tenderId, Long userId) { ... }
    public boolean canDecide(Long tenderId, Long userId) { ... }
}
```

**改** `TenderEvaluationDTO.java` 加两个 boolean：`canFillEvaluation`、`canDecideBid`。

**改** `TenderEvaluationSubmissionService.java`：
- `loadOrInitDraft` 注入 permissions 并在 DTO 上填两个 boolean
- `saveDraft` / `submit` 进入前 `permissions.canFill(tenderId, userId)` false → `AccessDeniedException`

**改** 投标 / 弃标控制器端点：
- 去掉 `@PreAuthorize("hasRole('ADMIN')")`
- 改为 service 层调 `permissions.canDecide(tenderId, userId)`，false → `AccessDeniedException`

**保持不变**：`TenderProjectAccessGuard.assertCanAccessTender`（管 read，不动）。

### 前端

**改** `DetailPage.vue`：
```vue
<TenderEvaluationForm
  :evaluation="tenderEvaluation"
  :can-fill="tenderEvaluation?.canFillEvaluation || false"
  :can-decide="tenderEvaluation?.canDecideBid || false"
  :tender-id="tender.id"
  ...
/>
```
不再读 `useUserStore.userRole`。

**改** `useTenderEvaluationForm.js`：删 `currentUserRole`、`isProjectManager`、`isAdmin`；改用 props.canFill / canDecide：
```js
const isEditable = computed(() => props.canFill && !isSubmitted.value)
const showDraftSubmitButtons = computed(() => props.canFill && !isSubmitted.value)
const showDecisionButtons = computed(() => props.canDecide && isSubmitted.value)
```

**改** `TenderEvaluationForm.spec.js`：13 个测试用 canFill × canDecide × evaluationStatus 矩阵替换 role 字符串。

## 实施清单（依赖顺序）

1. 新建 `TenderAssignmentPermissions.java` + `TenderAssignmentPermissionsTest.java`（4 case）
2. 改 `TenderEvaluationDTO.java`（加 2 boolean）
3. 改 `TenderEvaluationSubmissionService.java`（注入 + DTO 填值 + 写端点权限）
4. 改 `TenderEvaluationSubmissionServiceTest.java`（增加 "non-assignee → 403" / "non-assigned_by → 403" case）
5. 改投标 / 弃标控制器端点（去 hasRole + 加 canDecide）
6. 改 `DetailPage.vue`（传 canFill/canDecide）
7. 改 `useTenderEvaluationForm.js`（删 role）
8. 改 `TenderEvaluationForm.spec.js`（矩阵改写）

## 测试矩阵（前端）

| canFill | canDecide | evaluationStatus | 期望 |
|---|---|---|---|
| true | false | null | 表单可填、保存/提交按钮在、无决策按钮 |
| true | false | DRAFT | 表单可填、保存/提交按钮在 |
| true | false | SUBMITTED | 只读、无按钮 |
| false | true | SUBMITTED | 只读、决策按钮显示 |
| false | true | DRAFT | 只读、无决策按钮（评估先决策后契约） |
| false | false | any | 只读、无按钮（只能看） |

## 风险

1. **数据完整性**：依赖 `tender_assignment_records` 每条标讯有 latest 记录。当前数据是否如此？预查询确认；如有空缺标讯需 fallback 提示。
2. **测试覆盖**：投标 / 弃标端点之前靠 ADMIN role 守，删了后必须靠 canDecide。如果 canDecide 实现有 bug，决策就敞开了。MVC 集成测试必须覆盖。
3. **未分配标讯**：标讯创建后未分配前，没人能填、没人能决策（这是正确行为）。UI 需提示"请先分配项目经理"。

## 范围

约 9 个文件改、~250 行净增、~40 个新测试 case。建议独立 PR，不与其他改动混合。

## 已显式排除

- 角色管理后台页面（不本任务范围）
- 权限点 / 角色矩阵自定义页面（更大架构，不在 YAGNI 目标内）
- assigned_by 失效兜底（YAGNI，等真出现再处理）
- "分配人"中文 label 歧义修正（不本任务范围）
