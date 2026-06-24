# Implementation Plan: 选人控件统一 + 事件库同步启用

**Branch**: `agent/mimo/user-picker-event-sync` | **Date**: 2026-06-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `.specify/specs/021-user-picker-event-sync/spec.md`

## Summary

启用事件库 SDK（Kafka 消费 OSS 组织架构变更）保持本地 users 表准实时同步；封装统一 UserPicker 组件替换 22 处碎片化选人实现；合并两个候选人 API 为单一端点并补齐权限过滤。改造分三层：后端配置启用 + API 统一、前端组件封装 + 迁移、数据同步链路验证。

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.2 + JPA | Vue 3 + Vite 5 + Element Plus

**Primary Dependencies**:
- 后端：EHSY Event Library ClientSDK（Kafka 消费）、Spring Data JPA、Flyway
- 前端：Element Plus `el-select filterable remote`、Pinia store

**Storage**: MySQL 8.0（users 表 + organization_departments 表 + organization_event_logs 表）

**Testing**: JUnit 5 + Mockito + MockMvc（后端）、Vitest（前端单元）、Playwright（E2E）

**Target Platform**: Linux server（后端）、现代浏览器（前端）

**Project Type**: Web application（前后端分离）

**Performance Goals**: 选人控件搜索响应 ≤500ms（本地 DB 查询）、事件库同步延迟 ≤5min

**Constraints**: 单 Java 文件 ≤300 行（硬上限）、不引入新前端依赖、不扩大 SecurityConfig 放行范围

**Scale/Scope**: 22 处选人使用点迁移、3 处失效控件修复、2 个候选人端点合并、1 个事件库 SDK 启用

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. FP-Java Architecture | ✅ Pass | 统一候选人端点的过滤逻辑抽取为 Pure Core（`AssignmentCandidatePolicy` record），Controller/Service 仅做编排 |
| II. Real-API Only | ✅ Pass | 事件库 SDK 消费后回查真实 OSS 目录接口，无 Mock；选人控件查本地真实数据 |
| III. Test-Driven Development | ✅ Pass | 先写测试：AssignmentCandidatePolicy 单测、UserPicker 组件测试、事件库消费集成测试 |
| IV. Split-First & Simplicity | ✅ Pass | UserPicker 拆为组件（`UserPicker.vue`）+ composable（`useUserPicker.js`）；后端候选人服务拆为 Policy + Service |
| V. OSS Integration | ✅ Pass | 事件库消费后批量回查 OSS 目录接口（`fetchUserByUserId`）；角色映射复用现有 `JobRoleLookupResolver`（大小写安全、人员>部门>岗位优先级） |
| VI. Boring Proven Patterns | ✅ Pass | UserPicker 基于现有 el-select 能力封装，不引入新依赖；事件库 SDK 代码已实现，仅启用配置 |

**Gate Result**: 全部通过，无违规需记录。

## Project Structure

### Documentation (this feature)

```text
.specify/specs/021-user-picker-event-sync/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── api.md           # 统一候选人 API 契约
└── tasks.md             # Phase 2 output（待 /speckit-tasks 生成）
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/xiyu/bid/
│   ├── integration/organization/          # 事件库 SDK（已实现，仅启用配置）
│   │   ├── infrastructure/sdk/
│   │   │   └── OrganizationEventSdkConsumerAdapter.java  # 已实现
│   │   └── application/
│   │       ├── OrganizationDirectorySyncAppService.java  # 已实现
│   │       └── OrganizationUserSyncWriter.java             # 已实现
│   ├── user/                               # 新增：统一候选人模块
│   │   ├── controller/
│   │   │   └── AssignmentCandidateController.java         # 新增：统一端点
│   │   ├── service/
│   │   │   └── AssignmentCandidateAppService.java         # 新增：编排服务
│   │   ├── core/
│   │   │   └── AssignmentCandidatePolicy.java             # 新增：纯核心过滤逻辑
│   │   └── dto/
│   │       └── AssignmentCandidateDTO.java                # 新增：统一返回结构
│   ├── mention/                            # 已有：/api/users/search（保持不变）
│   │   ├── controller/UserSearchController.java
│   │   └── service/UserSearchService.java
│   └── entity/User.java                    # 已有：用户实体
├── src/main/resources/
│   ├── application.yml                     # 修改：启用 event-sdk.enabled=true
│   └── db/migration-mysql/
│       └── V1093__add_users_employee_number.sql           # 新增：补齐 employee_number 列
└── src/test/java/com/xiyu/bid/
    └── user/
        ├── core/
        │   └── AssignmentCandidatePolicyTest.java         # 新增：纯核心单测
        └── controller/
            └── AssignmentCandidateControllerTest.java     # 新增：API 集成测试

src/
├── components/
│   └── common/
│       └── UserPicker.vue                 # 新增：统一选人组件
├── composables/
│   └── useUserPicker.js                   # 新增：选人逻辑封装
├── api/modules/
│   └── users.js                           # 修改：新增统一候选人 API 调用
├── stores/
│   └── user.js                            # 修改：移除未填充的 users state
└── views/                                 # 修改：22 处选人迁移到 UserPicker
    ├── Project/
    ├── Bidding/
    ├── Dashboard/
    └── Resource/
```

**Structure Decision**: Web application 结构（前后端分离）。后端新增 `user/` 模块承载统一候选人逻辑，遵循 FP-Java 分层（core 纯核心 + service 编排 + controller 入口）。前端新增 `components/common/UserPicker.vue` 统一组件 + `composables/useUserPicker.js` 逻辑封装。

## Complexity Tracking

> 无 Constitution 违规，无需记录。

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A | N/A | N/A |

## Implementation Phases

### Phase A: 后端 — 事件库 SDK 启用 + 数据基线修复

1. **启用事件库 SDK 配置**：`application.yml` 中 `xiyu.integrations.organization.event-sdk.enabled` 默认值改为 `true`（或通过环境变量 `XIYU_ORG_EVENT_SDK_ENABLED=true` 注入）
2. **补齐 users 表 employee_number 列**：新增 Flyway 迁移 `V1093__add_users_employee_number.sql`，修复实体定义与 schema 不一致
3. **验证事件库消费链路**：确认 `OrganizationEventSdkConsumerAdapter` → `OrganizationDirectorySyncAppService` → `OrganizationUserSyncWriter` 链路工作正常

### Phase B: 后端 — 统一候选人 API

1. **新建 `AssignmentCandidatePolicy`（Pure Core）**：纯核心过滤逻辑，输入候选人列表 + 当前用户权限上下文 + 业务 context 参数，输出过滤后列表。可独立单测。
2. **新建 `AssignmentCandidateAppService`（Imperative Shell）**：编排服务，调用 UserRepository 查本地 users 表 + ProjectAccessScopeService 获取权限 + AssignmentCandidatePolicy 过滤。
3. **新建 `AssignmentCandidateController`**：暴露 `GET /api/users/assignable-candidates?context=task|tender&deptCode=&roleCode=`，`@PreAuthorize("isAuthenticated()")`。
4. **废弃旧端点**：`/api/tasks/assignment-candidates` 和 `/api/tenders/assignment-candidates` 标记 `@Deprecated`，内部委托新端点逻辑，保持向后兼容。
5. **统一返回 DTO**：`AssignmentCandidateDTO`（userId, name, employeeNumber, roleCode, roleName, deptCode, deptName, enabled）

### Phase C: 前端 — 统一 UserPicker 组件

1. **封装 `UserPicker.vue`**：基于 `el-select filterable remote`，支持两种模式：
   - `mode="search"`：远程搜索，调用 `/api/users/search`
   - `mode="candidates"`：预加载，调用 `/api/users/assignable-candidates?context=`
2. **封装 `useUserPicker.js`**：管理搜索状态、防抖、加载、选项格式化
3. **统一 emit 格式**：`@select` 事件返回 `{ id, name, employeeNumber, roleCode, roleName, deptCode, deptName }` 完整对象
4. **统一 v-model**：绑定 userId（Long），不再绑定 user.name 字符串

### Phase D: 前端 — 22 处选人迁移

按优先级分批迁移：
1. **P0 - 修复失效控件**（3 处）：BasicInfoStep、TaskStep、CollaborationCenter → 替换为 UserPicker search 模式
2. **P1 - 远程搜索迁移**（11 处）：TaskForm、MentionInput、ProjectCollaboratorsDialog、TaskKanban、TaskDecomposeDialog、TenderSearchCard、useReminderSettings、CAFormDialog、InitiationStage、DraftingStage、useProjectSearch
3. **P2 - 候选人列表迁移**（6 处）：AssignDialog、List.vue、TenderCreatePage、useBiddingDetailPage、useTenderDistribution、useTaskAssigneeOptions
4. **P3 - 清理**：移除 `userStore.users` state 及相关引用

### Phase E: 测试 + 验证

1. **后端单测**：AssignmentCandidatePolicy 过滤逻辑、幂等性、权限边界
2. **后端集成测试**：AssignmentCandidateController 端到端、事件库消费链路
3. **前端组件测试**：UserPicker 两种模式、防抖、空状态、emit 格式
4. **E2E**：选人控件在任务表单、标讯指派、项目创建页的交互验证
5. **门禁**：`npm run build` + `mvn test` + `ArchitectureTest` 全绿
