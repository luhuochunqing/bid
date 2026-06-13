## ADDED Requirements

### Requirement: Closure Review Flow (REQ-TL-003)
系统 MUST 支持项目结项审核流程：项目负责人提交结项申请，投标管理员/组长审核通过或驳回，审核通过后项目进入 CLOSED 终态。

#### Scenario: Submit closure application
- **GIVEN** a project in `RETROSPECTIVE` or `RESULT_PENDING` stage
- **AND** the current user is the project leader
- **WHEN** all deposit return fields are valid and user clicks "提交结项"
- **THEN** the system creates a closure application with `reviewStatus = PENDING`
- **AND** the bid manager receives the application for review

#### Scenario: Approve closure
- **GIVEN** a closure application with `reviewStatus = PENDING`
- **AND** the current user is a bid manager (ADMIN/MANAGER)
- **WHEN** the manager clicks "审核通过"
- **THEN** the system sets `reviewStatus = APPROVED`, `stageLocked = true`, `closedAt = now`
- **AND** the project stage transitions to `CLOSED`
- **AND** all project fields become locked (不可修改)

#### Scenario: Reject closure
- **GIVEN** a closure application with `reviewStatus = PENDING`
- **AND** the current user is a bid manager
- **WHEN** the manager clicks "驳回" with a rejection reason
- **THEN** the system sets `reviewStatus = REJECTED`, records `rejectionReason` and `reviewedBy`
- **AND** the project leader can re-edit the deposit fields and submit again

#### Scenario: Already closed rejection
- **GIVEN** a project with `stageLocked = true` or `reviewStatus = APPROVED`
- **WHEN** a user tries to submit or approve closure again
- **THEN** the system returns a 423 LOCKED error with message "项目已结项，不可重复操作"

### Requirement: Deposit Return Gate (REQ-TL-004)
结项提交前，系统 MUST 对保证金退回信息进行强校验，特定状态必须填全相关字段。

#### Scenario: Has deposit but NOT_RETURNED blocks submission
- **GIVEN** a project with `hasDeposit = true`
- **AND** the deposit return status is `NOT_RETURNED`
- **WHEN** the project leader tries to submit closure
- **THEN** the system returns a 409 CONFLICT error with "保证金未退回"

#### Scenario: FULLY_RETURNED requires date and evidence
- **GIVEN** the deposit return status is `FULLY_RETURNED`
- **WHEN** the `depositReturnDate` or `depositReturnEvidenceId` is missing
- **THEN** the system returns a 422 UNPROCESSABLE_ENTITY with "必须提供退回日期与退回凭证"

#### Scenario: TRANSFERRED_TO_FEE requires amount and evidence
- **GIVEN** the deposit return status is `TRANSFERRED_TO_FEE`
- **WHEN** the `transferAmount` or `depositReturnEvidenceId` is missing
- **THEN** the system returns a 422 with "必须提供转服务费金额与证明文件"

#### Scenario: No deposit bypasses validation
- **GIVEN** a project with `hasDeposit = false`
- **WHEN** the project leader submits closure
- **THEN** the system bypasses all deposit validation and proceeds normally

### Requirement: AI Case Precipitation (REQ-TL-005)
项目结项后，系统 SHOULD 支持将项目沉淀为企业案例，自动或手动触发。

#### Scenario: Trigger case precipitation after closure
- **GIVEN** a project with `reviewStatus = APPROVED`
- **WHEN** a user clicks "AI 生成案例" button
- **THEN** the system triggers asynchronous case precipitation
- **AND** returns a success message "案例沉淀任务已触发"

#### Scenario: Check precipitation readiness
- **GIVEN** a project has been approved for closure
- **WHEN** the closure stage loads
- **THEN** the system checks and returns which prerequisites are met for case precipitation
