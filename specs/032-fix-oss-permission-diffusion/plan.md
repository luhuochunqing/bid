# Implementation Plan: 修复 OSS 用户权限扩散导致越权看所有菜单

**Branch**: `agent/claude/fix-oss-permission-diffusion` | **Date**: 2026-07-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/032-fix-oss-permission-diffusion/spec.md`

## Summary

OSS 用户（如 03063/06234）登录后能看到系统所有菜单，根因是 OSS 用户被 `JobRoleLookupResolver` 映射为内部 `admin` roleCode 后，在两个独立链路触发 admin 权限扩散：

1. **后端 authorities 链**（`UserDetailsServiceImpl`）：OSS admin 用户命中 L120 扩散分支，把 `RoleProfileCatalog.seedDefinitions()` 所有角色的 menuPermissions + `all` 加入 authorities；L143 又补发 `system.admin`/`warehouse.manage`。
2. **前端 menuPermissions 链**（`DataScopeConfigService.getRoleMenuPermissions`）：OSS admin 用户合并 `RoleProfileCatalog.definitionForCode("admin").menuPermissions()` = `["all"]`，前端 `hasPermission` 看到 `all` 短路放行。

第一层最小修复：在两条链路上对 `isOssUser=true` 加守卫，OSS 用户不扩散、不合并 admin seed、不补发系统级权限键；本地 admin 行为完全不变。前端 `hasPermission` 的 `all` 短路逻辑对 OSS 用户失效（defense-in-depth）。

技术方案遵循 lessons-learned §22（外部诊断根因必须复核）+ §29（前后端对称修复）+ decisions §3（Controller 放宽为 isAuthenticated，真权限交给 Service 层），不引入新抽象（Constitution VIII）。

## Technical Context

**Language/Version**: Java 21（后端）+ JavaScript ES2022/Vue 3（前端）

**Primary Dependencies**: Spring Boot 3.2 + Spring Security 6 + Pinia + Element Plus

**Storage**: MySQL 8.0（`users` 表 `external_org_source_app` 字段标识 OSS 用户）+ Redis（`OssPermissionCache` 持久化缓存）

**Testing**: JUnit 5 + Mockito（后端单元测试）+ ArchUnit（架构边界）+ Vitest（前端单元测试）+ Playwright（E2E）

**Target Platform**: Linux 服务器（后端 Spring Boot JAR）+ 浏览器（前端 SPA）

**Project Type**: Web application（前后端分离）

**Performance Goals**: 登录响应无明显变化（<200ms p95，权限扩散逻辑是内存操作）

**Constraints**: 
- 不能破坏本地 admin 体验（回归测试必须覆盖）
- 不能动 `ROLES_WITHOUT_LEGACY_ROLE_COMPAT` 防线
- 不能动 `JobRoleLookupResolver` 的 "投标系统管理员" → admin 映射（第二层治理范围）
- 不能混入 specs/024 的 177 处 `@PreAuthorize` 迁移

**Scale/Scope**: 2 个后端文件 + 2 个前端文件 + 测试文件；改动行数 <100

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查结果 | 说明 |
|---|---|---|
| **I. FP-Java Architecture** | ✅ 通过 | 修改落在 `UserDetailsServiceImpl`（Imperative Shell）和 `DataScopeConfigService`（Application Service），不修改纯核心。新增 OSS 用户权限判定纯函数可放 `security.domain` 包。 |
| **II. Real-API Only** | ✅ 通过 | 不引入 Mock，依赖真实 `OssPermissionCache` 数据。 |
| **III. Test-Driven Development** | ✅ 通过 | 遵循 Red → Green → Refactor。先写测试：OSS admin 用户 authorities 不含 `all`/`system.admin`；本地 admin 行为不变；前端 `hasPermission` 对 OSS 用户不短路。 |
| **IV. Split-First & Simplicity** | ✅ 通过 | 不新增类，只修改既有类的条件分支。单文件改动行数 <50。 |
| **V. OSS Integration** | ✅ 通过 | 不修改 OSS 接口调用，只修改 OSS 权限数据的消费逻辑。`OssPermissionCache` 缓存行为不变。 |
| **VI. Authorization Unification** | ✅ 通过 | 不新增 `hasAnyRole`，不修改 `@PreAuthorize` 注解。本次只改 authorities 构建逻辑，不改鉴权表达式。 |
| **VII. Defensive Collection & Graceful Degradation** | ✅ 通过 | 不涉及 `Collectors.toMap` 或装饰性 enrichment。 |
| **VIII. Boring Proven Patterns** | ✅ 通过 | 用最平淡的 `if (!isOssUser)` 守卫，不引入新抽象。 |
| **Security & Access Control** | ✅ 通过 | 强化权限最小化：OSS 用户只看 OSS 返回的菜单。不放宽 `SecurityConfig` 范围，不新增角色码。 |
| **Development Workflow & Multi-Agent SOP** | ✅ 通过 | 已跑早操同步，已创建任务分支 `agent/claude/fix-oss-permission-diffusion`。 |

**Gate 结果**: 全部通过，无 Constitution 违规。

## Project Structure

### Documentation (this feature)

```text
specs/032-fix-oss-permission-diffusion/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── oss-permission-contract.md
├── spec.md              # /speckit-specify 产物
├── tasks.md             # Phase 2 output (/speckit-tasks)
└── checklists/
    └── requirements.md  # /speckit-specify 产物
```

### Source Code (repository root)

```text
backend/
├── src/
│   ├── main/java/com/xiyu/bid/
│   │   ├── auth/
│   │   │   └── UserDetailsServiceImpl.java       # 修改点 1: OSS 用户守卫 admin 扩散
│   │   └── admin/service/
│   │       └── DataScopeConfigService.java        # 修改点 2: OSS 用户不合并 admin seed
│   └── test/java/com/xiyu/bid/
│       ├── auth/
│       │   └── UserDetailsServiceImplTest.java    # 新增/补充测试
│       └── admin/service/
│           └── DataScopeConfigServiceTest.java    # 新增/补充测试

src/
├── stores/
│   └── user.js                                    # 修改点 3: hasPermission 对 OSS 用户不短路
└── components/layout/
    └── Sidebar.vue                                # 可能修改（如果 hasPermission 在此处有副本）

e2e/
└── oss-permission-diffusion.spec.js               # 新增 E2E（可选，P2）
```

**Structure Decision**: Web application 结构（前后端分离）。改动集中在 3 个源文件 + 对应测试文件，无新增模块。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

无 Constitution 违规，无需填写。
