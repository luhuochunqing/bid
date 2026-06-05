# Research Notes: 西域事件库 SDK 订阅与 CRM 出向客户端

**Created**: 2026-05-16

## R1: ClientSDK 集成模式

**Decision**: 定义 `ClientSdkAdapter` 接口，实现类按 `@ConditionalOnClass` 条件装配。SDK jar 可用时自动启用 SDK 订阅路径；jar 不可用时回退到 HTTP-only 模式。

**Rationale**: SDK jar 由客户交付，交付时间不确定。Spring Boot 条件装配允许同一套代码在有无 SDK 的环境下运行，不需维护分支。

**Alternatives considered**:
- Maven profile 切换 → 拒绝：需要改 pom.xml，分支管理复杂
- 运行时检测 → 过于间接，Spring 条件装配更标准化

## R2: HTTP 灾备接口设计

**Decision**: 提供 `POST /api/xiyu/org-events/fallback` 端点，接收与 SDK 事件相同结构的 JSON body。Controller 层仅验证签名和格式，立即写入 inbox 表后返回 202。异步处理由后台 scheduler 拉取 inbox 执行。

**Rationale**: 快速 ACK 避免西域平台超时；异步处理与 SDK 路径共享 inbox → processor 链路。

**Alternatives considered**:
- 同步处理 → 拒绝：长时间阻塞可能触发上游超时
- 独立表 → 拒绝：FR-003 要求共用 inbox

## R3: 事件幂等策略

**Decision**: 两层幂等：(1) inbox 表 `(trace_id, span_id, event_topic)` 复合唯一索引防重写；(2) Redis `SET NX EX <ttl>` 快速去重（TTL=24h），Redis 命中则跳过 DB 写入。

**Rationale**: 数据库唯一索引是终极防线；Redis 减少 DB 争用。24h TTL 覆盖事件重放窗口，且 Redis 不可用时 fallback 到 DB 索引。

**Alternatives considered**:
- 仅 DB 唯一索引 → 可行但不必要地增加 DB 写冲突
- Redis-only → 拒绝：不持久化，重启丢失去重状态

## R4: CRM Token 内存缓存设计

**Decision**: 使用 `ConcurrentHashMap` + `ReentrantLock` 实现单飞。TTL 由 applyToken 响应中的 `expires_in` 字段驱动，提前续约阈值 = TTL * 0.1（最后 10% 窗口期）。支持手动 `/logout` 触发清理。

**Rationale**: 单实例内存缓存足够，不引入 Redis 依赖（D5 决策已明确）。单飞避免并发 applyToken 导致的限流或重复 token 问题。

**Alternatives considered**:
- Caffeine cache → 过度设计，Token 只有单条不需要淘汰策略
- Redis → 拒绝：D5 决策明确不要 Redis 依赖

## R5: CRM HTTP 客户端选择

**Decision**: 使用 Spring `RestTemplate` + `HttpComponentsClientHttpRequestFactory` 配置连接池（max 50 conn, 30s timeout）。重试通过 `RetryTemplate` 实现。不引入 WebClient 或第三方 HTTP 库。

**Rationale**: RestTemplate 是 Spring Boot 默认同步客户端，本项目不需要响应式。连接池控制 + 超时配置满足所有 7 个接口需求。

**Alternatives considered**:
- WebClient → 拒绝：引入响应式依赖，与项目架构不一致
- OkHttp → 可行但增加一个第三方依赖

## R6: 对账时间窗实现

**Decision**: 对账使用时间戳分页拉取西域变更日志（如接口支持），否则全量对比。本地侧按 `updated_at` 过滤最近 N 天记录，分批对比。差异数量 > 阈值（可配，默认 1000）触发告警，不自动修复。

**Rationale**: 时间窗限制减少对比量；大量差异时人工介入防止自动修复导致数据污染。

**Alternatives considered**:
- 全量对比 → 拒绝：10 万级别实体每次全量对比过于浪费
- 仅依赖事件 → 拒绝：文档第 8 节要求对账独立于增量同步

## R7: 敏感字段脱敏

**Decision**: 实现 `SensitiveDataMasker` 工具类，Logback `%replace` + 自定义 Converter 双重覆盖：(1) 日志框架层面通过正则替换；(2) 代码层面 `toString()` 方法不输出敏感字段。

**Rationale**: 日志框架层面提供最后防线，即使代码遗漏也可兜底。双重覆盖应对不同日志输出路径。

**Alternatives considered**:
- 仅代码层面 → 有遗漏风险
- Logback 自定义 layout → 维护成本高，性能影响大
