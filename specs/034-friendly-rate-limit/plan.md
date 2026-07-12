# Implementation Plan: 限流提示友好化优化

**Branch**: `agent/codex/friendly-rate-limit` | **Date**: 2026-07-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/034-friendly-rate-limit/spec.md`

## Summary

本次任务优化系统中因后端 429 限流返回的提示对普通用户不友好的问题。核心改动分为三层：

1. **文案层**：前端统一 429 提示文案，从命令式技术语言改为温和、可操作的表达，并支持根据 `Retry-After` 显示具体等待秒数。
2. **前端交互层**：合并同一短时间窗口内的重复 429 toast；轮询类请求触发 429 时静默退避，不打扰用户。
3. **后端协议层**：`RateLimitFilter` 返回项目统一 `ApiResponse` 格式（含中文 `msg`），并附加标准 `Retry-After` 响应头，同时记录可观测日志。

本次改动不调整限流阈值，不涉及权限模型、数据模型或外部接口契约变更。

## Technical Context

**Language/Version**: Vue 3 + Vite 5 + Element Plus | Java 21 + Spring Boot 3.2

**Primary Dependencies**: Axios (frontend), Spring Web / Servlet Filter / Redis (backend)

**Storage**: N/A

**Testing**: Jest/Vitest (frontend), JUnit 5 + Mockito (backend)

**Target Platform**: Web application (Chrome/Edge/Firefox/Safari)

**Project Type**: Web application with separate frontend and backend

**Performance Goals**: 限流提示合并逻辑不得增加单次请求处理延迟；后端 429 响应构造保持 O(1)

**Constraints**: 单文件硬上限 300 行；后端核心规则必须为纯函数、无框架依赖；不得修改限流阈值

**Scale/Scope**: 影响全局 API 错误处理和 `RateLimitFilter`，覆盖所有 GET `/api/**`、登录及账号类敏感端点

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. FP-Java Architecture | ✅ 合规 | 文案映射规则抽离为纯核心 `RateLimitMessageResolver`，HTTP/框架逻辑留在拦截器/Filter 外壳 |
| II. Real-API Only | ✅ 合规 | 仅优化真实 API 错误提示，不引入 Mock |
| III. Test-Driven Development | ✅ 合规 | 前后端均先写/更新测试再实现 |
| IV. Split-First & Simplicity | ✅ 合规 | 新增纯核心文件 <100 行；Filter/拦截器只做编排 |
| V. OSS Integration | N/A | 不涉及 OSS 集成 |
| VI. Authorization Unification | N/A | 不涉及权限模型 |
| VII. Defensive Collection & Graceful Degradation | ✅ 合规 | 限流响应构造失败时 fallback 到通用格式，不影响主流程 |
| VIII. Boring Proven Patterns | ✅ 合规 | 使用标准 `Retry-After` 头和节流/防抖成熟模式 |

**Re-check after design**: 无新增违规。

## Project Structure

### Documentation (this feature)

```text
specs/034-friendly-rate-limit/
├── plan.md              # This file
├── spec.md              # Feature specification
├── checklists/
│   └── requirements.md  # Spec quality checklist
├── research.md          # Phase 0 output (if needed)
├── data-model.md        # N/A for this feature
├── quickstart.md        # N/A for this feature
├── contracts/           # N/A for this feature
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
src/
├── api/
│   ├── client.js                    # 统一响应拦截器（429 处理入口）
│   └── rate-limit-message-resolver.js   # NEW: 纯核心文案解析
└── composables/
    └── useNotifications.js          # 轮询 429 退避逻辑（已存在，需 review）

backend/src/main/java/com/xiyu/bid/
├── config/
│   ├── RateLimitFilter.java         # MODIFIED: 统一响应格式 + Retry-After + 日志
│   └── RateLimitConfig.java         # EXISTING: RateLimiter（Redis + 本地 fallback）
├── dto/
│   └── ApiResponse.java             # EXISTING: 统一响应体
└── exception/
    ├── ExceptionResponseStrategy.java   # EXISTED（必要时使用）
    └── RateLimitResponseFactory.java    # NEW: 纯核心 429 响应构造

backend/src/test/java/com/xiyu/bid/
├── config/
│   └── RateLimitFilterTest.java     # MODIFIED: 覆盖新响应格式和响应头
└── exception/
    └── RateLimitResponseFactoryTest.java  # NEW: 纯核心规则测试

src/api/__tests__/ 或 src/api/*.spec.js  # MODIFIED/NEW: client.js 429 处理测试
```

**Structure Decision**: 采用前后端分离改造。后端新增纯核心 `RateLimitResponseFactory` 负责把限流元数据映射为 `ApiResponse`，`RateLimitFilter` 只做编排；前端新增纯核心 `rate-limit-message-resolver.js` 负责把后端响应映射为用户文案，`client.js` 只做展示调用。

## Implementation Design

### 后端：RateLimitFilter 与响应协议

**当前问题**:
- `RateLimitFilter.applyRateLimit` 被限流时返回 `{"error":{"code":"rate_limit_exceeded","message":"Too many requests..."}}`
- 前端 `response.data?.msg` 取不到值，只能 fallback 到固定中文
- 无 `Retry-After` 头

**改动方案**:
1. 在 `RateLimitFilter` 中计算 `retryAfterSeconds = max(1, window.getSeconds() - elapsed)`
2. 调用新增的 `RateLimitResponseFactory.build(rateLimitKey, retryAfterSeconds)` 生成 `ApiResponse<Void>`
3. 设置响应状态 429、响应头 `Retry-After`、`Content-Type: application/json`
4. 写入 JSON 响应体
5. 日志记录：`log.warn("Rate limit exceeded: key={}, path={}, retryAfter={}s", key, requestURI, retryAfterSeconds)`

**纯核心拆分**:
- `RateLimitResponseFactory` 只负责"限流元数据 → ApiResponse"的映射，无 Spring、无 Servlet
- `RateLimitFilter` 负责 HTTP 协议、响应头、日志，调用工厂

### 前端：文案解析与提示合并

**当前问题**:
- `client.js` 中所有 429 统一显示 `ElMessage.warning(serverMsg || '请求过于频繁，请稍后再试')`
- 多个并发 429 会弹出多个 toast
- 轮询类请求（如通知中心）也会弹 toast

**改动方案**:
1. 新增 `rate-limit-message-resolver.js` 纯核心模块：
   - 输入：HTTP status、response.data（含 `msg`、`code`）、响应头 `Retry-After`
   - 输出：{ message, waitSeconds, isRateLimit }
2. 在 `client.js` 中：
   - 429 时调用 resolver 得到文案
   - 使用一个 module-level 的限流提示冷却期（如 3 秒），同一窗口内不重复弹 toast
   - 对 `config?.silentRateLimit` 标记的请求（轮询）跳过 toast
3. 保留 AI 解析等业务的特殊文案：resolver 优先使用后端返回的 `msg`，业务层仍可在捕获后覆盖

### 测试策略

- **后端**:
  - `RateLimitFilterTest`: 验证 429 响应体格式、`Retry-After` 头、日志输出
  - `RateLimitResponseFactoryTest`: 验证纯核心映射规则
- **前端**:
  - `rate-limit-message-resolver.spec.js`: 覆盖有/无 `Retry-After`、有/无 `msg`、AI 文案等场景
  - `client.spec.js`: 模拟多个并发 429，验证只弹一次 toast；验证 `silentRateLimit` 不弹 toast

## Complexity Tracking

> 无 Constitution 违规，无需填写 Complexity Tracking 表。
