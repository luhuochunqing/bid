# Feature Specification: 工作台产品蓝图对齐

**Feature Branch**: `codex/workbench-blueprint-alignment`

**Created**: 2026-05-17

**Status**: Draft

**Input**: "西域数智化投标管理平台 - 产品蓝图-初稿-V1.1" 飞书文档第四章「工作台」需求对齐

## User Scenarios & Testing

### User Story 1 - 管理员查看 deadline 指标卡片 (Priority: P1)

管理员登录工作台后，在 Dashboard 顶部看到4张 deadline 指标卡片，分别展示本日报名截止、本周开标、本月保证金截止、本月报名截止的项目数量，数据按全量项目统计。

**Why this priority**: 核心需求差距——现有指标卡片只显示通用统计（中标金额/中标率/标讯数），蓝图要求按时间窗口展示关键 deadline 节点。

**Independent Test**: 以 admin 账号登录 → 访问工作台 → 确认看到4张 deadline 卡片，数字与数据库 Tender/Fee 表一致。

**Acceptance Scenarios**:

1. **Given** Admin 已登录，**When** 进入工作台 Dashboard，**Then** 显示4张 deadline 指标卡片（今日报名截止/本周开标/本月保证金截止/本月报名截止），数值与当月实际 deadline 数据一致
2. **Given** 当月无任何 deadline，**When** Admin 进入工作台，**Then** 每张卡片显示 "0"
3. **Given** Tender/Fee 表中 deadline 跨多日，**When** 指标计算执行，**Then** 本日计数仅包含当天、本周包含周一至日、本月包含整月

---

### User Story 2 - 非管理员按权限查看 deadline 指标 (Priority: P1)

非管理员（manager/bid_admin/staff/sales 等）登录后，只能看到自己权限范围内项目的 deadline 统计，指标卡片数量和内容随权限变化。

**Why this priority**: 蓝图要求「分权限展示」，必须复用现有 ProjectAccessScopeService 做项目级数据过滤。

**Independent Test**: 以 bid_admin 登录（只能看自己管理的项目）→ 确认 deadline 数值仅包含授权范围内项目。

**Acceptance Scenarios**:

1. **Given** Manager 登录（负责项目 1,2），**When** 查看 deadline 指标，**Then** 仅统计项目 1,2 关联的 Tender deadline
2. **Given** Staff 登录（无任何项目权限），**When** 查看 deadline 指标，**Then** 所有卡片显示 "0"
3. **Given** bid_specialist（新增角色）登录，**When** 查看指标，**Then** 凭 `project` 权限看到3张卡片（非 analytics 权限所以不是4张）

---

### User Story 3 - 快捷入口操作区 (Priority: P2)

工作台 WelcomeBanner 下方新增独立的快捷操作区，包含「发起项目」「查看标讯」「处理待办」三个大按钮，任何有对应权限的用户都可点击跳转。

**Why this priority**: 蓝图要求「快速发起项目、查看标讯、处理待办」，当前 banner 动作按钮只有每角色2个且交互较弱。

**Independent Test**: 登录任意角色 → 确认快捷入口区存在且按钮可点击 → 点击「发起项目」跳转至 /project/create。

**Acceptance Scenarios**:

1. **Given** 用户有 project 权限，**When** 点击「发起项目」，**Then** 跳转到项目创建页
2. **Given** 用户有 bidding 权限，**When** 点击「查看标讯」，**Then** 跳转到标讯列表
3. **Given** 用户登录，**When** 点击「处理待办」，**Then** 滚动到待办区域或打开待办抽屉

---

### User Story 4 - AI 商机预测占位 (Priority: P3)

工作台展示「AI 商机预测」占位卡片，标题和描述提示该功能处于规划中。

**Why this priority**: 蓝图标注「规划中」，当前只做 UI 占位，不做后端实现。

**Independent Test**: 登录 → 确认工作台底部显示 AI 预测占位卡片。

**Acceptance Scenarios**:

1. **Given** 用户登录，**When** 滚动到工作台底部，**Then** 看到「AI 商机预测」占位卡片，标注规划中

---

### User Story 5 - 日程日历项目里程碑增强 (Priority: P3)

WorkCalendar 组件中项目里程碑类型事件增加特殊标记（里程碑图标 + 标签），与普通日历事件视觉区分。

**Why this priority**: 蓝图要求「投标关键节点、项目里程碑可视化」，强化已有日历组件。

**Acceptance Scenarios**:

1. **Given** 日历中存在项目里程碑事件，**When** 渲染日历，**Then** 里程碑事件显示特殊图标和「里程碑」标签

---

### User Story 6 - 待办区标书评审接入 (Priority: P3)

工作台「我的待办」区域接入标书评审状态，确保四类待办（任务待办/任务审核/标书评审/流程审批）完整展示。

**Why this priority**: 蓝图要求涵盖全部四类待办，当前标书评审入口不够明显。

**Acceptance Scenarios**:

1. **Given** 用户有待评审的标书，**When** 查看工作台待办区，**Then** 标书评审项可区分展示，可点击跳转

---

### Edge Cases

- Tender.deadline / Tender.bidOpeningTime 为 null 时，不影响正常统计（Policy 已处理 null）
- Fee.feeType='BID_BOND' 且 status='CANCELLED' 时不计数
- 跨月查询时，当前月天数正确（lengthOfMonth 处理 28/29/30/31）
- 前端 API 调用失败时，MetricCards 显示 loading 状态和重试按钮（复用现有 EmptyState 组件）

## Requirements

### Functional Requirements

- **FR-001**: 后端 MUST 提供 `GET /api/workbench/deadline-stats` 端点，返回分时段的 deadline 统计
- **FR-002**: 后端 MUST 通过 `ProjectAccessScopeService.getAllowedProjectIdsForCurrentUser()` 过滤数据权限
- **FR-003**: deadline 统计 MUST 覆盖三类节点：报名截止（registrationDeadline）、开标时间（bidOpeningTime）、保证金截止（BID_BOND feeDate）
- **FR-004**: 前端 MUST 使用 `hasAnyPermission(menuPermissions, [...])` 驱动指标卡片配置，不硬编码角色名
- **FR-005**: 前端 MUST 新增 `QuickEntrySection` 快捷入口组件
- **FR-006**: 前端 MUST 新增 `AiPredictionPlaceholder` 占位组件
- **FR-007**: 后端纯核心 `WorkbenchDeadlinePolicy` MUST 放在 `com.xiyu.bid.workbench.domain`，不可变 record，零框架依赖
- **FR-008**: 系统 MUST 清理 `WorkbenchScheduleQueryService` 中的 Demo 遗留引用

### Key Entities

- **WorkbenchDeadlinePolicy** (纯核心 record): 时间窗口边界计算 + 计数聚合，无副作用
- **WorkbenchDeadlineStatsDTO** (不可变 record): API 返回的 deadline 统计 DTO
- **WorkbenchDeadlineQueryService** (应用服务): 编排 ProjectAccessScopeService → Repository → Policy → DTO

## Success Criteria

- **SC-001**: Admin 登录工作台后，deadline 指标数字与数据库查询结果一致（偏差 0）
- **SC-002**: 非 Admin 用户只能看到授权项目范围内的 deadline 数据
- **SC-003**: 后端 `FPJavaArchitectureTest` 和 `MaintainabilityArchitectureTest` 全部通过
- **SC-004**: 前端 `npm run build` 无错误
- **SC-005**: 新增代码单元测试覆盖率 ≥ 80%

## Assumptions

- 保证金缴纳截止 = Fee 表 `feeType='BID_BOND'` 且 `status='PENDING'` 的 `feeDate`
- 新增端点 `/api/workbench/deadline-stats`，不动现有 `/api/analytics/summary`
- AI 商机预测仅做 UI 占位，不实现后端逻辑
- Demo 引用只清理 `WorkbenchScheduleQueryService`，不动其他模块
- 前端权限模型已由 PR #281 迁移至 `hasAnyPermission(menuPermissions, [...])` 驱动
