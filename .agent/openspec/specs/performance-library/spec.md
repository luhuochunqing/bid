# performance-library Specification

## Purpose
TBD - created by archiving change align-performance-validation. Update Purpose after archive.
## Requirements
### Requirement: Performance Fields Validation (REQ-PL-001)
业绩档案在新增或修改保存时，系统 SHALL 强制执行以下字段的校验：
- 截止日期必须晚于签约日期。
- 若填写了总截止日期，总截止日期必须大于等于截止日期。
- 客户联系方式必须符合手机、固话或邮箱格式之一。
- 合同协议附件为必传项。
- 若客户类型为“央企”，则“央企名录截图”和“关系证明”均为必传项。
- 若启用了中标通知书，则“中标通知书附件”为必传项。

#### Scenario: Save fails when date range invalid
- **WHEN** user sets expiry date earlier than signing date
- **THEN** save is rejected with warning "截止日期必须晚于签约日期"

#### Scenario: Save fails when contact info invalid
- **WHEN** user enters invalid string for contact info
- **THEN** save is rejected with warning "请输入有效的联系方式"

#### Scenario: Save fails when CENTRAL_SOE missing attachments
- **WHEN** user selects customer type as CENTRAL_SOE but does not upload SOE_DIRECTORY or RELATIONSHIP_PROOF
- **THEN** save is rejected with warning "央企客户必须上传央企名录截图" or "央企客户必须上传关系证明"

### Requirement: Performance Editing Warning Prompt (REQ-PL-002)
在修改业绩档案时，如果用户修改了“客户类型”或“签约日期”等敏感字段，系统 SHALL 在保存前进行弹窗警示确认。

#### Scenario: Warning prompt on editing customer type
- **GIVEN** editing an existing performance record
- **WHEN** user changes customer type and clicks save
- **THEN** alert dialog shows "修改客户类型将影响到期提醒规则，请确认必要性"
- **AND** save proceeds only after user confirms

#### Scenario: Warning prompt on editing signing date
- **GIVEN** editing an existing performance record
- **WHEN** user changes signing date and clicks save
- **THEN** alert dialog shows "修改签约日期将影响业绩归档时间"
- **AND** save proceeds only after user confirms

