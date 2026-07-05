# Phase 0 Research: 修复平台账号密码查看权限异常类型误用

**Date**: 2026-07-05
**Status**: Complete — 无 NEEDS CLARIFICATION 项

## Research Tasks

本次修改无 unknowns，无需外部研究。所有决策基于项目已有代码与 Constitution v2.0.0。

## Decisions

### Decision 1: 异常类型选择 — `AccessDeniedException`

**Decision**: 使用 `org.springframework.security.access.AccessDeniedException`。

**Rationale**:
1. 项目已有 `GlobalExceptionHandler.handleAccessDeniedException` handler，返回 403 + WARN 级日志 + 不上报 Sentry，完全符合本次需求。
2. 项目已有同类权限校验范式使用 `AccessDeniedException`（`PlatformAccountViewerPolicy.checkCanManageAccount` L84、`checkCanCreateAccount` L110、`checkCanExportAccount` L136），保持一致性。
3. Spring Security 内置异常，语义最准确（"访问被拒绝"）。

**Alternatives considered**:
- `BusinessException(403, "...")`：项目自定义异常，需要 handler 透传 message。现有 `BusinessException` handler 不在本次修改范围，引入新依赖不必要。
- `AccessDeniedException` 的子类（如 Spring Security 的 `AuthorizationServiceException`）：过度细化，无收益。
- 自定义权限异常类：违反 YAGNI，已有 `AccessDeniedException` 完全够用。

### Decision 2: 不修改 `AccessDeniedException` handler 透传 message

**Decision**: 不修改 `GlobalExceptionHandler.handleAccessDeniedException`，仍返回固定 message "权限不足，无法访问该资源"。

**Rationale**:
1. 现有 handler 返回固定 message，不携带原异常 message。这是有意为之的安全设计——避免向前端泄露权限细节（如"必须是联系人"会暴露系统内部权限结构）。
2. 前端只要识别 403 状态码即可，不依赖 message 字段具体内容做分支。
3. 修改 handler 会扩大本次 PR 范围，违反"最小化原则"。

**Alternatives considered**:
- 修改 handler 透传 `ex.getMessage()`：会让所有 `AccessDeniedException` 调用点的 message 暴露给前端，需要单独评估每个调用点的 message 是否包含敏感信息。本次不做。

### Decision 3: `currentUser == null` 也归到 `AccessDeniedException`（403）而非 `AuthenticationException`（401）

**Decision**: 第 247 行 `if (currentUser == null) throw new IllegalStateException("Authentication required")` 改为 `throw new AccessDeniedException("Authentication required")`，返回 403 而非 401。

**Rationale**:
1. 严格语义上"未认证"应返回 401，但生产环境正常路径不应触发此分支（Spring Security filter chain 先拦截未认证请求）。
2. 此分支是防御性校验，作为兜底。即使返回 403 也是可接受的——前端只区分 4xx vs 5xx。
3. 引入 `AuthenticationException` 子类（如 `InsufficientAuthenticationException`）会让代码更复杂，无实际收益。
4. 三个校验点统一抛 `AccessDeniedException` 让代码更一致、更可读。

**Alternatives considered**:
- `InsufficientAuthenticationException`（Spring Security 内置 401 异常）：语义更准确，但与另外 2 处权限校验使用不同异常类型，破坏一致性。
- 自定义 `UnauthorizedException`：过度工程化。

### Decision 4: 不做权限校验下沉到 Policy 的对称化重构

**Decision**: 本次只在 `PlatformAccountService.getPassword` Service 层内联替换异常类型，不抽取 `PlatformAccountViewerPolicy.checkCanViewPassword` 方法。

**Rationale**:
1. spec 明确"本次不做权限校验下沉到 Policy 的对称化重构（仅在 Service 层替换异常类型），保持改动最小"。
2. 当前 `getPassword` 在 Service 层内联权限判定是设计偏差但不违反 Constitution。
3. 抽取 Policy 方法会涉及 `checkCanReturnAccount`（同样存在 ISE 问题）的同步修复，扩大 PR 范围。
4. 作为技术债单独处理更合规（可建立独立 spec 走 Spec Kit 流程）。

**技术债登记**:
- `PlatformAccountViewerPolicy.checkCanReturnAccount`（L50-55）仍使用 `IllegalStateException`，与 `checkCanManageAccount` 等其他方法不一致。Sentry 暂未上报（return 接口调用频率低），作为技术债单独处理。
- `getPassword` 权限校验未下沉到 Policy，与 `manageAccount`、`createAccount`、`exportAccount` 等已下沉方法不一致。建议后续建立 `checkCanViewPassword` 方法统一权限校验入口。

### Decision 5: 测试文件策略

**Decision**: 检查 `PlatformAccountServiceTest.java` 是否存在，若存在则补充测试用例，若不存在则新建。

**Rationale**:
1. 遵循 TDD 原则，先写测试覆盖 3 处权限校验失败路径，断言抛出 `AccessDeniedException`。
2. 测试用例覆盖：
   - `currentUser == null` → `AccessDeniedException`
   - `bid-Team` 角色非账号绑定联系人 → `AccessDeniedException`
   - 非特权非 `bid-Team` 角色 → `AccessDeniedException`
   - `admin` / `/bidAdmin` / `bid-TeamLeader` 特权角色 → 成功返回密码
   - `bid-Team` 角色且为账号绑定联系人 → 成功返回密码

## Summary

无 NEEDS CLARIFICATION 项，所有决策基于项目已有代码与 Constitution。可进入 Phase 1 设计。
