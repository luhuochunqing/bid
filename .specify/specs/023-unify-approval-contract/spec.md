# Feature Specification: 统一审批接口契约

**Feature Branch**: `agent/qoder/unify-approval-contract`

**Created**: 2026-07-02

**Status**: Draft

**Input**: User description: "按照改进建议修复审批接口契约不统一问题——制定全项目审批接口契约规范，统一所有审批类 Controller 的签名风格、DTO 命名、字段名和前端调用方式，并补充测试覆盖契约接缝"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 后端审批接口契约统一（Priority: P1）

作为后端开发者，当我要新增一个审批类接口（通过/驳回）时，我希望项目里有统一的契约规范可以参照，所有审批类 Controller 都用相同的签名风格（`@Valid @RequestBody XxxApprovalRequest`）、统一的 DTO 命名（`Xxx{Approval|Rejection}Request`）和统一字段名（`comment`），这样我就不会在"严格 DTO"、"宽松 Map"、"无 body"三种风格之间犹豫，也不会因为选错风格导致前后端契约脱节。

**Why this priority**: 这是 CO-459 审批 bug 的元根因——项目内存在四种契约风格，新接口选哪种全凭个人偏好。统一契约是防复发的根本措施，必须最先做。

**Independent Test**: 可以通过静态扫描验证——全项目所有审批类 Controller 签名符合规范，不存在 `Map<String,String>` 或 `required=false` 接收审批 body 的情况。

**Acceptance Scenarios**:

1. **Given** 项目中有 4 个审批类 Controller（立项/标书/结项/CA借用），**When** 开发者查看它们的签名，**Then** 所有 approve/reject 接口都使用 `@Valid @RequestBody XxxApprovalRequest` 风格的 DTO，不存在 `Map<String,String>` 或 `required=false`
2. **Given** 某个审批接口的 DTO 字段名为 `comment`，**When** 前端调用该接口，**Then** 前端传的对象 key 也是 `comment`，前后端字段名一致
3. **Given** 开发者要新增第五个审批接口，**When** 查看 `docs/architecture/approval-contract.md` 规范文档，**Then** 能在 5 分钟内明确知道 DTO 怎么命名、字段怎么定、前端怎么调

---

### User Story 2 - 前端 API 模块禁用默认参数兜底契约（Priority: P1）

作为前端开发者，当我调用审批类接口时，我希望 API 模块强制要求显式传对象，而不是用 `data = {}` 这种假防御兜底，这样就不会因为 JavaScript 默认参数陷阱（`''` 不是 `undefined`，默认值不生效）导致发送空字符串给后端。

**Why this priority**: 这是 CO-459 bug 的直接技术根因——`data = {}` 默认参数对 `''` 不生效。必须移除所有审批类 API 模块的默认参数兜底，强制显式传对象。

**Independent Test**: 可以通过静态扫描验证——全项目所有审批类前端 API 方法（approve/reject）都不使用 `data = {}` 默认参数。

**Acceptance Scenarios**:

1. **Given** 前端 API 模块有 `async approve(id, data = {})`，**When** 开发者调用 `approve(id, '')` 传空字符串，**Then** 这种调用应该被代码审查或 lint 规则拦截
2. **Given** 后端审批接口要求 `@RequestBody`（必需），**When** 前端 API 模块定义该方法，**Then** 不使用默认参数 `data = {}`，强制调用方显式传对象
3. **Given** 前端调用 `caStore.approveApplication(id, { comment: '同意' })`，**When** Store 透传给 `caApi.approve(id, { comment: '同意' })`，**Then** axios 正确序列化为 JSON `{"comment":"同意"}`，后端反序列化成功

---

### User Story 3 - 审批类 Controller 必须有反序列化测试覆盖（Priority: P2）

作为后端开发者，当我写一个审批类 Controller 时，我必须有 `@WebMvcTest` 覆盖三种反序列化场景（空 body / 空字符串 / 缺字段），这样契约接缝处的反序列化失败能在单测阶段被发现，而不是等到生产环境报错。

**Why this priority**: CO-459 bug 的第三层根因是单测只覆盖 store 层不覆盖契约接缝。补测试是防复发的重要兜底，但优先级低于契约统一本身（P1），因为契约统一后新接口自然照葫芦画瓢，测试是双保险。

**Independent Test**: 可以通过 `mvn test` 验证——所有审批类 Controller 都有对应的 `@WebMvcTest`，覆盖空 body / 空字符串 / 缺字段三种场景返回 400。

**Acceptance Scenarios**:

1. **Given** `CaCertificateController.approve` 接口要求 `@Valid @RequestBody CaApprovalRequest`，**When** 测试发送空 body，**Then** 返回 400 + "请求体格式错误"
2. **Given** 同一接口，**When** 测试发送空字符串 `''`，**Then** 返回 400 + "请求体格式错误"
3. **Given** 同一接口，**When** 测试发送 `{"comment": ""}`（缺字段值），**Then** 返回 400 + "审批意见不能为空"
4. **Given** 同一接口，**When** 测试发送 `{"comment": "同意"}`，**Then** 返回 200 + 正常业务结果

---

### Edge Cases（已决策）

- **ClosureController.approve 无 body 场景**：统一改为 `@RequestBody` + `comment` 字段可选（`@Valid` 不加 `@NotBlank`，或加 `@NotBlank` 但前端默认传 `"同意"`）。保持契约统一（都有 body），但允许通过操作不填意见。驳回操作仍必填。
- **DraftingController 迁移兼容性**：直接迁移到 DTO 风格，新增 `DraftingApprovalRequest`/`DraftingRejectionRequest` DTO，同步更新所有前端调用点（DraftingStage.vue, TaskBoardBidReviewActions.vue）。`Map<String,String>` 的字段名（comment/reason）本来就和 DTO 统一，前端改动最小。
- **多字段 DTO 命名规范**：`comment` 为主字段（审批意见），其他业务字段按各自语义命名（如 `projectId`, `assigneeId`）。规范只约束 `comment` 字段必须存在且统一命名，不强制 DTO 只有 `comment` 一个字段。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 制定一份审批接口契约规范文档（`docs/architecture/approval-contract.md`），覆盖 DTO 命名规则、字段命名规则、Controller 签名风格、前端调用方式
- **FR-002**: 全项目所有审批类 Controller（approve/reject）MUST 统一使用 `@Valid @RequestBody XxxApprovalRequest` 风格的 DTO，禁止使用 `Map<String,String>` 或 `required=false`
- **FR-003**: 全项目审批类 DTO 的"审批意见"字段 MUST 统一命名为 `comment`，不混用 `reason`、`rejectionReason` 等同义词
- **FR-004**: 前端 API 模块的审批类方法（approve/reject）MUST 禁止使用 `data = {}` 默认参数兜底契约，强制调用方显式传对象
- **FR-005**: 所有审批类 Controller MUST 有 `@WebMvcTest` 覆盖三种反序列化场景：空 body / 空字符串 / 缺字段
- **FR-006**: 迁移历史接口时 MUST 保持前端调用兼容——如果前端调用方式改变，必须同步更新所有调用点
- **FR-007**: 审批类操作 MUST 有 E2E 烟雾测试覆盖（至少 CA 借用审批要有，因为它是 CO-459 的事故点）

### Key Entities *(include if feature involves data)*

- **ApprovalRequest DTO**: 审批通过请求体，字段 `comment`（审批意见，必填，String）
- **RejectionRequest DTO**: 驳回请求体，字段 `comment`（驳回原因，必填，String）—— 注意：统一用 `comment`，不再用 `reason`/`rejectionReason`
- **审批类 Controller**: 任何提供 `approve`/`reject` 端点的 REST Controller，MUST 遵循统一契约

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 全项目审批类 Controller 100% 使用统一契约风格（静态扫描 0 个 `Map<String,String>` 或 `required=false` 违规）
- **SC-002**: 全项目审批类前端 API 方法 100% 移除 `data = {}` 默认参数（静态扫描 0 个违规）
- **SC-003**: 所有审批类 Controller 100% 有 `@WebMvcTest` 覆盖空 body / 空字符串 / 缺字段三种场景
- **SC-004**: 新增审批接口时，开发者能在 5 分钟内根据规范文档确定契约写法（无需对比多个历史样板）
- **SC-005**: CO-459 的 CA 借用审批 E2E 烟雾测试通过（真实点击→请求→后端 200 落库）

## Assumptions

- 假设历史接口（DraftingController 的 `Map<String,String>`）的前端调用方可以同步迁移到 DTO 风格，不会因兼容性问题阻塞契约统一
- 假设 ClosureController.approve 当前无 body 的设计可以改为 `@RequestBody` + `comment` 可选（`@Valid` + `@NotBlank` 改为空校验），保持契约统一
- 假设审批类 DTO 字段统一用 `comment` 不会与历史数据库字段（如 `reject_reason`）冲突——DTO 字段与 DB 字段是两层映射
- 假设 E2E 烟雾测试只需要覆盖 CA 借用审批（事故点），其他审批接口的 E2E 可以作为技术债后续补
