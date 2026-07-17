# 工作台待办模块角色化改造规格

> 创建日期：2026-07-15
> 分支：agent/trae1/workbench-role-based-todos
> 关联 PR：待创建
> 关联 Linear：待创建

## 1. 背景与目标

当前工作台第二部分"待办模块"4 张卡片对所有角色展示相同数据，不符合业务实际——不同角色关注的待办内容不同。本次改造按角色差异化展示 4 个模块的待办数据，让每个角色看到与自己相关的待办。

## 2. 角色定义

| 业务角色名 | roleCode | 说明 |
|---|---|---|
| 投标管理员 | `/bidAdmin` | 复盘审核与结项闸门审批 |
| 投标系统管理员 | `bid-SystemAdmin` | 权限等同投标管理员 |
| 投标组长 | `bid-TeamLeader` | 标书编制与评标推进负责人 |
| 投标项目负责人 | `bid-projectLeader` | 立项发起人 |
| 投标专员 | `bid-Team` | 投标辅助、标书审核与任务处理 |
| 跨部门协同人员 | `bid-otherDept` | 项目任务处理（无标书制作权限） |

**角色分组**（用于业务逻辑）：
- `admin_lead` = 投标管理员 + 投标系统管理员 + 投标组长
- `sales` = 投标项目负责人 + 跨部门协同人员
- `bid_team` = 投标专员

## 3. 模块需求

### 模块 1：任务·待办（所有角色相同）

**数据源**：
- 标书制作阶段（`Project.stage = 'DRAFTING'`）的项目下的任务
- 当前用户为任务执行人（`Task.assigneeId = 当前用户ID`）
- 任务状态非已完成（`Task.status != COMPLETED`）

**后端改动**：
- `GET /api/tasks/my` 新增 `projectStage` 查询参数（String，可选）
- TaskRepository 新增 JOIN 查询方法
- TaskService 新增带 stage 参数的重载方法

**点击跳转**：
| 角色 | 跳转目标 |
|---|---|
| 跨部门协同人员 `bid-otherDept` | `/task-board`（独立任务看板） |
| 其他所有角色 | 项目详情页 → 标书制作阶段任务看板 |

### 模块 2：标讯·待办（角色差异）

| 角色组 | 显示内容 | 后端过滤条件 |
|---|---|---|
| `admin_lead`（投标管理员/系统管理员/组长） | 待分配 + 已评估的标讯 | `status IN (PENDING_ASSIGNMENT, EVALUATED)` |
| `sales` 中的项目负责人 `bid-projectLeader` | 跟踪中的标讯 | `status = TRACKING` |
| 其他角色（投标专员、跨部门协同人员） | 不显示此模块 | — |

**后端改动**：
- `GET /api/tenders` 确认支持 `status` 多值过滤（如不支持则扩展）
- 前端按角色传不同 status 参数

### 模块 3：项目·待办（角色差异）

| 角色组 | 显示内容 | 数据源 |
|---|---|---|
| `admin_lead` | 已立项项目（`stage = INITIATED`）+ 作为标书审核人的项目（`BidDocumentReviewEntity.reviewerId = 当前用户ID`） | Project JOIN BidDocumentReview |
| 投标专员 `bid-Team` | 自己是投标负责人或辅助人员的项目（`ProjectLeadAssignment.primaryLeadUserId/secondaryLeadUserId = 当前用户ID`），且项目未结项（`stage != CLOSED`） | Project JOIN ProjectLeadAssignment，排除 CLOSED |
| 项目负责人 `bid-projectLeader` | 待立项（`stage = INITIATED`）+ 标书审核人 + 待结项（`stage = RETROSPECTIVE`） | 多条件 OR |

**后端改动**：
- 新增 `GET /api/projects/workbench-todos` 接口，按当前用户角色返回差异化项目列表
- Service 层按角色分支查询

### 模块 4：资源·待办

**数据源**：
- 待审批（`status = PENDING_APPROVAL`）的账户借用申请（`AccountBorrowApplication`）
- 待审批（`status = PENDING_APPROVAL`）的 CA 借用申请（`CaBorrowApplicationEntity`）
- 合并展示，只显示前 4 条

**后端改动**：
- 新增 `GET /api/dashboard/resource-pending-approvals` 聚合接口
- 返回合并后的待审批列表

**注意**：所有角色都显示此模块（只要有待审批数据）。

## 4. 前端改动

### 4.1 useWorkbenchRebuild.js 改造

`buildTodoCategoryCards` 改为接收角色和用户ID参数，按角色构建不同的卡片数据：

```js
const todoCategoryCards = computed(() => buildTodoCategoryCards({
  role: currentUserRole.value,
  userId: currentUserId.value,
  taskTodos: taskTodosRef?.value || [],      // 标书制作阶段的任务
  tenderTodos: tenderTodosRef?.value || [],   // 按角色过滤的标讯
  projectTodos: projectTodosRef?.value || [], // 按角色过滤的项目
  resourceTodos: resourceTodosRef?.value || [],// 待审批申请
}))
```

### 4.2 角色判断

前端统一用 `currentUser.roleCode`（fallback `currentUser.role`）判断角色，使用 `roleCodes.js` 常量。

### 4.3 跳转逻辑

```js
function handleTodoCardClick({ cardKey, item }) {
  if (cardKey === 'task') {
    if (isCrossDeptRole(currentRole)) {
      router.push('/task-board')  // 跨部门 → 独立看板
    } else {
      navigateToProject(router, item.projectId, { stage: 'drafting' })  // 其他 → 项目详情
    }
  }
  // ... 其他模块跳转
}
```

### 4.4 API 调用

| 模块 | API | 参数 |
|---|---|---|
| 任务待办 | `GET /api/tasks/my?projectStage=DRAFTING` | assigneeId + projectStage |
| 标讯待办 | `GET /api/tenders?status=PENDING_ASSIGNMENT,EVALUATED` | 按 role 决定 status |
| 项目待办 | `GET /api/projects/workbench-todos` | 后端按当前用户角色返回 |
| 资源待办 | `GET /api/dashboard/resource-pending-approvals` | 无参数 |

## 5. 数据库

无表结构变更，全部基于现有表查询。

## 6. 测试要求

### 后端
- TaskRepository 新查询方法的单测
- TaskService stage 过滤逻辑单测
- Project workbench-todos 接口按角色返回的集成测试
- Resource pending-approvals 聚合接口单测

### 前端
- buildTodoCategoryCards 按角色构建卡片的单测
- handleTodoCardClick 跳转逻辑按角色分支的单测

## 7. 验收标准

1. 投标管理员登录工作台，任务待办只显示标书制作阶段的任务
2. 投标管理员登录，标讯待办显示待分配+已评估的标讯
3. 项目负责人登录，标讯待办显示跟踪中的标讯
4. 投标专员登录，项目待办显示自己是投标负责人/辅助人员的项目
5. 跨部门协同人员点击任务待办，跳转到独立任务看板
6. 资源待办显示待审批的账户+CA 申请
7. 所有改动通过后端单测和前端构建
