# Feature Specification: 标书审核流程

**Feature Branch**: `agent/codex/bidding-document-review-flow`

**Created**: 2026-06-06

**Status**: Draft

**Input**: 投标文件模块 — 投标负责人/辅助人员完成标书编写后上传投标文件，选择审核人提交审核，审核人收到代办并可驳回/通过。

## User Scenarios & Testing

### User Story 1 - 投标负责人提交标书审核 (Priority: P1)

投标负责人（或辅助人员）完成完整标书编写后，上传投标文件，选择标书审核人，点击提交审核。

**Why this priority**: 这是审核流程的起点，没有提交就没有后续的审核和投标。

**Independent Test**: 可独立验证：负责人上传文件+选审核人+提交后，页面按钮变"审核中"且置灰。

**Acceptance Scenarios**:

1. **Given** 投标负责人已上传投标文件，**When** 选择标书审核人（模糊搜索）并点击提交审核，**Then** 提交审核按钮变为"审核中"且置灰
2. **Given** 投标负责人未选择审核人，**When** 点击提交审核，**Then** 提示"请先选择标书审核人"
3. **Given** 审核已被驳回，**When** 负责人修改后重新提交审核，**Then** 状态重新变为"审核中"

---

### User Story 2 - 审核人收到代办并审核 (Priority: P1)

标书审核人收到一个代办事项，查看标书详情（项目名称、招标主体、开标时间、投标负责人/辅助人员信息、投标文件附件），选择通过或驳回。

**Why this priority**: 这是审核流程的核心价值——让审核人能查看标书并做出决策。

**Independent Test**: 可独立验证：审核人打开代办通知→查看标书附件→点击通过或驳回。

**Acceptance Scenarios**:

1. **Given** 投标负责人已提交审核，**When** 审核人打开通知中心，**Then** 看到一条代办通知（标题含项目名称，可查看投标文件附件）
2. **Given** 审核人查看标书后点击"审核通过"，**Then** 投标负责人端按钮变为"已通过审核"
3. **Given** 审核人查看标书后点击"驳回"，**When** 填写驳回原因并确认，**Then** 投标负责人端显示驳回原因，按钮变为"重新提交标书审核"

---

### User Story 3 - 审核通过后完成投标并提交 (Priority: P2)

审核通过后，投标负责人可以勾选"已完成投标"复选框，然后点击"提交投标"推进到评标阶段。

**Why this priority**: 这是流程的最终闭环，依赖于审核通过状态。

**Independent Test**: 可独立验证：审核通过后，页面出现"已完成投标"复选框→勾选后点击"提交投标"→页面推进至评标阶段。

**Acceptance Scenarios**:

1. **Given** 审核已通过，**When** 投标负责人勾选"已完成投标"并点击"提交投标"，**Then** 项目推进至评标阶段
2. **Given** 审核未通过或正在审核中，**When** 投标负责人试图提交投标，**Then** "已完成投标"复选框和提交按钮不可用

---

### Edge Cases

- 审核人模糊搜索时，只精确匹配人员，不显示无关用户
- 驳回后重新提交审核，审核人收到新的代办通知
- 项目 stage 为 CLOSED 时拒绝任何审核操作

## Requirements

### Functional Requirements

- **FR-001**: System MUST 持久化标书审核状态（待审核/审核中/已通过/已驳回）
- **FR-002**: System MUST 记录审核人、审核时间、驳回原因
- **FR-003**: System MUST 在提交审核时向审核人发送代办通知（含项目名称、招标主体、开标时间、负责人信息、投标文件附件）
- **FR-004**: System MUST 支持审核人查看并下载投标文件附件
- **FR-005**: System MUST 审核通过后允许投标负责人提交投标（推进至 EVALUATING 阶段）
- **FR-006**: System MUST 驳回时投标负责人可重新提交审核
- **FR-007**: System MUST 审核人搜索只精确匹配人员

### Key Entities

- **BidDocumentReview**: 标书审核记录，关联 projectId、reviewerId、提交人、状态（REVIEWING/APPROVED/REJECTED）、驳回原因、审核时间
- **Notification** (已有): 增加 BID_REVIEW 类型，用于发送审核代办

## Success Criteria

### Measurable Outcomes

- **SC-001**: 投标负责人可在 3 步内完成提交审核（上传文件→选审核人→提交）
- **SC-002**: 审核人提交审核结果后，投标负责人端状态实时更新
- **SC-003**: 驳回时审核人必须填写原因，否则无法驳回

## Assumptions

- 已有通知系统 `NotificationApplicationService` 和 `NotificationType.APPROVAL` 可复用扩展
- 已有 `ProjectDraftingService` 中的 `approveBid`/`rejectBid` 骨架需要补全实现
- 前端 `DraftingStage.vue` 已有基本UI骨架，需对接后端真实API
- 审核状态通过新表 `bid_document_review` 持久化
