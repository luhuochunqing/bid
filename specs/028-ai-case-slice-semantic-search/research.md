# Research: AI 案例切片语义检索

## Decision: Embedding 客户端实现模式

**Decision**: 扩展 `AiProvider` 接口，新增 `embed(String text)` 方法，由 `RoutingAiProvider` 路由到 `OpenAiCompatibleClient`，统一走 OpenAI 兼容的 `/embeddings` 端点。

**Rationale**:
- 项目已有 `RoutingAiProvider` 作为 AI 调用的统一入口，业务代码（如 `CaseAiMatcher`）已通过 `resolveActiveConfig()` 借道调用。
- 全仓当前零 embedding 调用，需要从零扩展但应复用现有认证、重试、错误处理基础设施。
- `OpenAiCompatibleClient` 已有 RetryTemplate 和 `ExternalServiceException` 处理，复用可降低风险。
- `AiProviderRuntimeConfig` 当前 `baseUrl` 已带 `/chat/completions` 后缀，因此需要新增 `embeddingBaseUrl` 和 `embeddingModel` 字段，避免与 chat 端点冲突。

**Alternatives considered**:
- 业务模块直接调用 embedding API（如 `CaseAiMatcher` 自己拼 RestTemplate）：与现有统一入口背道而驰，增加重复代码。
- 引入 Spring AI / LangChain4j：增加新依赖，与项目"最小依赖"约束冲突。

---

## Decision: 向量存储与召回方案

**Decision**: MySQL 8.0 存储向量（`MEDIUMBLOB` 列），应用启动时加载全部 embedding 到内存 `ConcurrentHashMap`，推荐时全内存计算余弦相似度。

**Rationale**:
- 项目当前技术栈是 MySQL 8.0 + Redis（普通 Redis），无向量数据库。
- 切片规模 8144 条 × 1024 维 × 4 字节 ≈ 33MB，内存加载完全可行。
- 内存召回延迟 < 50ms，满足 SC-001 的 5 秒目标。
- 避免引入 pgvector/Milvus 等独立组件，符合 Constitution "Boring Proven Patterns" 原则。
- 未来规模增长后可平滑迁移：抽象 `EmbeddingSearchPort` 接口，届时加 Redis Stack / Milvus adapter 即可。

**Alternatives considered**:
- pgvector（PostgreSQL）：需要切换数据库，与项目 MySQL 8.0 硬约束冲突。
- Redis Stack RediSearch：需要升级 Redis 镜像，运维成本高于当前方案。
- MySQL 8.4 VECTOR 类型：项目当前用 MySQL 8.0，升级数据库版本风险大。

---

## Decision: 切片表与现有 knowledge_case 的关系

**Decision**: 新建独立表 `bid_case_slice`，不与 `knowledge_case` 合并。

**Rationale**:
- `knowledge_case` 的语义是"评分项 → 应答片段"的配对，有 `scoring_point_title` / `requirement_raw` / `response_text` 等 NOT NULL 字段。
- 当前 8144 条切片只有"章节标题 + 正文预览"，没有对应的评分项，硬塞进 `knowledge_case` 会导致必填字段无法填充。
- 独立表更清晰地表达"历史投标文件参考素材库"这一概念，未来可扩展为真正的案例沉淀输入源。

**Alternatives considered**:
- 旁挂 `knowledge_case_embedding` 表：仍需把切片伪装成 knowledge_case，字段语义不匹配。
- 直接加 `embedding` 列到 `knowledge_case`：污染原表且无法满足 NOT NULL 约束。

---

## Decision: 召回 + 精排架构

**Decision**: 两阶段架构：向量余弦召回 Top-50 → `BidCaseSliceMatchPolicy` 精排 → Top-20。

**Rationale**:
- 纯向量召回会丢失业务规则（如文件类别一致、章节层级、正文充实度）。
- 精排策略作为纯核心（Pure Core）独立存在，不依赖 Spring，可单独测试。
- 与现有 `KnowledgeCaseMatchPolicy` 平行但独立，因为切片没有 `bid_result` / `customer_type` 等字段，不能复用原策略。

**精排权重设计**:
- 余弦相似度：40 分
- 标题 Jaccard 相似度：25 分
- 文件类别一致：15 分
- 正文充实度（段落数）：10 分
- 章节层级加权（L1/L2 优先）：10 分

---

## Decision: 权限校验

**Decision**: 推荐接口复用现有 `ProjectAccessScopeService.assertCurrentUserCanAccessProject(projectId)`。

**Rationale**:
- `KnowledgeCaseController` 已使用该服务做项目访问断言，行为一致。
- 新增接口涉及 `projectId`，符合 Constitution "Project Access Guard" 要求。
- 按当前项目约定，案例推荐本身没有单独的业务权限键，使用 `isAuthenticated()` + 项目访问断言即可。

---

## Decision: Embedding 文本源

**Decision**: 切片 embedding 输入为 `title + "\n" + text_preview`；查询 embedding 输入为 `scoreItemTitle + "\n" + scoreRuleText`。

**Rationale**:
- 只用标题语义信息不足，加入正文预览可保留应答内容特征。
- 评分项规则原文（`scoreRuleText`）当前在推荐中完全未被使用，是语义富矿。
- 输入长度控制在 embedding 模型上下文窗口内（text-embedding-v3 支持 8192 tokens，中文约 4000-6000 字，当前片段远小于此）。

---

## Decision: 批量向量化策略

**Decision**: 后台任务按批次处理（每批 100 条），限速 10 QPS，支持断点续跑、失败重试 3 次。

**Rationale**:
- 通义千问 embedding 接口通常有 QPS 限制，10 QPS 是安全起步值。
- 8144 条 × 100ms ≈ 14 分钟（不含重试），可接受。
- 断点续跑通过 `WHERE embedding IS NULL LIMIT 100` 实现，失败记录用 `embedding_model='FAILED'` 标记避免死循环。

---

## Open Questions Resolved

| 问题 | 决策 |
|---|---|
| Embedding 提供商 | 默认通义千问 text-embedding-v3；通过配置可切换 OpenAI / 豆包 |
| DeepSeek 是否可用 | 不可用，DeepSeek 无 embedding API |
| 向量存在哪 | MySQL `MEDIUMBLOB` + 应用内存缓存 |
| 是否改现有 `knowledge_case` | 不改，新建 `bid_case_slice` |
| 前端按钮是否改路径 | 新增 `/api/case-slices/recommend` 接口，前端后续可切换 |
| 历史字段缺失怎么办 | `bid_result` / `customer_type` 等允许为空 |
