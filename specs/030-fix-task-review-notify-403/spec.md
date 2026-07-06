# Feature Specification: 修复任务审核通知接收人广播导致的无权限跳转

**Feature Branch**: `agent/zcode/fix-task-review-notify-403`

**Created**: 2026-07-06

**Status**: Draft

**Input**: 用户 06131（投标专员）收到大量任务审核通知，点击跳转报"没有权限"。根因是任务审核通知派发时按角色全局广播给所有投标专员/负责人，未过滤接收人对项目的访问权限，且跳转链接硬编码为项目详情页，导致无项目访问权的接收人点击后被 403 拦截。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 无项目访问权的人不应收到该项目的任务审核通知 (Priority: P1)

投标专员小 A 没有参与"西安地铁 17 号线"项目（既不是项目负责人、副负责人、任务执行人、评审人，项目也未授权给她的客户/部门）。某天项目里有人提交了一个任务的审核，系统不应把"该任务已提交审核"通知派发给小 A——因为她即使收到也无法打开，通知对她纯属噪音，还会引发"为什么我打不开？"的疑惑。

**Why this priority**: 这是 06131 案例的直接复现路径，影响所有投标专员和投标负责人，是当前 Bug 的核心症状。修不好这一条，"通知跳转报错"问题就还存在。

**Independent Test**: 让一个不在项目 X 可见范围内的投标专员账户登录，触发项目 X 内任务的审核提交流程，断言该账户的通知列表里**不出现**项目 X 的任务审核通知。

**Acceptance Scenarios**:

1. **Given** 投标专员小 A 不在项目 P 的可访问范围（`ProjectAccessScopeService.getAllowedProjectIds(小A)` 不含 P），**When** 项目 P 内有人提交任务 T 的审核（触发 `notifyTaskReviewSubmitted(P, T, ...)`），**Then** 小 A 不出现在通知接收人列表中，`user_notification` 表中小 A 没有项目 P 的这条 TASK_UPDATE 记录。
2. **Given** 投标负责人小 B 是项目 P 的副负责人（在可访问范围内），**When** 项目 P 内有人提交任务 T 的审核，**Then** 小 B 正常收到通知，点击跳转能打开项目详情页。
3. **Given** 项目 P 的可访问范围内只有 1 个人（提交人自己），**When** 提交任务审核，**Then** 排除提交人后接收人列表为空，系统不创建任何通知（不报错、不阻塞主业务）。

---

### User Story 2 - 通知点击跳转链接必须对收件人有效，无效时降级到通知中心 (Priority: P2)

作为通知接收人，我收到的任何一条通知，点击后要么打开对应资源，要么打开"通知中心"页面，**绝不弹红色报错弹窗**。即便接收人筛选逻辑有漏网（例如未来新增角色未更新过滤规则），用户体验也不应崩坏。

**Why this priority**: 这是 User Story 1 的兜底防线。即便过滤逻辑漏网，前端跳转降级能保证用户不会看到"没有权限"的报错。

**Independent Test**: 构造一个"接收人无法访问目标项目"的通知（模拟过滤漏网），点击该通知，断言跳转到通知中心页（而非项目详情页触发 403）。

**Acceptance Scenarios**:

1. **Given** 通知 N 的 `targetUrl=/project/P/drafting`，接收人 U 当前对项目 P 无访问权（模拟过滤漏网或权限被回收），**When** U 点击该通知，**Then** 前端跳转到通知中心页 `/notifications`（或显示"该项目已不可访问"的友好提示），**不**发起对 `/api/projects/P` 的请求。
2. **Given** 通知派发时已知接收人 U 可能无项目访问权，**When** 后端生成通知 payload，**Then** `targetUrl` 优先使用接收人可访问的安全路径，而非硬编码 `/project/{id}/drafting`。

---

### User Story 3 - 同类广播派发问题需登记审视清单 (Priority: P3)

本次发现的是"任务审核通知"（`TaskReviewNotificationService.notifyTaskReviewSubmitted`）的广播问题。仓库里还有其他通知派发器（任务分配、阶段流转、标书审核、CA 通知、标讯评估、待分配通知等）可能存在同类"按角色全局广播但未过滤项目可见性"的问题。需要一个审视清单和技术债登记，避免逐个返工。

**Why this priority**: 治本—but 不阻塞当前 P1 修复。这一条保证本次教训沉淀为系统级检查清单，未来同类 Bug 不会重复发生。

**Independent Test**: 检查仓库内所有调用 `userRepository.findEnabledByRoleProfileCodes(...)` 的通知派发器，输出清单：哪些已对接收人做项目可见性过滤，哪些没有。

**Acceptance Scenarios**:

1. **Given** 仓库内所有通知派发器（grep `findEnabledByRoleProfileCodes`），**When** 审视每个调用点，**Then** 产出一份清单登记到技术债 tracker，标注每个派发器是否需要同类修复。
2. **Given** `ProjectNotificationService.notifyTaskAssigned`（任务分配通知，CO-474 已部分修复），**When** 审视其接收人策略，**Then** 验证它只发给被分配人本人（单点），不会广播给无项目访问权的角色。

---

### Edge Cases

- **管理员（admin）角色**：dataScope=all，能访问所有项目，过滤逻辑应直接放行，避免对每个项目都全量计算 allowedProjectIds。
- **接收人列表计算性能**：项目内一次任务提交审核可能广播给十几个候选接收人，对每个候选全量计算 `getAllowedProjectIds` 开销大（每次含多条 SQL 查询）。需要轻量级"是否可访问该项目 X"的判定，而非全量列表。
- **OSS 同步链路变更角色**：用户角色可能在运行期被 OSS 同步改动（`EffectiveRoleResolver` 已处理），过滤逻辑应使用与权限闸门一致的角色解析口径，避免过滤时和实际访问时角色不一致。
- **跨部门协同人员（bid-otherDept）**：dataScope=self，可访问项目集仅含自己作为 assignee 的任务所属项目。即便在 CO-474 修复里他们跳 `/task-board`，但如果他们根本没参与这个项目，仍不应收到通知。
- **通知派发失败不阻塞主业务**：现有 try-catch 容错语义保留——过滤逻辑或权限查询出错时不应阻断任务审核提交主链路。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 在派发"任务已提交审核"通知时，对接收人列表按"对该项目有访问权限"进行过滤，仅保留有访问权的接收人。
- **FR-002**: 系统 MUST 提供轻量级的"用户 X 是否可访问项目 P"判定能力，避免对每个候选接收人都全量计算其可访问项目集合。
- **FR-003**: 接收人过滤逻辑 MUST 与项目详情接口的权限闸门（`ProjectAccessScopeService`）使用同一套可访问项目集来源（成员/负责人/任务执行人/评审人/CRM 授权客户/部门可见性），避免过滤结果与实际访问判定不一致。
- **FR-004**: 当通知派发时已知接收人对项目无访问权，系统 MUST 把该通知的 `targetUrl` 降级为接收人一定可访问的安全路径（通知中心 `/notifications`），而非硬编码的项目详情页。
- **FR-005**: 接收人过滤后的列表为空时，系统 MUST 安全跳过通知创建（不报错、不阻塞业务主链路），并打 INFO 日志记录"任务审核通知因无可见接收人被跳过"。
- **FR-006**: 接收人过滤逻辑 MUST 以纯函数形式实现核心判定（无副作用、参数显式传入），便于单元测试覆盖。
- **FR-007**: 系统 MUST 保留现有"通知派发失败不阻断主业务"的容错语义（try-catch 兜底），过滤逻辑的异常不应抛到主链路。
- **FR-008**: 仓库内所有调用 `findEnabledByRoleProfileCodes` 的通知派发器 MUST 被审视，输出一份"是否需同类修复"的清单登记到 `docs/exec-plans/tech-debt-tracker.md`。

### Key Entities *(include if feature involves data)*

- **通知（Notification）**：通过 `notification` + `user_notification` 两张表存储。`notification.payload_json.targetUrl` 存跳转链接，本次修复会改变它的生成逻辑（按接收人角色/权限动态选择）。
- **用户可访问项目集（ProjectAccessScope）**：`ProjectAccessScopeService.getAllowedProjectIds(user)` 计算用户能访问的所有项目 ID。来源包括：项目成员表、正/副投标负责人、项目负责人、任务执行人、标书评审人、CRM 客户授权、部门可见性。本次修复的接收人过滤将基于此集合作 `contains(projectId)` 判定。
- **任务审核通知接收人候选集**：当前由 `RoleProfileCatalog.TASK_MUTATION_ALLOWED_ROLES`（admin、/bidAdmin、bid-projectLeader、bid-Team）反查所有启用用户得到。本次修复在此之上叠加项目可见性过滤。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 用户（如 06131）登录后查看通知列表，**所有可点击的通知都能正常打开**目标页面，不再出现"权限不足，无法访问该项目"的报错。
- **SC-002**: 一次任务审核提交触发的通知派发，**接收人列表中的每个接收人都对该项目有访问权**（用接收人 id 反查 `getAllowedProjectIds` 必含该项目 id）。
- **SC-003**: 通知派发的请求耗时（单次 `notifyTaskReviewSubmitted`）相比修复前增量 **< 200ms**（轻量判定而非全量计算）。
- **SC-004**: 仓库内**所有**调用 `findEnabledByRoleProfileCodes` 的通知派发器都有明确的"是否需同类修复"判定记录，无遗漏。
- **SC-005**: 通知派发逻辑改动后，**现有单元测试 100% 通过**，且新增覆盖以下场景的测试用例：投标专员被广播到无权项目时不收到通知、管理员直接放行、过滤后列表为空安全跳过。

## Assumptions

- **现有权限闸门可信**：`ProjectAccessScopeService.getAllowedProjectIds` 已经是项目详情接口的权限来源（`ProjectController` → `ProjectService.assertCurrentUserCanAccessProject`），本次修复直接复用，不重新实现权限逻辑。
- **接收人候选集口径不变**：`TASK_MUTATION_ALLOWED_ROLES` 仍是"潜在审核人"的候选角色集，本次修复只在候选集之上叠加项目可见性过滤，不改变角色定义本身。
- **管理员放行策略**：admin（dataScope=all）和 /bidAdmin 在过滤时直接保留，不进入逐用户权限计算——这与他们能访问所有项目的事实一致。
- **存量脏数据不清理**：已派发给 06131 的历史"无权访问项目"通知不在本次范围内清理（属于症状清理，可后续单独执行 SQL）。本次只修增量派发逻辑。
- **bid-otherDept 同样受益**：本次修复不区分角色，所有非 admin 角色都走项目可见性过滤，bid-otherDept 即使在 CO-474 修复后跳 `/task-board`，本次额外保证他们根本不会收到无权项目的通知。
- **前端跳转降级为兜底**：User Story 2 的前端 targetUrl 降级是兜底防线，但后端过滤（User Story 1）才是主要修复。前端降级优先级低于后端过滤，如工期紧张可拆分到下一个迭代。
- **依赖现有 `EffectiveRoleResolver`**：接收人角色解析复用 `EffectiveRoleResolver.resolveRoleCode(user)`，与 `ProjectAccessScopeService` 保持一致口径，不引入新的角色解析逻辑。
