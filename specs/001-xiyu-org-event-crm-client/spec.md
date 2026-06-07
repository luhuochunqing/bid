# Feature Specification: 西域事件库 SDK 订阅与 CRM 出向客户端

**Feature Branch**: `agent/claude-add-xiyu-org-event-sdk-and-crm-client`

**Created**: 2026-05-16

**Status**: Draft

**Input**: User description: "西域事件库 SDK 订阅与 CRM 出向客户端基线规范 — 为投标系统与西域 backend 的两块对接补齐基线：organization-event-sync（SDK 主链订阅 + HTTP 灾备中转 + 全量初始化 + 日常对账）和 crm-customer-client（Token 缓存续约 + 7 个出向接口 + 重试/脱敏/可观测）"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 组织架构事件自动同步 (Priority: P1)

西域平台发生组织架构变更（部门新增/修改、用户入职/离职/调岗）时，投标系统自动感知并同步变更，确保项目成员、审批链、数据权限基于最新组织架构运行。

**Why this priority**: 组织架构是投标系统权限模型和审批流的基础数据。同步延迟或遗漏会导致标书分配给错误的人、审批流断裂、数据越权访问，直接影响业务正确性和合规性。

**Independent Test**: 在西域平台修改一个部门名称，投标系统在 5 分钟内完成同步，可在后台查看最新部门名称。

**Acceptance Scenarios**:

1. **Given** 西域平台新增一个部门，**When** SDK 推送 BaseOssDept 事件，**Then** 投标系统消费事件、回查部门接口获取完整数据、写入本地组织架构表，日志记录 traceId/spanId/eventTopic
2. **Given** 西域平台修改用户信息（如手机号），**When** SDK 推送 BaseOssUser 事件，**Then** 投标系统消费事件、回查用户接口、更新本地用户记录
3. **Given** SDK 主链不可用（jar 未交付或连接断开），**When** 西域平台发生组织架构变更，**Then** HTTP 中转接口接收事件、写入同一个 inbox 表、后续处理流程与 SDK 主链一致
4. **Given** 同一事件（相同 traceId+spanId+eventTopic）已成功处理，**When** 系统再次收到该事件（重放/重试），**Then** 事件层幂等生效，跳过重复处理，不产生重复数据
5. **Given** 某部门/用户被删除，**When** SDK 推送删除事件，**Then** 投标系统标记接口失效（不物理删除记录），保留引用完整性

---

### User Story 2 - 上线前全量初始化与日常对账 (Priority: P1)

系统首次上线时，批量导入西域现有全部组织架构数据作为基线；上线后定期对账，发现增量/删除/变更差异并自动修复。

**Why this priority**: 没有初始化就没有基线数据，事件驱动增量同步无从开始。没有对账就无法发现长期静默累积的数据不一致，风险与 P1 事件同步同等。

**Independent Test**: 执行初始化任务后，本地部门/用户数量与西域平台一致（允许接口分页差异）。执行对账任务后，报告列出所有差异项及修复状态。

**Acceptance Scenarios**:

1. **Given** 投标系统首次上线，**When** 执行全量初始化任务，**Then** 遍历西域组织架构接口逐页拉取所有部门/用户，写入本地表，标记初始化完成
2. **Given** 初始化已执行完毕，**When** 再次执行初始化任务，**Then** 跳过已存在的记录（幂等），仅补充缺失数据
3. **Given** 系统已运行一段时间，**When** 执行日常对账任务（最近 N 天时间窗），**Then** 对比本地与西域数据，发现差异按类型（新增/变更/删除）分别处理，输出对账报告
4. **Given** 对账发现某用户已在西域删除但本地仍有效，**When** 对账任务处理该差异，**Then** 标记本地用户接口失效状态

---

### User Story 3 - CRM Token 管理与客户查询 (Priority: P2)

投标系统通过 CRM 出向接口查询客户信息时，自动管理访问 Token 的获取、缓存和续约，确保业务用户无需感知认证细节即可完成客户搜索和关联。

**Why this priority**: 客户信息是标书编制的基础数据（采购单位、联系方式等），但依赖 Token 才能访问 CRM 接口。没有 Token 管理则所有 CRM 功能无法使用。

**Independent Test**: 调用客户模糊查询接口，系统自动获取 Token 并缓存，后续查询复用 Token，Token 过期后自动续约，全程对调用方透明。

**Acceptance Scenarios**:

1. **Given** 系统首次调用 CRM 接口，**When** Token 缓存为空，**Then** 自动调用 applyToken 获取新 Token，缓存到内存，设置 TTL
2. **Given** Token 缓存有效且未过期，**When** 再次调用 CRM 接口，**Then** 复用缓存 Token，不重新申请
3. **Given** 单个 Token 正在获取中，**When** 其他并发请求同时到达，**Then** 等待首个请求完成（单飞），共用同一个结果，不发起多次 applyToken
4. **Given** CRM 返回 401 未授权，**When** 系统检测到 401，**Then** 强制清除当前缓存 Token，重新申请，重试原请求一次
5. **Given** Token 即将过期（TTL 剩余 < 10%），**When** 下一次请求到达，**Then** 触发自动续约，获取新 Token 替换旧 Token
6. **Given** 服务正常下线，**When** 执行 logout，**Then** 清除缓存 Token，释放资源

---

### User Story 4 - CRM 业务接口调用（客户/菜单/员工/消息）(Priority: P2)

业务用户通过投标系统执行客户模糊搜索、查看客户负责人、浏览菜单树、查询员工信息、发送企微或站内消息等操作。

**Why this priority**: 这些是 CRM 出向接口的核心业务价值。P2 因为可与 Token 管理（US3）并行开发，但 US3 是前置依赖。

**Independent Test**: 在客户选择器中输入"西域"，返回前 20 条匹配客户（短名靠前）。选择客户后查询负责人列表。发送一条企微消息给指定员工。

**Acceptance Scenarios**:

1. **Given** 用户在标书编制页面的客户选择器，**When** 输入"西域"进行模糊查询，**Then** 返回前 20 条匹配客户，短名称匹配的排在前面
2. **Given** 已选定目标客户，**When** 查询该客户的负责人，**Then** 返回负责人列表（含姓名、联系方式）
3. **Given** 用户需要查看 CRM 菜单结构，**When** 按系统类型查询菜单树，**Then** 返回完整菜单树，支持缓存以减少重复请求
4. **Given** 已知目标员工 Token，**When** 查询该员工详细信息，**Then** 返回员工姓名、部门、联系方式
5. **Given** 需要通知相关人员，**When** 调用发送消息接口（企微或站内），**Then** 单条消息直接发送，批量消息自动拆分后逐批发送，返回逐条结果

---

### User Story 5 - 安全合规与可观测性 (Priority: P3)

所有与西域的通信确保安全（密钥不外泄、传输加密、敏感字段脱敏），并提供充分的监控指标和日志，支持运维排查和告警。

**Why this priority**: 安全和可观测性是生产就绪的必要条件。P3 因为可在核心功能验证通过后集中补齐，但必须在正式上线前完成。

**Independent Test**: 查看日志，确认 Token、手机号、邮箱等敏感字段已脱敏。查看监控面板，确认有请求量、延迟、错误率、重试次数等指标。

**Acceptance Scenarios**:

1. **Given** 日志输出包含 CRM Token 或用户手机号，**When** 查看日志，**Then** Token 仅显示前后各 4 位（中间脱敏），手机号中间 4 位脱敏，邮箱仅显示域名
2. **Given** 所有与西域的通信，**When** 发起请求，**Then** 强制使用 HTTPS，不发起明文 HTTP 请求
3. **Given** 生产环境部署，**When** 检查配置，**Then** ClientSDK appId/secret 和 CRM clientId/clientSecret 均从环境变量或配置中心获取，未硬编码或入库
4. **Given** 系统运行中，**When** 查看 Micrometer 指标，**Then** 包含：事件消费成功率/延迟、HTTP 灾备请求量、CRM Token 获取成功率/延迟、各接口调用量/错误率/重试次数
5. **Given** CRM 接口出现大量 5xx 错误，**When** 持续超过阈值，**Then** 触发告警通知运维人员

---

### Edge Cases

- SDK 推送事件乱序到达时（先收到 user update 再收到 user create），通过回查接口校验实体存在性，不存在则暂存待后续重试
- HTTP 灾备接口收到格式不合法的请求体时，返回明确错误码并记录原始 payload 日志
- 全量初始化过程中西域接口超时或分页中断时，记录断点并从断点继续，支持断点续传
- CRM Token 申请连续失败 3 次时，进入冷却期（不再尝试），冷却期过后重新尝试
- 批量发送消息时部分成功部分失败，返回每条的发送结果，失败条目不阻塞成功条目
- 对账时发现大量差异（超过阈值），触发告警而非自动修复，防止批量错误数据污染

## Requirements *(mandatory)*

### Functional Requirements — 组织架构事件同步

- **FR-001**: 系统 MUST 通过 ClientSDK `@AcceptEvent` 机制订阅 BaseOssDept 和 BaseOssUser 事件，事件到达时写入 inbox 表。
- **FR-002**: 系统 MUST 提供 HTTP 中转灾备接口接收事件，与 SDK 主链共享同一个 inbox 表和处理逻辑。SDK 是主链路，HTTP 在 SDK jar 未交付或主链故障时作为灾备。
- **FR-003**: 系统 MUST 实现事件层幂等：以 (traceId, spanId, eventTopic) 三元组去重，已成功处理的事件不再重复处理。
- **FR-004**: 系统 MUST 将事件 data 仅作为触发器使用——收到事件后必须回查西域组织架构接口获取最新主数据，不得直接使用事件 payload 中的 data 覆盖本地记录。
- **FR-005**: 系统 MUST 实现业务主键 upsert（deptId / userId），确保本地数据与西域保持一致。
- **FR-006**: 当组织架构实体在西域被删除时，系统 MUST 标记本地记录为接口失效状态，不得物理删除。
- **FR-007**: 系统 MUST 支持全量初始化任务，逐页拉取西域组织架构接口，批量写入本地，支持幂等重跑和断点续传。
- **FR-008**: 系统 MUST 支持日常对账任务（可按最近 N 天时间窗配置），对比本地与西域数据，输出差异报告并自动修复。
- **FR-009**: 初始化/增量同步/对账三条处理路径 MUST 分离为独立用例，不得混合在同一代码路径中。
- **FR-010**: 系统 MUST 对同步失败的事件实施退避重试（指数退避 + 最大重试次数），超时时间可配置。
- **FR-011**: 系统 MUST 输出结构化日志（包含 traceId、eventTopic、处理结果）并提供 Micrometer 指标（消费成功率/延迟、灾备请求量）。

### Functional Requirements — CRM 出向客户端

- **FR-012**: 系统 MUST 实现 Token 生命周期管理：applyToken 获取 → 内存缓存（含 TTL）→ 自动续约 → logout 清理。
- **FR-013**: 系统 MUST 对并发的 Token 申请实施单飞（single flight）：同一时刻仅允许一个 applyToken 请求，其他请求等待并复用结果。
- **FR-014**: 系统 MUST 在收到 401 响应时强制清除缓存 Token、重新申请 Token、重试原请求一次。
- **FR-015**: 系统 MUST 实现客户模糊查询接口（返回前 20 条，短名称匹配优先），支持按中文名称或简称搜索。
- **FR-016**: 系统 MUST 实现客户负责人批量查询接口，输入客户 ID 列表，返回各客户的负责人列表。
- **FR-017**: 系统 MUST 实现菜单树查询接口（按系统类型过滤），结果可缓存以减少重复请求。
- **FR-018**: 系统 MUST 实现员工信息查询接口（按 Token），返回姓名、部门、联系方式。
- **FR-019**: 系统 MUST 实现发送消息接口（企微 + 站内），单条直接发送，批量自动按单次上限拆分后逐批发送，返回逐条结果。
- **FR-020**: 系统 MUST 对所有 CRM 出向请求使用统一请求契约：HTTPS / Bearer Token 鉴权 / 标准响应结构 (code-msg-data-success)。
- **FR-021**: 系统 MUST 实施重试策略：5xx 退避重试（最多 3 次，指数退避），4xx 和业务失败 (code != 0) 不重试。
- **FR-022**: 系统 MUST 对敏感字段实施脱敏：日志中的 Token (仅首尾各 4 位)、手机号 (中间 4 位)、邮箱 (仅域名可见)。
- **FR-023**: 所有密钥（ClientSDK appId/secret、CRM clientId/clientSecret）MUST 通过环境变量或配置中心注入，不得硬编码或存入数据库。

### Key Entities

- **OrganizationEventInbox**: 事件收件箱，存储接收的原始事件，包含 traceId、spanId、eventTopic、payload、处理状态、重试次数
- **LocalDepartment / LocalUser**: 本地缓存的西域组织架构数据副本，通过事件驱动增量 + 对账保持与西域一致
- **CrmToken**: Token 内存缓存对象，包含 accessToken、TTL、获取时间、过期时间
- **CrmCustomer**: 客户信息（从 CRM 查询获取），包含客户 ID、名称、简称、状态
- **CrmMenuNode**: 菜单树节点，包含节点 ID、名称、父节点、系统类型、排序

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 组织架构事件从推送到投标系统完成同步的端到端延迟不超过 5 分钟（P95）
- **SC-002**: 事件幂等机制确保重复事件处理成功率达到 100%（无重复数据写入）
- **SC-003**: 全量初始化任务在 10 万条部门+用户数据规模下 30 分钟内完成
- **SC-004**: 日常对账任务在 1 万条差异数据规模下 10 分钟内完成并输出报告
- **SC-005**: CRM Token 获取成功率达到 99.9%，缓存命中率超过 95%
- **SC-006**: 客户模糊查询在 1 秒内返回结果（P95）
- **SC-007**: 5xx 重试机制在 3 次内恢复成功率达到 90%
- **SC-008**: 生产日志中 0 条未脱敏的 Token、手机号或邮箱记录
- **SC-009**: 所有密钥 100% 来自环境变量或配置中心，代码仓库和数据库中 0 条硬编码或存储的密钥

## Assumptions

- 西域 ClientSDK jar 由客户侧交付，交付方式和版本管理待确认（Nexus 私服或离线 jar）
- 西域 CRM applyToken 的 TTL 由上游下发，客户端被动接受（不自行设置固定值）
- 多 agent worktree 环境是否共用 consumerGroup 待客户侧确认
- HTTP 灾备接口由西域平台主动调用（push 模式），投标系统不主动拉取
- 消息发送单次批量上限由 CRM 接口文档定义（默认假设 100 条/批）
- 对账时间窗 N 的默认值为 7 天，可通过配置文件调整
- 超时和重试配置提供合理默认值（连接超时 5s，读取超时 30s，最大重试 3 次），均可通过配置覆盖
