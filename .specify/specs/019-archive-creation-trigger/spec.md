# Feature Specification: 档案创建时机修复 — 立项通过时创建档案

**Feature ID**: 019-archive-creation-trigger
**Feature Name**: 档案创建时机修复 — 立项通过时创建档案
**Feature Branch**: `019-archive-creation-trigger`
**Created**: 2026-06-08
**Status**: Draft
**Input**: User description: "产品蓝图 §4.1.1.1.1 要求『项目档案在立项审批通过时创建』。当前实现错误地把档案创建放在 ProjectService.createProject()，导致项目草稿/待立项阶段也会创建档案。修复方案是把 createArchive 调用从 ProjectService.createProject() 移到 ProjectInitiationApprovalService.approve()。"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 立项审批通过后自动创建项目档案 (Priority: P1)

投标管理员/组长在处理项目立项审批时，希望只有审批真正通过（即项目从「待立项」流转到「立项中 / ACTIVE」状态）的那一刻才创建项目档案。立项审批被驳回、撤销或回到草稿状态时，不应创建档案。

**Why this priority**: 这是产品蓝图 §4.1.1.1.1 的明确业务规则 — 档案是项目执行过程中的"事实容器"，必须在项目被批准为"可执行项目"时才开始积累。当前实现将档案创建前置到了「项目草稿」阶段，导致大量"未通过审批就被废弃"的草稿项目污染档案库，是阻塞合规交付的最高优先级问题。

**Independent Test**: 以投标管理员身份登录 → 提交一个项目立项申请 → 状态为「待立项」 → 数据库/档案中心确认该项目**无档案记录** → 审批通过 → 状态变为「立项中（ACTIVE）」 → 数据库/档案中心确认该项目**已自动创建一条 ACTIVE 状态档案**。整个闭环可独立验收。

**Acceptance Scenarios**:

1. **Given** 一个项目处于「待立项（PENDING_INITIATION）」状态，**When** 任何人查询该项目档案，**Then** 档案中心**不应**存在该项目档案记录
2. **Given** 一个项目处于「草稿（DRAFT）」状态，**When** 任何人查询该项目档案，**Then** 档案中心**不应**存在该项目档案记录
3. **Given** 一个项目处于「待立项」状态，**When** 投标管理员调用 `ProjectInitiationApprovalService.approve()` 审批通过，**Then** 项目状态变为「立项中（ACTIVE）」，并且项目档案被自动创建，状态为 `ACTIVE`，档案名与项目名一致
4. **Given** 一个项目处于「待立项」状态，**When** 审批被驳回（reject）或撤销（revoke），**Then** 项目状态变化到对应驳回态/草稿态，**且不**创建项目档案
5. **Given** 项目档案已存在（状态 ACTIVE），**When** 因任何原因再次触发立项审批通过，**Then** 档案创建调用应**幂等**（不创建重复档案、不抛异常）

---

### User Story 2 - 历史项目批量导入仍创建档案 (Priority: P2)

运维/管理员在导入历史项目数据时，希望每个历史项目（其 stage 为 `INITIATED`）在导入过程中仍能正常创建对应的项目档案。这条路径必须保持原行为，不能因为主路径（审批通过触发）调整而回归。

**Why this priority**: 历史项目数据是已经发生过的"已审批通过的项目"批量迁移，本质等同于"已审批通过"的事实记录，需要在导入时同步建立档案。这条路径与 US1 的立项审批通过触发档案创建是**两个并行的合法入口**，必须同时保留。

**Independent Test**: 准备一份包含 1 个历史项目（stage=INITIATED）的导入数据 → 调用 `ProjectImportService.archiveIfEnabled()` → 数据库确认该历史项目**已创建**档案（状态 ACTIVE）。整个闭环可独立验收。

**Acceptance Scenarios**:

1. **Given** 历史项目数据中 stage 为 `INITIATED`（已审批通过的历史项目），**When** 调用 `ProjectImportService.archiveIfEnabled()`，**Then** 该项目档案被创建，状态为 `ACTIVE`
2. **Given** 历史项目数据中 stage 为 `DRAFT` 或 `PENDING_INITIATION`（尚未审批通过），**When** 调用 `ProjectImportService.archiveIfEnabled()`，**Then** 该项目**不**应创建档案（导入历史未审批项目是异常流程，不在本次回归范围）
3. **Given** 历史项目档案已存在（幂等保护），**When** 重复导入同一项目，**Then** 不创建重复档案、不抛异常

---

### Edge Cases

- **重复触发幂等**：若 `ProjectInitiationApprovalService.approve()` 因业务补偿/重试被多次调用，档案必须只创建一次（由 `ProjectArchiveWorkflowService.createArchive()` 内部的 `archiveRepository.findByProjectId(projectId).orElseGet(...)` 与 DB UNIQUE 约束共同保证）。
- **审批驳回后再次提交并通过**：项目回到「待立项」后被再次审批通过，档案必须被正常创建（不应受此前状态机历史影响）。
- **历史导入与立项审批混合**：某项目先被历史导入（档案已存在），后续又走一次审批通过调用，档案创建必须幂等，不抛 `DataIntegrityViolationException`。
- **审批人无项目访问权限**：`ProjectAccessScopeService` 必须先于档案创建校验访问权限；如审批人被权限门禁拦截，档案不应被创建。
- **审批流程中事务回滚**：若审批通过后事务回滚，档案创建必须随事务一起回滚（不允许出现"项目状态未变更但档案已存在"的脏数据）。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 在 `ProjectInitiationApprovalService.approve()` 方法**执行成功并提交事务后**，自动为对应项目创建一条 `ACTIVE` 状态的项目档案；档案 `name` 取自 `project.getName()`。
- **FR-002**: 系统 MUST **不再**在 `ProjectService.createProject()` 内部创建项目档案（无论项目是草稿、待立项、还是一次性创建为 ACTIVE），项目创建流程只负责落库项目实体本身。
- **FR-003**: `ProjectArchiveWorkflowService.createArchive()` MUST 保持幂等：传入 `projectId` 已存在档案时直接返回原档案，不创建重复记录、不抛 `DataIntegrityViolationException`。
- **FR-004**: `ProjectImportService.archiveIfEnabled()` MUST **保持原行为**继续为 stage=`INITIATED` 的历史项目创建档案，确保历史导入回归不破。
- **FR-005**: 系统 MUST 删除与 `alerts/service/QualificationExpiryScanTask` 重复的 `qualification/application/QualificationExpiryScanTask`（PR #315 引入的重复类），以消除扫描任务双注册/双触发隐患。

### Key Entities *(include if feature involves data)*

- **Project**：项目主实体。`stage` 字段决定是否走档案创建路径：仅 `ACTIVE`（立项通过后）走创建；`DRAFT` / `PENDING_INITIATION` 不走。
- **ProjectArchive**：项目档案实体。与 `Project` 一对一关系，DB 端通过 `UNIQUE(project_id)` 约束保证幂等。
- **ProjectInitiation**：项目立项审批单实体。审批通过（APPROVED）触发档案创建；驳回/撤销/驳回后再次提交均不重复创建。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 单元测试 `mvn -Dtest=ProjectInitiationApprovalServiceTest,ProjectServiceDemoModeTest,ProjectImportServiceTest test` 全绿（8/8 通过），覆盖以下事实：审批通过创建档案、createProject 不创建档案、历史导入仍创建档案。
- **SC-002**: `bash scripts/ci-pre-pr.sh` 12/13 通过；唯一失败项必须是 `test:agent-start-task-contract` 这一**与本改动无关**的 pre-existing 失败。
- **SC-003**: 业务规则 100% 对齐产品蓝图 §4.1.1.1.1："项目档案在立项审批通过时创建" — 不再出现"草稿/待立项阶段误建档案"的情况。
- **SC-004**: `ProjectArchiveWorkflowService.createArchive()` 幂等性回归测试通过：同一 projectId 重复调用 2 次以上，DB 中 `project_archive` 表对应记录数仍为 1。
- **SC-005**: `ProjectService.createProject()` 改造后，DB 中 `project` 表与 `project_archive` 表的写入比例从"1:1"收敛到"1:N 审批通过后才 1:1"；草稿/待立项项目不再产生冗余档案。

## Assumptions

- 产品蓝图 §4.1.1.1.1 的口径是本任务的最高约束；如与本规范冲突，以蓝图为准。
- `ProjectArchiveWorkflowService.createArchive()` 的幂等实现（`archiveRepository.findByProjectId(projectId).orElseGet(...)` + DB `UNIQUE(project_id)`）在本次改动中**已存在且可用**，本次仅调用方迁移，不修改该方法的实现。
- `ProjectInitiationApprovalService.approve()` 当前实现是事务边界清晰的 Spring `@Transactional` 方法（`@Transactional` 注解已存在），新增的档案创建调用会跟随该事务一起提交/回滚，天然满足"事务回滚则档案不创建"的硬性要求。
- `ProjectService.createProject()` 当前确实调用了 `archiveWorkflowService.createArchive(...)`（这是本次要删除的错误调用点），删除后该字段在 `ProjectService` 中将变为未引用，**必须同时删除字段注入**以保持代码整洁。
- 删除 `qualification/application/QualificationExpiryScanTask` 不影响现有调度（已确认 `alerts/service/QualificationExpiryScanTask` 是唯一的活跃注册点），删除后通过 `mvn test` 的现有调度测试仍应全绿。
- 本次改动**不涉及**前端 UI 变更、Flyway 迁移（DB 结构已具备 UNIQUE 约束）、权限模型调整；只动 2 个 main 文件 + 删除 1 个重复类 + 新增/修改 3 个测试文件。
