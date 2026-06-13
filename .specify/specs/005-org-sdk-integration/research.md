# Research: 西域对接 — 组织架构SDK接入

## Decision 1: Bearer Token 换取模式

**Chosen**: `applyToken` 接口动态换取，参考现有 `CrmAuthService` 模式。

**Rationale**: PDF §7.3 明确"调用西域组织架构接口"必须传递鉴权 header，头脑风暴确认 token 来源为动态换取而非固定 token。`CrmAuthService` 已有完整实现（applyToken → cache → auto-renew → cooldown），可直接复用同一架构。

**Alternatives considered**:
- 固定 Bearer token（不选：无 token 有效期管理，token 泄露风险高）
- OAuth2 client_credentials 流程（不选：西域接口为 applyToken 而非标准 OAuth2）

---

## Decision 2: SDK 接入方式

**Chosen**: 直接通过 `@AcceptEvent` 注解订阅，HTTP fallback 移除。

**Rationale**: PDF §4 提供了完整的 `@AcceptEvent` Java 示例，SDK Maven 坐标已明确（`com.ehsy.eventlibrary:ClientSDK:release_0.0.2`），无需维护双路径。

**Alternatives considered**:
- 保留 HTTP fallback 作为备选（不选：增加测试和维护成本；SDK jar 已可获取）
- 通过 Kafka consumer group 直连（不选：不使用 SDK 的事件路由和注册机制）

---

## Decision 3: Token Service 放置位置

**Chosen**: `integration/organization/application/OrganizationTokenService.java`

**Rationale**: Token 换取是外部 I/O 操作，属于应用编排层职责，不属于纯核心。放置在 `application` 包符合 FP-Java 约束。HTTP gateway 依赖该 service。

---

## Decision 4: HTTP Fallback 清理时机

**Chosen**: Task 3 中完整清理，不保留 dev profile。

**Rationale**: `OrganizationEventWebhookController` 的核心价值（HMAC 签名校验 + 幂等占位）已被 SDK 路径覆盖；保留 controller 会导致两套入口长期并行，增加测试路径。

---

## Pending Items (待西域确认)

| 项目 | 当前假设 | 需确认 |
|---|---|---|
| applyToken 接口路径 | `/auth/applyToken` | 是否正确？ |
| Bearer token header | `Authorization: Bearer {token}` | 还是有其他 header？ |
| YAPI base URL | `https://yapi.ehsy.com` | 生产环境域名？ |
| @AcceptEvent 包名 | `com.ehsy.eventlibrary.annotations`（推测） | SDK jar 到货后验证 |
| EventResult 基类 | `EventResult`（PDF 示例） | SDK jar 到货后验证 |
| Maven 私服地址 | 未获取 | 需西域提供 |
