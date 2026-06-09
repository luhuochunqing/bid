# 实现计划：标讯UI操作按钮一致性优化

## 改动总览

涉及 **4 个文件**，前端纯核心 + Vue 组件，无后端变更。

## 步骤

### Step 1: actionMatrix.js — 纯核心矩阵增加创建人参数

**文件**: `src/views/Bidding/detail/actionMatrix.js`

**改动**:

1. `getHeaderActions(status, role, hasOriginalUrl, currentUserId, creatorId)` 新增参数
2. `HEADER_MATRIX.PENDING_ASSIGNMENT` 中 `sales` 和 `bid_specialist` 的行改为工厂函数模式：
   - 当 `currentUserId === creatorId` 时 → `['edit', 'delete']`
   - 否则 → `[]`
3. admin_lead 行保持不变（已有编辑/删除权限）
4. `getBottomActions(status, role, ...)` 同理增加创建人感知，但当前 PENDING_ASSIGNMENT 底部 admin_lead 已有 edit，销售角色不需要底部编辑按钮

**核心逻辑**（纯函数，无副作用，可单测）：

```javascript
export function getHeaderActions(status, role, hasOriginalUrl, currentUserId, creatorId) {
  const group = resolveRoleGroup(role)
  if (!group) return []
  const statusActions = HEADER_MATRIX[status]
  if (!statusActions) return []

  let keys = statusActions[group]
  if (!keys) return []

  // 处理函数类型的 keys（动态权限判定）
  if (typeof keys === 'function') {
    keys = keys({ currentUserId, creatorId })
  }

  let result = keys.map((k) => ({ ...ACTION_DEFS[k] }))

  // bid_lead 不能有 delete
  if (role === 'bid_lead') {
    result = result.filter((a) => a.key !== 'delete')
  }

  if (hasOriginalUrl && ACTION_DEFS.viewAnnouncement) {
    result.push({ ...ACTION_DEFS.viewAnnouncement })
  }

  return result
}
```

矩阵定义变化：
```javascript
const HEADER_MATRIX = {
  PENDING_ASSIGNMENT: {
    admin_lead: ['assign', 'delete'],
    sales: ({ currentUserId, creatorId }) =>
      currentUserId === creatorId ? ['edit', 'delete'] : [],
    bid_specialist: ({ currentUserId, creatorId }) =>
      currentUserId === creatorId ? ['edit', 'delete'] : [],
  },
  // ...其他状态不变
}
```

### Step 2: DetailPage.vue — 传入 creatorId

**文件**: `src/views/Bidding/detail/DetailPage.vue`

**改动**:

1. 从 `tender` 中读取 `creatorId`：`const tenderCreatorId = computed(() => tender.value?.creatorId)`
2. 将 `tenderCreatorId` 传给 `useDetailActions` 或直接传给 action 计算

```javascript
const tenderCreatorId = computed(() => tender.value?.creatorId)
const currentUserId = computed(() => userStore.currentUser?.id)

const { headerActions, bottomActions, handleAction } = useDetailActions(
  tender, userRole, loadTenderDetail, {
    // handlers 不变
  },
  activeTab, isEvaluationSubmitted, currentUserId, tenderCreatorId
)
```

### Step 3: useDetailActions.js — 透传 creatorId

**文件**: `src/views/Bidding/detail/useDetailActions.js`

**改动**: `useDetailActions` 新增 `currentUserId` 和 `creatorId` 参数，透传给 `getHeaderActions` / `getBottomActions`

### Step 4: TenderCreatePage.vue — 修复底部按钮 + 保存后跳转

**文件**: `src/views/Bidding/TenderCreatePage.vue`

**改动**:

1. **`canProceedToNext` 修复**：移除 `if (isAdminOrLead.value) return true`。TRACKING 状态下仅当 `currentUserId === projectLeaderId`（当前用户是被分配的项目负责人）时才显示下一步/提交按钮
2. **保存后跳转详情页**：`handleSave` 成功后统一跳转 `/bidding/${createdTenderId}` 而非停留在创建页

### Step 5: actionMatrix.spec.js — 新增创建人场景测试

**文件**: `src/views/Bidding/detail/actionMatrix.spec.js`

**新增测试用例**：

- sales 作为创建人 → PENDING_ASSIGNMENT → 头部显示编辑+删除
- bid_specialist 作为创建人 → PENDING_ASSIGNMENT → 头部显示编辑+删除
- sales 非创建人 → PENDING_ASSIGNMENT → 头部无按钮
- sales 作为创建人 → TRACKING → 头部无按钮（仅 PENDING 状态下允许）
- 管理员角色不受 currentUserId/creatorId 影响
