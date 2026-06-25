# P0 权限问题修复执行计划

> **For agentic workers:** REQUIRED SUB-SKILL: 按 TDD 四阶段（plan → tdd → code-review → refactor-clean）执行。步骤使用 checkbox（`- [ ]`）语法跟踪。

**Goal:** 修复 7 个 P0 级权限漏洞，确保投标项目全流程权限与《投标项目 · 权限矩阵 V1.0》完全对齐。

**Architecture:** 按 FP-Java Profile + Split-First Rule 实现：
- 纯核心（core/domain）：Policy 类，纯函数，只做业务规则判断，返回 Decision/Result 对象
- Application Service：只做编排，负责取数、调用纯核心、事务、保存
- Controller：只做 HTTP 边界，调用 Service

**Tech Stack:** Java 21 + Spring Boot 3.2 + Spring Security + JUnit 5 + Vue 3 + Element Plus

---

## 改动范围总览

### 后端文件（创建 + 修改）

| 文件 | 操作 | 职责 |
|---|---|---|
| `com.xiyu.bid.task.core.TaskOperationPolicy` | **创建** | 任务操作权限策略（纯核心） |
| `com.xiyu.bid.task.core.TaskOperationDecision` | **创建** | 任务操作决策结果对象（record） |
| `TaskService.java` | 修改 | 加入 Policy 调用，移除直接权限判断 |
| `ProjectDocumentWorkflowPolicy.java` | 修改 | 新增查看/下载权限判断方法 |
| `ProjectDocumentWorkflowService.java` | 修改 | 加入查看/下载权限校验 |
| `ProjectResultController.java` | 修改 | 补充 BID_PROJECTLEADER 角色 |
| `RoleProfileCatalog.java` | 修改 | 给 bid-projectLeader 补充 retrospective.submit 权限 |
| `ProjectClosureController.java` | 修改 | 补充 BID_PROJECTLEADER 角色，修复 bidAdmin 笔误 |

### 前端文件（修改）

| 文件 | 操作 |
|---|---|
| `TaskKanban.vue` | 修改 — 审核按钮加 v-if 权限控制 |

### 测试文件（创建）

| 文件 | 职责 |
|---|---|
| `TaskOperationPolicyTest.java` | 任务操作权限策略单元测试 |
| `ProjectDocumentWorkflowPolicyTest.java` | 项目文档权限策略单元测试 |

---

## P0 问题拆解与任务

### Track A: 任务权限修复（问题 1-2）

#### Task A1: 创建任务操作权限纯核心 Policy

**Files:**
- Create: `backend/src/main/java/com/xiyu/bid/task/core/TaskOperationPolicy.java`
- Create: `backend/src/main/java/com/xiyu/bid/task/core/TaskOperationDecision.java`
- Test: `backend/src/test/java/com/xiyu/bid/task/core/TaskOperationPolicyTest.java`

**业务规则（来自权限矩阵 §2.3.1）：**

| 操作 | admin | bidAdmin | bid-TeamLeader | bid-projectLeader | bid-Team (辅助) | bid-Team (执行人) | bid-otherDept | bid-administration |
|---|---|---|---|---|---|---|---|---|
| 创建任务 | ✅ | ✅ | ✅ | ✅ (主负责人) | ✅ (辅助) | ❌ | ❌ | ❌ |
| 分配任务 | ✅ | ✅ | ✅ | ✅ (主负责人) | ✅ (辅助) | ❌ | ❌ | ❌ |
| 提交任务 | — | — | — | — | — | ✅ (本人) | ❌ | ❌ |
| 上传交付物 | — | — | — | — | — | ✅ (本人) | ❌ | ❌ |
| 审核通过 | ✅ | ✅ | ✅ | ✅ (主负责人) | ✅ (辅助) | ❌ | ❌ | ❌ |
| 审核驳回 | ✅ | ✅ | ✅ | ✅ (主负责人) | ✅ (辅助) | ❌ | ❌ | ❌ |
| 强行干预(重分配) | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |

**纯核心签名：**
```java
public final class TaskOperationPolicy {
    // 能否创建/编辑/分配任务（管理类操作）
    public static TaskOperationDecision canManageTask(String roleCode, Long currentUserId, Long primaryLeadId, Long secondaryLeadId) { ... }
    
    // 能否提交任务（仅执行人本人）
    public static TaskOperationDecision canSubmitTask(String roleCode, Long currentUserId, Long assigneeId) { ... }
    
    // 能否上传交付物（仅执行人本人）
    public static TaskOperationDecision canUploadDeliverable(String roleCode, Long currentUserId, Long assigneeId) { ... }
    
    // 能否审核任务（通过/驳回）
    public static TaskOperationDecision canReviewTask(String roleCode, Long currentUserId, Long primaryLeadId, Long secondaryLeadId) { ... }
}

public record TaskOperationDecision(boolean allowed, String reason) { ... }
```

- [ ] **Step 1: 写失败测试（Red）**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 实现 Policy 纯核心（Green）**
- [ ] **Step 4: 运行测试确认通过**
- [ ] **Step 5: 提交**

#### Task A2: TaskService 接入 Policy 校验

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/task/service/TaskService.java`

**改动点：**
1. `createTask` — 调用 `TaskOperationPolicy.canManageTask()`，不通过则抛 AccessDeniedException
2. `updateTask` — 调用 `TaskOperationPolicy.canManageTask()`（管理人）或 `canSubmitTask()`（执行人提交）
3. `updateTaskStatus` — 根据目标状态：
   - REVIEW：`canSubmitTask()` + assignee 校验
   - COMPLETED：`canReviewTask()`
   - TODO（驳回）：`canReviewTask()`
4. `createTaskDeliverable` — `canSubmitTask()` + assignee 校验

- [ ] **Step 1: 写集成测试（Red）— 确认当前无权限校验**
- [ ] **Step 2: 接入 Policy 调用（Green）**
- [ ] **Step 3: 运行测试确认通过**
- [ ] **Step 4: 提交**

#### Task A3: 前端 TaskKanban 审核按钮加权限

**Files:**
- Modify: `src/views/Project/stages/components/TaskKanban.vue`

**改动点：**
- REVIEW 列的"通过"、"驳回"按钮加 `v-if="canReviewTasks"` 包裹
- 新增 `canReviewTasks` 计算属性：`isAdminLead || isLeadOrAssist`

- [ ] **Step 1: 确认前端审核按钮无权限控制**
- [ ] **Step 2: 加 v-if 权限控制**
- [ ] **Step 3: 本地构建验证**
- [ ] **Step 4: 提交**

---

### Track B: 项目文档权限修复（问题 3）

#### Task B1: ProjectDocumentWorkflowPolicy 新增查看/下载权限

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/projectworkflow/core/ProjectDocumentWorkflowPolicy.java`
- Test: `backend/src/test/java/com/xiyu/bid/projectworkflow/core/ProjectDocumentWorkflowPolicyTest.java`

**业务规则（来自权限矩阵 §2.3.3）：**

| 操作 | admin | bidAdmin | bid-TeamLeader | bid-projectLeader | bid-Team (辅助) | bid-Team (执行人) | bid-otherDept | bid-administration |
|---|---|---|---|---|---|---|---|---|
| 查看列表 | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| 下载 | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| 上传 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ (参与人) | ❌ | ❌ |
| 删除 | ✅ | ✅ | ✅ (组长) | ❌ | ❌ | ❌ | ❌ | ❌ |

**纯核心签名：**
```java
// 能否查看项目文档列表
public static Decision canViewProjectDocuments(String roleCode, Long currentUserId, Long primaryLeadId, Long secondaryLeadId, boolean isProjectCollaborator) { ... }

// 能否下载项目文档
public static Decision canDownloadProjectDocument(String roleCode, Long currentUserId, Long primaryLeadId, Long secondaryLeadId, boolean isProjectCollaborator) { ... }

// 能否上传项目文档（已有，确认全员参与人均可）
public static Decision canUploadProjectDocument(String roleCode) { ... }

// 能否删除项目文档（已有，补充 bid-TeamLeader）
public static Decision canDeleteProjectDocument(String roleCode) { ... }
```

- [ ] **Step 1: 写失败测试（Red）**
- [ ] **Step 2: 运行测试确认失败**
- [ ] **Step 3: 实现 Policy 方法（Green）**
- [ ] **Step 4: 运行测试确认通过**
- [ ] **Step 5: 提交**

#### Task B2: ProjectDocumentWorkflowService 接入校验

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/projectworkflow/service/ProjectDocumentWorkflowService.java`

**改动点：**
1. `getProjectDocuments` — 调用 `ProjectDocumentWorkflowPolicy.canViewProjectDocuments()`，不通过则抛 AccessDeniedException
2. `createProjectDocument` — 调用 `ProjectDocumentWorkflowPolicy.canUploadProjectDocument()`
3. `deleteProjectDocument` — 使用更新后的 Policy（已补充 bid-TeamLeader）
4. 需要从 guardService 或 project 中取 primaryLeadId / secondaryLeadId

- [ ] **Step 1: 接入 Policy 调用（Green）**
- [ ] **Step 2: 运行测试确认通过**
- [ ] **Step 3: 提交**

---

### Track C: 角色权限补全（问题 4-7）

#### Task C1: 结果确认补充 bid-projectLeader

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/project/controller/ProjectResultController.java`

**改动点：**
- `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')`
  → `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')`

- [ ] **Step 1: 确认缺少 BID_PROJECTLEADER**
- [ ] **Step 2: 补充角色到注解**
- [ ] **Step 3: 提交**

#### Task C2: 项目复盘补充 bid-projectLeader 权限

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/entity/RoleProfileCatalog.java`

**改动点：**
- 在 `case "bid-projectLeader"` 的 grant 列表中补充 `retrospective.submit` 权限

- [ ] **Step 1: 确认缺少 retrospective.submit**
- [ ] **Step 2: 补充权限授予**
- [ ] **Step 3: 运行 RoleProfileServicePersistenceTest 验证**
- [ ] **Step 4: 提交**

#### Task C3: 结项补充 bid-projectLeader + 修复笔误

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/project/controller/ProjectClosureController.java`

**改动点：**
1. 保证金审核（`approveDeposit`/`rejectDeposit`）：
   - 原：`hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')`
   - 新：`hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')`
2. 结项审核（`approveClosure`/`rejectClosure`）：
   - 原：`hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')`
   - 新：`hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')`
3. 修复 `BIDADMIN` 重复写两次的笔误

- [ ] **Step 1: 确认缺少 BID_PROJECTLEADER + 笔误**
- [ ] **Step 2: 补充角色 + 修复笔误**
- [ ] **Step 3: 提交**

---

## 验收标准

1. **TaskService 任务权限**：
   - 非执行人不能提交任务、不能上传交付物
   - 非审核角色（admin/组长/负责人/辅助）不能审核任务
   - 非管理角色不能创建/分配任务

2. **项目文档权限**：
   - 任务执行人（仅被分配任务的 bid-Team）不能查看文档列表、不能下载文档
   - 投标组长可以删除文档
   - 全员参与人均可上传文档

3. **角色权限补全**：
   - bid-projectLeader 可以操作结果确认
   - bid-projectLeader 可以提交复盘
   - bid-projectLeader 可以参与保证金审核和结项审核

4. **架构合规**：
   - 纯核心（Policy）不依赖 Spring、Repository、日志
   - Application Service 只做编排
   - 单个 Java 文件不超过 300 行

5. **测试通过**：
   - `TaskOperationPolicyTest` 全绿
   - `ProjectDocumentWorkflowPolicyTest` 全绿
   - `FPJavaArchitectureTest` 通过
   - `ProjectAccessGuardCoverageTest` 通过
   - `RoleProfileServicePersistenceTest` 通过
   - 前端 `npm run build` 通过

---

## 风险点

1. **TaskService 改造范围**：TaskService 已有较多方法，需确认哪些需要加权限校验，避免遗漏
2. **项目文档执行人角色判定**：需要从 project 中判断当前用户是否为"仅任务执行人"（即既不是负责人也不是辅助，只是被分配了任务的人）
3. **RoleProfileCatalog 迁移**：修改 RoleProfileCatalog 可能影响 bootstrap，需确认 persistence 测试通过
4. **前后端权限对齐**：前端 TaskKanban 的权限判断需与后端 Policy 对齐

---

## 执行顺序

建议并行执行三条 Track：
- Track A（任务权限）：A1 → A2 → A3
- Track B（项目文档权限）：B1 → B2
- Track C（角色权限补全）：C1 + C2 + C3（可完全并行）

三条 Track 之间没有依赖关系，可以同时进行。
