# Research: 选人控件统一 + 事件库同步启用

**Date**: 2026-06-24 | **Feature**: 021-user-picker-event-sync

## 研究任务清单

| # | 未知点 | 状态 |
|---|--------|------|
| R1 | 事件库 SDK 启用的完整配置链路 | ✅ 已解决 |
| R2 | users 表 employee_number 列缺失影响 | ✅ 已解决 |
| R3 | 统一候选人端点权限过滤策略 | ✅ 已解决 |
| R4 | UserPicker 组件模式设计 | ✅ 已解决 |
| R5 | 旧候选人端点废弃策略 | ✅ 已解决 |
| R6 | userStore.users 清理策略 | ✅ 已解决 |
| R7 | 事件库 SDK Kafka 连接失败降级行为 | ✅ 已解决 |

---

## R1: 事件库 SDK 启用的完整配置链路

**Decision**: 通过环境变量 `XIYU_ORG_EVENT_SDK_ENABLED=true` 启用，不改 `application.yml` 默认值（保持 `false` 以便其他环境可控）。

**Rationale**:
- `application.yml` 中 `event-sdk.enabled` 默认 `false` 是安全默认值，避免开发环境意外连接 Kafka
- 生产环境通过环境变量注入 `XIYU_ORG_EVENT_SDK_ENABLED=true` 启用
- `OrganizationEventSdkConsumerAdapter` 有双重条件：`@ConditionalOnClass`（SDK jar 在 classpath）+ `@ConditionalOnProperty`（enabled=true）
- SDK jar 已在 `backend/pom.xml` 中声明（通过 `ehsy-client-sdk` 依赖）

**配置链路**:
```
环境变量 XIYU_ORG_EVENT_SDK_ENABLED=true
  → application.yml: xiyu.integrations.organization.event-sdk.enabled=${...:false}
  → OrganizationEventSdkConsumerAdapter @ConditionalOnProperty 激活
  → 消费 BaseOssUser/BaseOssDept/BaseOssJob 三个 Kafka topic
  → OrganizationEventAppService.receiveViaSdk()
  → OrganizationDirectorySyncAppService.receiveWebhook()
  → lookupAndWrite() 回查 OSS 目录接口
  → OrganizationUserSyncWriter.upsert() 写入本地 users 表
```

**Kafka 连接参数**（需运维注入）:
- `XIYU_ORG_EVENT_BROKER_SERVER_LIST`：Kafka broker 地址
- `XIYU_ORG_EVENT_BROKER_ZK_SERVERS`：ZooKeeper 地址
- `XIYU_ORG_EVENT_BROKER_ENV`：环境标识（test/staging/prod）
- `XIYU_ORG_EVENT_SERVICE_NAME`：服务注册名
- `XIYU_ORG_EVENT_SERVER_REGISTER_URL`：服务注册 URL

**Alternatives Considered**:
- 改 `application.yml` 默认值为 `true`：否决，开发环境无 Kafka 会启动报错
- 使用 `@Profile` 控制：否决，`@ConditionalOnProperty` 更灵活，支持环境变量覆盖

---

## R2: users 表 employee_number 列缺失影响

**Decision**: 新增 Flyway 迁移 `V1093__add_users_employee_number.sql` 补齐列，修复实体与 schema 不一致。

**Rationale**:
- `User.java` 实体定义了 `@Column(name = "employee_number")` 但迁移脚本中无对应 `ADD COLUMN`
- `UserSearchService` 返回的 `UserSearchResult` 包含 `employeeNumber` 字段
- `TaskAssignmentCandidateDTO` 也包含 `employeeNumber` 字段
- 生产环境可能因 JPA 自动 DDL（`hibernate.ddl-auto`）补齐了列，但这是不可靠的
- 需要通过 Flyway 显式管理 schema，符合 Constitution 的 DB Migrations 规则

**迁移脚本内容**:
```sql
-- V1093__add_users_employee_number.sql
ALTER TABLE users ADD COLUMN employee_number VARCHAR(32) NULL;
CREATE INDEX idx_users_employee_number ON users(employee_number);
```

**回滚脚本**:
```sql
-- U1093__add_users_employee_number.sql
DROP INDEX idx_users_employee_number ON users;
ALTER TABLE users DROP COLUMN employee_number;
```

**Alternatives Considered**:
- 从 User.java 移除 employeeNumber 字段：否决，多个 DTO 依赖该字段，移除影响面大
- 依赖 JPA auto-DDL：否决，违反 Constitution 的 Flyway 管理规则

---

## R3: 统一候选人端点权限过滤策略

**Decision**: 复用现有 `ProjectAccessScopeService.getAllowedDepartmentCodes` + `RoleProfileService.hasGlobalAccess`，按 context 参数应用不同过滤策略。

**Rationale**:
- 现有 `/api/tasks/assignment-candidates` 已实现权限过滤逻辑（`TaskAssignmentSupport`）
- 现有 `/api/tenders/assignment-candidates` 无权限过滤（安全隐患）
- 统一后两个 context 都应用权限过滤，修复标讯端点安全问题
- 权限过滤核心逻辑抽取为 `AssignmentCandidatePolicy`（Pure Core record），可独立单测

**过滤策略**:
```java
public record AssignmentCandidatePolicy() {
    public List<AssignmentCandidate> filter(
            List<User> candidates,
            User currentUser,
            AssignmentContext context,
            Set<String> allowedDeptCodes,
            boolean hasGlobalAccess
    ) {
        return candidates.stream()
            .filter(u -> hasGlobalAccess || allowedDeptCodes.contains(u.getDepartmentCode()))
            .filter(u -> context.deptCode() == null || context.deptCode().equalsIgnoreCase(u.getDepartmentCode()))
            .filter(u -> context.roleCode() == null || context.roleCode().equalsIgnoreCase(u.getRoleCode()))
            .sorted(...)
            .map(AssignmentCandidate::from)
            .toList();
    }
}
```

**Context 参数**:
- `context=task`：任务指派场景，复用现有 TaskAssignmentSupport 过滤逻辑
- `context=tender`：标讯分配场景，应用相同权限过滤（修复当前无过滤问题）
- 两个 context 的过滤策略一致，context 参数主要用于审计和未来扩展

**Alternatives Considered**:
- 不同 context 不同过滤策略：否决，增加复杂度且无实际需求差异
- 不传 context，统一过滤：否决，前端需要知道当前是任务还是标讯场景以便审计

---

## R4: UserPicker 组件模式设计

**Decision**: 支持两种模式（`mode` prop），覆盖所有 22 处场景。

**Rationale**:
- 11 处场景用远程搜索（输入关键字异步查询 `/api/users/search`）
- 6 处场景用预加载列表（一次性加载 `/api/users/assignable-candidates`）
- 3 处失效控件用远程搜索模式修复
- 2 处任务候选人场景用预加载模式

**组件 API**:
```vue
<UserPicker
  v-model="userId"
  mode="search"           <!-- 或 "candidates" -->
  :context="task"         <!-- mode=candidates 时必传 -->
  :dept-code="filterDept"  <!-- 可选：预过滤部门 -->
  :role-code="filterRole"  <!-- 可选：预过滤角色 -->
  :placeholder="请选择执行人"
  @select="onUserSelect"
/>
```

**emit 事件**:
- `update:modelValue`：userId（Long）
- `select`：完整用户对象 `{ id, name, employeeNumber, roleCode, roleName, deptCode, deptName }`

**composable 封装**:
- `useUserPicker(mode, context)` 返回 `{ options, loading, search, loadCandidates, formatLabel }`
- 防抖搜索（300ms）
- 统一标签格式化（复用现有 `formatDisplayName.js`）

**Alternatives Considered**:
- 单一模式（只支持远程搜索）：否决，预加载场景（如标讯指派）需要一次性加载全部候选人
- 不封装 composable，逻辑写在组件内：否决，违反 Split-First 原则，组件文件会超 200 行

---

## R5: 旧候选人端点废弃策略

**Decision**: 标记 `@Deprecated`，内部委托新端点逻辑，保持向后兼容，不立即删除。

**Rationale**:
- 22 处前端使用点无法一次性全部迁移，需要分批进行
- 旧端点保持可用，避免迁移期间功能中断
- `@Deprecated` 注解 + 日志警告，提醒前端尽快迁移
- 所有前端迁移完成后，在后续 PR 中删除旧端点

**废弃路径**:
1. 新端点 `/api/users/assignable-candidates` 上线
2. 旧端点 `/api/tasks/assignment-candidates` 和 `/api/tenders/assignment-candidates` 标记 `@Deprecated`
3. 旧端点内部委托 `AssignmentCandidateAppService`，行为与新端点一致（包括权限过滤）
4. 前端分批迁移到新端点
5. 全部迁移完成后，删除旧端点

**Alternatives Considered**:
- 立即删除旧端点：否决，22 处前端迁移需要时间，立即删除会中断功能
- 旧端点保持原逻辑：否决，标讯端点无权限过滤的安全隐患需要立即修复

---

## R6: userStore.users 清理策略

**Decision**: 移除 `userStore.users` state 及相关 action，3 处失效控件迁移到 UserPicker 后清理。

**Rationale**:
- `userStore.users` 初始化为 `[]` 后从未被任何 action 填充，是死代码
- 3 处依赖它的控件（BasicInfoStep、TaskStep、CollaborationCenter）下拉始终为空
- 迁移到 UserPicker 后，这些控件不再依赖 `userStore.users`
- 移除死代码符合 Constitution 的 Simplicity 原则

**清理步骤**:
1. 3 处失效控件迁移到 UserPicker（Phase D P0）
2. 确认无其他地方引用 `userStore.users`
3. 从 `stores/user.js` 移除 `users: []` state
4. 移除相关 computed（如 `Project/Create.vue` 中的 `userList`）

**Alternatives Considered**:
- 保留 `userStore.users` 并添加 action 填充：否决，与 UserPicker 组件方案冲突，且 Pinia store 不适合缓存用户列表（数据量大、需实时性）

---

## R7: 事件库 SDK Kafka 连接失败降级行为

**Decision**: SDK 启动失败不阻塞应用启动，本地 users 表保留上次同步数据继续可用。

**Rationale**:
- `OrganizationEventSdkConsumerAdapter` 使用 `@ConditionalOnProperty`，Kafka 连接失败不影响 Bean 注册
- EHSY SDK 内部有重试和断路机制，连接恢复后自动消费
- 本地 users 表已有历史数据（通过批量窗口同步或历史事件消费积累），选人控件可继续使用
- 符合 Constitution 的 Boring Proven Patterns：降级而非崩溃

**降级行为**:
- Kafka 不可用 → SDK 记录错误日志，不抛出异常
- 应用正常启动，选人控件使用本地已有数据
- Kafka 恢复后，SDK 自动重新消费，期间错过的变更通过 Kafka offset 机制补齐

**Alternatives Considered**:
- Kafka 不可用时应用启动失败：否决，选人控件是辅助功能，不应阻塞核心业务
- Kafka 不可用时禁用选人控件：否决，本地数据仍可用，无需禁用

---

## 研究结论

所有技术未知点已解决，无 NEEDS CLARIFICATION 残留。关键决策：
1. 事件库 SDK 通过环境变量启用，不改默认值
2. 补齐 users 表 employee_number 列（V1093 迁移）
3. 统一候选人端点复用现有权限过滤服务
4. UserPicker 支持两种模式覆盖所有场景
5. 旧端点标记 @Deprecated 保持兼容
6. 移除 userStore.users 死代码
7. Kafka 不可用时降级使用本地数据
