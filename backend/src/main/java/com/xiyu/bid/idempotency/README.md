# Idempotency 模块 — 请求幂等

> 一旦我所属的文件夹有所变化，请更新我。

## 职责

为 controller 方法提供 **Stripe 风格 Idempotency-Key** 幂等保护：客户端在 HTTP 头里带 `Idempotency-Key: <uuid>`，同一 key 在 TTL 窗口内（默认 10 分钟）的相同请求只执行一次业务逻辑，后续请求直接复用首次响应。

## 主要类

| 文件 | 功能 |
|---|---|
| `Idempotent.java` | 方法注解；标注后即接入幂等保护，仅修改注解级别配置（`ttlSeconds`） |
| `IdempotencyStore.java` | 存储抽象；`find(key)` / `save(key, response, ttl)` |
| `RedisIdempotencyStore.java` | 生产实现，`@Primary @ConditionalOnBean(StringRedisTemplate.class)`；序列化 status + headers + body 为 JSON 存到 Redis |
| `InMemoryIdempotencyStore.java` | 兜底实现，进程内 ConcurrentHashMap；e2e profile 与无 Redis 环境使用 |
| `IdempotencyFilter.java` | `OncePerRequestFilter`（order=50），从 `Idempotency-Key` 头识别请求、定位带 `@Idempotent` 的 handler、实施缓存命中或落库逻辑；通过自定义 `BufferingRequestWrapper` 重新喂 body 防 input stream 消费 |

## Key 设计

`idem:{userId}:{HTTP_METHOD}:{path}:{header_key}` —— 含 userId 防跨用户串响应；含 path 防跨端点冲突。

## 行为契约

| 场景 | 状态码 |
|---|---|
| 首次请求成功 | 业务正常状态码（如 201） |
| 同 key + 同 body 重放 | 与首次完全一致（status + body） |
| 同 key + 不同 body | **422** + `{"success":false,"code":422,"message":"Idempotency-Key conflict..."}` |
| 无 `Idempotency-Key` 头 | 不走幂等流程，直接放行（保持向后兼容） |
| 业务返回 5xx 或 4xx | **不缓存**，下次同 key 重试可正常进入 controller |

## 何时加 `@Idempotent`

只给"会创建持久数据的 POST"加。GET / DELETE 等天然幂等的方法不需要。

## 测试

- `InMemoryIdempotencyStoreTest`
- `IdempotencyFilterTest`（含命中、422、错误不缓存、注解未标注路径直通）
