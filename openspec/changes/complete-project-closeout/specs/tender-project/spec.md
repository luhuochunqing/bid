## ADDED Requirements

### Requirement: Project Closeout Page Display (REQ-TP-005)
项目结项页面 SHALL 展示保证金信息（只读）、保证金管理表单（项目负责人可填写）、项目总结（富文本）、文档导出、审核结果提示和操作按钮。

#### Scenario: Deposit info display (read-only for all users)
- **GIVEN** any user views the closure stage
- **THEN** the DepositReturnPanel shows: 是否需要保证金、保证金金额、缴纳方式、退回状态、退回日期、转服务费金额、退回金额、退回凭证

#### Scenario: Fill deposit return details (project leader only)
- **GIVEN** a project leader views the closure stage
- **AND** the project is not yet closed
- **WHEN** the project has deposit
- **THEN** they can fill: 保证金退回情况（下拉选择）、退回日期、退回凭证上传、转服务费金额

#### Scenario: File upload with tag
- **GIVEN** a project leader uploads a deposit return evidence or proof file
- **WHEN** the file is uploaded via el-upload
- **THEN** the `data` payload includes `tag: "保证金银行回单"` for server-side classification

#### Scenario: Project summary rich text
- **GIVEN** a project leader views the closure stage
- **WHEN** they scroll to "项目总结" section
- **THEN** they see a wangEditor rich text editor (Toolbar + Editor) for entering the summary

#### Scenario: Document export (bid team)
- **GIVEN** a bid team member (manager/lead/assistant) or project leader views the closure stage
- **WHEN** they click "导出项目全部资料"
- **THEN** the system triggers a ZIP download of all project archive files

#### Scenario: AI case generation (bid team)
- **GIVEN** a bid team member views the closure stage
- **AND** the project is approved for closure
- **WHEN** they click "AI 生成案例"
- **THEN** the system triggers asynchronous case precipitation

#### Scenario: Submit closure button
- **GIVEN** a project leader with valid deposit return data
- **WHEN** all validation passes
- **THEN** the "提交结项" button is enabled

#### Scenario: Approve / Reject buttons (bid manager only)
- **GIVEN** a bid manager views a PENDING closure
- **THEN** they see "审核通过" (success) and "驳回" (danger) buttons
- **AND** clicking 驳回 opens a dialog requiring a rejection reason

#### Scenario: Rebid button (project leader, after approval)
- **GIVEN** a project leader views the closure stage
- **AND** the project has been approved for closure
- **WHEN** they click "二次招标"
- **THEN** the system creates a new project with pre-populated fields from the closed project
- **AND** navigates to the new project's detail page

#### Scenario: Approved state display
- **GIVEN** the closure has been approved
- **THEN** a success alert shows "项目已正式结项"
- **AND** "AI 生成案例" button appears for bid team members
- **AND** "已结项" tag is displayed
- **AND** "二次招标" button appears for project leader

#### Scenario: Rejected state display
- **GIVEN** the closure has been rejected
- **THEN** a warning alert shows "结项申请已被驳回"
- **AND** the rejection reason is displayed
