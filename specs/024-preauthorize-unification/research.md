# Research: 消除 @PreAuthorize hasAnyRole 双轨制技术债

**Feature**: 024-preauthorize-unification
**Date**: 2026-07-02
**Status**: Complete (all decisions resolved)

本文件记录技术决策、备选方案与根因证据，作为 plan.md 的研究依据。

---

## 决策 1：迁移目标形态 — isAuthenticated + hasAuthority 双合法

**Decision**: `@PreAuthorize` 最终只允许两种形态：
- `isAuthenticated()` — 类级早过滤，或公开读取/字典/无业务语义的 schema 接口
- `hasAuthority('<permission-key>')` — 方法级细粒度业务权限

**Rationale**:
- `isAuthenticated` 解决"是否登录"，是 Controller 早过滤层的天职
- `hasAuthority` 解决"是否有该业务权限"，权限键在 RoleProfileCatalog seed 统一管理，角色新增时自动通过 menuPermissions 获得权限键，无需修改 Controller
- 这是 Spring Security 官方推荐的细粒度权限模型，也是项目 CO-394 A/B/C/D 已验证的范式

**Alternatives considered**:
- **保留 hasAnyRole 但补全所有角色**：治标，每新增角色都会复发。已被 Constitution VI 否决。
- **引入自定义 SpEL 函数（如 @auth.can('perm', #id)）**：违反 Constitution VII "Boring Proven Patterns"，增加学习成本和魔法。Spring 原生 hasAuthority 已足够。
- **改用 Spring Security 的 @Secured 注解**：功能更弱（只支持角色枚举，不支持权限键），不满足需求。

---

## 决策 2：ArchitectureTest 守卫实现策略 — ArchUnit + 自定义谓词 + IS_BELONGED_TO exemption

**Decision**: 用 ArchUnit 的 `methods().that().areAnnotatedWith(PreAuthorize.class).should(...)` 描述规则，
自定义 `DescribedPredicate<JavaMethod>` 扫描注解的 SpEL 字符串值，若包含 `hasAnyRole` 或 `hasRole`
则判定违规。豁免通过" Controller 全限定名清单"实现（不是单个方法级，降低维护成本）。

**实现要点**:
```java
@ArchTest
public static final ArchRule controllers_must_not_use_hasAnyRole_or_hasRole =
    methods()
        .that().areAnnotatedWith(PreAuthorize.class)
        .should(notContainForbiddenAuthSpEL())
        .orShouldBeInExemptionList(...)  // 过渡期豁免
        .because("Constitution VI: 禁止角色枚举式白名单，参见 specs/024-preauthorize-unification");
```

**豁免清单生成方式**: 守卫规则启动时，用 `grep -rn "@PreAuthorize.*hasAnyRole\|hasRole" backend/src/main`
扫描实际使用点，与豁免清单常量做数量一致性自验。数量不一致 → 测试失败（防止清单与实际漂移）。

**Rationale**:
- ArchUnit 是项目已有的架构测试框架（当前 47 条规则全绿），无需引入新依赖
- `DescribedPredicate` 在项目已有先例（`no_circular_dependencies` 规则第 351 行）
- Controller 级豁免（而非方法级）降低维护成本——迁移完一个 Controller 删一行
- 数量一致性自验是关键创新，防止"迁移了 Controller 但忘了从豁免清单删除"或反之

**Alternatives considered**:
- **用 checkstyle/PMD 的正则规则**：项目已有 quality gate，但正则规则难表达"豁免清单与实际一致"这种语义，且错误信息不如 ArchUnit 友好
- **自定义 SonarQube 规则**：需额外工具链，不在项目当前 CI 内
- **ArchUnit 的 freeze 功能**：ArchUnit 0.23+ 的 `freeze()` 能冻结现状、禁止新增违规。但 freeze 的实现是首次运行时记录所有违规为"冻结基线"，后续新增才报错——这恰好符合需求！但 freeze 的基线存储在 `archunit_store` 目录，需要团队理解其机制。**作为备选**：若自定义谓词实现复杂度超预期，可 fallback 到 freeze。

---

## 决策 3：迁移批次划分 — 按业务模块 + 优先级，每批独立 PR

**Decision**: 177 处使用点按下表分批，每批一个 PR，含 SecurityTest 回归。

| 批次 | 模块 | Controller 数 | 使用点数 | 优先级理由 |
|---|---|---|---|---|
| **批次 1** | task | 2（TaskExtendedFieldController + TaskController 残留） | ~7 | P1 故事载体，含本次生产故障接口 |
| **批次 2** | casework | 4（ProjectArchiveController 剩余 + Case/KnowledgeCase） | ~12 | CO-452/CO-466 已部分迁移，需收尾 |
| **批次 3** | knowledge 知识库 | 已完成 CO-394 A/B/C/D | 0 | 已迁移，仅补 ArchitectureTest 守卫验证 |
| **批次 4** | resources 资源管理 | 6（BarSiteSubresource, CaCertificate, Expense 等） | ~20 | CO-409 补丁型混合表达式集中，需拆解 |
| **批次 5** | project 项目管理 | 6（ProjectController, ProjectDrafting, ProjectClosure 等） | ~30 | 含复合业务逻辑，需 Service Policy 兜底审查 |
| **批次 6** | tender 标讯 | 3（TenderController, TenderEvaluation 等） | ~20 | 含 SALES 角色历史包袱 |
| **批次 7** | platform + analytics + 其余分散模块 | ~15 个文件 | ~88 | 剩余收尾，多为简单的 hasAnyRole('ADMIN','MANAGER') |

**每批 PR 必须包含**:
1. Controller 注解迁移 diff（hasAnyRole → hasAuthority + RoleProfileCatalog 常量）
2. 若涉及新权限键：RoleProfileCatalog 注册 + Flyway V/U 迁移
3. SecurityTest 回归用例（应有权限角色 200 + 无权限角色 403）
4. ArchitectureTest 豁免清单同步删减对应条目
5. PR 描述附 grep 验证（本批迁移前后使用点数对比）

**Rationale**:
- 按业务模块分批，单个 PR 改动聚焦、易 review
- 优先级"先收尾已部分迁移的，再开新战场"，避免半成品堆积
- 每批独立可部署，失败可回滚不影响其他模块

---

## 决策 4：Edge case 处理

### 4.1 SecurityConfig 路径级兜底（例外，不迁移）

**Decision**: `SecurityConfig.java` 的 `.requestMatchers("/api/admin/**").hasRole("ADMIN")`
**保留不动**。宪法 VI 已标注为例外。

**Rationale**: 这是 Servlet Filter 链的路径级兜底，不是业务 Controller 的方法级 `@PreAuthorize`。
路径白名单是 URL 级早过滤，与"角色枚举式方法级白名单"是不同层面的东西。且 `/api/admin/**`
下的 Controller 方法本身也会用 `@PreAuthorize("hasRole('ADMIN')")`（双重保险），迁移方法级注解
时这一层会处理，路径级兜底保留作为 defense-in-depth。

### 4.2 EXTERNAL_API 角色（外部集成鉴权）

**Decision**: `TenderIntegrationController` 的 `@PreAuthorize("hasRole('EXTERNAL_API')")`
迁移为 `@PreAuthorize("hasAuthority('integration.external')")`，并在 RoleProfileCatalog
为外部 API Key 主体单独注册该权限键（不归属任何业务角色，由 ApiKeyAuthService 单独授予）。

**Rationale**: 外部 API Key 不是业务用户角色，但用 hasAuthority 统一模型后，鉴权链路更一致。
需确认 ApiKeyAuthService 授予权限的方式（若现状只授 ROLE_EXTERNAL_API，需同步改造）。
**列入批次 6 一起处理，单独写 SecurityTest。**

### 4.3 复合表达式（BatchOperationController 等）

**Decision**: 拆解为 `hasAuthority` + Service 层 Policy。例如：
```java
// 迁移前
@PreAuthorize("hasRole('ADMIN') or (hasRole('MANAGER') and !#type.equalsIgnoreCase('tender'))")

// 迁移后（Controller 只做模块权限早过滤）
@PreAuthorize("hasAuthority('batch.operation')")
public ResponseEntity<...> run(@RequestParam String type, ...) {
    // 业务级 type 限制下沉到 Service
    batchService.assertTypeAllowedForCurrentUser(type);
    ...
}
```

**Rationale**: SpEL 里的业务逻辑（`!#type.equalsIgnoreCase('tender')`）违反 Constitution
"Controller 不做业务授权"。这类表达式集中在批次 7，逐个拆解。

### 4.4 混合补丁表达式（CO-409 的 `hasAnyRole(...) or hasAuthority('ROLE_BID_TEAM')`）

**Decision**: CaCertificateController 的 7 处 `hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('ROLE_BID_TEAM')`
是 CO-409 的临时补丁，迁移时统一改为 `hasAuthority('resource-ca')`（或对应权限键），
移除 ROLE_BID_TEAM 硬编码。CO-409 已为 bid-Team 注册 resource-ca 权限，迁移后语义等价。

**Rationale**: 这类补丁是双轨制最丑陋的形态（同时用 hasAnyRole 和 hasAuthority），
迁移后彻底干净。**列入批次 4（resources 模块）。**

---

## 决策 5：无数据模型变更

**Decision**: 本特性不涉及 DB schema 变更。

**Rationale**:
- 权限键已在 RoleProfileCatalog.SeedDefinition.menuPermissions 定义
- 历史 Flyway 迁移已将权限键同步到 `roles` 表的 `menu_permissions` 列
- 若迁移中发现某模块缺权限键注册（如 batch.operation 不存在），单独补 Flyway V 脚本 + 对应 U 回滚（按宪法）
- 这是单点补丁，不是本特性的核心交付

---

## 决策 6：无 API 契约变更

**Decision**: API 契约不变（仍是 200/403），前端无配套改动。

**Rationale**:
- 迁移是后端鉴权方式调整，HTTP 语义不变
- 唯一行为变化：某些原本被 hasAnyRole 误伤返回 403 的角色，迁移后返回 200（这正是修复目标）
- 前端 store 的 catch 逻辑（如 loadTaskExtendedFields）保持不变，无需改动

---

## 根因证据（供 plan.md Constitution Check 引用）

- **eb58f2817**（2026-06-16）：引入 ROLES_WITHOUT_LEGACY_ROLE_COMPAT，切断 bid-otherDept 等
  的 legacy role 兼容，堵住越权。**这是正确决策，本特性不动它。**
- **177 处 hasAnyRole**：依赖被切断的 legacy role，形成双轨制。**这是 eb58f2817 未完成的部分。**
- **20+ 个 403 PR**（CO-362 → CO-466）：每次单点补漏，从未系统性治理。**本特性要根治。**
- **生产故障**（2026-07-02 工号 09118 bid-otherDept）：GET /api/task-extended-fields 403，
  traceId 50f8ae0e...，服务器日志铁证。**P1 故事的触发点。**
