# Feature Specification: CRM 商机关联补偿与认证解耦

**Feature Branch**: `agent/trae/crm-link-compensation`

**Created**: 2026-07-16

**Status**: Draft

**Input**: User description: "CRM 推送标讯时若 PM 尚未登录系统，因 OSS token 不存在导致 CRM 商机关联永久失败；且 `linkByChanceIdIfPresent` 误把 `external_id` 中的标讯 ID 当成商机 ID 去查 detail 接口，注定 404。需要：(1) 修正 sourceId 语义错误；(2) OSS 同步时填充 `crm_sales_no`；(3) 去掉 `generateToken` 对 OSS token 的强制依赖（已验证接口不校验 Authorization）。"

## 背景与根因

### 生产事故

- **tender 56**（`external_id=CRM:7`，`source_type=CRM_OPPORTUNITY`）创建于 2026-07-15 17:43:48
- 创建时 PM 王旭州（OSS 工号 04503）尚未首次登录系统，Redis 中没有 `oss:token:04503`
- `CrmAuthService.getValidTokenForUser("04503")` 抛 `TokenUnavailableException`
- 关联失败，`crm_opportunity_id` 保持 NULL
- 8 分钟后（17:52:10）王旭州登录，token 写入 Redis，但 tender 56 已关联失败，无补偿机制

### 三层根因

1. **代码语义错误**：`linkByChanceIdIfPresent` 把 `external_id="CRM:7"` 中的 `7`（标讯 ID）当成 `chanceId`（商机 ID）去调 `/customer-chance/detail?id=7`，但 `7` 是 CRM 的标讯 ID，真正的商机 ID 是 `6`
2. **字段未填充**：`users.crm_sales_no` 全表为 NULL，OSS 同步时未填充；实际 `salesNo = OSS 工号`（已验证）
3. **认证强依赖**：`CrmAuthService.fetchAndCacheUserToken` 强制要求 OSS access_token 才能换 CRM JWT，但 `generateToken` 接口实测不校验 Authorization header

### 验证证据

- **测试环境验证**：`POST /common/inner/generateToken` 不带 Authorization，仅传 `{"nickName":"王旭州","salesNo":"04503"}`，返回 `code:0` + 有效 CRM JWT
- **生产环境验证**：用王旭州的 nickName+salesNo 换 CRM JWT 后，调 `/customer-chance/page-list` 按 `code=CC2026071568` 查到商机详情（id=6, bidId=7, projectLeaderNo=04503）
- **DB 验证**：`users` 表所有用户 `crm_sales_no = NULL`，但 OSS 工号即 CRM salesNo

## User Scenarios & Testing

### User Story 1 - CRM 推送标讯时自动正确关联商机（Priority: P1）

CRM 系统通过 API 推送一条新标讯到本系统，本系统需要正确建立标讯与 CRM 商机的关联关系。即使 PM 尚未首次登录本系统，关联也不应失败。

**Why this priority**: 这是核心业务流程。当前所有 CRM 推送的标讯（52/53/56）的 `crm_opportunity_id` 都是 NULL，导致前端看不到关联的商机信息，影响 PM 评估和决策。

**Independent Test**: CRM 推送一条新标讯（PM 从未登录过），验证标讯创建后 `crm_opportunity_id` 立即写入正确的 CC 编号。

**Acceptance Scenarios**:

1. **Given** PM 从未登录本系统（Redis 无 `oss:token:{工号}`），**When** CRM 推送标讯（payload 含 `sourceSystem=CRM, sourceId=7, title=...`），**Then** 标讯创建成功且 `crm_opportunity_id` 写入正确的 CC 编号（如 `CC2026071568`）
2. **Given** PM 已登录本系统，**When** CRM 推送标讯，**Then** 标讯创建成功且 `crm_opportunity_id` 写入正确的 CC 编号（与场景 1 结果一致）
3. **Given** CRM 商机不存在或已关闭，**When** CRM 推送标讯，**Then** 标讯创建成功但 `crm_opportunity_id` 为 NULL，日志记录 WARN 级别告警，不抛异常

---

### User Story 2 - 历史未关联标讯自动补偿（Priority: P2）

对于已经创建但因各种原因未关联商机的标讯（如 tender 52/53/56），系统应能自动补偿关联，无需人工干预。

**Why this priority**: 当前已有 3 条历史标讯未关联，人工补偿成本高且易出错。自动补偿能确保数据一致性。

**Independent Test**: 启动补偿任务后，所有 `source_type=CRM_OPPORTUNITY AND crm_opportunity_id IS NULL` 的标讯在 N 分钟内被自动关联。

**Acceptance Scenarios**:

1. **Given** 存在 3 条 `crm_opportunity_id IS NULL` 的 CRM 标讯，**When** 补偿任务运行，**Then** 所有标讯的 `crm_opportunity_id` 被正确写入（或日志记录失败原因）
2. **Given** 标讯的 `external_id` 中的 sourceId 在 CRM 侧已找不到对应商机，**When** 补偿任务运行，**Then** 跳过该标讯，日志记录 WARN，不影响其他标讯
3. **Given** 补偿任务运行中，**When** 新的 CRM 标讯被推送，**Then** 新标讯按 User Story 1 正常关联，不受补偿任务影响

---

### User Story 3 - OSS 同步时填充 CRM 工号（Priority: P3）

OSS 同步用户信息时，应同时填充 `crm_sales_no` 字段，使所有 OSS 用户都具备调 CRM API 的身份标识。

**Why this priority**: 这是 User Story 1 的前置条件之一。当前 `crm_sales_no` 全表为 NULL，虽然 `generateToken` 不校验 Authorization，但仍需正确的 `salesNo` 才能换出有权限的 CRM JWT。

**Independent Test**: 触发一次 OSS 用户同步后，同步过的用户 `crm_sales_no` 字段不为 NULL，且与 `username`（OSS 工号）一致。

**Acceptance Scenarios**:

1. **Given** OSS 同步一个新用户（工号 04503），**When** 同步完成，**Then** `users` 表该用户的 `crm_sales_no = '04503'`
2. **Given** 已有用户 `crm_sales_no = NULL`，**When** OSS 触发该用户的更新事件，**Then** `crm_sales_no` 被填充为 OSS 工号
3. **Given** 用户工号变更（极少见），**When** OSS 同步该用户，**Then** `crm_sales_no` 同步更新为新工号

---

### Edge Cases

- **CRM 接口不可用**：关联/补偿失败时，标讯仍正常创建/更新，仅 `crm_opportunity_id` 为 NULL，日志记录 ERROR，不阻塞主流程
- **CRM JWT 过期**：调 detail/page-list 返回 401，需要重新换 JWT 重试一次
- **PM 的 OSS 工号在 CRM 中不存在**：`generateToken` 返回 `code:1`，日志记录 WARN，跳过该标讯
- **`external_id` 格式异常**（如 `CRM:` 缺失 sourceId）：跳过关联，日志记录 WARN
- **并发补偿**：多个补偿任务实例同时运行时，通过 `crm_opportunity_id IS NULL` 条件天然幂等，但需加乐观锁或 `SELECT ... FOR UPDATE` 避免重复处理

## Requirements

### Functional Requirements

- **FR-001**: 系统 MUST 在 CRM 推送标讯时，使用 `external_id` 中的 sourceId 作为**标讯 ID**（而非商机 ID）进行 CRM 反查
- **FR-002**: 系统 MUST 通过 CRM `page-list` 接口按标讯 ID（`bidId`）反查关联的商机详情（含商机编号 code、商机名称、项目负责人）
- **FR-003**: 系统 MUST 在调 `generateToken` 时采用 fallback 策略：OSS token 存在时走 `postWithAuth`（带 Authorization，原路径），OSS token 缺失时 fallback 到 `postJson`（无 Authorization）。这确保测试环境已登录用户不受影响，生产环境未登录 PM 也能换 JWT
- **FR-004**: 系统 MUST 在 OSS 用户同步时，用 OSS 工号（`username`）填充 `crm_sales_no` 字段
- **FR-005**: 系统 MUST 提供定时补偿任务，扫描 `source_type=CRM_OPPORTUNITY AND crm_opportunity_id IS NULL` 的标讯，自动重跑关联
- **FR-006**: 补偿任务 MUST 是幂等的：已关联成功的标讯（`crm_opportunity_id IS NOT NULL`）不被重复处理
- **FR-007**: 补偿任务 MUST 不修改标讯的 `status` 字段（避免状态回退）
- **FR-008**: 系统 MUST 在关联失败时记录 WARN 级别日志，包含 tender_id、sourceId、失败原因
- **FR-009**: 系统 MUST 在 CRM 接口返回 401 时，自动重新换 JWT 并重试一次
- **FR-010**: 系统 MUST 不阻塞主流程：关联失败不影响标讯创建/更新本身

### Key Entities

- **Tender**: 标讯实体，关键字段 `external_id`（格式 `{sourceSystem}:{sourceId}`）、`crm_opportunity_id`（商机 CC 编号）、`crm_opportunity_name`、`source_type`、`project_manager_id`
- **User**: 用户实体，关键字段 `username`（OSS 工号）、`crm_sales_no`（CRM 工号，与 OSS 工号相同）、`full_name`（姓名，作为 nickName）
- **CRM 商机**: 外部实体，字段 `id`（商机主键）、`code`（CC 编号）、`name`（商机名称）、`bidId`（关联标讯 ID）、`projectLeaderNo`（项目负责人工号）

## Success Criteria

### Measurable Outcomes

- **SC-001**: CRM 推送的新标讯在创建后 5 秒内完成商机关联（`crm_opportunity_id` 非 NULL）
- **SC-002**: PM 从未登录的情况下，CRM 推送的标讯关联成功率达到 99%（排除 CRM 侧商机不存在的情况）
- **SC-003**: 补偿任务运行后，历史未关联标讯（52/53/56）在 10 分钟内完成关联
- **SC-004**: OSS 用户同步后，`crm_sales_no` 字段填充率达到 100%（同步过的用户）
- **SC-005**: 关联失败时不阻塞标讯创建，主流程响应时间增加不超过 200ms
- **SC-006**: 补偿任务单次运行处理 100 条标讯耗时不超过 30 秒

## Assumptions

- **OSS 工号即 CRM salesNo**：已通过生产环境验证（王旭州 OSS 工号 04503 = CRM salesNo 04503）
- **`generateToken` 接口环境差异**：生产环境不校验 Authorization（2026-07-16 实测确认），测试环境要求 Authorization（401 "Full authentication is required"）。fallback 策略确保两种环境已登录用户都不受影响，只有测试环境 + 未登录用户会失败（CRM 配置问题，非代码问题）
- **CRM `page-list` 接口支持按 `bidId` 查询**：需在实现阶段确认接口是否支持此查询条件；若不支持，需改用其他反查路径（如按 `projectLeaderNo` 查全部再本地匹配）
- **OSS 同步事件携带工号**：已确认 `OrganizationUserSnapshot` 有 `username` 字段（OSS 工号）
- **补偿任务运行频率**：默认每 5 分钟一次，可配置
- **本系统不会删除 CRM 推送的标讯**：补偿任务只处理已存在的标讯，不涉及删除场景

## Out of Scope

- 修改 CRM 推送方 payload（不传商机编号的问题）——需客户方配合，不在本 spec 范围
- 评估表基础信息字段缺失（`planned_shortlisted_count` 等）——属于 CRM 推送方字段缺失，不在本 spec 范围
- `creator_name` / `department` 字段缺失——属于 CRM 推送路径的独立问题，不在本 spec 范围
- Redis token 与 OSS 平台状态不同步问题——需要 OSS 平台配合，不在本 spec 范围
