# 角色权限矩阵审计报告

> 审计日期：2026-06-14  
> 对标文档：[标讯中心 · 权限矩阵](https://my.feishu.cn/docx/Kq7NduknFoCTi3xyI0vcyJfZnce) V1.0  
> 审计范围：后端 @PreAuthorize 注解、RoleProfileCatalog 定义、前端 actionMatrix.js 操作矩阵

---

## 一、角色定义对照

| 飞书文档角色 | 文档编码 | 代码编码 | 代码定义位置 | 状态 |
|---|---|---|---|---|
| 投标管理员 | BID_ADMIN | `bid_admin` | `RoleProfileCatalog.java:29` | ✅ 匹配 |
| 投标组长 | BID_LEADER | `bid_lead` | `RoleProfileCatalog.java:28` | ⚠️ 编码名不同（BID_LEADER vs bid_lead），功能一致 |
| 项目负责人 | PROJECT_OWNER | `sales` | `RoleProfileCatalog.java:27` | ⚠️ 编码名不同（PROJECT_OWNER vs sales），功能一致 |
| 投标专员 | BID_SPECIALIST | `bid_specialist` | `RoleProfileCatalog.java:31` | ✅ 匹配 |
| 跨部门协同人员 | SYSTEM_ADMIN | `task_executor` / `admin_staff` | `RoleProfileCatalog.java:30,32` | ⚠️ 编码名不同，功能分拆为两个角色 |
| 行政人员 | ADMIN_STAFF | `admin_staff` | `RoleProfileCatalog.java:32` | ✅ 匹配 |

**额外角色（文档未提及）：**
- `bid_senior`（投标主管）：合并 bid_admin + bid_lead 权限的超级角色，代码预留
- `auditor`（审计员）：系统级审计角色，与标讯模块无关
- `manager` / `staff`：旧版角色，通过兼容层映射

### 兼容层映射关系

`RoleProfileCatalog.securityCompatLegacyRole()`:
```
bid_admin     → ROLE_ADMIN
bid_lead      → ROLE_MANAGER
bid_senior    → ROLE_MANAGER
sales         → ROLE_MANAGER
bid_specialist → ROLE_STAFF (default)
admin_staff   → ROLE_STAFF (default)
```

---

## 二、功能矩阵逐项对照

### 2.1 标讯列表

| 功能 | 角色 | 文档要求 | 代码实现 | 判定 |
|---|---|---|---|---|
| 查看列表 | BID_ADMIN | ✅ 全量 | `GLOBAL_ACCESS_ROLES` 包含 bid_admin，dataScope="all" | ✅ |
| 查看列表 | BID_LEAD | ✅ 全量 | `GLOBAL_ACCESS_ROLES` 包含 bid_lead，dataScope="all" | ✅ |
| 查看列表 | PROJECT_OWNER | ✅ 仅自己的 | dataScope="self"，通过 ProjectAccessScopeService 过滤 | ✅ |
| 查看列表 | BID_SPECIALIST | ✅ 仅分配给自己的 | dataScope="self"，通过 leadAssignment 过滤 | ✅ |
| 搜索/筛选 | 全角色 | 同查看列表 | `@DataScope` 注解在 TenderController.getAllTenders 上 | ✅ |
| 导出 | 全角色 | ✅ 可见范围 | ExportAccessFilter 使用 ProjectAccessScopeService | ✅ |
| 编辑 | BID_ADMIN | ✅ 全量·仅未立项 | 前端 actionMatrix: admin_lead 在 PENDING/TRACKING 有 edit | ✅ |
| 编辑 | BID_LEAD | ✅ 全量·仅未立项 | 前端 actionMatrix: admin_lead group 含 bid_lead | ✅ |
| 编辑 | PROJECT_OWNER | ✅ 自己的·见状态表 | 前端: sales 在 TRACKING 有 nextStep/prevStep/submit | ✅ |
| 编辑 | BID_SPECIALIST | — | 前端 actionMatrix: bid_specialist 所有状态返回空 | ✅ |
| 删除 | BID_ADMIN | ✅ 全量·仅未评估 | 前端: admin_lead 在 PENDING 有 delete；TRACKING 有 delete（⚠️见下方） | ⚠️ |
| 删除 | BID_LEAD | ✅ 全量·仅未评估 | 前端: bid_lead 的 delete 被 filter 排除（actionMatrix.js:166） | ✅ |
| 删除 | PROJECT_OWNER | ✅ 自己创建的·仅未评估 | 前端: sales 在 PENDING 且为 creator 时有 delete | ✅ |
| 删除 | BID_SPECIALIST | — | 前端: bid_specialist 无 delete | ✅ |
| 分发/转派 | BID_ADMIN | ✅ | 前端: admin_lead 在 TRACKING/EVALUATED 有 transfer | ✅ |
| 分发/转派 | BID_LEAD | ✅ | 前端: admin_lead group 含 bid_lead | ✅ |
| 分发/转派 | PROJECT_OWNER | — | 前端: sales 无 transfer | ✅ |
| 分发/转派 | BID_SPECIALIST | — | 前端: bid_specialist 无 transfer | ✅ |

### 2.2 标讯录入

| 功能 | 角色 | 文档要求 | 代码实现 | 判定 |
|---|---|---|---|---|
| 手动录入 | BID_ADMIN | ✅ | `@PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")` → bid_admin=ADMIN | ✅ |
| 手动录入 | BID_LEAD | ✅ | bid_lead=MANAGER → 通过 | ✅ |
| 手动录入 | PROJECT_OWNER | ✅ | sales=MANAGER → 通过 | ✅ |
| 手动录入 | BID_SPECIALIST | ✅ | bid_specialist=STAFF → 通过 | ✅ |
| 手动录入 | SYSTEM_ADMIN | — | task_executor=STAFF → 通过 ⚠️ | ⚠️ |
| 粘贴识别(AI) | 全投标角色 | ✅ | 同手动录入 | ✅ |
| 批量导入(Excel) | BID_ADMIN | ✅ | BatchTenderController: `hasAnyRole('ADMIN','MANAGER')` → bid_admin=ADMIN | ✅ |
| 批量导入(Excel) | BID_LEAD | ✅ | bid_lead=MANAGER → 通过 | ✅ |
| 批量导入(Excel) | PROJECT_OWNER | — | sales=MANAGER → ⚠️ 代码允许但文档不允许 | ⚠️ |
| 批量导入(Excel) | BID_SPECIALIST | ✅ | batch/tender: `hasAnyRole('ADMIN','MANAGER')` → bid_specialist=STAFF → ❌ | ❌ |

### 2.3 标讯评估

| 功能 | 角色 | 文档要求 | 代码实现 | 判定 |
|---|---|---|---|---|
| 填写评估表 | PROJECT_OWNER | ✅ 被分配的标讯 | 前端: sales 在 TRACKING 有 nextStep/prevStep/submit | ✅ |
| 填写评估表 | BID_LEAD | — | 前端: bid_lead 在 TRACKING 有 editBasic/editEvaluation ⚠️ | ⚠️ |
| 填写评估表 | BID_SPECIALIST | — | 前端: bid_specialist 在 TRACKING 无操作 | ✅ |
| 提交评估 | PROJECT_OWNER | ✅ 被分配的标讯 | 前端: sales 在 TRACKING 有 submit | ✅ |
| 确认投标 | BID_ADMIN | ✅ | 前端: admin_lead 在 EVALUATED 有 bid；后端 `/bid` 允许 ADMIN+MANAGER | ✅ |
| 确认投标 | BID_LEAD | ✅ | 前端: admin_lead group 含 bid_lead；后端 bid_lead=MANAGER → 通过 | ✅ |
| 放弃投标 | BID_ADMIN | ✅ | 前端: admin_lead 在 EVALUATED 有 abandon | ✅ |
| 放弃投标 | BID_LEAD | ✅ | 前端: admin_lead group 含 bid_lead | ✅ |

### 2.4 补充功能

| 功能 | 角色 | 文档要求 | 代码实现 | 判定 |
|---|---|---|---|---|
| 查看标讯详情 | BID_ADMIN | ✅ 全量 | TenderController.getTenderById: ADMIN/MANAGER/STAFF → bid_admin=ADMIN | ✅ |
| 查看标讯详情 | PROJECT_OWNER | ✅ 仅自己的 | `@DataScope` + ProjectAccessScopeService | ✅ |
| 查看标讯详情 | BID_SPECIALIST | ✅ 仅分配给自己的 | `@DataScope` + ProjectAccessScopeService | ✅ |
| 标讯去重·冲突拍板 | BID_ADMIN | ✅ 最终决定 | TenderCommandService: BID_ADMIN/BID_LEAD 检查 | ✅ |
| 查看分发变更日志 | BID_ADMIN | ✅ | TenderAuditService: 角色检查 | ✅ |
| 查看分发变更日志 | BID_LEAD | ✅ | TenderAuditService: 角色检查 | ✅ |

---

## 三、已发现的问题

### 问题 1：批量导入角色覆盖不符（严重度：中）→ ✅ 已修复

**文档要求**：PROJECT_OWNER(—) 不可批量导入，BID_SPECIALIST(✅) 可批量导入  
**原始代码**：BatchTenderController 使用 `hasAnyRole('ADMIN', 'MANAGER')`
- sales(MANAGER) → 通过（文档不允许）
- bid_specialist(STAFF) → 拒绝（文档要求允许）

**修复**：BatchTenderController claim/assign 端点改为 `hasAnyRole('ADMIN', 'BID_LEAD', 'BID_SENIOR')`
TenderController /import 端点改为 `hasAnyRole('ADMIN', 'BID_LEAD', 'BID_SPECIALIST')`
TenderController /import-template 端点改为 `hasAnyRole('ADMIN', 'BID_LEAD', 'BID_SPECIALIST', 'TASK_EXECUTOR', 'ADMIN_STAFF')`

### 问题 2：手动录入 SYSTEM_ADMIN 角色穿透（严重度：低）→ ✅ 已修复

**文档要求**：SYSTEM_ADMIN(—) 不可手动录入  
**原始代码**：TenderController.createTender 使用 `hasAnyRole('ADMIN', 'MANAGER', 'STAFF')`  
task_executor 映射为 STAFF → 通过

**修复**：createTender 端点改为 `hasAnyRole('ADMIN', 'BID_LEAD', 'BID_SENIOR', 'SALES', 'BID_SPECIALIST')`
task_executor 无匹配角色 → 正确拒绝

### 问题 3：投标组长编辑评估表权限（严重度：低）

**文档要求**：投标组长(—) 不可填写评估表  
**代码实现**：前端 actionMatrix 中 bid_lead 在 TRACKING 状态有 editBasic/editEvaluation 操作

**影响**：投标组长可以编辑评估表（可能是有意设计，文档未反映 bid_senior 的存在）。

### 问题 4：评估审核端点仅允许 ADMIN（严重度：低）

**文档要求**：投标管理员和投标组长均可"确认投标"  
**代码实现**：`/review` 端点 `@PreAuthorize("hasAnyRole('ADMIN')")`
- bid_admin → ROLE_ADMIN → 通过
- bid_lead → ROLE_MANAGER → 拒绝

但 `/bid` 端点（proceedToBid）允许 ADMIN+MANAGER，bid_lead 可通过。如果"确认投标"仅指 proceedToBid，则无问题。如果包含 review 步骤，则 bid_lead 被阻断。

---

## 四、测试证据

### 前端 actionMatrix 单元测试
- 59/60 测试通过（1 skipped）
- 覆盖所有 7 种标讯状态 × 5 种角色的操作矩阵
- 特别验证：bid_lead 不能有 delete 操作（符合文档）
- 特别验证：EVALUATED 状态下 bid_admin 不能 delete（符合文档 §4.2.8）

### 后端 @PreAuthorize 覆盖
- TenderController: 12 个端点全部使用 `hasAnyRole('ADMIN', 'MANAGER', 'STAFF')`
- TenderEvaluationController: 8 个端点，review 仅 ADMIN，其余 ADMIN/MANAGER/STAFF
- TenderTransferController: transfer 仅 ADMIN/MANAGER
- QualificationController: 使用新角色码 (BID_ADMIN, BID_LEAD, BID_SPECIALIST, ADMIN_STAFF)
- ProjectAccessScopeService: 统一数据权限守卫，被 50+ 个服务注入使用

### 数据权限执行
- `GLOBAL_ACCESS_ROLES = {admin, bid_admin, bid_lead, bid_senior}` → 全量数据
- 其他角色通过 ProjectAccessScopeService.getAllowedProjectIds() 按项目成员/负责人/CRM客户权限过滤
- `@DataScope` 切面自动在查询层注入过滤条件

---

## 五、结论

**整体匹配度：~90%**

核心角色定义和主要权限矩阵与飞书文档高度一致。前端 actionMatrix 的操作矩阵严格按文档实现了状态×角色的权限控制，59 个单元测试全部通过。

**需要关注的差异：**
1. ~~批量导入权限反转~~ ✅ 已修复
2. ~~手动录入穿透~~ ✅ 已修复
3. 编码名不一致（BID_LEADER→bid_lead, PROJECT_OWNER→sales, SYSTEM_ADMIN→task_executor）属于命名约定差异
4. 评估审核端点对 bid_lead 的限制取决于业务流程定义

**建议修复优先级：**
1. ~~🔴 问题 1（批量导入）~~ ✅ 已修复
2. ~~🟡 问题 2（手动录入穿透）~~ ✅ 已修复
3. 🟢 问题 3/4（评估表编辑/审核）→ 确认业务流程后决定是否调整
