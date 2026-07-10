# 前后端鉴权口径不一致盘点报告

> **审计日期**：2026-07-10
> **触发事件**：用户 09118（OSS 用户，roleCode=bid-otherDept）访问 `https://winbid.ehsy.com/settings/alert-rules` 报 403
> **审计范围**：后端 `backend/src/main/java` 全量 Controller + 前端 `src/router/index.js` + `src/config/sidebar-menu.js`
> **审计结论**：系统性技术债，118 处 `hasAnyRole` 与前端权限点鉴权不一致，涉及全部 11 个业务模块

---

## 一、问题根因

### 1.1 鉴权口径双轨制

| 层 | 鉴权方式 | 数据来源 | 示例 |
|----|---------|---------|------|
| **前端** | 基于权限点（permissionKey） | `menu_permissions` 或 OSS 菜单映射 | `hasPermission('settings-alerts')` |
| **后端** | 基于角色名（Role） | `User.roleCode` → `ROLE_XXX` | `hasAnyRole('ADMIN','BIDADMIN','BID_TEAMLEADER')` |

### 1.2 后果

- 前端菜单可见 + 页面可访问（前端鉴权通过）
- API 调用返回 403（后端鉴权失败）
- 用户体验极差：能看到菜单但点进去报错

### 1.3 触发事件根因链

| 步骤 | 发生了什么 |
|------|-----------|
| 1. OSS 菜单授权 | 09118 在 OSS 系统中有菜单码 `1010`（系统设置） |
| 2. OSS 菜单映射 | `application.yml` 将 `1010` 映射为 `settings`, `system.admin`, `settings-alerts` |
| 3. 前端鉴权通过 | `router/index.js` `permissionKeys: ['settings', 'settings-alerts']` 检查通过 → 菜单显示 |
| 4. 后端鉴权失败 | `AlertRuleController` `@PreAuthorize("hasAnyRole('ADMIN', 'BIDADMIN', 'BID_TEAMLEADER')")` → 09118 是 `ROLE_BID_OTHERDEPT` → **403** |

### 1.4 生产日志证据

```
14:09:53 userId=1   roleCode=admin         GET /api/alerts/rules → 200 ✅ (管理员正常)
14:07:19 userId=112 roleCode=bid-otherDept GET /api/alerts/rules → 403 ❌ (跨部门协作人员被拒)
```

---

## 二、后端 @PreAuthorize 全量盘点

### 2.1 总量统计

| 维度 | 数量 |
|------|------|
| `hasAnyRole` 注解总数 | **118 处**（8 类级 + 110 方法级） |
| 涉及 Controller 文件 | **40 个** |
| `hasAuthority`/`hasAnyAuthority` 混用 | **4 个 Controller** |
| `hasRole('ADMIN')` 单独使用 | **1 处**（TenderEvaluationController:237） |

### 2.2 按文件分组的完整清单

#### 1. TenderController

- **文件**：`backend/src/main/java/com/xiyu/bid/tender/controller/TenderController.java`
- **@RequestMapping**：`/api/tenders`
- **类级别**：`@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 54 | (类级) | - | `hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')` |
| 86 | GET | `/{id}` | `hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')` |
| 98 | POST | `` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM', 'SALES')` |
| 108 | PUT | `/{id}` | `hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'SALES')` |
| 118 | PATCH | `/{id}/crm-opportunity` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM', 'SALES')` |
| 126 | DELETE | `/{id}` | `hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'SALES')` |
| 139 | GET | `/{id}/audit-logs` | `hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')` |
| 146 | POST | `/{id}/participate` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN')` |
| 155 | POST | `/{id}/abandon` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN')` |
| 164 | GET | `/import-template` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')` |
| 174 | POST | `/import` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')` |
| 189 | GET | `/import/{taskId}/progress` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')` |
| 199 | GET | `/status/{status}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 206 | GET | `/source/{source}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 213 | GET | `/statistics` | `hasAnyRole('ADMIN', 'MANAGER')` |

- **hasAnyRole 小计**：15 处（1 类级 + 14 方法级）

#### 2. ProjectController

- **文件**：`backend/src/main/java/com/xiyu/bid/project/controller/ProjectController.java`
- **@RequestMapping**：`/api/projects`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 55 | GET | `` | `isAuthenticated()` |
| 106 | GET | `/{id}` | `isAuthenticated()` |
| 114 | POST | `` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 129 | POST | `/import` | `hasAnyRole('ADMIN', 'BIDADMIN')` |
| 137 | PUT | `/{id}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 147 | DELETE | `/{id}` | **`hasAuthority('system.admin')`** |
| 155 | PUT | `/{id}/status` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 163 | PUT | `/{id}/team` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 171 | GET | `/status/{status}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 179 | GET | `/manager/{managerId}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 187 | GET | `/tender/{tenderId}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 195 | GET | `/active` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 203 | GET | `/search` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 212 | GET | `/statistics` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 220 | GET | `/export` | `isAuthenticated()` |

- **hasAnyRole 小计**：11 处（方法级）
- **双轨制混用**：是，1 处 `hasAuthority('system.admin')`

#### 3. TenderEvaluationController

- **文件**：`backend/src/main/java/com/xiyu/bid/tender/controller/TenderEvaluationController.java`
- **@RequestMapping**：`/api/tenders`
- **类级别**：`@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 57 | (类级) | - | `hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')` |
| 71 | GET | `/{tenderId}/evaluation` | `isAuthenticated()` |
| 83 | GET | `/{tenderId}/evaluation/ai-risk` | `isAuthenticated()` |
| 127 | PUT | `/{tenderId}/evaluation` | `isAuthenticated()` |
| 142 | POST | `/{tenderId}/evaluation/submit` | `isAuthenticated()` |
| 158 | POST | `/{tenderId}/review` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN')` |
| 174 | POST | `/{tenderId}/bid` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN')` |
| 191 | POST | `/{evaluationId}/evaluation/review` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 205 | POST | `/{tenderId}/evaluation/documents` | `isAuthenticated()` |
| 224 | GET | `/{tenderId}/evaluation/documents` | `isAuthenticated()` |
| 237 | DELETE | `/{tenderId}/evaluation/documents/{documentId}` | **`hasRole('ADMIN')`** |

- **hasAnyRole 小计**：4 处（1 类级 + 3 方法级）
- **特殊**：1 处 `hasRole('ADMIN')`（非 hasAnyRole）

#### 4. ProjectEvaluationController

- **文件**：`backend/src/main/java/com/xiyu/bid/project/controller/ProjectEvaluationController.java`
- **@RequestMapping**：`/api/projects/{projectId}/evaluation`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 44 | PATCH | `/sub-stage` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')` |
| 56 | POST | `/advance` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')` |
| 66 | POST | `/evidence` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')` |
| 79 | PATCH | `/form` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')` |
| 90 | GET | `` | `isAuthenticated()` |
| 99 | POST | `/abandon` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')` |

- **hasAnyRole 小计**：5 处（方法级）

#### 5. ProjectClosureController

- **文件**：`backend/src/main/java/com/xiyu/bid/project/controller/ProjectClosureController.java`
- **@RequestMapping**：`/api/projects/{projectId}/closure`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 46 | GET | `/preview` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')` |
| 54 | POST | `` | `hasAnyRole('ADMIN', 'BID_PROJECTLEADER')` |
| 71 | POST | `/approve` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')` |
| 84 | POST | `/reject` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')` |
| 97 | POST | `/export-documents` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')` |

- **hasAnyRole 小计**：5 处（方法级）

#### 6. ProjectInitiationController

- **文件**：`backend/src/main/java/com/xiyu/bid/project/controller/ProjectInitiationController.java`
- **@RequestMapping**：`/api/projects/{projectId}/initiation`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 45 | POST | `` | `hasAnyRole('ADMIN', 'BID_PROJECTLEADER', 'BID_TEAMLEADER')` |
| 58 | PATCH | `` | `hasAnyRole('ADMIN', 'BID_PROJECTLEADER', 'BID_TEAMLEADER')` |
| 69 | GET | `` | `isAuthenticated()` |
| 78 | POST | `/approve` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN')` |
| 90 | POST | `/reject` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN')` |
| 106 | POST | `/ai-risk-assessment` | `hasAnyRole('ADMIN', 'BID_PROJECTLEADER')` |

- **hasAnyRole 小计**：5 处（方法级）

#### 7. ProjectDraftingController

- **文件**：`backend/src/main/java/com/xiyu/bid/project/controller/ProjectDraftingController.java`
- **@RequestMapping**：`/api/projects/{projectId}/drafting`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 48 | PATCH | `/leads` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN')` |
| 60 | POST | `/advance` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')` |
| 71 | POST | `/submit-bid` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM', 'SALES')` |
| 83 | POST | `/submit-review` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM', 'SALES')` |
| 122 | POST | `/approve` | 无（类级 isAuthenticated 生效） |
| 134 | POST | `/reject` | 无 |
| 145 | GET | `` | 无 |

- **hasAnyRole 小计**：4 处（方法级）

#### 8. FeeController

- **文件**：`backend/src/main/java/com/xiyu/bid/fees/controller/FeeController.java`
- **@RequestMapping**：`/api/fees`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 54 | POST | `` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 66 | GET | `` | `isAuthenticated()` |
| 91 | GET | `/{id}` | `isAuthenticated()` |
| 102 | GET | `/project/{projectId}` | `isAuthenticated()` |
| 113 | GET | `/status/{status}` | `isAuthenticated()` |
| 124 | PUT | `/{id}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 138 | DELETE | `/{id}` | **`hasAuthority('system.admin')`** |
| 149 | POST | `/{id}/pay` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 163 | POST | `/{id}/return` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 177 | POST | `/{id}/cancel` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 188 | GET | `/statistics` | `hasAnyRole('ADMIN', 'MANAGER')` |

- **hasAnyRole 小计**：6 处（方法级）
- **双轨制混用**：是，1 处 `hasAuthority('system.admin')`

#### 9. BarSiteSubresourceController

- **文件**：`backend/src/main/java/com/xiyu/bid/resources/controller/BarSiteSubresourceController.java`
- **@RequestMapping**：`/api/resources/bar-assets/{assetId}`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 44 | GET | `/accounts` | `isAuthenticated()` |
| 50 | POST | `/accounts` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 60 | PUT | `/accounts/{accountId}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 71 | DELETE | `/accounts/{accountId}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 79 | PATCH | `/status` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 89 | POST | `/verify` | `isAuthenticated()` |
| 100 | GET | `/verification-records` | `isAuthenticated()` |
| 106 | GET | `/sop` | `isAuthenticated()` |
| 112 | PUT | `/sop` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 122 | GET | `/attachments` | `isAuthenticated()` |
| 128 | POST | `/attachments` | `isAuthenticated()` |
| 138 | DELETE | `/attachments/{attachmentId}` | `hasAnyRole('ADMIN', 'MANAGER')` |

- **hasAnyRole 小计**：6 处（方法级）

#### 10. ExpenseController

- **文件**：`backend/src/main/java/com/xiyu/bid/resources/controller/ExpenseController.java`
- **@RequestMapping**：`/api/resources/expenses`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 57 | POST | `` | `isAuthenticated()` |
| 65 | GET | `/{id}` | `isAuthenticated()` |
| 72 | GET | `` | `isAuthenticated()` |
| 90 | GET | `/ledger` | `isAuthenticated()` |
| 115 | GET | `/project/{projectId}` | `isAuthenticated()` |
| 131 | GET | `/category/{category}` | `isAuthenticated()` |
| 147 | GET | `/date-range` | `isAuthenticated()` |
| 164 | PUT | `/{id}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 175 | DELETE | `/{id}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 183 | GET | `/project/{projectId}/total` | `isAuthenticated()` |
| 190 | GET | `/project/{projectId}/statistics` | `isAuthenticated()` |
| 197 | GET | `/approval-records` | `isAuthenticated()` |
| 205 | POST | `/{id}/approve` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 215 | POST | `/{id}/return-request` | `isAuthenticated()` |
| 225 | POST | `/{id}/confirm-return` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 235 | POST | `/{id}/payments` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 245 | GET | `/{id}/payments` | `isAuthenticated()` |
| 252 | POST | `/{id}/return-reminder` | `isAuthenticated()` |

- **hasAnyRole 小计**：5 处（方法级）

#### 11. BarCertificateController

- **文件**：`backend/src/main/java/com/xiyu/bid/resources/controller/BarCertificateController.java`
- **@RequestMapping**：`/api/resources/bar-assets/{assetId}/certificates`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 40 | GET | `` | `isAuthenticated()` |
| 46 | POST | `` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 56 | PUT | `/{certificateId}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 67 | DELETE | `/{certificateId}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 77 | POST | `/{certificateId}/borrow` | `isAuthenticated()` |
| 88 | POST | `/{certificateId}/return` | `isAuthenticated()` |
| 100 | GET | `/{certificateId}/borrow-records` | `isAuthenticated()` |

- **hasAnyRole 小计**：3 处（方法级）

#### 12. BarAssetController

- **文件**：`backend/src/main/java/com/xiyu/bid/resources/controller/BarAssetController.java`
- **@RequestMapping**：`/api/resources/bar-assets`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 45 | POST | `` | `isAuthenticated()` |
| 53 | GET | `/{id}` | `isAuthenticated()` |
| 60 | GET | `` | `isAuthenticated()` |
| 78 | GET | `/type/{type}` | `isAuthenticated()` |
| 94 | GET | `/status/{status}` | `isAuthenticated()` |
| 110 | GET | `/value-range` | `isAuthenticated()` |
| 127 | GET | `/acquire-date-range` | `isAuthenticated()` |
| 144 | GET | `/search` | `isAuthenticated()` |
| 160 | PUT | `/{id}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 171 | DELETE | `/{id}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 179 | GET | `/total-value` | `isAuthenticated()` |
| 186 | GET | `/statistics` | `isAuthenticated()` |

- **hasAnyRole 小计**：2 处（方法级）

#### 13. AccountController (resources)

- **文件**：`backend/src/main/java/com/xiyu/bid/resources/controller/AccountController.java`
- **@RequestMapping**：`/api/resources/accounts`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 43 | POST | `` | `isAuthenticated()` |
| 51 | GET | `/{id}` | `isAuthenticated()` |
| 58 | GET | `` | `isAuthenticated()` |
| 76 | GET | `/type/{type}` | `isAuthenticated()` |
| 92 | GET | `/industry/{industry}` | `isAuthenticated()` |
| 108 | GET | `/region/{region}` | `isAuthenticated()` |
| 124 | GET | `/credit-level/{creditLevel}` | `isAuthenticated()` |
| 140 | GET | `/search` | `isAuthenticated()` |
| 156 | PUT | `/{id}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 167 | DELETE | `/{id}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 175 | GET | `/statistics` | `isAuthenticated()` |

- **hasAnyRole 小计**：2 处（方法级）

#### 14. CaseController

- **文件**：`backend/src/main/java/com/xiyu/bid/casework/controller/CaseController.java`
- **@RequestMapping**：`/api/knowledge/cases`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 57 | POST | `` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 66 | GET | `` | `isAuthenticated()` |
| 88 | GET | `/{id}` | `isAuthenticated()` |
| 95 | PUT | `/{id}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 103 | DELETE | `/{id}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 111 | GET | `/industry/{industry}` | `isAuthenticated()` |
| 118 | GET | `/outcome/{outcome}` | `isAuthenticated()` |
| 125 | GET | `/search/options` | `isAuthenticated()` |
| 132 | GET | `/{id}/related` | `isAuthenticated()` |
| 143 | POST | `/promote-from-project` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 152 | GET | `/{id}/share-records` | `isAuthenticated()` |
| 159 | POST | `/{id}/share-records` | `isAuthenticated()` |
| 168 | GET | `/{id}/references` | `isAuthenticated()` |
| 175 | POST | `/{id}/references` | `isAuthenticated()` |

- **hasAnyRole 小计**：4 处（方法级）

#### 15. CalendarController

- **文件**：`backend/src/main/java/com/xiyu/bid/calendar/controller/CalendarController.java`
- **@RequestMapping**：`/api/calendar`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 55 | GET | `` | `isAuthenticated()` |
| 75 | GET | `/month/{year}/{month}` | `isAuthenticated()` |
| 94 | GET | `/project/{projectId}` | `isAuthenticated()` |
| 111 | GET | `/urgent` | `isAuthenticated()` |
| 128 | POST | `` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 150 | PUT | `/{id}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 172 | DELETE | `/{id}` | `hasAnyRole('ADMIN', 'MANAGER')` |

- **hasAnyRole 小计**：3 处（方法级）

#### 16. CompetitionIntelController

- **文件**：`backend/src/main/java/com/xiyu/bid/competitionintel/controller/CompetitionIntelController.java`
- **@RequestMapping**：`/api/ai/competition`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 45 | GET | `/competitors` | `isAuthenticated()` |
| 56 | POST | `/competitors` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 69 | GET | `/project/{projectId}` | `isAuthenticated()` |
| 81 | POST | `/project/{projectId}/analyze` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 93 | POST | `/analysis` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 106 | GET | `/competitor/{id}/history` | `isAuthenticated()` |

- **hasAnyRole 小计**：3 处（方法级）

#### 17. CollaborationController

- **文件**：`backend/src/main/java/com/xiyu/bid/collaboration/controller/CollaborationController.java`
- **@RequestMapping**：`/api/collaboration`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 50 | GET | `/threads` | `isAuthenticated()` |
| 70 | GET | `/threads/{id}` | `isAuthenticated()` |
| 83 | POST | `/threads` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 100 | PUT | `/threads/{id}/status` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 118 | POST | `/threads/{id}/comments` | `isAuthenticated()` |
| 136 | PUT | `/comments/{id}` | `isAuthenticated()` |
| 153 | DELETE | `/comments/{id}` | `isAuthenticated()` |
| 166 | GET | `/mentions` | `isAuthenticated()` |

- **hasAnyRole 小计**：2 处（方法级）

#### 18. DocumentEditorController

- **文件**：`backend/src/main/java/com/xiyu/bid/documenteditor/controller/DocumentEditorController.java`
- **@RequestMapping**：`/api/documents/{projectId}/editor`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 58 | GET | `/structure` | `isAuthenticated()` |
| 73 | POST | `/structure` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 90 | GET | `/sections/tree` | `isAuthenticated()` |
| 105 | POST | `/draft-tree` | `isAuthenticated()` |
| 121 | POST | `/sections` | `isAuthenticated()` |
| 138 | PUT | `/sections/{id}` | `isAuthenticated()` |
| 148 | POST | `/assignments` | `isAuthenticated()` |
| 157 | POST | `/locks` | `isAuthenticated()` |
| 166 | POST | `/reminders` | `isAuthenticated()` |
| 183 | DELETE | `/sections/{id}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 199 | PUT | `/sections/reorder` | `isAuthenticated()` |

- **hasAnyRole 小计**：2 处（方法级）

#### 19. ROIAnalysisController

- **文件**：`backend/src/main/java/com/xiyu/bid/roi/controller/ROIAnalysisController.java`
- **@RequestMapping**：`/api/ai/roi`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 45 | GET | `/project/{projectId}` | `isAuthenticated()` |
| 61 | POST | `` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 81 | POST | `/project/{projectId}/calculate` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 102 | POST | `/sensitivity` | `isAuthenticated()` |

- **hasAnyRole 小计**：2 处（方法级）

#### 20. ContractBorrowController

- **文件**：`backend/src/main/java/com/xiyu/bid/contractborrow/controller/ContractBorrowController.java`
- **@RequestMapping**：`/api/contract-borrows`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 42 | GET | `/overview` | `isAuthenticated()` |
| 48 | GET | `` | `isAuthenticated()` |
| 61 | GET | `/{id}` | `isAuthenticated()` |
| 67 | POST | `` | `isAuthenticated()` |
| 74 | POST | `/{id}/approve` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 84 | POST | `/{id}/reject` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 94 | POST | `/{id}/return` | `isAuthenticated()` |
| 104 | POST | `/{id}/cancel` | `isAuthenticated()` |
| 114 | GET | `/{id}/events` | `isAuthenticated()` |

- **hasAnyRole 小计**：2 处（方法级）

#### 21. BidMatchModelController

- **文件**：`backend/src/main/java/com/xiyu/bid/bidmatch/controller/BidMatchModelController.java`
- **@RequestMapping**：`/api/bid-match/models`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 31 | GET | `` | `isAuthenticated()` |
| 37 | POST | `` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 46 | PUT | `` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 54 | POST | `/{id}/activate` | `hasAnyRole('ADMIN', 'MANAGER')` |

- **hasAnyRole 小计**：3 处（方法级）

#### 22. MarketPredictionController

- **文件**：`backend/src/main/java/com/xiyu/bid/marketprediction/MarketPredictionController.java`
- **@RequestMapping**：`/api/market-prediction`
- **类级别**：`@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 23 | (类级) | - | `hasAnyRole('ADMIN', 'MANAGER')` |
| 34 | GET | `/{purchaserHash}` | 无（类级生效） |
| 54 | POST | `/batch` | 无（类级生效） |
| 65 | GET | `/config/min-count` | 无（类级生效） |

- **hasAnyRole 小计**：1 处（类级）

#### 23. CustomerOpportunityController

- **文件**：`backend/src/main/java/com/xiyu/bid/marketinsight/controller/CustomerOpportunityController.java`
- **@RequestMapping**：`/api/customer-opportunities`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 42 | GET | `/insights` | `isAuthenticated()` |
| 49 | GET | `/{purchaserHash}/purchases` | `isAuthenticated()` |
| 56 | GET | `/{purchaserHash}/predictions` | `isAuthenticated()` |
| 64 | POST | `/refresh` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 70 | PUT | `/predictions/{id}/status` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 79 | PUT | `/predictions/{id}/convert` | `hasAnyRole('ADMIN', 'MANAGER')` |

- **hasAnyRole 小计**：3 处（方法级）

#### 24. AlertRuleController — 触发事件所在

- **文件**：`backend/src/main/java/com/xiyu/bid/alerts/controller/AlertRuleController.java`
- **@RequestMapping**：`/api/alerts/rules`
- **类级别**：`@PreAuthorize("hasAnyRole('ADMIN', 'BIDADMIN', 'BID_TEAMLEADER')")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 28 | (类级) | - | `hasAnyRole('ADMIN', 'BIDADMIN', 'BID_TEAMLEADER')` |
| 34 | POST | `` | 无（类级生效） |
| 40 | GET | `/{id}` | 无 |
| 46 | GET | `` | 无 |
| 52 | GET | `/enabled` | 无 |
| 58 | GET | `/type/{type}` | 无 |
| 64 | PUT | `/{id}` | 无 |
| 74 | DELETE | `/{id}` | 无 |
| 81 | PATCH | `/{id}/toggle` | 无 |

- **hasAnyRole 小计**：1 处（类级）

#### 25. AlertHistoryController

- **文件**：`backend/src/main/java/com/xiyu/bid/alerts/controller/AlertHistoryController.java`
- **@RequestMapping**：`/api/alerts/history`
- **类级别**：`@PreAuthorize("hasAnyRole('ADMIN', 'BIDADMIN', 'BID_TEAMLEADER')")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 41 | (类级) | - | `hasAnyRole('ADMIN', 'BIDADMIN', 'BID_TEAMLEADER')` |
| 49 | POST | `` | 无（类级生效） |
| 59 | GET | `` | 无 |
| 79 | GET | `/{id}` | 无 |
| 85 | GET | `/unresolved` | 无 |
| 92 | PATCH | `/{id}/acknowledge` | 无 |
| 97 | POST | `/{id}/resolve` | 无 |
| 103 | GET | `/statistics` | 无 |

- **hasAnyRole 小计**：1 处（类级）

#### 26. ProjectResultController

- **文件**：`backend/src/main/java/com/xiyu/bid/project/controller/ProjectResultController.java`
- **@RequestMapping**：`/api/projects/{projectId}/result`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 50 | POST | `` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')` |
| 68 | GET | `` | `isAuthenticated()` |

- **hasAnyRole 小计**：1 处（方法级）

#### 27. ProjectTransferController

- **文件**：`backend/src/main/java/com/xiyu/bid/project/controller/ProjectTransferController.java`
- **@RequestMapping**：`/api/projects`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 59 | POST | `/{projectId}/transfer` | `hasAnyRole('ADMIN', 'BIDADMIN')` |

- **hasAnyRole 小计**：1 处（方法级）

#### 28. TenderTransferController

- **文件**：`backend/src/main/java/com/xiyu/bid/tender/controller/TenderTransferController.java`
- **@RequestMapping**：`/api/tenders`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 44 | POST | `/{id}/transfer` | `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN')` |

- **hasAnyRole 小计**：1 处（方法级）

#### 29. AuditLogController

- **文件**：`backend/src/main/java/com/xiyu/bid/audit/controller/AuditLogController.java`
- **@RequestMapping**：`/api/audit`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 32 | GET | `` | `hasAnyRole('ADMIN', 'AUDITOR')` |
| 48 | GET | `/my` | `isAuthenticated()` |
| 75 | GET | `/project/{projectId}` | `isAuthenticated()` |

- **hasAnyRole 小计**：1 处（方法级）
- **注意**：使用幽灵角色 `AUDITOR`（RoleProfileCatalog 中已不存在）

#### 30. ApprovalController

- **文件**：`backend/src/main/java/com/xiyu/bid/approval/controller/ApprovalController.java`
- **@RequestMapping**：`/api/approvals`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 59 | POST | `/submit` | `isAuthenticated()` |
| 81 | POST | `/{id}/approve` | `isAuthenticated()` |
| 105 | POST | `/{id}/reject` | `isAuthenticated()` |
| 129 | DELETE | `/{id}` | `isAuthenticated()` |
| 147 | GET | `/pending` | `isAuthenticated()` |
| 170 | GET | `/statistics` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 180 | GET | `/{id}` | `isAuthenticated()` |
| 193 | PUT | `/{id}/read` | `isAuthenticated()` |
| 208 | GET | `/my` | `isAuthenticated()` |
| 226 | POST | `/{id}/resubmit` | `isAuthenticated()` |

- **hasAnyRole 小计**：1 处（方法级）

#### 31. TenderReminderController

- **文件**：`backend/src/main/java/com/xiyu/bid/tenderreminder/controller/TenderReminderController.java`
- **@RequestMapping**：`/api/tenders/{tenderId}/reminders`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 45 | GET | `` | `isAuthenticated()` |
| 56 | GET | `/{reminderId}` | `isAuthenticated()` |
| 71 | POST | `` | `isAuthenticated()` |
| 87 | PUT | `/{reminderId}` | `isAuthenticated()` |
| 103 | DELETE | `/{reminderId}` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 116 | POST | `/{reminderId}/toggle` | `isAuthenticated()` |

- **hasAnyRole 小计**：1 处（方法级）

#### 32. ScoreAnalysisController

- **文件**：`backend/src/main/java/com/xiyu/bid/scoreanalysis/controller/ScoreAnalysisController.java`
- **@RequestMapping**：`/api/ai/score-analysis`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 44 | GET | `/project/{projectId}` | `isAuthenticated()` |
| 56 | GET | `/project/{projectId}/history` | `isAuthenticated()` |
| 68 | POST | `` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 82 | GET | `/compare/{id1}/{id2}` | `isAuthenticated()` |

- **hasAnyRole 小计**：1 处（方法级）

#### 33. ProjectMemberController

- **文件**：`backend/src/main/java/com/xiyu/bid/security/controller/ProjectMemberController.java`
- **@RequestMapping**：`/api/projects/{projectId}/members`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 36 | GET | `` | `isAuthenticated()` |
| 43 | POST | `` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 54 | DELETE | `/{userId}` | `hasAnyRole('ADMIN', 'MANAGER')` |

- **hasAnyRole 小计**：2 处（方法级）

#### 34. ProjectWorkflowController

- **文件**：`backend/src/main/java/com/xiyu/bid/projectworkflow/controller/ProjectWorkflowController.java`
- **@RequestMapping**：`/api/projects/{projectId}`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 59 | GET | `/tasks` | `isAuthenticated()` |
| 65 | POST | `/tasks` | `isAuthenticated()` |
| 78 | POST | `/tasks/decompose` | `isAuthenticated()` |
| 89 | PATCH | `/tasks/{taskId}/status` | `isAuthenticated()` |
| 102 | GET | `/reminders` | `isAuthenticated()` |
| 108 | POST | `/reminders` | `isAuthenticated()` |
| 119 | GET | `/share-links` | `isAuthenticated()` |
| 125 | POST | `/share-links` | `isAuthenticated()` |
| 136 | POST | `/score-drafts/parse` | `isAuthenticated()` |
| 146 | GET | `/score-drafts` | `isAuthenticated()` |
| 152 | PATCH | `/score-drafts/{draftId}` | `isAuthenticated()` |
| 163 | POST | `/score-drafts/generate-tasks` | `isAuthenticated()` |
| 173 | DELETE | `/score-drafts` | `isAuthenticated()` |
| 182 | POST | `/submit-to-bid-document` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 190 | GET | `/bid-process-status` | `isAuthenticated()` |

- **hasAnyRole 小计**：1 处（方法级）

#### 35. DocumentVersionController

- **文件**：`backend/src/main/java/com/xiyu/bid/versionhistory/controller/DocumentVersionController.java`
- **@RequestMapping**：`/api/documents/{projectId}/versions`
- **类级别**：`@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| (类级) | - | - | `hasAnyRole('ADMIN', 'MANAGER')` |
| 52 | GET | `` | 无（类级生效） |
| 69 | GET | `/latest` | 无 |
| 86 | GET | `/{versionId}` | 无 |
| 104 | POST | `` | 无 |
| 124 | GET | `/{v1}/compare/{v2}` | 无 |
| 147 | POST | `/{versionId}/rollback` | 无 |

- **hasAnyRole 小计**：1 处（类级）

#### 36. PlatformAccountController

- **文件**：`backend/src/main/java/com/xiyu/bid/platform/controller/PlatformAccountController.java`
- **@RequestMapping**：`/api/platform/accounts`
- **类级别**：**`@PreAuthorize("hasAuthority('resource')")`**（非 hasAnyRole）

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| (类级) | - | - | `hasAuthority('resource')` |
| 67 | POST | `` | 无（类级生效） |
| 77 | GET | `` | 无 |
| 86 | GET | `/{id}` | 无 |
| 93 | PUT | `/{id}` | 无 |
| 103 | DELETE | `/{id}` | **`hasAuthority('system.admin')`** |
| 113 | POST | `/{id}/borrow` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 124 | POST | `/{id}/return` | 无（类级生效） |
| 151 | GET | `/{id}/password` | 无 |
| 169 | GET | `/statistics` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 177 | GET | `/overdue` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 185 | POST | `/{id}/return-with-password` | 无 |
| 199 | GET | `/template` | `hasAuthority('resource')` |
| 211 | POST | `/import` | `hasAuthority('resource')` |
| 224 | GET | `/import/tasks/{taskId}` | `hasAuthority('resource')` |
| 234 | GET | `/import/tasks` | `hasAuthority('resource')` |

- **hasAnyRole 小计**：3 处（方法级）
- **双轨制混用**：是，类级 `hasAuthority('resource')` + 方法级 `hasAuthority('system.admin')` + 方法级 `hasAuthority('resource')`

#### 37. TestController

- **文件**：`backend/src/main/java/com/xiyu/bid/controller/TestController.java`
- **@RequestMapping**：`/api`
- **类级别**：无（仅 `@Profile("dev")`）

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 27 | GET | `/public/health` | 无 |
| 35 | GET | `/user/profile` | 无 |
| 44 | GET | `/admin/dashboard` | **`hasAuthority('system.admin')`** |
| 52 | GET | `/manager/dashboard` | `hasAnyRole('ADMIN', 'MANAGER')` |

- **hasAnyRole 小计**：1 处（方法级）
- **双轨制混用**：是，1 处 `hasAuthority('system.admin')`

#### 38. CrmChanceController

- **文件**：`backend/src/main/java/com/xiyu/bid/crm/infrastructure/CrmChanceController.java`
- **@RequestMapping**：`/api/xiyu/crm/chances`
- **类级别**：`@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 28 | (类级) | - | `hasAnyRole('ADMIN', 'MANAGER')` |
| 44 | POST | `/page-list` | 无（类级生效） |
| 55 | POST | `/search-by-tender` | 无 |
| 66 | POST | `/bid-info-sync` | 无 |
| 80 | POST | `/contact-persons` | 无 |

- **hasAnyRole 小计**：1 处（类级）

#### 39. OrganizationQueryController

- **文件**：`backend/src/main/java/com/xiyu/bid/integration/organization/controller/OrganizationQueryController.java`
- **@RequestMapping**：`/api/admin/organization`
- **类级别**：`@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| (类级) | - | - | `hasAnyRole('ADMIN', 'MANAGER')` |
| 31 | GET | `/departments` | 无（类级生效） |

- **hasAnyRole 小计**：1 处（类级）

#### 40. DocumentAssemblyController

- **文件**：`backend/src/main/java/com/xiyu/bid/documents/controller/DocumentAssemblyController.java`
- **@RequestMapping**：`/api/documents/assembly`
- **类级别**：`@PreAuthorize("isAuthenticated()")`

| 行号 | HTTP 方法 | 路径 | @PreAuthorize |
|---|---|---|---|
| 40 | GET | `/templates` | `isAuthenticated()` |
| 62 | POST | `/templates` | `hasAnyRole('ADMIN', 'MANAGER')` |
| 76 | GET | `/{projectId}` | `isAuthenticated()` |
| 89 | POST | `/{projectId}/assemble` | `isAuthenticated()` |
| 112 | PUT | `/{id}/regenerate` | `isAuthenticated()` |

- **hasAnyRole 小计**：1 处（方法级）

### 2.3 hasAnyRole 数量分布汇总表

| Controller | 类级 | 方法级 | 合计 |
|---|---|---|---|
| TenderController | 1 | 14 | 15 |
| ProjectController | 0 | 11 | 11 |
| FeeController | 0 | 6 | 6 |
| BarSiteSubresourceController | 0 | 6 | 6 |
| ProjectEvaluationController | 0 | 5 | 5 |
| ProjectClosureController | 0 | 5 | 5 |
| ProjectInitiationController | 0 | 5 | 5 |
| ExpenseController | 0 | 5 | 5 |
| TenderEvaluationController | 1 | 3 | 4 |
| ProjectDraftingController | 0 | 4 | 4 |
| CaseController | 0 | 4 | 4 |
| BarCertificateController | 0 | 3 | 3 |
| CompetitionIntelController | 0 | 3 | 3 |
| BidMatchModelController | 0 | 3 | 3 |
| CustomerOpportunityController | 0 | 3 | 3 |
| CalendarController | 0 | 3 | 3 |
| PlatformAccountController | 0 | 3 | 3 |
| BarAssetController | 0 | 2 | 2 |
| AccountController | 0 | 2 | 2 |
| CollaborationController | 0 | 2 | 2 |
| DocumentEditorController | 0 | 2 | 2 |
| ROIAnalysisController | 0 | 2 | 2 |
| ContractBorrowController | 0 | 2 | 2 |
| ProjectMemberController | 0 | 2 | 2 |
| MarketPredictionController | 1 | 0 | 1 |
| AlertRuleController | 1 | 0 | 1 |
| AlertHistoryController | 1 | 0 | 1 |
| DocumentVersionController | 1 | 0 | 1 |
| CrmChanceController | 1 | 0 | 1 |
| OrganizationQueryController | 1 | 0 | 1 |
| ProjectResultController | 0 | 1 | 1 |
| ProjectTransferController | 0 | 1 | 1 |
| TenderTransferController | 0 | 1 | 1 |
| AuditLogController | 0 | 1 | 1 |
| ApprovalController | 0 | 1 | 1 |
| TenderReminderController | 0 | 1 | 1 |
| ScoreAnalysisController | 0 | 1 | 1 |
| ProjectWorkflowController | 0 | 1 | 1 |
| TestController | 0 | 1 | 1 |
| DocumentAssemblyController | 0 | 1 | 1 |
| **合计** | **8** | **110** | **118** |

---

## 三、前端 permissionKeys 全量盘点

### 3.1 路由配置（router/index.js）

共 25 处 `permissionKeys` 配置：

| # | path | name | permissionKeys | 行号 |
|---|------|------|----------------|------|
| 1 | `ai-center` | `AICenter` | `['ai-center']` | 116 |
| 2 | `knowledge/archive` | `ProjectArchive` | `['knowledge', 'knowledge-archive']` | 167 |
| 3 | `knowledge/qualification` | `Qualification` | `['knowledge', 'knowledge-qualification']` | 174 |
| 4 | `knowledge/personnel` | `Personnel` | `['knowledge', 'knowledge-personnel']` | 180 |
| 5 | `knowledge/performance` | `Performance` | `['knowledge', 'knowledge-performance']` | 186 |
| 6 | `knowledge/brand-auth` | `BrandAuth` | `['knowledge', 'knowledge-brand-auth']` | 192 |
| 7 | `knowledge/warehouse` | `Warehouse` | `['knowledge', 'knowledge-warehouse']` | 198 |
| 8 | `knowledge/case` | `Case` | `['knowledge', 'knowledge-case']` | 205 |
| 9 | `knowledge/template` | `Template` | `['knowledge', 'knowledge-template']` | 212 |
| 10 | `resource/margin` | `MarginManagement` | `['resource', 'resource-margin']` | 224 |
| 11 | `resource/account` | `Account` | `['resource', 'resource-account']` | 236 |
| 12 | `resource/ca-management` | `CAManagement` | `['resource', 'resource-ca']` | 242 |
| 13 | `analytics/dashboard` | `AnalyticsDashboard` | `['analytics-dashboard']` | 285 |
| 14 | `task-board` | `TaskBoard` | `['task-board']` | 295 |
| 15 | `operation-logs` | `OperationLogs` | `['operation-logs']` | 301 |
| 16 | `audit-logs` | `AuditLogs` | `['audit-logs']` | 307 |
| 17 | `settings` | `Settings` | `['settings']` | 313 |
| 18 | `settings/organization` | `OrganizationManagement` | `['settings']` | 319 |
| 19 | `settings/workflow-forms` | `WorkflowFormDesigner` | `['settings', 'settings-workflow-forms']` | 325 |
| 20 | `settings/alert-rules` | `AlertRules` | `['settings', 'settings-alerts']` | 331 |
| 21 | `settings/alert-history` | `AlertHistory` | `['settings', 'settings-alerts']` | 337 |
| 22 | `settings/ai-models` | `AiModels` | `['settings', 'settings-ai-models']` | 343 |
| 23 | `settings/messages-tasks` | `MessagesTasks` | `['settings', 'settings-messages-tasks']` | 349 |
| 24 | `settings/integration` | `SystemIntegration` | `['settings', 'settings-integration']` | 355 |
| 25 | `bidding/keyword-subscription` | `KeywordSubscription` | `['bidding', 'bidding-list']` | 367 |

### 3.2 侧边栏菜单配置（sidebar-menu.js）

共 30 处 `permissionKeys` 配置：

**顶层菜单项：**

| # | path | name | permissionKeys |
|---|------|------|----------------|
| 1 | `/dashboard` | `Dashboard` | `['dashboard']` |
| 2 | `/bidding` | `Bidding` | `['bidding', 'bidding-list']` |
| 3 | `/project` | `Project` | `['project']` |
| 4 | `/knowledge` | `Knowledge` | `['knowledge']` |
| 5 | `/resource` | `Resource` | `['resource']` |
| 6 | `/analytics/dashboard` | `AnalyticsDashboard` | `['analytics-dashboard']` |
| 7 | `/task-board` | `TaskBoard` | `['task-board']` |
| 8 | `/settings` | `Settings` | `['settings']` |

**子菜单项：**

| # | path | name | permissionKeys |
|---|------|------|----------------|
| 9 | `/project` | `ProjectList` | `['project-list']` |
| 10 | `/knowledge/archive` | `ProjectArchive` | `['knowledge-archive']` |
| 11 | `/knowledge/case` | `Case` | `['knowledge-case']` |
| 12 | `/knowledge/template` | `Template` | `['knowledge-template']` |
| 13 | `/knowledge/qualification` | `Qualification` | `['knowledge-qualification']` |
| 14 | `/knowledge/personnel` | `Personnel` | `['knowledge-personnel']` |
| 15 | `/knowledge/warehouse` | `Warehouse` | `['knowledge-warehouse']` |
| 16 | `/knowledge/performance` | `Performance` | `['knowledge-performance']` |
| 17 | `/knowledge/brand-auth` | `BrandAuth` | `['knowledge-brand-auth']` |
| 18 | `/resource/margin` | `MarginManagement` | `['resource-margin']` |
| 19 | `/resource/account` | `Account` | `['resource-account']` |
| 20 | `/resource/ca-management` | `CAManagement` | `['resource-ca']` |
| 21 | `/settings` | `SettingsRoot` | `['settings']` |
| 22 | `/settings/organization` | `OrganizationManagement` | `['settings']` |
| 23 | `/settings/workflow-forms` | `WorkflowFormDesigner` | `['settings-workflow-forms']` |
| 24 | `/settings/messages-tasks` | `MessagesTasks` | `['settings-messages-tasks']` |
| 25 | `/settings/alert-rules` | `AlertRules` | `['settings-alerts']` |
| 26 | `/settings/alert-history` | `AlertHistory` | `['settings-alerts']` |
| 27 | `/settings/ai-models` | `AiModels` | `['settings-ai-models']` |
| 28 | `/ai-center` | `AICenter` | `['ai-center']` |
| 29 | `/settings/integration` | `SystemIntegration` | `['settings-integration']` |
| 30 | `/operation-logs` | `OperationLogs` | `['operation-logs']` |

### 3.3 前端权限点字符串汇总

#### A. 菜单级权限点

| 权限点 | 使用位置 |
|--------|----------|
| `'dashboard'` | router/index.js:35 |
| `'bidding'` | Workbench.vue:348, helpers.js:53 |
| `'project'` | workbench-deadline-core.js:87, Workbench.vue:198 |
| `'settings'` | Header.vue:186, helpers.js:52 |
| `'analytics'` | workbench-deadline-core.js:83, useWorkbenchMetrics.js:47 |
| `'audit-logs'` | Settings.vue:122 |
| `'knowledge-brand-auth'` | BrandAuth.vue:173, BrandAuthDetailDrawer.vue:133 |
| `'settings-alerts'` | Workbench.vue:155 |
| `'all'` | useQualificationPage.js:29, Settings.vue:122/123, useProjectDetailState.js:79, helpers.js:52/53/54/55 |

#### B. 操作级权限点（细粒度操作权限）

| 权限点 | 使用位置 |
|--------|----------|
| `'knowledge:qualification:manage'` | useQualificationPage.js:29 |
| `'certificate.manage'` | useQualificationPage.js:29 |
| `'performance.manage'` | useKnowledgePermission.js:30 |
| `'warehouse.manage'` | useKnowledgePermission.js:37 |
| `'personnel.manage'` | useKnowledgePermission.js:44 |
| `'task.review'` | useProjectDetailState.js:82 |
| `'bidding.manage'` | helpers.js:52 |
| `'bidding.create'` | helpers.js:53 |
| `'bidding.delete'` | helpers.js:54 |
| `'bidding.sync'` | helpers.js:55 |
| `'project.create'` | Workbench.vue:198, helpers.js:53 |

#### C. 工作台 widget 级权限点（`dashboard:*` 前缀，共 16 个）

| 权限点 |
|--------|
| `'dashboard:view_welcome_banner'` |
| `'dashboard:view_metric_cards'` |
| `'dashboard:view_calendar'` |
| `'dashboard:view_tender_list'` |
| `'dashboard:view_technical_task'` |
| `'dashboard:view_review_list'` |
| `'dashboard:view_customer_followup'` |
| `'dashboard:view_active_projects'` |
| `'dashboard:view_team_task'` |
| `'dashboard:view_team_performance'` |
| `'dashboard:view_approval_list'` |
| `'dashboard:view_process_timeline'` |
| `'dashboard:view_activity_list'` |
| `'dashboard:view_priority_todos'` |
| `'dashboard:view_project_list'` |
| `'dashboard:view_global_projects'` |

---

## 四、前后端鉴权不一致对照（按模块）

### 4.1 告警模块 — 已确认问题

| 前端路由 | 前端 permissionKeys | 后端 Controller | 后端 @PreAuthorize | 不一致 |
|----------|---------------------|-----------------|-------------------|--------|
| `/settings/alert-rules` | `['settings','settings-alerts']` | AlertRuleController:28 | `hasAnyRole('ADMIN','BIDADMIN','BID_TEAMLEADER')` | ✅ 类级 |
| `/settings/alert-history` | `['settings','settings-alerts']` | AlertHistoryController:41 | `hasAnyRole('ADMIN','BIDADMIN','BID_TEAMLEADER')` | ✅ 类级 |

### 4.2 系统设置/组织管理

| 前端路由 | 前端 permissionKeys | 后端 Controller | 后端 @PreAuthorize | 不一致 |
|----------|---------------------|-----------------|-------------------|--------|
| `/settings/organization` | `['settings']` | OrganizationQueryController | `hasAnyRole('ADMIN','MANAGER')` 类级 | ✅ |

### 4.3 投标项目模块

| 前端路由 | 前端 permissionKeys | 后端 Controller | 后端 @PreAuthorize | 不一致 |
|----------|---------------------|-----------------|-------------------|--------|
| `/project` | `['project']` / `['project-list']` | ProjectController | 11处 `hasAnyRole('ADMIN','MANAGER')` | ✅ |
| `/project` (initiation) | 同上 | ProjectInitiationController | 5处 `hasAnyRole(...)` | ✅ |
| `/project` (closure) | 同上 | ProjectClosureController | 5处 `hasAnyRole(...)` | ✅ |
| `/project` (drafting) | 同上 | ProjectDraftingController | 4处 `hasAnyRole(...)` | ✅ |
| `/project` (evaluation) | 同上 | ProjectEvaluationController | 5处 `hasAnyRole(...)` | ✅ |
| `/project` (result) | 同上 | ProjectResultController | 1处 `hasAnyRole(...)` | ✅ |
| `/project` (transfer) | 同上 | ProjectTransferController | 1处 `hasAnyRole('ADMIN','BIDADMIN')` | ✅ |
| `/project` (members) | 同上 | ProjectMemberController | 2处 `hasAnyRole('ADMIN','MANAGER')` | ✅ |

### 4.4 标讯（招标）模块

| 前端路由 | 前端 permissionKeys | 后端 Controller | 后端 @PreAuthorize | 不一致 |
|----------|---------------------|-----------------|-------------------|--------|
| `/bidding` | `['bidding','bidding-list']` | TenderController | 15处 `hasAnyRole(...)` 含 `MANAGER`/`SALES` | ✅ |
| `/bidding` (evaluation) | 同上 | TenderEvaluationController | 4处 `hasAnyRole(...)` + 1处 `hasRole('ADMIN')` | ✅ |
| `/bidding` (transfer) | 同上 | TenderTransferController | 1处 `hasAnyRole(...)` | ✅ |
| `/bidding` (reminder) | 同上 | TenderReminderController | 1处 `hasAnyRole('ADMIN','MANAGER')` | ✅ |

### 4.5 知识库模块

| 前端路由 | 前端 permissionKeys | 后端 Controller | 后端 @PreAuthorize | 不一致 |
|----------|---------------------|-----------------|-------------------|--------|
| `/knowledge/case` | `['knowledge','knowledge-case']` | CaseController | 4处 `hasAnyRole('ADMIN','MANAGER')` | ✅ |
| `/knowledge/template` | `['knowledge','knowledge-template']` | DocumentAssemblyController | 1处 `hasAnyRole('ADMIN','MANAGER')` | ✅ |

### 4.6 资源管理模块

| 前端路由 | 前端 permissionKeys | 后端 Controller | 后端 @PreAuthorize | 不一致 |
|----------|---------------------|-----------------|-------------------|--------|
| `/resource/margin` | `['resource','resource-margin']` | FeeController | 6处 `hasAnyRole('ADMIN','MANAGER')` | ✅ |
| `/resource/account` | `['resource','resource-account']` | AccountController | 2处 `hasAnyRole('ADMIN','MANAGER')` | ✅ |
| `/resource/ca-management` | `['resource','resource-ca']` | BarAssetController | 2处 `hasAnyRole('ADMIN','MANAGER')` | ✅ |
| `/resource` (证书) | 同上 | BarCertificateController | 3处 `hasAnyRole('ADMIN','MANAGER')` | ✅ |
| `/resource` (费用) | 同上 | ExpenseController | 5处 `hasAnyRole('ADMIN','MANAGER')` | ✅ |
| `/resource` (子资源) | 同上 | BarSiteSubresourceController | 6处 `hasAnyRole('ADMIN','MANAGER')` | ✅ |
| `/resource` (平台账号) | `['resource']` | PlatformAccountController | 类级 `hasAuthority('resource')` + 3处 `hasAnyRole('ADMIN','MANAGER')` 混用 | ✅ 双轨制 |

### 4.7 数据分析模块

| 前端路由 | 前端 permissionKeys | 后端 Controller | 后端 @PreAuthorize | 不一致 |
|----------|---------------------|-----------------|-------------------|--------|
| `/analytics/dashboard` | `['analytics-dashboard']` | ROIAnalysisController | 2处 `hasAnyRole('ADMIN','MANAGER')` | ✅ |
| `/analytics/dashboard` | 同上 | ScoreAnalysisController | 1处 `hasAnyRole('ADMIN','MANAGER')` | ✅ |
| `/analytics/dashboard` | 同上 | CustomerOpportunityController | 3处 `hasAnyRole('ADMIN','MANAGER')` | ✅ |
| `/analytics/dashboard` | 同上 | MarketPredictionController | 类级 `hasAnyRole('ADMIN','MANAGER')` | ✅ |
| `/analytics/dashboard` | 同上 | BidMatchModelController | 3处 `hasAnyRole('ADMIN','MANAGER')` | ✅ |

### 4.8 任务看板模块

| 前端路由 | 前端 permissionKeys | 后端 Controller | 后端 @PreAuthorize | 不一致 |
|----------|---------------------|-----------------|-------------------|--------|
| `/task-board` | `['task-board']` | ProjectWorkflowController | 1处 `hasAnyRole('ADMIN','MANAGER')` (submit-to-bid-document) | ✅ |

### 4.9 审计日志模块

| 前端路由 | 前端 permissionKeys | 后端 Controller | 后端 @PreAuthorize | 不一致 |
|----------|---------------------|-----------------|-------------------|--------|
| `/audit-logs` | `['audit-logs']` | AuditLogController | `hasAnyRole('ADMIN','AUDITOR')` — `AUDITOR` 角色已不存在 | ✅ + 幽灵角色 |

### 4.10 AI 中心/协作模块

| 前端路由 | 前端 permissionKeys | 后端 Controller | 后端 @PreAuthorize | 不一致 |
|----------|---------------------|-----------------|-------------------|--------|
| `/ai-center` | `['ai-center']` | CompetitionIntelController | 3处 `hasAnyRole('ADMIN','MANAGER')` | ✅ |
| `/ai-center` | 同上 | CollaborationController | 2处 `hasAnyRole('ADMIN','MANAGER')` | ✅ |
| `/ai-center` | 同上 | DocumentEditorController | 2处 `hasAnyRole('ADMIN','MANAGER')` | ✅ |
| `/ai-center` | 同上 | DocumentVersionController | 类级 `hasAnyRole('ADMIN','MANAGER')` | ✅ |

### 4.11 其他

| 前端路由 | 前端 permissionKeys | 后端 Controller | 后端 @PreAuthorize | 不一致 |
|----------|---------------------|-----------------|-------------------|--------|
| `/settings/workflow-forms` | `['settings','settings-workflow-forms']` | ApprovalController:170 | `hasAnyRole('ADMIN','MANAGER')` (statistics) | ✅ |
| `/settings/integration` | `['settings','settings-integration']` | CrmChanceController | 类级 `hasAnyRole('ADMIN','MANAGER')` | ✅ |
| `/settings/messages-tasks` | `['settings','settings-messages-tasks']` | CalendarController | 3处 `hasAnyRole('ADMIN','MANAGER')` | ✅ |
| — | — | ContractBorrowController | 2处 `hasAnyRole('ADMIN','MANAGER')` | ✅ |

---

## 五、额外发现的问题

### 5.1 幽灵角色

以下角色在 `@PreAuthorize` 中被使用，但在 `RoleProfileCatalog` 中**已不存在**：

| 幽灵角色 | 使用位置 | 说明 |
|---------|---------|------|
| `MANAGER` | 95+ 处 | RoleProfileCatalog 7 个标准角色中无 `manager`，所有写 `MANAGER` 的检查实际上只有 `ADMIN` 在生效 |
| `AUDITOR` | AuditLogController:32 | `auditor` 角色已不存在（见 CLAUDE.md） |
| `SALES` | TenderController, ProjectDraftingController | `SALES` 不是标准角色（`bid-projectLeader` 按岗位映射，但角色名不是 `SALES`） |

### 5.2 双轨制混用

以下 Controller 同时使用 `hasAnyRole` 和 `hasAuthority`/`hasAnyAuthority`：

| Controller | hasAnyRole 处数 | hasAuthority 处数 | 混用详情 |
|---|---|---|---|
| ProjectController | 11 | 1 | DELETE `/{id}` 使用 `hasAuthority('system.admin')` |
| FeeController | 6 | 1 | DELETE `/{id}` 使用 `hasAuthority('system.admin')` |
| PlatformAccountController | 3 | 5 | 类级 `hasAuthority('resource')`；DELETE 使用 `hasAuthority('system.admin')`；template/import 系列使用 `hasAuthority('resource')` |
| TestController | 1 | 1 | GET `/admin/dashboard` 使用 `hasAuthority('system.admin')` |

### 5.3 hasRole('ADMIN') 单独使用

`TenderEvaluationController.java:237` 使用 `hasRole('ADMIN')`（非 `hasAnyRole`），属于第三种写法：

```java
@PreAuthorize("hasRole('ADMIN')")  // 行 237
public ResponseEntity<Void> deleteEvaluationDocument(...)
```

### 5.4 OSS 菜单映射权限扩散

OSS 菜单码 `1010`（系统设置）在 `application.yml` 中映射为 `settings`, `system.admin`, `settings-alerts` 等权限点。这意味着任何在 OSS 中有 `1010` 菜单的用户（包括跨部门协作人员）都会获得这些权限点，从而前端菜单可见，但后端 `hasAnyRole` 会拒绝访问。这是 spec-032/033 正在治理的 OSS 权限扩散问题。

### 5.5 09118 用户的矛盾解释

用户 09118 的 roleCode 和菜单权限来自两个不同的 OSS API，不是同一个缓存：

| 数据 | 来源 API | 09118 的值 |
|------|----------|-----------|
| **roleCode** | OSS `getUserJobList` → `sysRoleList` | `bid-otherDept`（岗位角色未变） |
| **菜单权限** | OSS `getUserPermission` → 菜单码 `1010` 等 | `settings`, `settings-alerts`, `system.admin` 等 |

OSS 系统可能改了 09118 的**菜单权限**（给了 1010 系统设置），但 `getUserJobList` 返回的 `sysRoleList` 中的 roleCode 还是 `bid-otherDept`。两者独立变化，不矛盾。

---

## 六、统计汇总

| 维度 | 数量 |
|------|------|
| 后端 `hasAnyRole` 注解总数 | **118 处**（8 类级 + 110 方法级） |
| 涉及 Controller 文件 | **40 个** |
| 前端路由 permissionKeys 配置 | 25 处（router）+ 30 处（sidebar） |
| 前后端鉴权口径不一致的模块 | **全部 11 个模块** |
| 幽灵角色（MANAGER/AUDITOR/SALES） | **3 个** |
| 双轨制混用（hasAnyRole + hasAuthority） | **4 个 Controller** |
| `hasRole('ADMIN')` 单独使用 | **1 处** |

---

## 七、根因分析

这是一个**系统性技术债**，不是单个 Controller 的问题。后端在早期开发时使用角色名鉴权（`hasAnyRole`），后来前端引入了基于权限点的细粒度鉴权（`permissionKeys`），但后端没有同步迁移。spec-024 已经在治理 `@PreAuthorize hasAnyRole` 双轨制，但尚未完成。

### 5 Whys 分析

1. **为什么 09118 访问告警规则页面报 403？**
   → 后端 AlertRuleController 使用 `hasAnyRole('ADMIN','BIDADMIN','BID_TEAMLEADER')`，09118 是 `bid-otherDept`，不在允许列表。

2. **为什么前端菜单可见但后端拒绝？**
   → 前端基于权限点 `settings-alerts` 鉴权（09118 通过 OSS 菜单映射获得），后端基于角色名鉴权，口径不一致。

3. **为什么前后端鉴权口径不一致？**
   → 后端早期使用角色名，前端后来引入权限点，但后端没有同步迁移。

4. **为什么后端没有同步迁移？**
   → 118 处 `hasAnyRole` 分布在 40 个 Controller 中，迁移工作量大，且缺乏强制门禁阻止新增 `hasAnyRole`。

5. **为什么缺乏强制门禁？**
   → spec-024 正在治理但尚未完成，pre-push gate 未对 `hasAnyRole` 新增做拦截。

---

## 八、建议的修复策略

### 8.1 短期（紧急修复）

先修复用户当前遇到的告警规则 403 问题：
- AlertRuleController：类级 `hasAnyRole` → `hasAuthority('settings-alerts')`
- AlertHistoryController：类级 `hasAnyRole` → `hasAuthority('settings-alerts')`

### 8.2 中期（分批迁移）

按模块优先级分批迁移：

| 优先级 | 模块 | Controller 数量 | hasAnyRole 处数 |
|--------|------|----------------|----------------|
| P0 | 告警模块 | 2 | 2 |
| P1 | 资源管理模块 | 7 | 27 |
| P2 | 知识库模块 | 2 | 5 |
| P3 | 投标项目模块 | 8 | 34 |
| P4 | 标讯模块 | 4 | 21 |
| P5 | 数据分析模块 | 5 | 10 |
| P6 | 其他模块 | 12 | 19 |

### 8.3 长期（统一治理）

- 统一到 `hasAuthority('权限点')` 模式，消除所有 `hasAnyRole`
- 消除幽灵角色（MANAGER/AUDITOR/SALES）
- 在 pre-push gate 中增加 `hasAnyRole` 新增拦截
- 完成 spec-024 的 `@PreAuthorize hasAnyRole` 双轨制治理

---

## 九、关联文档

- [AGENTS.md](../AGENTS.md) — 项目导航地图
- [SECURITY.md](../SECURITY.md) — Mock 政策、权限守卫
- spec-024 — `@PreAuthorize hasAnyRole` 双轨制治理
- spec-032 — OSS 用户权限扩散修复
- spec-033 — OSS 与本地用户权限代码路径分离
- [CLAUDE.md](../CLAUDE.md) — 角色清单（RoleProfileCatalog）

---

**审计人**：Trae Agent
**审计日期**：2026-07-10
**文档状态**：备查
