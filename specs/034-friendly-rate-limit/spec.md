# Feature Specification: 限流提示友好化优化

**Feature Branch**: `agent/codex/friendly-rate-limit`

**Created**: 2026-07-11

**Status**: Draft

**Input**: 系统中很多页面总是显示"请求过于频繁 请稍后再试"，普通用户误以为是报错，体验不友好。需要分三层优化：文案层、前端交互层、后端协议层。

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 限流提示文案更友好 (Priority: P1)

当用户操作过快或页面请求过多触发后端限流时，系统应使用通俗、温和、可操作的文案，而不是让用户以为是系统报错的命令式技术提示。

**Why this priority**: 这是最直接的用户痛点。当前"请求过于频繁 请稍后再试"容易被理解为系统故障，导致用户反复刷新或投诉。

**Independent Test**: 在任意页面触发 429 响应后，弹出的提示文案应不含生硬命令，且能让普通用户理解"自己操作太快了，等一等就好"。

**Acceptance Scenarios**:

1. **Given** 用户在页面内短时间内发起大量请求触发 429，**When** 前端显示提示，**Then** 文案为"操作太快了，请稍等几秒再试"或类似的温和表达，而非"请求过于频繁，请稍后再试"。
2. **Given** 后端返回 429 且携带了剩余等待秒数，**When** 前端显示提示，**Then** 文案包含具体等待时间，例如"操作太快了，请等待 5 秒后再试"。
3. **Given** AI 解析标讯触发 429，**When** 前端显示提示，**Then** 文案保持业务语境，提示"AI 服务请求过于频繁，请稍后再试，当前可手动填写"。

---

### User Story 2 - 前端避免重复和打扰式提示 (Priority: P1)

同一类限流错误在短时间内不应连续弹出多个提示，避免用户被多个 toast 轰炸；轮询类请求触发限流时应静默退避，不打扰用户。

**Why this priority**: 很多页面同时发起多个请求，如果每个请求都弹一个 toast，会叠加成"满屏报错"的观感，进一步让用户觉得是系统崩溃。

**Independent Test**: 在触发 429 的场景下，无论同时有多少个请求失败，用户最多只看到一个提示；通知中心等轮询失败时不应弹任何提示。

**Acceptance Scenarios**:

1. **Given** 页面同时有 3 个 GET 请求在 1 秒内全部触发 429，**When** 响应返回，**Then** 页面只显示 1 个限流提示，而不是 3 个。
2. **Given** 通知中心轮询接口返回 429，**When** 轮询失败，**Then** 不弹出任何 toast，且轮询自动暂停 60 秒后恢复。
3. **Given** 用户连续点击提交按钮触发 429，**When** 第一次 429 已经提示后，**Then** 在冷却期内再次点击不再重复弹出相同提示。

---

### User Story 3 - 后端返回标准、可消费的限流信息 (Priority: P2)

后端在返回 429 时应携带标准或项目约定格式的错误信息，让前端能够显示中文友好文案、展示剩余等待时间，并为运维排查提供可追踪的上下文。

**Why this priority**: 当前 `RateLimitFilter` 返回的 429 响应体是 `{error: {message: "Too many requests..."}}`，前端取不到 `msg`，只能 fallback 到固定中文。统一响应格式后才能做文案层优化。

**Independent Test**: 任意 429 响应都应能被前端识别出"这是限流错误"，并能提取出剩余等待时间或窗口信息。

**Acceptance Scenarios**:

1. **Given** `RateLimitFilter` 触发 429，**When** 后端构造响应，**Then** 返回与项目统一错误格式一致的响应体（含 `code`、`msg`、`data` 或等效字段），且 `msg` 为中文友好文案。
2. **Given** `RateLimitFilter` 触发 429，**When** 后端设置响应头，**Then** 包含 `Retry-After` 头，值为剩余等待秒数。
3. **Given** 后端限流日志记录，**When** 运维排查，**Then** 日志中包含被限流的 key（用户名或 IP）和请求路径，便于定位高频来源。

---

### Edge Cases

- 如果 429 响应没有携带 `Retry-After`，前端应使用默认文案而不显示具体时间。
- 如果后端 Redis 不可用，`RateLimitFilter` fallback 到本地内存限流，此时返回的 429 仍应保持一致的响应格式。
- 登录接口被限流（15 分钟窗口）时，提示文案不应暴露账号是否存在等安全信息。
- 多个 tab/窗口同时操作时，同一用户可能在多个页面触发限流，提示应各自独立但不超过一个页面内合并。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 前端统一 429 错误提示文案，默认文案从"请求过于频繁，请稍后再试"优化为"操作太快了，请稍等几秒再试"。
- **FR-002**: 前端 429 提示应能根据后端返回的 `Retry-After` 或等效字段显示具体等待秒数。
- **FR-003**: 同一页面/会话在短时间内多次触发 429 时，前端最多只弹出 1 个限流提示，后续重复提示被合并或忽略。
- **FR-004**: 轮询类请求（如通知中心）返回 429 时，前端不弹 toast，并自动进入退避逻辑。
- **FR-005**: 后端 `RateLimitFilter` 返回的 429 响应应使用项目统一的 `ApiResponse` 格式（含 `code`、`msg`），`msg` 为中文友好文案。
- **FR-006**: 后端 `RateLimitFilter` 应在 429 响应中附加 `Retry-After` 头，值为当前窗口剩余秒数。
- **FR-007**: 后端 `RateLimitFilter` 应在日志中记录被限流的 key、请求路径、剩余窗口信息，便于排查。
- **FR-008**: AI provider 相关的 429 提示保持现有业务文案，但统一走前端 429 处理通道以便合并提示。

### Key Entities

- **RateLimitFilter**: 后端全局限流过滤器，负责识别和拦截超频请求。
- **ApiResponse**: 项目统一响应体，包含 `success`、`code`、`msg`、`data`。
- **httpClient**: 前端 Axios 实例，统一处理所有 API 响应和错误提示。
- **RateLimitMessageResolver**: 纯核心规则组件，负责把后端限流元数据映射为前端可读文案（与 HTTP/框架解耦）。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 用户触发 429 后，提示文案的平均可读性评分（抽样 5 名非技术用户）从当前的 2/5 提升到 4/5 以上。
- **SC-002**: 单次页面加载过程中，用户看到的 429 提示数量从"与失败请求数相同"收敛到最多 1 个。
- **SC-003**: 通知中心等轮询接口触发 429 时，用户零感知（无 toast、无页面抖动）。
- **SC-004**: 后端 429 响应 100% 使用项目统一格式，且携带 `Retry-After` 头。
- **SC-005**: 新增相关单元测试覆盖率不低于 80%，且 `RateLimitFilterTest` 与 `client.js` 相关测试全绿。

## Assumptions

- 当前限流阈值（GET 60 秒 100 次、登录 15 分钟 5 次）保持不变；本次优化只改善提示和协议，不放宽安全策略。
- 前端 toast 组件使用 Element Plus `ElMessage`，文案替换为全局常量即可。
- 后端 `RateLimitFilter` 中 `X-RateLimit-Reset` 已经计算了窗口结束时间戳，可基于它推导 `Retry-After`。
- 用户提到的"很多页面"主要对应 GET `/api/**` 全局限流，登录/账号类限流为辅。
- **AI 差异化文案归属**：AI 服务（如标讯解析）的 429 限流文案由 AI 服务层自行返回 `data.msg`，`RateLimitFilter` 不识别 AI 路径也不做差异化处理。前端 `rate-limit-message-resolver` 优先使用 `data.msg`，因此 AI 特定文案能自然透传。`RateLimitResponseFactory` 只负责通用限流文案。
