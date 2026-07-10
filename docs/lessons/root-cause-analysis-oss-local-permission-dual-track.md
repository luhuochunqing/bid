# 根因分析：OSS 与本地用户共用权限代码路径导致反复踩坑

> **报告日期**：2026-07-10
> **影响范围**：所有 OSS 同步用户在登录鉴权、菜单渲染、选人接口、权限校验四个环节均可能复现
> **排查方法**：根因猎手（Root Cause Hunter）+ 状态回溯 + 必然性证明
> **关联 Issue**：CO-361 / CO-373 / CO-551 / spec 032 / spec 024 / bid-Team 菜单泄漏 / 标讯 403
> **关联 Spec Kit**：`specs/033-oss-local-permission-path-separation/`

## 1. 问题描述

系统存在两套人员权限体系：
1. **登录鉴权体系**：登录时调用 OSS 鉴权，获取用户的密码、角色、菜单树，实时加载到 `OssPermissionCache`（Redis + 内存双写，TTL 25h）。
2. **选人业务体系**：从组织架构事件库同步人员信息到 DB `role_profile` 表，用于选人接口返回候选人及其角色。

设计上两套体系各自独立、井水不犯河水（见 `DbRoleSnapshotResolver.java` L12-25 类注释）。

**实际现象**：跨 CO-361 → CO-373 → spec 032 → CO-551 → bid-Team 菜单泄漏 → 标讯 403 等 10+ 轮修复，每次"修一次好一阵子，下一个场景又炸"。

## 2. 全链路证据链

### 2.1 设计声明层：项目"以为"两套是分离的

`backend/src/main/java/com/xiyu/bid/user/core/DbRoleSnapshotResolver.java` L12-25 类注释：

```
EffectiveRoleResolver：登录鉴权时使用，OSS 用户优先读 OSS 缓存。
DbRoleSnapshotResolver：业务操作时使用，统一读 DB role_profile 快照。
```

**预期**：OSS 用户走 EffectiveRoleResolver → OssPermissionCache；本地用户和选人接口走 DbRoleSnapshotResolver → DB role_profile。

**实际**：声明归声明，代码实现层根本没有真正分离。

### 2.2 核心逻辑层：4 个交叉污染点 + 1 个 fallback 雷区

`specs/032-fix-oss-permission-diffusion/spec.md` 已自认 4 个扩散点：

| # | 位置 | 扩散行为 |
|---|---|---|
| 1 | `UserDetailsServiceImpl.java#L126` | OSS admin → 触发 `menuPermissions.contains("all")` → 展开 seed 全权限 |
| 2 | `UserDetailsServiceImpl.java#L156` | OSS admin → 补发 `system.admin` / `warehouse.manage` |
| 3 | `DataScopeConfigService.java#L138` | OSS 缓存菜单直接加入 authorities 时未过滤 "all" |
| 4 | `DataScopeConfigService.java#L148` | OSS 用户合并 catalog seed 时未过滤 "all" |

加上 `User.java` L186-194 的 fallback 雷区：

```java
@Deprecated
public String getRoleCode() {
    if (roleProfile != null && roleProfile.getCode() != null && !roleProfile.getCode().isBlank()) {
        return roleProfile.getCode().trim();
    }
    return role == null ? "manager" : role.name().toLowerCase(...);
}
```

OSS 用户 `role_id=NULL` → fallback 返回 `"manager"` → CO-361 五次反复的根因。

### 2.3 入口层：根本没有"OSS-only"代码路径

两个 Resolver 名义上分离，但选人接口 `UserSearchService.search()` L48、`AssignmentCandidatePolicy.filter()` L65 直接调 `user.getRoleCode()`，绕过 `DbRoleSnapshotResolver`。这是 CO-373 的根因，也是 `scripts/check-rolecode-direct-calls.mjs` pre-push 拦截器存在的原因。

## 3. 零号病人定位

### 3.1 第一行错误

`User.java:186-194` 的 `getRoleCode()` fallback 不是零号病人——它只是症状放大器。

**真正的零号病人是一个架构决策**：

> 决定让 OSS 同步用户与本地用户共用同一套 `UserDetailsService` / `DataScopeConfigService` / `User` 实体代码路径，靠"分支判断 + 字段标识 + 后续修补"来区分两种身份。

### 3.2 必然性证明

```
A. OSS 用户走 loginOssUser() → 写入 OssPermissionCache（隔离的、干净的）
   ↓
B. 但 buildAuthResponse() / Service 层权限校验 → 走为本地用户写的 DataScopeConfigService.getRoleCode()
   ↓
C. DataScopeConfigService 内部遇到 "OSS 用户 roleCode=admin" 分支 → 触发 admin 扩散逻辑（本应只对本地 admin 生效）
   ↓
D. 扩散出 "all" + system.admin + warehouse.manage → OSS 用户看到所有菜单（spec 032 现象）

并行链路 1：
E. 选人接口直调 user.getRoleCode() → OSS 用户 role_id=NULL → fallback "manager"
   ↓
F. "manager" 被当成管理员 → 越权 OR 被错误过滤 → CO-361/CO-373 五轮反复

并行链路 2：
G. 前端 hasPermission 对含 "all" 的权限短路放行（本为本地 admin 设计）
   ↓
H. OSS 用户被扩散出 "all" → 前端短路 → spec 032 第三层扩散

并行链路 3：
I. RoleProfileCatalog.bid-Team 的 menuPermissions 包含 ai-center/operation-logs（本地内存目录）
   ↓
J. UserDetailsServiceImpl 合并 catalog seed → OSS bid-Team 用户拿到本地菜单权限
   ↓
K. 但 OSS 端未配置这些菜单 → 前端有权限、后端无对应 OSS 菜单 → 菜单泄漏
```

**数学上的必然**：每当新增一个"按角色判断"的业务分支，都会同时影响 OSS 用户和本地用户，但二者数据源不同，必然产生新的不一致场景。

### 3.3 状态变迁图

```
   OSS 4 步 API（隔离、干净）
            ↓
   OssPermissionCache（隔离、干净）
            ↓
   ┌────────────────────────────────────────┐
   │  buildAuthResponse / Service 层          │
   │  ↓ 走"本地用户也用"的代码路径 ↓           │  ← 交叉感染区
   │  DataScopeConfigService                  │
   │  ↓                                      │
   │  catalog seed 合并  ← 污染源 1           │
   │  admin 扩散逻辑     ← 污染源 2           │
   │  "all" 短路         ← 污染源 3           │
   │  User.getRoleCode() ← 污染源 4           │
   └────────────────────────────────────────┘
            ↓
   OSS 用户的 authorities 被污染
            ↓
   💥 越权看所有菜单 / 误判角色 / 403 / 菜单泄漏
```

## 4. 历史踩坑时间线

| 时间 | Issue/Spec | 现象 | 修复 | 是否根治 |
|---|---|---|---|---|
| 06-27 | CO-361 | 项目负责人 403 / 投标负责人只看自己 / 执行人看不到自己 | #1245 改 `DataScopeConfigService.getRoleCode` | 局部 |
| 06-28 | CO-373 | 27 处直调 `User.getRoleCode()` 引爆同类问题 | #1259 引入 `EffectiveRoleResolver` + `@Deprecated` + pre-push 拦截 | 系统性但未根治 |
| 07-04 | bid-Team 菜单泄漏 | bid-Team 看到 ai-center/operation-logs | #1661 删除 `RoleProfileCatalog` 中 bid-Team 的菜单权限 | 局部 |
| 07-08 | spec 032 / CO-551 | OSS 用户 03063/06234 看到所有菜单 | 4 个扩散点加 `isOssUser` 守卫 + 前端 `hasPermission` 守卫 | 三层防御但根因仍在 |
| 07-09 | 标讯 403 | OSS 用户 audit-logs 接口 403 | #1921 回退到 `hasAnyRole` | 单点修补 |
| 07-09 | CO-551 矛盾 | spec 说"OSS 不应持有 system.admin"，代码却允许 | #1916 改 spec 与代码对齐 | 文档对齐，未根治代码 |

**5 个 PR、跨度 13 天、每次"修一次好一阵子"**——典型"补交叉感染点不治根因"模式。

## 5. 根因类型

| 类型 | 命中 |
|---|---|
| 架构边界失守 | ✅ OSS 与本地两套身份共用同一代码路径，无 ArchUnit 强制隔离 |
| 配置漂移 | ✅ spec 032 与 CO-551 自相矛盾（spec 说"OSS 不应持有 system.admin"，代码却允许） |
| 缓存不一致 | ✅ Redis 25h TTL + DB role_profile 各自更新时机不同 |
| 双轨制技术债 | ✅ 177 处 @PreAuthorize hasAnyRole + EffectiveRoleResolver vs User.getRoleCode() |

**Bug 类型**：**架构边界失守 + 双轨制技术债复合型**。

## 6. 修复方向（三选一，详见 specs/033）

### 方案 A：真正的代码路径分离（推荐根治）

- OSS 用户走独立的 `OssUserDetailsService` + `OssAuthResponseBuilder`
- 本地用户走现有 `UserDetailsServiceImpl`
- ArchUnit 强制：OSS 用户相关类不得依赖 `RoleProfileCatalog` / `DataScopeConfigService` 的本地分支
- 删除 `User.getRoleCode()` 的 `"manager"` fallback，改为抛异常（fail-closed）

### 方案 B：强约束门禁（最小代价）

- 扩展 `scripts/check-rolecode-direct-calls.mjs`：禁止任何 `user.getRoleCode()` 直调（含 `UserSearchService`、`AssignmentCandidatePolicy`）
- 新增 ArchUnit 规则：`UserDetailsServiceImpl` 中所有 `roleCode.equals("admin")` 分支必须前置 `!isOssUser` 守卫
- 新增测试：每个 OSS 角色必须有一个"权限不扩散"测试用例

### 方案 C：彻底消除 "all" 短路 + admin 扩散（中间态）

- 删除 `UserDetailsServiceImpl` 中 admin 扩散逻辑
- 删除前端 `hasPermission` 的 "all" 短路
- 本地 admin 通过显式 seed 拿到全权限（不靠扩散）
- 这是 spec 032 的本意，但 CO-551 又部分回退了

## 7. 防复发机制

### 7.1 代码层

```java
// ArchUnit: OSS 用户代码路径不得依赖本地 catalog
@ArchTest
static final ArchRule ossUserClassesMustNotDependOnLocalCatalog =
    classes().that().resideInAPackage("..oss..")
        .should().onlyDependOnClassesThat()
        .resideInAnyPackage("..oss..", "java..", "org.springframework..");

// 单元测试: OSS 用户登录后 authorities 严格等于 OSS 返回值
@Test
void ossUserAuthoritiesMustEqualOssReturnOnly() {
    OssPermissionCache.put("oss-user", List.of("menu1", "menu2"), "bid-Team");
    UserDetails details = service.loadUserByUsername("oss-user");
    assertThat(details.getAuthorities())
        .containsExactlyInAnyOrder(
            new SimpleGrantedAuthority("ROLE_BID_TEAM"),
            new SimpleGrantedAuthority("menu1"),
            new SimpleGrantedAuthority("menu2")
        );
}
```

### 7.2 流程层

1. **新增"按角色判断"业务分支时的检查清单**：
   - [ ] 该分支是否会同时影响 OSS 用户和本地用户？
   - [ ] OSS 用户走到该分支时数据源是什么（OSS cache / DB role_profile）？
   - [ ] 是否需要前置 `isOssUser` 守卫？
   - [ ] 是否需要新增"权限不扩散"测试用例？

2. **Spec Kit 门禁**：新增角色或权限相关改动必须走 `specs/` 下的 Spec Kit 流程，禁止单点 PR 修补。

3. **CO-373 拦截器扩展**：`scripts/check-rolecode-direct-calls.mjs` 现已拦截新增 `user.getRoleCode()` 直调，未来扩展到拦截 `DataScopeConfigService` 内部的 `roleCode.equals("admin")` 无守卫分支。

## 8. SOP 取舍说明

| §23 Layer | 是否适用 | 原因 |
|---|---|---|
| Layer 1 Sentry | ❌ 不适用 | `AccessDeniedException` 属 `NON_CRITICAL_EXCEPTIONS`，不上报 Sentry |
| Layer 2 日志+TraceId | ✅ 主场 | 业务/权限校验问题主场，`UserDetailsServiceImpl` 日志 + `OssPermissionCache` 状态可定位扩散 |
| Layer 3 git 追溯 | ✅ 辅助 | 判定是回归还是原设计缺陷 → 结论：原设计缺陷（架构决策，非单一 commit 引入）|

## 9. 关联文件

| 文件 | 角色 |
|---|---|
| `backend/.../entity/User.java` L186-194 | `getRoleCode()` fallback 雷区（已 `@Deprecated`）|
| `backend/.../security/EffectiveRoleResolver.java` | OSS 缓存优先的统一入口 |
| `backend/.../user/core/DbRoleSnapshotResolver.java` | 选人业务用的 DB 快照入口（声明分离但被绕过）|
| `backend/.../config/UserDetailsServiceImpl.java` | 4 个扩散点所在 |
| `backend/.../admin/service/DataScopeConfigService.java` | 4 个扩散点所在 |
| `backend/.../entity/RoleProfileCatalog.java` | 本地内存目录，混合菜单权限与业务权限 |
| `backend/.../mention/service/UserSearchService.java` L48 | 直调 `user.getRoleCode()` 绕过 Resolver |
| `backend/.../user/core/AssignmentCandidatePolicy.java` L65 | 直调 `user.getRoleCode()` 绕过 Resolver |
| `scripts/check-rolecode-direct-calls.mjs` | CO-373 pre-push 拦截器 |
| `specs/032-fix-oss-permission-diffusion/` | 第一层止血 Spec Kit |
| `specs/033-oss-local-permission-path-separation/` | 本根因的根治 Spec Kit |
| `.wiki/pages/lessons-learned/CO-361-five-rounds-no-fix.md` | CO-361 五次反复修复的完整教训 |

## 10. 二元结论

| 条件 | 状态 |
|---|---|
| 零号病人已定位（架构决策：共用代码路径） | ✅ |
| 必然性已证明（A→B→C→D 逻辑闭环） | ✅ |
| 证据已获取（CO-361 5 轮、spec 032 4 个扩散点、CO-551 矛盾、bid-Team 泄漏） | ✅ |
| 修复方向已提供（方案 A/B/C） | ✅ |
| 防复发测试已设计（ArchUnit + 单元测试） | ✅ |

**Verdict**: [PASS]

---

**一句话总结**：零号病人不是某一行代码，是一个架构决策——让 OSS 用户与本地用户共用 `UserDetailsService` / `DataScopeConfigService` / `User.getRoleCode()`，靠"分支判断 + 后续修补"区分身份。这种设计下，新踩坑不是"会不会"的问题，是"什么时候"的问题。
