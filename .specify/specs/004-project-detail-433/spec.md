# Feature Specification: 项目详情 §4.3.3 — 6 项差距补齐

**Feature Directory**: `specs/004-project-detail-433`

**Created**: 2026-05-26

**Status**: Draft

**Input**: 蓝图 §4.3.3 项目详情 — 基于现有实现(80% 完成)补齐 6 项差距

## User Scenarios & Testing

### User Story 1 — 评标时间线 (Priority: P1)

项目负责人在评标阶段查看评标进展时间线，了解评标委员会的操作记录和状态变更历史。

**Why this priority**: 评标阶段需要过程透明度。当前完全缺失时间线功能，用户无法回溯评标事件。

**Independent Test**: 可在项目从标书制作推进到评标阶段后，验证时间线是否记录评标状态变更、文件上传等事件。

**Acceptance Scenarios**:

1. **Given** 项目在评标阶段，**When** 用户打开评标 tab，**Then** 看到按时间倒序排列的评标事件列表
2. **Given** 评标状态从"评标中"切换为"待上会"，**When** 状态变更完成，**Then** 时间线新增一条事件记录
3. **Given** 用户上传评标文件，**When** 上传完成，**Then** 时间线新增"评标文件上传"事件
4. **Given** 时间线有 20+ 条事件，**When** 页面加载，**Then** 默认显示最近 20 条，支持展开更多

---

### User Story 2 — 结果确认竞争对手情况 (Priority: P2)

项目负责人在结果确认阶段记录竞争对手信息，便于后续复盘分析。

**Why this priority**: 蓝图明确要求"竞争对手情况"，但当前结果确认页无此功能。

**Independent Test**: 在结果确认 tab 选择结果类型后，验证竞争对手信息输入和保存正常。

**Acceptance Scenarios**:

1. **Given** 项目在结果确认阶段，**When** 用户查看页面，**Then** 看到"竞争对手情况"输入区域（默认 3 行）
2. **Given** 用户填写竞争对手信息，**When** 提交结果确认，**Then** 竞争对手数据被保存
3. **Given** 需要更多行，**When** 点击"添加行"，**Then** 动态新增一行
4. **Given** 某行数据为空，**When** 提交，**Then** 允许空行存在（非必填）

---

### User Story 3 — 评标子阶段选项补齐 (Priority: P2)

投标管理员在评标阶段可以准确选择 4 种子状态（含 RESULT_OUT）。

**Why this priority**: 后端枚举有 4 种，前端只展示了 3 种，是全链路功能缺失。

**Independent Test**: 打开评标阶段页面，验证下拉选项包含全部 4 种子状态。

**Acceptance Scenarios**:

1. **Given** 项目在评标阶段，**When** 打开评标状态下拉，**Then** 看到 4 个选项：评标中/待上会/已出结果/已公示
2. **Given** 选择"已出结果"，**When** 确认提交，**Then** 状态切换成功

---

### User Story 4 — 复盘流程亮点字段 (Priority: P3)

项目负责人在复盘阶段可以单独填写"流程亮点"，与"中标优势"并列。

**Why this priority**: 蓝图要求独立的"流程亮点"字段，当前合并到 summary 中。

**Independent Test**: 在复盘 tab 中验证"流程亮点"输入框存在且数据可保存。

**Acceptance Scenarios**:

1. **Given** 项目在复盘阶段且结果为中标，**When** 页面加载，**Then** 看到"流程亮点"输入框
2. **Given** 填写流程亮点并提交复盘，**When** 重新加载页面，**Then** 数据回显正确

---

### User Story 5 — AI 生成复盘案例 (Priority: P3)

项目结项时，系统根据复盘数据自动生成案例草稿，供用户确认后入库。

**Why this priority**: 蓝图要求结项阶段"AI 生成复盘案例"，当前无此功能。

**Independent Test**: 在结项 tab 中点击"AI 生成复盘案例"按钮，验证案例草稿生成。

**Acceptance Scenarios**:

1. **Given** 复盘已完成且结果已确认，**When** 点击"AI 生成复盘案例"，**Then** 基于复盘数据生成案例草稿
2. **Given** 案例草稿生成完成，**When** 用户查看，**Then** 展示案例标题、摘要和详细内容
3. **Given** 用户确认案例内容，**When** 点击"保存到案例库"，**Then** 案例入库成功

---

### User Story 6 — 项目文档导出 (Priority: P3)

项目结项时，用户可以导出包含项目全生命周期文档的归档包。

**Why this priority**: 蓝图要求结项阶段"文档导出"，当前仅支持列表级 Excel 导出。

**Independent Test**: 在结项 tab 中点击导出按钮，验证归档包生成。

**Acceptance Scenarios**:

1. **Given** 项目满足结项条件，**When** 用户点击"导出项目总结"，**Then** 生成包含项目信息的文档
2. **Given** 导出的文档，**When** 用户打开，**Then** 包含项目基本信息、各阶段关键数据

---

### Edge Cases

- 评标阶段未有事件发生时，时间线显示"暂无评标记录"提示
- 结果确认时竞对信息为空可提交
- 复盘阶段流标/弃标结果不显示"流程亮点"字段
- AI 生成案例时如果复盘数据不足，提示用户补全
- 结项导出时如果文件生成失败，显示友好错误

## Requirements

### Functional Requirements

- **FR-001**: 评标阶段页面 MUST 展示按时间倒序的事件时间线
- **FR-002**: 评标状态变更、文件上传、弃标操作 MUST 自动记录到时间线
- **FR-003**: 评标状态下拉选择 MUST 包含 4 个选项（增加 RESULT_OUT）
- **FR-004**: 结果确认页面 MUST 包含"竞争对手情况"输入区域（默认 3 行，可添加/删除）
- **FR-005**: 竞争对手信息 MUST 随结果确认一起提交保存
- **FR-006**: 复盘页面 MUST 为中标结果显示独立的"流程亮点"输入框
- **FR-007**: 结项页面 MUST 提供"AI 生成复盘案例"按钮和结果展示
- **FR-008**: 结项页面 MUST 提供"导出项目总结"功能
- **FR-009**: 复盘阶段 MUST 根据结果类型（中标/未中标/流标/弃标）动态显示不同字段
- **FR-010**: 评标事件时间线的后端存储 MAY 复用现有的审计日志机制

### Key Entities

- **EvaluationTimelineEvent**: 评标事件记录（event_type, project_id, description, created_at, operator_id）
- **CompetitorRecord**: 竞争对手记录（project_id, name, bid_amount, score）
- **RetrospectiveProcessHighlight**: 流程亮点（独立于 summary 的字段）
- **ProjectArchiveDocument**: 项目归档文档（document_id, project_id, type, generated_at）

## Success Criteria

### Measurable Outcomes

- **SC-001**: 评标时间线在状态变更后 1 秒内显示新事件
- **SC-002**: 竞争对手信息支持动态添加至少 10 行
- **SC-003**: AI 生成复盘案例在 30 秒内完成
- **SC-004**: 复盘流程亮点独立存储，不影响现有 summary 数据

## Assumptions

- 评标时间线可复用现有的 `ProjectReminder` 或审计日志机制，无需新建表
- 竞争对手信息可作为 ProjectResult 的 JSON 字段存储
- 流程亮点可作为 ProjectRetrospective 的新增字段
- AI 案例生成可调用现有的 `casesApi` 接口
- 文档导出优先支持 PDF 格式，后续扩展
