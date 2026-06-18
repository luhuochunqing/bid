# Implementation Plan: OSS 批量岗位/角色回查优化

**Branch**: `001-oss-batch-job-role-sync` | **Date**: 2026-06-18 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-oss-batch-job-role-sync/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

接入 OSS 批量查询岗位/角色接口 `POST /oss/admin-web/v1/output/data/getUserJobListByJobNumberList`，按工号列表一次性回查用户的岗位名称（`jobName`）与系统角色列表（`sysRoleList`），并据此优化现有组织架构同步流程：

- 用批量回查替换当前对每个 `positionId` 单独调用 `/subscription/msg/job` 的逐条查询。
- 将 `sysRoleList` 作为角色映射的第四优先级来源（人员 > 部门 > 岗位 > sysRoleList）。
- 保持角色映射大小写安全与向后兼容。
- 新增可配置项（接口路径、超时、批量大小）与完整的单元/集成测试覆盖。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.3, Spring Web (RestTemplate), Jackson, JPA, Flyway

**Storage**: MySQL 8.0

**Testing**: JUnit 5, Mockito, AssertJ, Spring Boot Test, Testcontainers (for integration tests)

**Target Platform**: Linux server (backend service)

**Project Type**: Web application backend

**Performance Goals**: 同步 1,000 个用户时，OSS 岗位/角色查询调用次数从约 1,000 次降至不超过 20 次；单批 50 个工号；批量接口超时配置默认连接 3s、读取 10s。

**Constraints**: 批量接口不可用时同步任务不能整体失败；角色映射优先级和大小写不敏感行为必须保持向后兼容；单个 Java 文件不超过 300 行。

**Scale/Scope**: 当前 OSS 全量用户约 8,637 人，目标岗位过滤后通常 <100 人/批次。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status | Notes |
|---|---|---|---|
| I. FP-Java Architecture | 核心映射/解析逻辑 MUST 可单测、不依赖框架 | ✅ Pass | `PositionToRoleMapper` 与新增 `JobRoleLookupResolver` 均为纯函数风格；`OrganizationUserSyncWriter` 中的角色解析逻辑将拆出独立 Policy。 |
| II. Real-API Only | 禁止 Mock，调用真实 OSS 接口 | ✅ Pass | 新增 Gateway 方法直接调用真实 OSS 批量接口；测试使用 WireMock/Testcontainers 模拟真实 HTTP 行为，不引入业务 Mock。 |
| III. TDD | Red → Green → Refactor；核心测试覆盖 80%+ | ✅ Pass | 先补充失败测试，再实现批量查询与角色优先级。 |
| IV. Split-First & Simplicity | 禁止上帝类；单文件 <300 行 | ✅ Pass | 新增 `JobRoleLookupResolver` / `SystemRoleListMapper` 独立类；`OrganizationUserSyncWriter` 角色解析逻辑外迁。 |
| V. OSS Integration | 批量优先、大小写安全、映射优先级 | ✅ Pass | 批量接口替代逐条调用；映射优先级与大小写安全写入代码与测试。 |
| VI. Boring Proven Patterns | 平淡可预测的技术模式 | ✅ Pass | 沿用现有 RestTemplate Gateway、Jackson DTO、ConfigurationProperties 模式。 |

## Project Structure

### Documentation (this feature)

```text
specs/001-oss-batch-job-role-sync/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/xiyu/bid/integration/organization/
│   ├── application/
│   │   ├── OrganizationDirectoryGateway.java          # 新增批量查询方法契约
│   │   ├── OrganizationIntegrationProperties.java     # 新增批量接口路径/超时/大小配置
│   │   ├── OrganizationUserSyncWriter.java            # 接入批量回查与 sysRoleList 映射
│   │   └── NoOpOrganizationDirectoryGateway.java      # 实现新增批量方法（空实现）
│   ├── domain/
│   │   └── policy/                                    # 新增角色解析纯函数策略
│   │       ├── JobRoleLookupResolver.java
│   │       └── SystemRoleListMapper.java
│   ├── dto/
│   │   └── OssUserJobAndRoleDto.java                  # 批量接口响应 DTO
│   └── infrastructure/
│       ├── client/
│       │   ├── OrganizationDirectoryHttpGateway.java  # 实现批量接口调用
│       │   └── OrganizationDirectoryJsonMapper.java   # 新增批量响应解析
│       └── mapper/
│           └── PositionToRoleMapper.java              # 保持现有岗位映射
└── src/test/java/com/xiyu/bid/integration/organization/
    ├── application/
    │   ├── OrganizationUserSyncWriterTest.java        # 增加批量回查 + sysRoleList 测试
    │   └── JobRoleLookupResolverTest.java             # 新增优先级/大小写测试
    ├── infrastructure/client/
    │   └── OrganizationDirectoryHttpGatewayTest.java  # 增加批量接口契约/失败测试
    └── infrastructure/mapper/
        └── SystemRoleListMapperTest.java              # 新增 sysRoleList 映射测试
```

**Structure Decision**: 采用纯后端增量改造方案，复用现有 organization integration 模块的分层结构；新增纯函数 Policy 类承载角色解析规则，避免 `OrganizationUserSyncWriter` 继续膨胀。

## Complexity Tracking

> 无 Constitution 违规，无需复杂度说明。
