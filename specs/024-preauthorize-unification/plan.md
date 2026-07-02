# Implementation Plan: 消除 @PreAuthorize hasAnyRole 双轨制技术债

**Branch**: `024-preauthorize-unification` (working on `agent/zcode-init`) | **Date**: 2026-07-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/024-preauthorize-unification/spec.md`

## Summary

后端 177 处 `@PreAuthorize` 使用 `hasAnyRole`/`hasRole` 角色枚举式白名单，与 `RoleProfileCatalog`
的细粒度 `menuPermissions` 形成双轨制，导致 20+ 个反复返工的 403 PR。本计划分三层治理：
P1 立即修复生产故障接口（TaskExtendedFieldController）、P2 用 ArchitectureTest 守卫止血防复发、
P3 按 7 个批次将 177 处迁移到 `isAuthenticated` + `hasAuthority` 单一模型，最终守卫升级为硬失败门禁。

技术方案遵循 CO-394 A/B/C/D 已验证的迁移范式（commit b64592304），不引入新抽象（Constitution VII）。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.2 + Spring Security 6（`@EnableMethodSecurity` 已启用）、
Spring Data JPA、ArchUnit（架构测试，当前 47 条规则全绿）

**Storage**: MySQL 8.0 + Flyway（本特性**无** schema 变更，详见 research.md 决策 5）

**Testing**: JUnit 5 + Mockito + Spring Security Test（`@WithMockUser` 模拟角色）+ ArchUnit

**Target Platform**: Linux 服务器（systemd + Nginx 反代）

**Project Type**: Web service（Spring Boot 后端，前端 Vue 3 独立部署）

**Performance Goals**: 迁移不改变接口性能特征（注解求值是 O(1)）

**Constraints**: 不破坏现有 API 契约（200/403 语义不变）；每个迁移批次 PR 必须含 SecurityTest
回归证据；不修改 `ROLES_WITHOUT_LEGACY_ROLE_COMPAT`（堵越权防线不动）

**Scale/Scope**: 177 处 `@PreAuthorize` 使用点，分布在 35 个 Controller，分 7 批迁移

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Constitution Principle | 本特性如何对齐 |
|---|---|
| **I. FP-Java Architecture** | 迁移不涉及业务逻辑变更，只改 Controller 注解 + 补 Policy（Service 层 Pure Core）。✅ |
| **II. Real-API Only** | 不涉及 Mock。✅ |
| **III. Test-Driven Development** | P2 守卫必须 Red→Green（先写违反规则的测试用例）；P3 每批 PR 含 SecurityTest。✅ |
| **IV. Split-First & Simplicity** | 不新增文件，只改现有 Controller 注解 + ArchitectureTest 一个文件。✅ |
| **V. OSS Integration** | 不涉及 OSS 集成。✅ |
| **VI. Authorization Unification (新增 v1.3.0)** | **本特性就是落地这条原则**。所有 `@PreAuthorize` 迁移到 `isAuthenticated`/`hasAuthority`。✅ |
| **VII. Boring Proven Patterns** | 不引入自定义 SpEL 函数，用 Spring 原生 `hasAuthority` + ArchUnit 原生 API。✅ |
| **Security & Access Control §API Authorization** | 强化后的条款直接约束本特性。✅ |
| **Security & Access Control §Authorization ArchTest Gate** | 本特性 P2 交付物即此守卫。✅ |

**结论**: 无 Constitution 违规，无需 Complexity Tracking 表。本特性是 Constitution v1.3.0 的
首个落地项目，与原则高度一致。

## Project Structure

### Documentation (this feature)

```text
specs/024-preauthorize-unification/
├── plan.md              # 本文件
├── research.md          # 决策记录（6 个决策 + 根因证据）
├── spec.md              # 功能规格
├── checklists/
│   └── requirements.md  # spec 质量自检清单
└── tasks.md             # 待 /speckit-tasks 生成
```

### Source Code (本特性触及的代码)

```text
backend/
├── src/main/java/com/xiyu/bid/
│   ├── task/controller/
│   │   ├── TaskExtendedFieldController.java     # P1: 删方法级 @PreAuthorize
│   │   └── TaskController.java                  # P3 批次1: hasRole('ADMIN') 残留
│   ├── entity/
│   │   └── RoleProfileCatalog.java              # 权限键真相源（可能补常量）
│   ├── [各业务模块]/controller/                  # P3 批次2-7: 35 个 Controller
│   └── config/
│       └── SecurityConfig.java                  # 路径级兜底（例外，不迁移）
├── src/main/resources/db/migration-mysql/       # 若补权限键，加 V/U 脚本
└── src/test/java/com/xiyu/bid/
    ├── ArchitectureTest.java                    # P2: 新增守卫规则 + 豁免清单
    └── [各模块]SecurityTest.java                # P3: 每批回归用例
```

**Structure Decision**: 不新建包/模块。所有改动在现有 Controller + ArchitectureTest 文件内。
遵循 Constitution IV "不新增文件，只改现有"。

### 已验证的迁移范式参考

CO-394-A（commit `b64592304`）`ManufacturerAuthorizationController` 的标准迁移 diff：

```java
// 迁移前
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public ResponseEntity<...> create(...) { ... }

// 迁移后
private static final String CREATE_PERM = RoleProfileCatalog.BRAND_AUTH_CREATE_PERMISSION;
@PreAuthorize("hasAuthority('" + CREATE_PERM + "')")
public ResponseEntity<...> create(...) { ... }
```

要点：类级放宽 `isAuthenticated()`、方法级引用 RoleProfileCatalog 常量（不硬编码字符串）、
补 RoleProfileCatalogTest 断言。

## 迁移批次清单（P3 详细，来自 research.md 决策 3）

| 批次 | 模块 | Controller 数 | 使用点数 | 优先级 |
|---|---|---|---|---|
| 1 | task（含 P1 目标） | 2 | ~7 | P1+P3 |
| 2 | casework（收尾 CO-452/466） | 4 | ~12 | P3 |
| 3 | knowledge 知识库 | 0（已完成 CO-394） | 0 | 仅守卫验证 |
| 4 | resources（含 CO-409 混合补丁） | 6 | ~20 | P3 |
| 5 | project | 6 | ~30 | P3 |
| 6 | tender（含 EXTERNAL_API） | 3 | ~20 | P3 |
| 7 | platform + analytics + 分散模块 | ~15 | ~88 | P3 |

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

无 Constitution 违规，本表留空。

## Phase 计划

- **Phase 0 (Research)**: ✅ 完成，见 research.md
- **Phase 1 (Design)**: ✅ 完成，无数据模型/契约变更；项目结构如上
- **Phase 2 (Tasks)**: 待 `/speckit-tasks` 生成，按 P1→P2→P3 批次顺序分解
- **Phase 3+ (Implement)**: 待用户审批 plan 后进入
