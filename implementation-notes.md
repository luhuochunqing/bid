# CO-394 后端权限表达式统一 — 实施笔记

> 任务：知识库 5 模块后端 Controller `@PreAuthorize` 统一到 Warehouse 模板风格（`hasAuthority('<permission-constant>')`）。
> 分支：`agent/zcode/co394-backend-perm-align`
> 入口 ticket：[CO-394](https://linear.app/ericforai/issue/CO-394)
> 范围：仅后端 P0+P1（不含前端 P2、不含 dataScope）。

## 决策记录（spec 之外或与 spec 不一致的部分）

### 1. CO-394 描述/评论基于过时审计，与实际代码有出入

CO-394 description 与评论 1 的实施方案多处基于**过时审计**，实际代码已部分迁移：

| CO-394 描述 | 实际代码现状 | 本次处理 |
|---|---|---|
| 人员证书类级 `hasAnyRole('ADMIN','MANAGER')` 死锁 | 类级已是 `isAuthenticated()`（L55），方法级混用 `hasAnyAuthority(...)` + `hasAuthority('personnel.view')` | 写端点统一为 `personnel.manage`，读端点保持 `personnel.view` |
| 品牌授权"类级+方法级全 `ROLE_MANAGER`" | 类级已迁移到 `brand-auth.view`（L48），仅写端点仍 `hasAnyRole('ADMIN','MANAGER')` | 仅改写端点，常量已就绪直接复用 |
| 业绩管理"仅 GET 可达" | 读端点退化为 `isAuthenticated()`（更宽松） | 读端点也收紧为 `performance.manage`（修复过宽权限） |

### 2. 权限点粒度选择：单一 `*.manage`（非读写分离）

CO-394 评论 1 提议的 `KNOWLEDGE_AUTHORITIES = "hasAnyAuthority('/bidAdmin', 'bid-TeamLeader', 'bid-Team', 'admin')"` **未被采用**——这仍是角色码白名单思路，只是从 `hasAnyRole` 换成 `hasAnyAuthority`，未对齐 Warehouse 模板。

实际选择：每模块单一 `*.manage` 权限点（对齐 `warehouse.manage`），原因是：
- Warehouse 模块已是单一权限点，作为目标范本
- brand-auth 已有 view/create/edit/revoke 4 个细粒度常量，但读写分离会让 Flyway 脚本和 catalog 改动量翻倍，且与 Warehouse 不一致
- 人员证书已有 `personnel.view`（只读）在用，保留 view + 新增 manage 是最小破坏

### 3. 必须配套 Flyway 脚本（关键约束）

`RoleProfileBootstrapArchitectureTest` 架构门禁**禁止** bootstrap 同步 menuPermissions。CO-393/403/409 全部是「Java + Flyway」双写。仅改 Java 代码会导致已运行 DB 的角色 menuPermissions 不含新权限点 → `hasAuthority` 403。

- `menu_permissions` 是**逗号分隔字符串**（varchar 4000），非 JSON
- 参照 V1118 的 `CASE WHEN ... LIKE '%xxx%' THEN ... ELSE CONCAT(..., ',"xxx"') END` 幂等追加模式
- 版本号 V1120-V1123（V1119 已被 CO-1400 占用）

### 4. Ticket 拆分：按模块 4 个子 ticket

CO-394 评论 1 提议 6 个子 ticket（含前端 P2）。本次仅后端，拆 4 个：
- CO-394-A 品牌授权（P0）
- CO-394-B 人员证书（P0，兼顾 CO-391）
- CO-394-C 业绩管理（P1）
- CO-394-D 资质证书（P1，含错名修正）

### 5. 业绩读端点收紧决策

业绩管理读端点（list/get）当前是 `isAuthenticated()`（任何登录用户可读），本次收紧为 `hasAuthority('performance.manage')`。这是**修复过宽权限**，但可能挡住非 3 角色 + admin 的用户。评审确认：业绩本就只对投标三角色 + admin 开放，收紧符合业务意图。

## 各模块实施记录

### CO-394-A 品牌授权

**改动文件**：
- `ManufacturerAuthorizationController.java`：类级从 `hasAuthority('brand-auth.view')` 放宽为 `isAuthenticated()`（对齐 Warehouse 模板），10 个方法级注解从 `hasAnyRole('ADMIN','MANAGER')` 或硬编码字符串切换为 `hasAuthority('<PERM>')` + `RoleProfileCatalog` 常量
- `RoleProfileCatalogTest.java`：新增 2 个断言（三角色含 view/create/edit；组长+管理员含 revoke，专员不含）

**权限点映射**：
- list/detail/logs/export/template → `BRAND_AUTH_VIEW_PERMISSION`（只读）
- create/uploadAttachments/importExcel → `BRAND_AUTH_CREATE_PERMISSION`（写入）
- update → `BRAND_AUTH_EDIT_PERMISSION`
- revoke → `BRAND_AUTH_REVOKE_PERMISSION`

**Flyway 脚本**：**不需要**。V1012 已写入 brand-auth.* 权限点到旧角色码，V1092 角色码重命名时权限点随行保留。DB 中 `/bidAdmin`/`bid-TeamLeader`/`bid-Team` 三角色 menuPermissions 已含 brand-auth 权限点。

**验证**：`mvn -f backend/pom.xml compile` 通过；`RoleProfileCatalogTest` 9 tests passed（原 7 + 新增 2）。

**决策权衡**：类级注解从 `brand-auth.view` 改为 `isAuthenticated()`，是为了对齐 Warehouse 模板（类级最宽松入口 + 方法级收敛）。实际安全性不变——所有端点都有方法级权限点收敛。

### CO-394-B 人员证书

**改动文件**：
- `RoleProfileCatalog.java`：新增 `PERSONNEL_MANAGE_PERMISSION = "personnel.manage"` 常量，3 角色（bid-TeamLeader/bidAdmin/bid-Team）menuPermissions 追加 `personnel.manage`
- `PersonnelController.java`：写端点（create/update/delete/restore/uploadCertAttachment）从 `hasAnyAuthority(...)` 角色码白名单切换为 `hasAuthority('personnel.manage')`；只读端点（getOperationLogs/downloadCertAttachment）收敛为 `hasAuthority('personnel.view')`
- `PersonnelImportController.java`：4 个端点从混合 `hasAnyAuthority(...,'ROLE_BIDADMIN',...)` 切换为 `hasAuthority('personnel.manage')`
- `RoleProfileCatalogTest.java`：新增 2 个断言（3 角色含 personnel.manage；3 角色保留 personnel.view）
- `PersonnelImportControllerSecurityTest.java`：重写测试，从验证角色码白名单改为验证 `personnel.manage` 权限点
- `KnowledgeAccessSecurityTest.java`：更新 revoke 端点测试的 DisplayName 和注释（鉴权机制从 ADMIN/MANAGER 变为 brand-auth.revoke 权限点）

**Flyway 脚本**：`V1121__add_personnel_manage_permission.sql`，3 角色 menuPermissions 追加 `personnel.manage`

**关键决策：delete/restore 权限收窄 vs 三角色一致性**
CO-394 明确要求"三角色 CRUD 端点完全相同"。原 delete/restore 端点不含 bid-Team（投标专员不能删除/恢复人员）。按 CO-394 目标，统一为 `personnel.manage`，投标专员获得 delete/restore 权限——这是**业务权限变更**，不是纯技术对齐。如果业务上投标专员确实不应删除，需在 CO-394 评审时提出，本次按"三角色一致性"目标实施。

**验证**：`mvn -f backend/pom.xml test -Dtest=RoleProfileCatalogTest,PersonnelImportControllerSecurityTest,KnowledgeAccessSecurityTest` → 39 tests passed, BUILD SUCCESS

### CO-394-C 业绩管理

**改动文件**：
- `RoleProfileCatalog.java`：新增 `PERFORMANCE_MANAGE_PERMISSION = "performance.manage"` 常量，3 角色 menuPermissions 追加
- `PerformanceController.java`：所有 9 个方法级注解从 `hasAnyRole('ADMIN','MANAGER')` 或 `isAuthenticated()` 切换为 `hasAuthority('" + PERM + "')`
- `RoleProfileCatalogTest.java`：新增 1 个断言（3 角色含 performance.manage）

**Flyway 脚本**：`V1122__add_performance_manage_permission.sql`，3 角色 menuPermissions 追加 `performance.manage`

**关键决策：读端点收紧**
业绩管理读端点（list/get）原为 `isAuthenticated()`（任何登录用户可读），本次收紧为 `hasAuthority('performance.manage')`。这修复了过宽权限，但也意味着非 3 角色 + admin 的用户（如项目负责人、行政人员）将无法读取业绩列表。符合 CO-394"业绩只对投标三角色 + admin 开放"的业务意图。

**验证**：`mvn -f backend/pom.xml test -Dtest=RoleProfileCatalogTest` → 12 tests passed, BUILD SUCCESS。无既有业绩模块 @PreAuthorize 集成测试需更新。

### CO-394-D 资质证书

**改动文件**：
- `RoleProfileCatalog.java`：新增 `QUALIFICATION_MANAGE_PERMISSION = "qualification.manage"` 常量，3 角色 menuPermissions 追加；行政人员保留 `qualification.view`（只读）
- `QualificationController.java`：所有 15 个方法级注解从 `hasAnyRole(...)` 切换为 `hasAuthority('" + PERM + "')`
- `QualificationExportController.java`：7 个方法级注解从 `hasAnyRole(...)` 或 `isAuthenticated()` 切换为 `hasAuthority('" + PERM + "')`
- `RoleProfileCatalogTest.java`：新增 2 个断言（3 角色含 qualification.manage；行政人员仅 view 不含 manage）

**Flyway 脚本**：`V1123__add_qualification_manage_permission.sql`，3 角色 menuPermissions 追加 `qualification.manage`

**错名修正**：
- `QualificationExportController` 的 `BIDADMIN` 重复 bug（template/import/import-combined/batch-attach 端点）自动修复——统一为 `hasAuthority` 后不再有重复
- `BID_ADMINISTRATION` 错名问题：原注解混用 `BID_ADMINISTRATION`（带下划线，对应 `bid-administration` 行政人员），行政人员不应有资质写入权限，统一为 `qualification.manage` 后行政人员自然被排除（仅有 `qualification.view`）

**scanExpiringQualifications 端点放宽**：原仅 `ADMIN, BIDADMIN`（最窄），按三角色一致性改为 `qualification.manage`，3 角色均可扫描过期资质。

**验证**：`mvn -f backend/pom.xml test -Dtest=RoleProfileCatalogTest` → 14 tests passed, BUILD SUCCESS。无既有资质模块 @PreAuthorize 集成测试需更新。

## 验证记录

### 全量测试结果

`mvn -f backend/pom.xml test -Dtest=RoleProfileCatalogTest,PersonnelImportControllerSecurityTest,KnowledgeAccessSecurityTest,RoleProfileBootstrapArchitectureTest`

→ **Tests run: 43, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS**

各测试类明细：
- `RoleProfileCatalogTest`: 14 tests（原 7 + CO-394 新增 7）
- `PersonnelImportControllerSecurityTest`: 10 tests（重写，从角色码白名单改为权限点鉴权）
- `KnowledgeAccessSecurityTest`: 18 tests（更新 revoke 端点测试注释）
- `RoleProfileBootstrapArchitectureTest`: 1 test（架构门禁，验证未触碰 bootstrap 同步逻辑）

### Flyway 脚本

- V1121: 人员库 `personnel.manage` 权限点（3 角色）
- V1122: 业绩管理 `performance.manage` 权限点（3 角色）
- V1123: 资质证书 `qualification.manage` 权限点（3 角色）
- V1120 未使用（品牌授权无需新脚本，V1012+V1092 已就绪）

### 编译验证

`mvn -f backend/pom.xml compile` → BUILD SUCCESS（无错误）

### 提交记录

| Commit | 模块 | 描述 |
|---|---|---|
| `b64592304` | CO-394-A | 品牌授权 Controller 权限注解切换 |
| `c450007ed` | CO-394-B | 人员证书 Controller 权限注解切换 |
| `c091ea95f` | CO-394-C | 业绩管理 Controller 权限注解切换 |
| `fdcb4861d` | CO-394-D | 资质证书 Controller 权限注解切换 |

### 行数预算

`RoleProfileCatalog.java` 保持在 300 行（line-budget 限制），通过合并注释和 menuPermissions 行尾追加实现。

## 设计评估决策记录（2026-06-30）

思维链 Review 流程识别出 2 个业务行为变更，经产品决策后**均保留现状不修改**：

### P0：投标专员获得人员 delete/restore 权限 — 保留现状

**位置**：`PersonnelController.java:144-157`（delete）+ `PersonnelController.java:175-183`（restore）
**变更前**：`hasAnyAuthority('admin','/bidAdmin','bid-TeamLeader')` — 刻意排除投标专员
**变更后**：`hasAuthority('personnel.manage')` + bid-Team 持有该权限 — 投标专员可删除/恢复
**决策依据**：CO-394 ticket 明确要求"三角色 CRUD 完全相同"，此为有意行为变更而非技术副作用。投标专员获得 delete/restore 是三角色一致性的直接体现。
**PR 标注要求**：在 PR 描述中明确说明这是有意行为变更。

### P1：业绩读端点收紧到 performance.manage — 保留现状

**位置**：`PerformanceController.java:59-60`（list）+ `PerformanceController.java:84-85`（get）
**变更前**：`@PreAuthorize("isAuthenticated()")` — 任何登录用户可读
**变更后**：`@PreAuthorize("hasAuthority('" + PERM + "')")` 即 `performance.manage`
**影响**：项目负责人（sales）、行政人员（admin_staff）将无法读取业绩列表
**决策依据**：业绩管理属于投标知识库范畴，收紧到投标三角色符合模块定位。原 `isAuthenticated()` 是松散默认值，非有意开放给所有角色。
**PR 标注要求**：在 PR 描述中明确说明此收紧。

### 其他低优技术债（不在本次修改范围）

- `docs/permission-matrix/` 不覆盖知识库 5 模块 — 后续补文档
- `PersonnelImportControllerSecurityTest` 重写丢失 CO-391 roleCode 漂移兜底回归语义 — 可补组合断言
- `RoleProfileCatalog.java:164` menuPermissions 行过长 — 后续若持续增长可拆分文件
