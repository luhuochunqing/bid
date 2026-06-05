# Feature Specification: 项目档案时间戳补全

**Feature Branch**: `[016-project-archive-stage-timestamps]`

**Created**: 2026-06-04

**Status**: Draft

**Input**: User description: "项目档案详情页（档案详情抽屉 ArchiveDetailDrawer.vue + 后端 ProjectArchiveDetailService.java）需要在基础信息中展示以下4个时间节点，但当前数据源断裂：1. 立项日期（initiatedAt）：项目状态机进入 INITIATED 阶段的时间；2. 标书提交日期（bidSubmissionAt）：项目状态机进入 EVALUATING 阶段的时间；3. 结项日期（closedAt）：项目状态机进入 CLOSED/ARCHIVED 阶段的时间；4. 历史档案导入（historical import）：支持系统初始化时导入历史档案信息"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 查看档案详情中的真实时间节点 (Priority: P1)

档案管理员在查看任意项目档案详情时，能看到该项目从立项到结项的真实时间节点记录，包括立项日期、标书提交日期、开标日期和结项日期。

**Why this priority**: 档案的核心价值在于历史溯源，时间节点是档案完整性的基础要求。若档案详情中时间戳始终为空或显示占位符，档案即失去其作为历史记录的可信度。

**Independent Test**: 可通过档案详情 API 直接验证响应中 initiatedAt / bidSubmissionAt / closedAt 字段是否返回有效时间值，无需 UI。

**Acceptance Scenarios**:

1. **Given** 档案详情服务正常响应，**When** 用户打开某已进入 EVALUATING 阶段的项目的档案详情，**Then** initiatedAt 字段返回 Project 创建时间，bidSubmissionAt 字段返回进入 EVALUATING 阶段的时间，bidOpeningAt 字段返回 Tender 开标时间
2. **Given** 项目已推进至 CLOSED 阶段，**When** 用户打开该项目档案详情，**Then** closedAt 字段返回进入 CLOSED 阶段的时间
3. **Given** 项目处于 INITIATED 或 DRAFTING 阶段，**When** 用户打开该项目档案详情，**Then** bidSubmissionAt 和 closedAt 字段返回 null（尚未到达该阶段）
4. **Given** 档案详情页面，**When** 时间字段有值，**Then** 页面正确渲染为 YYYY-MM-DD HH:mm 格式；无值时显示 "-"

---

### User Story 2 - 状态变更自动记录时间节点 (Priority: P1)

项目在各阶段间推进时，系统自动记录进入该阶段的时间，无需人工干预。

**Why this priority**: 时间戳的准确性依赖状态变更时机的正确填充。如果需要人工补录，则违背了"系统自动记录"的设计初衷，且无法保证数据完整性。

**Independent Test**: 可通过单元测试模拟 ProjectStageService 的 requestTransition 调用，验证 Project 实体相应时间戳字段被正确赋值。

**Acceptance Scenarios**:

1. **Given** 新建项目进入 INITIATED 阶段，**When** Project 记录被首次保存，**Then** initiatedAt 自动填充为当前时间（由 Project.onCreate 或 ProjectService.createProject 写入）
2. **Given** 项目从 DRAFTING 推进至 EVALUATING，**When** ProjectStageService.requestTransition 被调用，**Then** 该 Project 的 evaluatingAt 字段自动填充为当前时间
3. **Given** 项目从 RETROSPECTIVE 推进至 CLOSED，**When** ProjectStageService.requestTransition 被调用，**Then** 该 Project 的 closedAt 字段自动填充为当前时间
4. **Given** 历史遗留项目通过导入方式创建，**When** 导入数据包含阶段时间信息，**Then** 各阶段时间戳字段按导入值填充，而非自动生成

---

### User Story 3 - 批量导入历史档案时间线 (Priority: P2)

系统支持在初始化阶段批量导入历史项目档案，并保留其原始的时间线信息。

**Why this priority**: 企业可能有大量历史投标项目需要纳入系统管理。如果不支持导入，这些项目的档案将永远缺失时间戳数据，无法满足档案完整性要求。

**Independent Test**: 可通过后端服务层或导入 API 批量导入测试数据集，验证数据库中各时间戳字段是否正确映射。

**Acceptance Scenarios**:

1. **Given** 管理员调用历史档案导入接口并传入项目数据（含各阶段时间），**When** 导入完成，**Then** Project 实体的 initiatedAt / evaluatingAt / closedAt 字段按传入值填充，档案记录同步创建
2. **Given** 导入数据中某阶段时间缺失，**When** 导入处理该记录，**Then** 对应字段留空（null），不影响其他字段正常导入
3. **Given** 导入数据中项目阶段时间晚于系统当前时间，**When** 导入处理该记录，**Then** 系统接受该值并在档案详情中正确展示（不做合理性校验，尊重历史数据）

---

### Edge Cases

- **无 Tender 关联的项目**：部分项目可能无对应 Tender 记录，档案详情中 initiatedAt 降级为 Project.createdAt
- **阶段时间早于项目创建时间**：导入历史数据时可能出现此情况，应允许通过并记录
- **状态机倒退**：若业务上允许阶段倒退（如从 EVALUATING 回退至 DRAFTING），evaluatingAt 不应被清除，时间戳保留首次进入该阶段的记录
- **归档与结项的关系**：蓝图要求"结项日期"对应进入 CLOSED 阶段的时间，但项目可能先归档（ARCHIVED）后结项（CLOSED）。closedAt 统一记录进入 CLOSED 阶段的时间，与归档状态无关

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Project 实体必须存储以下 3 个阶段时间戳：initiatedAt（立项时间）、evaluatingAt（标书提交/进入评标时间）、closedAt（结项时间）
- **FR-002**: 项目首次创建并保存时，initiatedAt 必须被填充为当前时间（UTC+8）
- **FR-003**: ProjectStageService.requestTransition 推进至 EVALUATING 时，必须将当前时间写入该 Project 的 evaluatingAt 字段
- **FR-004**: ProjectStageService.requestTransition 推进至 CLOSED 时，必须将当前时间写入该 Project 的 closedAt 字段
- **FR-005**: ProjectStageService.requestTransition 进入新阶段时，若对应时间戳字段已有值，**不得覆盖**（保留首次进入时间）
- **FR-006**: ProjectArchiveDetailService.getArchiveDetail 必须从 Project 实体读取 initiatedAt / evaluatingAt / closedAt 时间戳，并正确映射至响应 DTO 的 initiatedAt / bidSubmissionAt / closedAt 字段
- **FR-007**: ArchiveDetailDrawer.vue 中 initiatedAt / bidSubmissionAt / closedAt 字段有值时渲染为 YYYY-MM-DD HH:mm 格式；无值时显示 "-"
- **FR-008**: 系统必须提供历史档案批量导入接口，允许在创建 Project 记录时直接指定 initiatedAt / evaluatingAt / closedAt 时间戳值
- **FR-009**: 数据库迁移脚本必须向后兼容已有数据：已有 Project 记录的 `initiated_at` 字段默认填充为 `created_at`（作为历史追溯的近似值）。`evaluating_at` 和 `closed_at` 在历史数据中无原始数据来源，迁移后保持 NULL（仅对新创建或推进的项目自动填充）

### Key Entities

- **Project（项目）**: 核心业务实体，新增 initiatedAt / evaluatingAt / closedAt 三个阶段时间戳字段
- **ProjectStage（项目阶段枚举）**: 阶段枚举（INITIATED → DRAFTING → EVALUATING → RESULT_PENDING → RETROSPECTIVE → CLOSED），时间戳记录进入各关键节点的时间
- **ProjectArchive（项目档案）**: 与 Project 一一对应，档案详情的时间戳数据最终来源于 Project 实体的时间戳字段
- **历史导入记录（Historical Import Record）**: 导入接口的输入数据格式，包含项目基础信息及各阶段时间戳

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 所有已完成 EVALUATING 阶段的项目，其档案详情 API 响应中 bidSubmissionAt（evaluatingAt）字段非空率不低于 95%
- **SC-002**: 所有已 CLOSED 阶段的项目，其档案详情 API 响应中 closedAt 字段非空率不低于 95%
- **SC-003**: 新建项目从 INITIATED 推进至 EVALUATING，档案详情中 bidSubmissionAt 字段可在阶段推进后 5 秒内查询到有效值
- **SC-004**: 批量导入 100 条历史档案记录，所有时间戳字段的映射准确率不低于 99%（允许 1 条字段缺失）
- **SC-005**: 档案详情页面时间戳字段展示正确率（格式 + 空值处理）达到 100%

## Assumptions

- **项目阶段时间戳的首次写入原则**：同一阶段的时间戳仅在首次进入时写入，不在后续推进中被覆盖，确保历史数据的不可篡改性
- **阶段对应关系**：initiatedAt → INITIATED（立项），evaluatingAt → EVALUATING（评标/标书提交），closedAt → CLOSED（结项）。DRAFTING / RESULT_PENDING / RETROSPECTIVE 不产生独立时间戳字段
- **Tender 降级策略**：当 Project 无对应 Tender 记录时，initiatedAt 降级使用 Project.createdAt，确保档案详情至少有立项时间可用
- **前端格式约定**：时间字段统一渲染为 "YYYY-MM-DD HH:mm" 格式，使用前端 formatDateTime 工具函数处理，空值显示 "-"
- **迁移数据处理**：已有 Project 记录的 `initiatedAt` 在迁移脚本中回填为 `createdAt`（作为历史追溯的近似基准）。`evaluatingAt` 和 `closedAt` 无历史数据来源，迁移后保持 NULL
