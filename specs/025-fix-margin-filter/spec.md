# Feature Specification: 保证金看板状态筛选修复

**Feature Branch**: `agent/codex/fix-margin-filter-not-working`

**Created**: 2026-07-02

**Status**: Draft

**Input**: User description: "`https://winbid-test.ehsy.com/resource/margin` 筛选条件不生效！有个 Bug，请严格按照 lessons-learned.md 里的【全链路日志排查 SOP】帮我找出根因。"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 筛选"未到期"看到所有未到期行 (Priority: P1)

投标专员在保证金看板页面（`/resource/margin`）查看保证金台账时，希望点击状态筛选中的"未到期"标签后，能看到所有当前显示为"未到期"的行，包括已经缴纳且未到期的保证金记录，以及立项表中需要缴纳但尚未缴纳的占位记录。

**Why this priority**: 这是用户原始报告的核心 Bug。未筛选时页面显示 N 条"未到期"行，点击"未到期"筛选后行数骤减，给用户造成"筛选条件不生效"的强烈错觉，直接影响日常对账工作。

**Independent Test**: 在测试环境准备 1 条已缴纳且 `fee_date >= NOW()` 的 BID_BOND fee + 1 条 `need_deposit='YES'` 但无 fees 的立项记录，点击"未到期"筛选，应看到 2 行。

**Acceptance Scenarios**:

1. **Given** 数据库存在 1 条 `fees.status='PAID', fee_type='BID_BOND', fee_date >= NOW()` 的记录，**When** 用户在保证金看板选择状态筛选"未到期"，**Then** 该行出现在筛选结果中
2. **Given** 数据库存在 1 条 `project_initiation_details.need_deposit='YES', deposit_amount > 0` 且无对应 BID_BOND fees 的立项记录，**When** 用户在保证金看板选择状态筛选"未到期"，**Then** 该立项占位行出现在筛选结果中
3. **Given** 数据库存在 1 条 `fees.status='PAID', fee_type='BID_BOND', fee_date < NOW()` 的已超期记录，**When** 用户选择状态筛选"未到期"，**Then** 该行不出现在筛选结果中

---

### User Story 2 - 筛选"已退回"看到所有已退回行 (Priority: P2)

投标管理员在保证金看板筛选"已退回"状态时，希望看到所有标签为"已退回"的行，包括 `RETURNED` 状态和 `CANCELLED` 状态的 BID_BOND fee 记录（这两类在标签函数中都显示为"已退回"）。

**Why this priority**: P2 因为 `CANCELLED` 的 BID_BOND 在生产中数量较少，但语义不一致是真实漏洞，会随退保操作积累而放大。

**Independent Test**: 在测试环境准备 1 条 `fees.status='RETURNED'` + 1 条 `fees.status='CANCELLED'` 的 BID_BOND，点击"已退回"筛选，应看到 2 行。

**Acceptance Scenarios**:

1. **Given** 数据库存在 1 条 `fees.status='RETURNED', fee_type='BID_BOND'` 的记录，**When** 用户选择状态筛选"已退回"，**Then** 该行出现在筛选结果中
2. **Given** 数据库存在 1 条 `fees.status='CANCELLED', fee_type='BID_BOND'` 的记录，**When** 用户选择状态筛选"已退回"，**Then** 该行出现在筛选结果中
3. **Given** 数据库存在 1 条 `fees.status='PAID'` 的记录，**When** 用户选择状态筛选"已退回"，**Then** 该行不出现在筛选结果中

---

### User Story 3 - 筛选"已超期"行为保持不变 (Priority: P3)

用户筛选"已超期"时，行为应保持不变：只返回 `fees.status NOT IN ('RETURNED','CANCELLED')` 且 `fee_date < NOW()` 的行。立项占位行（`exp_return_date IS NULL`）不应被算作"已超期"。

**Why this priority**: P3 因为这是回归保护性需求，当前 OVERDUE 分支本身没有 Bug，但修复 P1/P2 时必须保证不破坏 OVERDUE 语义。

**Independent Test**: 准备 1 条 `fees.status='PAID', fee_date < NOW()` + 1 条立项占位行，点击"已超期"筛选，应只看到 1 行（立项占位行不应出现）。

**Acceptance Scenarios**:

1. **Given** 数据库存在 1 条 `fees.status='PAID', fee_type='BID_BOND', fee_date < NOW()` 的记录，**When** 用户选择状态筛选"已超期"，**Then** 该行出现在筛选结果中
2. **Given** 数据库存在 1 条立项占位行（`exp_return_date IS NULL`），**When** 用户选择状态筛选"已超期"，**Then** 该行不出现在筛选结果中

---

### Edge Cases

- 当用户切换不同状态筛选时，其他筛选维度（项目名称、时间范围等）应保持联动，不被状态筛选覆盖
- 当数据库中无任何 BID_BOND fee 记录时，"未到期"筛选仍应返回立项占位行（如有）
- 当 `fees.status='PENDING'`（已发起待支付）且 `fee_date < NOW()` 时，该行应被"已超期"筛选命中（保持现有语义）
- 当 `fees.status='PENDING'` 且 `fee_date >= NOW()` 时，该行应被"未到期"筛选命中

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 在状态筛选为"未到期"时返回所有满足以下条件的派生表行：`status NOT IN ('RETURNED','CANCELLED')` 且 (`exp_return_date IS NULL` 或 `exp_return_date >= NOW()`)
- **FR-002**: 系统 MUST 在状态筛选为"已退回"时返回所有 `status IN ('RETURNED','CANCELLED')` 的派生表行
- **FR-003**: 系统 MUST 在状态筛选为"已超期"时返回 `status NOT IN ('RETURNED','CANCELLED')` 且 `exp_return_date < NOW()` 的派生表行（行为不变）
- **FR-004**: 状态筛选的语义 MUST 与表格标签函数（label）的语义保持一致，即"筛选某标签后返回的行数"等于"未筛选时该标签出现的行数"
- **FR-005**: 系统 MUST 提供行为层回归测试覆盖以下场景：(a) 立项占位行在"未到期"筛选下出现；(b) CANCELLED fee 在"已退回"筛选下出现；(c) 立项占位行在"已超期"筛选下不出现
- **FR-006**: 系统 MUST NOT 改变派生表的 UNION ALL 结构、标签函数的语义、以及其他筛选维度（项目名/时间/金额）的行为

### Key Entities *(include if feature involves data)*

- **Margin 派生表**：由两支 UNION ALL 构成的查询结果集。fees 分支代表已缴纳的 BID_BOND 保证金记录（status 取自 fee.status，exp_return_date 取自 fee.fee_date）；init 分支代表立项表中需要缴纳但尚未缴纳的占位行（status 固定为 'PENDING'，exp_return_date 为 NULL）
- **Fee**：保证金缴费记录，fee_type='BID_BOND'，status 可能取值 PENDING/PAID/RETURNED/CANCELLED
- **ProjectInitiationDetails**：立项详情，need_deposit='YES' 且 deposit_amount>0 的项目会产生保证金占位行

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 用户在保证金看板选择"未到期"筛选后看到的行数，等于未筛选时表格中显示"未到期"标签的行数（100% 一致）
- **SC-002**: 用户在保证金看板选择"已退回"筛选后看到的行数，等于未筛选时表格中显示"已退回"标签的行数（100% 一致）
- **SC-003**: 用户在保证金看板选择"已超期"筛选后看到的行数，等于未筛选时表格中显示"已超期"标签的行数（100% 一致）
- **SC-004**: 新增的行为层回归测试在 CI 中全绿，覆盖立项占位行、CANCELLED fee、NULL exp_return_date 三个关键边界场景
- **SC-005**: 修复不影响其他筛选维度（项目名/时间范围/金额）的现有行为，相关既有测试保持通过

## Assumptions

- 标签函数 `label()` 的语义保持不变："已退回" = RETURNED + CANCELLED；"未到期" = exp_return_date IS NULL OR >= NOW()；"已超期" = 其他非退回行
- 派生表的 UNION ALL 结构（fees 分支 + init 分支）保持不变，不重构为 JOIN 或其他结构
- 修复范围限定于状态筛选分支（PENDING/RETURNED/OVERDUE 三个 case），不动其他筛选维度
- 既有 `MarginQuerySupportMysqlIntegrationTest` 的"只验证 SQL 不抛异常"测试模式将被保留，但新增测试必须验证返回行数/内容
- 测试环境使用 MySQL 8.0，与生产环境一致
- 不需要前端改动（前端只传 status 参数，后端返回数据即可）
