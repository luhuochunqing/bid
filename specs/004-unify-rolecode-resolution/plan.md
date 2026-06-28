# Implementation Plan: 统一服务层角色码解析入口

**Branch**: `agent/zcode/co373-unify-rolecode` | **Date**: 2026-06-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-unify-rolecode-resolution/spec.md`

## Summary

OSS 用户登录时角色码正确写入 OSS 权限缓存（8h TTL），但服务层权限校验存在两套分歧的读取路径：4 处走缓存（正确）、19 处直调 `User.getRoleCode()`（读到实体回退值 `"manager"`，错误）。本计划将角色码解析逻辑提炼为纯核心策略 + 外壳编排，所有服务层权限校验统一走该入口，彻底消除直调实体回退方法的路径。同时修复前端投标辅助人员字段回显缺陷。

## Technical Context

**Language/Version**: Java 21（后端）/ Vue 3 + Vite 5（前端）

**Primary Dependencies**: Spring Boot 3.2 + JPA (Hibernate) + Redis 7（OssPermissionCache）/ Element Plus（前端）

**Storage**: MySQL 8.0（users 表 `role_id`、`external_org_source_app` 字段）+ Redis（`oss:perm:` 前缀 key，8h TTL）

**Testing**: JUnit 5 + Mockito（后端单元测试）/ Vitest（前端单元测试）/ ArchUnit（FPJavaArchitectureTest 纯核心门禁）

**Target Platform**: Linux 服务器（后端 Spring Boot）/ 浏览器（前端 Vue）

**Project Type**: Web 服务（前后端分离）

**Performance Goals**: 角色码解析每次请求一次缓存查询（Redis GET），与现有 `resolveEffectiveRoleCode` 性能一致；不新增 DB 查询。

**Constraints**: FP-Java Profile——纯核心不得依赖 Spring/Redis/JPA；单文件软上限 200 行硬上限 300 行；权限模型调整属 RULES.md §9.3 串行任务。

**Scale/Scope**: 后端 1 个纯核心策略类 + 1 个外壳解析器 + 19 处服务落点改造 + 4 处既有实现收敛 + 1 处前端回显修复。约 25 个文件。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 检查 | 结果 |
|------|------|------|
| I. FP-Java Architecture | 角色码解析决策逻辑必须放纯核心（不依赖 Spring/Redis），缓存读取由外壳层完成 | ✅ 通过：纯核心 `EffectiveRolePolicy` 接收缓存值与实体属性作入参，外壳 `EffectiveRoleResolver` 负责读缓存后调用纯核心 |
| II. Real-API Only | 不引入 Mock，用真实 OssPermissionCache | ✅ 通过：复用现有真实缓存组件 |
| III. TDD | 先写测试再实现；ArchUnit 门禁保持全绿 | ✅ 通过：计划含纯核心单测 + 各落点回归测试 |
| IV. Split-First & Simplicity | 先拆 Application Service / Domain Policy / Gateway 再实现 | ✅ 通过：拆为 `EffectiveRolePolicy`（纯核心）+ `EffectiveRoleResolver`（外壳编排）+ 复用 `OssPermissionCache`（Gateway） |
| V. OSS Integration | 复用现有 OSS 缓存读取，不新增 OSS 调用 | ✅ 通过：不调 OSS 实时接口，只读本地缓存 |
| VI. Boring Proven Patterns | 复用 PR #1241 已验证的 `resolveEffectiveRoleCode` 模式 | ✅ 通过：提炼为共享组件 |

**Gate 结论**：全部通过，无违规需豁免。

## Project Structure

### Documentation (this feature)

```text
specs/004-unify-rolecode-resolution/
├── plan.md              # 本文件
├── research.md          # Phase 0 研究产物
├── data-model.md        # Phase 1 数据模型
├── quickstart.md        # Phase 1 快速开始
├── contracts/           # Phase 1 接口契约
└── tasks.md             # Phase 2 任务清单（/speckit-tasks 生成）
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/
├── security/
│   ├── domain/                      # 纯核心（新增）
│   │   └── EffectiveRolePolicy.java # 角色码解析决策（纯函数）
│   ├── EffectiveRoleResolver.java   # 外壳编排（新增）：读缓存 + 调纯核心 + 日志
│   └── CurrentUserResolver.java     # 改造：getCurrentRoleCode 改调 resolver
├── task/service/
│   └── TaskPermissionGuard.java     # 改造：4 处直调改 resolver
├── tender/service/
│   └── TenderCommandAccessGuard.java
├── projectworkflow/service/
│   ├── ProjectTaskAuthorizationGuard.java
│   └── ProjectTaskWorkflowService.java
├── service/
│   ├── ProjectAccessScopeService.java
│   └── ...
├── admin/service/DataScopeConfigService.java   # 评估后保留独立收紧实现（方向C，不收敛）
├── auth/UserDetailsServiceImpl.java           # 评估后保留（auth-sync 路径，非权限决策，R6 保留）
├── project/service/ProjectDraftingService.java # 收敛：删除私有 resolveEffectiveRoleCode
└── ...

src/views/Project/stages/
└── useInitiationStageActions.js   # 前端回显兜底修复
```

**Structure Decision**: 采用单仓库前后端分离结构。纯核心 `EffectiveRolePolicy` 放 `security/domain` 包（受 FPJavaArchitectureTest 门禁）。外壳 `EffectiveRoleResolver` 放 `security` 包（可注入 Spring Bean）。各 Guard/Service 改为注入 `EffectiveRoleResolver` 并调用。

## Complexity Tracking

无 Constitution 违规，无需豁免记录。
