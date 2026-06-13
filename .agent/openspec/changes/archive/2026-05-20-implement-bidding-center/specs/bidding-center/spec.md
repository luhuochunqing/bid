## MODIFIED Requirements

### Requirement: Tender Data Model (REQ-BC-001)
标讯实体 SHALL 包含完整的 28 字段数据模型，覆盖基本信息、联系人体系、人员分配和业务分类。

#### Scenario: Create tender with required fields
- **GIVEN** a user with tender creation permission
- **WHEN** they submit with 项目名称, 招标机构, 业主单位, 总部所在地, 报名截止时间, 开标时间, 联系人, 联系方式, 客户类型, 优先级
- **THEN** tender is created with status PENDING_ASSIGNMENT and all fields persisted
