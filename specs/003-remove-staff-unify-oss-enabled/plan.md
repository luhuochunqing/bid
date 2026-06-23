# Implementation Plan: 移除 staff 角色并统一用户启用状态为 OSS

**Branch**: `agent/codex/003-remove-staff-unify-oss-enabled` (待创建；当前分支为 `agent/codex/fix-crm-link-409-ui-rollback`，规划完成后需切到正确任务分支)  
**Date**: 2026-06-23  
**Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-remove-staff-unify-oss-enabled/spec.md`

## Summary

本特性解决当前系统中 OSS 权限与本地用户状态不一致的核心矛盾：

1. 删除 `staff` 角色及其全部技术债引用，使系统仅保留 7 个业务角色。
2. 将用户启用状态的权威统一为 OSS 在职状态，本地设置页不再允许修改 OSS 用户的启用状态。
3. 在登录 gate 增加角色白名单校验，未映射到 7 个业务角色的 OSS 用户无法登录。
4. 选人控件统一返回本地 `enabled=true` 的用户，不额外按角色过滤。

实施范围覆盖后端认证链路、OSS 同步、角色目录、用户枚举、前端权限判断、系统设置页以及大量测试和初始化数据。

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.2，Vue 3 + Vite 5

**Primary Dependencies**: Spring Security, JPA/Hibernate, Flyway, Element Plus

**Storage**: MySQL 8.0（用户、角色、权限数据）；OSS Gateway 提供组织架构/角色/在职状态

**Testing**: JUnit 5 + Mockito（后端），Playwright（E2E），Vitest（前端单元）

**Target Platform**: Web 应用（前后端分离）

**Performance Goals**: 登录接口 p95 < 500ms；选人接口 p95 < 300ms；OSS 同步后启用状态 5 分钟内生效

**Constraints**: 
- 不得重新引入 Mock 模式或双数据源切换。
- 必须保持本地系统管理员、开发账号、E2E 测试账号可登录。
- 架构测试（ArchUnit）必须保持全绿；禁止新增跨层依赖或上帝类。

**Scale/Scope**: 
- 影响后端约 80+ 文件（移除 staff 引用、调整登录 gate、调整同步逻辑）。
- 影响前端约 40+ 文件（路由权限、角色标签、系统设置页）。
- 需要一条 Flyway 迁移脚本处理现有 staff 用户数据。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 状态 | 说明 |
|---|---|---|
| I. FP-Java Architecture | ✅ 通过 | 登录角色校验将抽取为 Pure Core（不可变规则），应用服务仅做编排。 |
| II. Real-API Only | ✅ 通过 | 登录与同步均使用真实 OSS API；不引入 Mock。 |
| III. Test-Driven Development | ✅ 通过 | 每个改动先补/改测试，再改实现；架构测试保持全绿。 |
| IV. Split-First & Simplicity | ✅ 通过 | 移除 staff 会减少文件体积；新增 gate 逻辑拆分为独立类。 |
| V. OSS Integration | ✅ 通过 | 复用现有 `JobRoleLookupResolver` 的大小写安全映射与优先级规则。 |
| VI. Boring Proven Patterns | ✅ 通过 | 使用枚举白名单 + 现有 Repository 查询，不引入新中间件。 |

**质量门禁**: 
- Checkstyle/PMD/SpotBugs 需全绿。
- `ArchitectureTest` 需全绿。
- `npm run check:front-data-boundaries` / `check:doc-governance` / `check:line-budgets` 需全绿。

## Project Structure

### Documentation (this feature)

```text
specs/003-remove-staff-unify-oss-enabled/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
├── checklists/
│   └── requirements.md  # Spec quality checklist
└── tasks.md             # Phase 2 output (via /speckit-tasks)
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/
├── entity/
│   ├── User.java                 # 移除 Role.STAFF；保留 enabled
│   └── RoleProfileCatalog.java   # 移除 staff seed
├── crm/application/
│   ├── AuthService.java          # OSS 登录增加角色白名单与启用状态校验
│   ├── OssLoginFlowService.java  # 登录时刷新 OSS 角色/在职状态
│   └── CrmAuthService.java       # 同步时统一角色映射
├── integration/organization/application/
│   ├── OrganizationUserSyncWriter.java  # 移除 staff fallback；未映射用户标记为不可登录
│   └── OrganizationRoleMenuSyncAppService.java
├── integration/organization/domain/policy/
│   └── JobRoleLookupResolver.java       # 保留 7 角色映射；staff 不再兜底
├── auth/
│   └── UserDetailsServiceImpl.java      # 对 OSS 用户使用实时 OSS 状态
├── mention/service/
│   └── UserSearchService.java           # 保持 enabled=true 查询，不过滤角色
├── batch/service/
│   └── TenderAssignmentQueryService.java # 保持 enabled=true 查询
├── task/service/
│   └── TaskAssignmentSupport.java        # 保持 enabled=true 查询
└── bootstrap/
    ├── DefaultAdminInitializer.java      # 不受影响
    ├── LocalDevAccountInitializer.java   # 不受影响
    └── E2eDemoDataInitializer.java       # xiaowang 角色从 staff 改为其他有效角色

src/
├── router/permissions.js / workbench-role-core.js  # 移除 staff 分支
├── views/System/settings/RoleManagementPanel.vue    # 移除 staff 选项、禁用 OSS 用户启用开关
├── components/common/UserPicker.vue / 选人控件      # 保持 enabled 查询，不增加角色过滤
└── stores/user.js                                   # 移除 staff 相关逻辑

e2e/
└── tests/                    # 移除/替换 staff 角色相关测试

backend/src/main/resources/db/migration-mysql/
└── V{next}__migrate_staff_users.sql  # 将现有 staff 用户禁用或迁移
```

**Structure Decision**: 本特性为全栈改动，以后端认证与同步为核心，前端主要清理权限判断和 UI；按现有包结构分布，不新增独立模块。

## Complexity Tracking

> 本特性涉及范围较广，但所有复杂度均来自清理历史技术债，未引入新的架构层或外部依赖。

| 潜在违规/复杂度 | 为什么需要 | 已考虑的简化方案 |
|---|---|---|
| 大量文件修改（>100） | 必须一次性删除 staff 并统一登录/启用语义，分批会导致中间状态不一致 | 按 P0/P1/P2 分阶段合入，但每阶段内部保持原子 |
| 登录 gate 同时依赖 OSS + 本地缓存 | 需要登录时实时校验 OSS 角色，避免本地缓存过期导致权限扩散 | 保留本地缓存用于菜单权限，登录时强制刷新角色/在职状态 |
| E2E demo 数据角色调整 | `xiaowang` 从 staff 改为其他角色会影响既有 E2E 用例 | 同步更新对应 E2E 断言 |
