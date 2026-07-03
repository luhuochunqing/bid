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

