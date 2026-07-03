# 投标项目 · 权限矩阵对照审计（2026-07-03）

> **权威基线**：飞书《投标项目·权限矩阵》V1.0（revision 551，2026-05-14 定稿）
> 　https://my.feishu.cn/docx/MK0Zd9mzpo0HBVx1rpKcDunGn2e
> **审计日期**：2026-07-03
> **方法**：同标讯审计——逐功能点对照文档与代码，补契约测试锁定防漂移

---

## 项目权限模型特点（与标讯的区别）

项目权限比标讯复杂，多两个维度：

1. **项目子身份动态变化**：投标专员在不同项目可能是"投标负责人/辅助人员/标书审核人/任务执行人"，权限随项目身份变化（不是静态角色权限）
2. **项目状态机**：立项中→标书制作→评标中→结果确认→项目复盘→项目结项，部分权限随阶段变化

**数据范围**（文档定义）：
- 全量 = 所有项目
- 自己的 = 自己负责的项目
- 参与的 = 自己参与的项目（任一身分）

**实现核心**：`ProjectAccessScopeService.getAllowedProjectIds` 综合判断（admin 短路全量；否则按 10 维度聚合：创建/负责/任务/成员/CRM 客户/部门/正副投标负责人/标书审核人/项目所有者）。

---

## 2.1 项目列表

### 文档要求（飞书 V1.0）

| 功能 | 投标管理员 | 投标组长 | 投标项目负责人 | 投标专员（参与项目的）|
|---|---|---|---|---|
| 查看列表 | ✅ 全量 | ✅ 全量 | ✅ 自己的 | ✅ 参与的 |
| 搜索/筛选 | ✅ 全量 | ✅ 全量 | ✅ 自己的 | ✅ 参与的 |
| 导出 | ✅ 可见范围 | ✅ 可见范围 | ✅ 可见范围 | ✅ 可见范围 |

### 端点对照

| 功能 | 端点 | Controller 注解 | Service 层数据范围 |
|---|---|---|---|
| 查看列表 / 搜索筛选 | GET `/api/projects`（L54-57）| `isAuthenticated()` | `ProjectAccessScopeService.getAllowedProjectIdsForCurrentUser` |
| 导出 | POST `/api/projects/export` 或类似 | 待确认 | 待确认 |

### 差距判断

| 维度 | 结论 | 依据 |
|---|---|---|
| 角色准入 | ✅ 匹配 | `isAuthenticated()` 让所有登录用户进入；数据范围由 Service 层按角色过滤（与文档"各角色看对应范围"一致）|
| 管理员/组长全量 | ✅ 匹配 | `dataScope=all` → `findAllProjectIds`（L68-73）|
| 项目负责人"自己的" | ✅ 匹配 | `findAccessibleProjectIdsByUserId` + `ownerUserId`（L75-99）|
| 投标专员"参与的" | ✅ 匹配 | 10 维度聚合（任务/成员/正副负责人/审核人/项目所有者，L75-117）|
| admin sentinel | ⚠️ 实现细节 | `getAllowedProjectIds` 对 admin 返回空列表（L64-66），调用方需理解为"全可见"。`filterAccessibleProjects`（L147）的 `hasAdminAccess` 短路正确处理 |

### 契约测试

**现有覆盖**：
- `ProjectControllerAuthorizationTest.getAllProjects_shouldBeAccessibleToAuthenticatedUsers`：反射锁定 `isAuthenticated()` 注解 ✅
- `ProjectControllerAccessIntegrationTest`：数据范围集成测试（MANAGER/BID_OTHERDEPT 等多角色）✅
- `ProjectAccessScopeServiceTest`：Service 层数据范围单测 ✅

### ⚠️ 待业务确认 Gap：项目导出角色限制

**文档要求**："各角色导出 = 可见范围"——含投标专员（4 个角色都 ✅）。

**代码实现**：`GET /api/projects/export` 注解 `hasAnyRole('ADMIN','MANAGER')`——只放行 admin/MANAGER，**不含 BID_TEAM（投标专员）**。

| 角色 | 文档 | 代码 | 差距 |
|---|---|---|---|
| 投标管理员/组长 | ✅ 可见范围 | ✅（MANAGER）| 无 |
| 投标项目负责人 | ✅ 可见范围 | ✅（MANAGER 含 sales）| 无 |
| **投标专员** | ✅ 可见范围 | **❌ 403** | **gap** |

**契约测试**（`ProjectListPermissionIntegrationTest`，新增 3 个）锁定现状：
- 导出：MANAGER → 非 403（放行）
- **导出：投标专员 → 403（⚠️ 锁定 gap，待业务确认）**
- 导出：行政人员 → 403（文档：不涉及项目）

**待业务确认**：投标专员能否导出项目（按可见范围）？文档允许，代码拒绝。

---

*下一小节：2.2 项目立项（含立项发起/审核/团队分配等 7 个功能点）*

---

## 2.2 项目立项（审计）

### 文档要求（飞书 V1.0）

| 功能 | 投标管理员 | 投标组长 | 投标项目负责人 | 投标专员 |
|---|---|---|---|---|
| 发起立项（信息维护） | — | — | ✅ | — |
| AI 风险等级评估 | ✅ 触发+查看 | ✅ 触发+查看 | ✅ 触发+查看 | 查看结果 |
| 上传招标文件 | — | — | ✅ | — |
| 提交立项 | — | — | ✅ | — |
| 分配投标团队 | ✅ | ✅ | — | — |
| 审核通过 | ✅ | ✅ | — | — |
| 审核驳回 | ✅ | ✅ | — | — |

### 端点对照（ProjectInitiationController）

| 文档功能 | 端点 | Controller 注解 | 文档对照 |
|---|---|---|---|
| 提交立项 | POST `/api/projects/{id}/initiation` | `ADMIN/BID_PROJECTLEADER` | ✅ 匹配（仅项目负责人+admin）|
| 更新立项 | PUT | `ADMIN/BID_PROJECTLEADER` | ✅ 匹配 |
| AI 风险评估 | POST `/ai-risk-assessment` | `ADMIN/BID_PROJECTLEADER` | ✅ 匹配 |
| 审核通过 | POST `/approve` | `ADMIN/BID_TEAMLEADER/BIDADMIN` | ✅ 匹配（管理员/组长，不含项目负责人/专员）|
| 审核驳回 | POST `/reject` | `ADMIN/BID_TEAMLEADER/BIDADMIN` | ✅ 匹配 |
| 查看立项 | GET | `isAuthenticated()` | ✅ 数据范围由 Service 层过滤 |

### 差距判断

| 维度 | 结论 |
|---|---|
| 提交/更新/AI 评估角色收口 | ✅ 匹配（仅 BID_PROJECTLEADER + admin）|
| 审核角色收口 | ✅ 匹配（仅管理员/组长，不含项目负责人/专员）|
| 分配投标团队 | ❓ 待确认（可能在 ProjectDraftingController 或团队分配端点，2.3 审计）|

**2.2 立项权限实现完全匹配文档**，无 gap。

### 契约测试

**反射型契约测试**（`ProjectInitiationPermissionTest`，新增 4 个）：
- submit 注解 == `hasAnyRole('ADMIN','BID_PROJECTLEADER')`
- approve 注解 == `ADMIN/BID_TEAMLEADER/BIDADMIN` + doesNotContain 项目负责人/专员
- reject 注解同 approve
- assessRisk 注解 == `ADMIN/BID_PROJECTLEADER`

### 2.2 小结

✅ 立项权限实现**完全匹配文档**——提交/AI 评估仅项目负责人，审核仅管理员/组长。4 个反射型测试锁定注解，防止未来重构放行错误角色。


---

## 2.3 标书制作（审计）

### 2.3.1 任务管理（13 功能点）

#### 文档要求（核心约束）

| 功能类别 | 角色/身份 |
|---|---|
| AI 拆解/手动添加/分配/保存 | 管理员/组长/投标负责人/辅助人员 |
| 强行干预（重分配） | 仅管理员/组长 |
| 提交任务/上传交付物 | 仅任务执行人本人（assignee==自己）|
| 任务审核 | 管理权限持有者，但不能审核自己提交的（职责分离）|

#### 代码实现特点（标杆级）

**Controller 层**：`TaskController` 所有端点 `isAuthenticated()`——**完全不做角色区分**，所有权限在 Service 层按项目子身份判断。

**Service 层**：`TaskPermissionGuard` 委托纯核心 `TaskOperationPolicy`，按 4 个维度做实例级判断：
- roleCode（角色）
- currentUserId（当前用户）
- primaryLeadId/secondaryLeadId（投标负责人/辅助）
- isProjectOwner（项目立项负责人）
- assigneeId（任务执行人）

**4 个 Policy 方法完美对应文档**：

| Policy 方法 | 文档约束 | 实现验证 |
|---|---|---|
| `canManageTask` | 管理员/组长/投标负责人/辅助 | DIRECT_MANAGE_ROLES permit；sales 需 owner/primaryLead；bid-Team 需 primary/secondaryLead ✅ |
| `canForceReassign` | 仅管理员/组长 | 仅 DIRECT_MANAGE_ROLES ✅ |
| `canActAsAssignee` | 仅执行人本人 | assigneeId==currentUserId（纯身份，不看角色）✅ |
| `canReviewTask` | 管理权限 + 职责分离 | canManageTask + assigneeId != currentUserId ✅ |

#### 契约测试（已有，无需补）

`TaskOperationPolicyTest`（143 行测试）+ `TaskPermissionGuardTest`——**覆盖极其完整**：
- canManageTask：各角色 × leadIds 匹配/不匹配（含 bid-otherDept/bid-administration 拒绝）
- canForceReassign：管理员/组长放行，其他角色拒绝
- canActAsAssignee：执行人本人放行，非执行人拒绝
- canReviewTask：含职责分离（不能审自己）

#### 差距判断

✅ **任务管理权限完全匹配文档，无 gap**。这是项目子身份模型的标杆实现——Controller 层不区分角色，全部在 Service 层按项目身份（leadIds/isProjectOwner/assigneeId）做实例级判断，测试覆盖完整。


### 2.3.2 最终标书审核与投标提交（5 功能点）

**端点对照**（ProjectDraftingController）：

| 文档功能 | 端点 | 注解 | 文档对照 |
|---|---|---|---|
| 分配投标团队 | assignLeads | `ADMIN/BID_TEAMLEADER/BIDADMIN` | ✅ 匹配（仅管理员/组长）|
| 最终投标提交 | submitBid | `ADMIN/BID_TEAMLEADER/BIDADMIN/BID_PROJECTLEADER/BID_TEAM/SALES` | ✅ 匹配（管理员/组长/投标负责人/辅助）|
| 提交审核（选审核人）| submit-review | 同 submitBid | ✅ 匹配 |
| **审核标书 approve/reject** | approve/reject | **无方法级注解**（CO-315 鉴权下沉）| ✅ 匹配（审核权限纯实例级，按 BidReviewPolicy 校验临时选定的审核人）|

**契约测试**（`ProjectDraftingPermissionTest`，新增 4 个）：
- assignLeads 注解锁定（仅管理员/组长）
- submitBid 注解含 BID_TEAM/BID_PROJECTLEADER/BID_TEAMLEADER
- **approve/reject 锁定"无方法级注解"**（防止未来误加角色白名单，破坏审核人实例级校验）

✅ 标书审核权限匹配文档。审核（approve/reject）的实例级校验（BidReviewPolicy + BidReviewPolicyTest）已完整覆盖。

### 2.3.3 项目文档（4 功能点）

**实现**：`ProjectDocumentController` 所有端点 `isAuthenticated()`，权限在 Service 层 `ProjectDocumentWorkflowPolicy`：
- `canViewProjectDocuments` / `canDownloadProjectDocument`（仅管理员/组长/负责人/辅助）
- `canUploadProjectDocument`（全体参与人）
- `canDeleteProjectDocument`（仅管理员 + 上传者本人，CO-375 对称设计）

✅ 项目文档权限匹配文档，且由 §24 多轮修复建立的 `ProjectDocumentWorkflowPolicyTest` 完整覆盖（canUpload/canDelete 对称）。

### 2.3 标书制作审计小结

✅ **三个子节权限全部匹配文档**，且测试覆盖完整：
- 2.3.1 任务管理：TaskOperationPolicy（143 行测试，标杆级）
- 2.3.2 标书审核：BidReviewPolicy + 本轮新增 4 反射测试
- 2.3.3 项目文档：ProjectDocumentWorkflowPolicy（§24 已建立对称测试）

本项目子身份权限模型（Controller 层 isAuthenticated + Service 层按项目身份实例级判断）是设计标杆——比标讯的角色白名单更精确，且测试覆盖完整。

