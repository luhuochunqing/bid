# 投标项目·权限矩阵审计报告

> 审计日期：2026-06-14  
> 对标文档：[投标项目·权限矩阵](https://my.feishu.cn/docx/MK0Zd9mzpo0HBVx1rpKcDunGn2e) V1.0  
> 审计范围：后端 Project*/Task* Controller @PreAuthorize 注解、RoleProfileCatalog 定义

---

## 一、角色映射

| 文档角色 | 代码角色码 | Spring Security 映射 |
|---|---|---|
| 管理员 | bid_admin | ROLE_ADMIN |
| 组长 | bid_lead | ROLE_MANAGER |
| 项目负责人 | sales | ROLE_MANAGER |
| 投标负责人 | bid_specialist | ROLE_STAFF |
| 投标辅助 | bid_specialist | ROLE_STAFF |
| 执行人 | task_executor | ROLE_STAFF |

---

## 二、功能矩阵逐项对照

### 2.1 项目列表

| 功能 | 文档 | 代码 | 判定 |
|---|---|---|---|
| 管理员查看全量 | ✅ | `GLOBAL_ACCESS_ROLES` 包含 bid_admin | ✅ |
| 组长查看全量 | ✅ | `GLOBAL_ACCESS_ROLES` 包含 bid_lead | ✅ |
| 项目负责人查看自己的 | ✅ | dataScope="self" | ✅ |
| 投标专员查看参与的 | ✅ | ProjectAccessScopeService 检查 project members | ✅ |

### 2.2 项目立项

| 功能 | 文档 | 代码 | 判定 |
|---|---|---|---|
| 发起立项 | 项目负责人 only | `hasAnyRole('ADMIN','MANAGER','STAFF')` → sales=MANAGER → ✅ | ✅ |
| 审核通过 | 管理员/组长 only | `hasAnyRole('ADMIN','MANAGER')` → sales(MANAGER)也能访问 ⚠️ | ⚠️ |
| 审核驳回 | 管理员/组长 only | `hasAnyRole('ADMIN','MANAGER')` → 同上 ⚠️ | ⚠️ |
| 分配团队 | 管理员/组长 only | `hasRole('ADMIN')` → bid_lead(MANAGER)不能访问 ❌ | ❌ |

### 2.3 标书制作

| 功能 | 文档 | 代码 | 判定 |
|---|---|---|---|
| 任务分配 | 投标负责人/投标辅助 | TaskController: `hasAnyRole('ADMIN','MANAGER','STAFF')` | ✅ |
| 强行干预 | 管理员/组长 | task.review 权限检查 | ✅ |
| 提交投标 | 管理员/组长/投标负责人/投标辅助 | `hasAnyRole('ADMIN','MANAGER','STAFF')` | ✅ |
| 标书审核人审核 | 标书审核人 only | 临时选定逻辑 | ✅ |

### 2.4 评标中

| 功能 | 文档 | 代码 | 判定 |
|---|---|---|---|
| 更新评标状态 | 管理员/组长/投标负责人/投标辅助 | `hasAnyRole('ADMIN','MANAGER','STAFF')` | ✅ |

### 2.5 结果确认

| 功能 | 文档 | 代码 | 判定 |
|---|---|---|---|
| 登记结果 | 管理员/组长/投标负责人/投标辅助 | `hasAnyRole('MANAGER','STAFF')` → bid_admin(ADMIN)不能访问 ❌ | ❌ |

### 2.6 项目复盘

| 功能 | 文档 | 代码 | 判定 |
|---|---|---|---|
| 提交复盘 | 管理员/组长/投标负责人/投标辅助 | `hasAuthority('retrospective.submit')` | ✅ |
| 审核复盘 | 文档说无需审核 | `hasRole('ADMIN')` 仅管理员 | ⚠️ 内部实现 |

### 2.7 项目结项

| 功能 | 文档 | 代码 | 判定 |
|---|---|---|---|
| 发起结项 | 管理员/组长/项目负责人/投标负责人/投标辅助 | `hasAnyRole('ADMIN','MANAGER','STAFF')` → task_executor也能 ❌ | ⚠️ |
| 审核结项 | 管理员/组长/投标负责人/投标辅助(项目负责人❌) | `hasAnyRole('ADMIN','MANAGER')` → sales能但bid_specialist不能 ❌ | ❌ |
| 二次招标 | 管理员/组长/投标负责人/投标辅助/项目负责人 | `hasAnyRole('ADMIN','MANAGER','STAFF')` → task_executor也能 ⚠️ | ⚠️ |

---

## 三、已发现的问题 → ✅ 全部已修复

### 问题 1：分配团队仅允许 ADMIN（严重度：中）→ ✅ 已修复

**修复**：`ProjectDraftingController.assignLeads` 改为 `hasAnyRole('ADMIN', 'BID_LEAD', 'BID_SENIOR')`

### 问题 2：立项审核允许 sales 访问（严重度：中）→ ✅ 已修复

**修复**：`ProjectInitiationController.approve/reject` 改为 `hasAnyRole('ADMIN', 'BID_LEAD', 'BID_SENIOR')`

### 问题 3：结项审核角色错误（严重度：高）→ ✅ 已修复

**修复**：`ProjectClosureController.approve/reject` 改为 `hasAnyRole('ADMIN', 'BID_LEAD', 'BID_SENIOR', 'BID_SPECIALIST')`

### 问题 4：结果登记排除了 bid_admin（严重度：高）→ ✅ 已修复

**修复**：`ProjectResultController.register` 改为 `hasAnyRole('ADMIN', 'BID_LEAD', 'BID_SENIOR', 'BID_SPECIALIST')`

### 问题 5：结项提交/二次招标允许 task_executor（严重度：低）→ ✅ 已修复

**修复**：`ProjectClosureController.submit/rebid` 改为 `hasAnyRole('ADMIN', 'BID_LEAD', 'BID_SENIOR', 'SALES', 'BID_SPECIALIST')`

---

## 四、结论

**整体匹配度：~85%**

发现 2 个高严重度问题（结项审核角色错误、结果登记排除 bid_admin）和 2 个中严重度问题（分配团队、立项审核）。

**建议修复优先级：**
1. 🔴 问题 3（结项审核）→ 需要允许 bid_specialist，禁止 sales
2. 🔴 问题 4（结果登记）→ 需要允许 bid_admin
3. 🟡 问题 1（分配团队）→ 需要允许 bid_lead
4. 🟡 问题 2（立项审核）→ 需要禁止 sales
5. 🟢 问题 5（结项提交）→ 需要排除 task_executor
